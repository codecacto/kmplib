package br.com.codecacto.kmplib.testing.vm

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.testing.net.FalhaDeRede
import br.com.codecacto.kmplib.testing.net.ServidorDeTeste
import br.com.codecacto.kmplib.testing.net.json
import br.com.codecacto.kmplib.testing.net.recusa
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class SalvarState(val salvando: Boolean = false, val salvo: Boolean = false, val erro: String? = null)
private sealed interface SalvarAction { data object Salvar : SalvarAction }
private sealed interface SalvarEffect { data object Salvo : SalvarEffect }

/**
 * O ViewModel-modelo de uma ação que GRAVA, no padrão da fábrica: guarda síncrona contra o duplo toque e, quando a
 * conexão cai depois do envio, confere no servidor antes de dizer "sem conexão". [guardaSincrona] = `false`
 * reproduz o defeito (confere o `salvando` mas só o liga dentro do `launch`) para provar que o [duploToque] o pega.
 */
private class SalvarViewModel(
    private val api: DomainApiClient,
    private val guardaSincrona: Boolean = true,
) : BaseViewModel<SalvarState, SalvarAction, SalvarEffect>(SalvarState()) {

    override fun onAction(action: SalvarAction) {
        when (action) {
            SalvarAction.Salvar -> salvar()
        }
    }

    private fun salvar() {
        if (currentState.salvando) return
        if (guardaSincrona) setState { copy(salvando = true, erro = null) }
        viewModelScope.launch {
            // O defeito: o `salvando` só liga quando o launch roda — o 2º toque já passou pela conferência.
            if (!guardaSincrona) setState { copy(salvando = true, erro = null) }
            when (val r = api.postJson("/v2/itens", """{"id":"i1"}""")) {
                is DomainResult.Success -> concluir()
                is DomainResult.Error -> if (r.code == DomainResult.OFFLINE_CODE && api.getJson("/v2/itens/i1") is DomainResult.Success) {
                    concluir()
                } else {
                    setState { copy(salvando = false, erro = r.serverMessage ?: r.message) }
                }
                is DomainResult.Quota -> setState { copy(salvando = false, erro = r.userMessage) }
            }
        }
    }

    private fun concluir() {
        setState { copy(salvando = false, salvo = true) }
        sendEffect(SalvarEffect.Salvo)
    }
}

class ViewModelTestKitTest {

    /** "Banco" do servidor de teste: POST grava, GET confere. */
    private fun servidorDeItens(): Pair<ServidorDeTeste, MutableSet<String>> {
        val banco = mutableSetOf<String>()
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, "/v2/itens") { banco += "i1"; json("""{"id":"i1"}""", HttpStatusCode.Created) }
        servidor.rota(HttpMethod.Get, "/v2/itens/{id}") { c ->
            if (c.parametros["id"] in banco) json("""{"id":"i1"}""") else recusa(HttpStatusCode.NotFound, "NAO_ENCONTRADO")
        }
        return servidor to banco
    }

    @Test
    fun `fluxo item_criar tela sucesso - salva e emite o efeito`() = runViewModelTest {
        val (servidor, banco) = servidorDeItens()
        val vm = SalvarViewModel(servidor.domainApiClient(dispatcher = dispatcher))
        val efeitos = efeitos(vm)

        vm.dispatch(SalvarAction.Salvar)

        assertEquals(SalvarEffect.Salvo, efeitos.awaitItem())
        assertTrue(vm.state.value.salvo)
        assertEquals(setOf("i1"), banco)
    }

    @Test
    fun `fluxo item_criar tela recusa - mostra a frase do servidor`() = runViewModelTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, "/v2/itens") { recusa(HttpStatusCode.Conflict, "DUPLICADO", "Já existe um item com esse nome.") }
        val vm = SalvarViewModel(servidor.domainApiClient(dispatcher = dispatcher))

        vm.dispatch(SalvarAction.Salvar)

        val estado = vm.state.ateQue { !it.salvando && it.erro != null }
        assertEquals("Já existe um item com esse nome.", estado.erro)
    }

    @Test
    fun `fluxo item_criar tela conexao-caiu - gravou e a resposta se perdeu - confere e mostra sucesso`() = runViewModelTest {
        val (servidor, banco) = servidorDeItens()
        servidor.falhar(HttpMethod.Post, "/v2/itens", FalhaDeRede.GravouERespostaSePerdeu)
        val vm = SalvarViewModel(servidor.domainApiClient(dispatcher = dispatcher))

        vm.dispatch(SalvarAction.Salvar)

        val estado = vm.state.ateQue { !it.salvando }
        assertTrue(estado.salvo, "o servidor gravou: a tela tem de mostrar sucesso")
        assertNull(estado.erro)
        assertEquals(setOf("i1"), banco)
        assertEquals(1, servidor.chamadas(HttpMethod.Post, "/v2/itens").size, "e sem gravar duas vezes")
    }

    @Test
    fun `fluxo item_criar tela conexao-caiu - nao chegou ao servidor - mostra o erro de conexao`() = runViewModelTest {
        val (servidor, banco) = servidorDeItens()
        servidor.falhar(HttpMethod.Post, "/v2/itens", FalhaDeRede.SemConexao)
        val vm = SalvarViewModel(servidor.domainApiClient(dispatcher = dispatcher))

        vm.dispatch(SalvarAction.Salvar)

        assertTrue(vm.state.ateQue { !it.salvando }.erro != null)
        assertTrue(banco.isEmpty())
    }

    @Test
    fun `fluxo item_criar tela duplo-toque - grava uma vez so`() = runViewModelTest {
        val (servidor, _) = servidorDeItens()
        val vm = SalvarViewModel(servidor.domainApiClient(dispatcher = dispatcher))

        duploToque(vm::dispatch, SalvarAction.Salvar)

        vm.state.ateQue { it.salvo && !it.salvando }
        assertEquals(1, servidor.chamadas(HttpMethod.Post, "/v2/itens").size)
    }

    @Test
    fun duploToque_pega_a_guarda_que_so_liga_o_salvando_dentro_do_launch() = runViewModelTest {
        val (servidor, _) = servidorDeItens()
        val vm = SalvarViewModel(servidor.domainApiClient(dispatcher = dispatcher), guardaSincrona = false)

        duploToque(vm::dispatch, SalvarAction.Salvar)

        vm.state.ateQue { it.salvo && !it.salvando }
        assertEquals(2, servidor.chamadas(HttpMethod.Post, "/v2/itens").size, "o defeito tem de aparecer no teste")
    }

    @Test
    fun ateQue_espera_trabalho_com_atraso_virtual_e_reprova_com_o_ultimo_estado() = runViewModelTest {
        val estado = MutableStateFlow(0)
        test.backgroundScope.launch(dispatcher) { delay(1_000); estado.value = 1 }
        assertEquals(1, estado.ateQue { it == 1 })

        val erro = assertFailsWith<AssertionError> { estado.ateQue(timeout = kotlin.time.Duration.parse("100ms")) { it == 2 } }
        assertTrue("Último estado: 1" in (erro.message ?: ""), erro.message)
    }

    @Test
    fun manterQuente_mantem_o_stateIn_WhileSubscribed_atualizado() = runViewModelTest {
        val fonte = MutableStateFlow(1)
        val derivado = fonte.map { it * 10 }.stateIn(test.backgroundScope, SharingStarted.WhileSubscribed(), 0)
        manterQuente(derivado)
        agora()
        assertEquals(10, derivado.value)
        fonte.value = 2
        agora()
        assertEquals(20, derivado.value)
    }

    @Test
    fun efeitos_de_um_flow_qualquer() = runViewModelTest {
        val fluxo = MutableStateFlow("a")
        val t = efeitos(fluxo)
        assertEquals("a", t.awaitItem())
        fluxo.value = "b"
        assertEquals("b", t.awaitItem())
        t.cancelAndIgnoreRemainingEvents()
    }

    @Test
    fun runViewModelTest_pode_rodar_em_sequencia_com_main_novo() = runViewModelTest {
        val vm = SalvarViewModel(servidorDeItens().first.domainApiClient(dispatcher = dispatcher))
        vm.dispatch(SalvarAction.Salvar)
        vm.state.ateQue { it.salvo }
    }
}
