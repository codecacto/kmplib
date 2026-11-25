package br.com.codecacto.kmplib.testing.net

import br.com.codecacto.kmplib.sync.rest.DomainResult
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O [ServidorDeTeste] contra o `DomainApiClient` e o `createHttpClient` REAIS: cada [FalhaDeRede] tem de produzir,
 * no app, o mesmo resultado que a rede de verdade produz — inclusive a nova tentativa da lib.
 */
class ServidorDeTesteTest {

    private val caminho = "/v2/locacoes/{id}/cancelar"

    private fun TestScope.cliente(servidor: ServidorDeTeste, token: String = "t1", renovado: String = token) =
        servidor.domainApiClient(token = token, tokenRenovado = renovado, dispatcher = StandardTestDispatcher(testScheduler))

    @Test
    fun rota_responde_e_registra_a_chamada_com_parametros_query_e_corpo() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, caminho) { c -> json("""{"id":"${c.parametros["id"]}"}""") }

        val r = cliente(servidor).postJson("/v2/locacoes/l1/cancelar?motivo=teste", """{"x":1}""")

        assertEquals(DomainResult.Success("""{"id":"l1"}"""), r)
        val chamada = servidor.chamadas(HttpMethod.Post, caminho).single()
        assertEquals("/v2/locacoes/l1/cancelar", chamada.caminho)
        assertEquals("l1", chamada.parametros["id"])
        assertEquals("teste", chamada.parametroDaQuery("motivo"))
        assertEquals("""{"x":1}""", chamada.corpo)
        assertEquals("Bearer t1", chamada.cabecalhos[HttpHeaders.Authorization])
        assertEquals("POST /v2/locacoes/l1/cancelar", chamada.toString())
        servidor.confirmarTodasDeclaradas()
    }

    @Test
    fun a_ultima_rota_declarada_vence() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/v2/x") { json("1") }
        servidor.rota(HttpMethod.Get, "/v2/x") { json("2") }
        assertEquals(DomainResult.Success("2"), cliente(servidor).getJson("/v2/x"))
    }

    @Test
    fun pedido_sem_rota_responde_404_no_envelope_e_fica_em_naoDeclaradas() = runTest {
        val servidor = ServidorDeTeste()
        val r = assertIs<DomainResult.Error>(cliente(servidor).getJson("/v2/nada"))
        assertEquals(404, r.code)
        assertEquals("ROTA_NAO_DECLARADA", r.serverCode)
        assertEquals(listOf("GET /v2/nada"), servidor.naoDeclaradas.map { it.toString() })
        assertFailsWith<AssertionError> { servidor.confirmarTodasDeclaradas() }
    }

    @Test
    fun base_com_caminho_e_tirada_do_caminho_da_rota() = runTest {
        val servidor = ServidorDeTeste("https://api.teste/apps")
        servidor.rota(HttpMethod.Get, "/v1/ping") { json("ok") }
        assertEquals(DomainResult.Success("ok"), cliente(servidor).getJson("/v1/ping"))
        assertEquals("/v1/ping", servidor.chamadas.single().caminho)
    }

    @Test
    fun sem_conexao_num_post_nao_chega_ao_servidor_nem_roda_a_rota() = runTest {
        val servidor = ServidorDeTeste()
        var gravou = 0
        servidor.rota(HttpMethod.Post, caminho) { gravou++; json("{}") }
        servidor.falhar(HttpMethod.Post, caminho, FalhaDeRede.SemConexao)

        val r = assertIs<DomainResult.Error>(cliente(servidor).postJson("/v2/locacoes/l1/cancelar", "{}"))

        assertEquals(DomainResult.OFFLINE_CODE, r.code)
        assertEquals(0, gravou)
        assertTrue(servidor.chamadas.isEmpty(), "não chegou ao servidor")
        assertEquals(1, servidor.tentativas.size, "POST não é repetido pela lib")
    }

    @Test
    fun sem_conexao_num_get_e_repetido_pela_lib_e_termina_bem() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/v2/locacoes") { json("[]") }
        servidor.falhar(HttpMethod.Get, "/v2/locacoes", FalhaDeRede.SemConexao)

        assertEquals(DomainResult.Success("[]"), cliente(servidor).getJson("/v2/locacoes"))
        assertEquals(2, servidor.tentativas.size)
        assertEquals(1, servidor.chamadas.size)
    }

    @Test
    fun falha_com_vezes_acima_das_tentativas_da_lib_vira_erro_de_conexao() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/v2/locacoes") { json("[]") }
        servidor.falhar(HttpMethod.Get, "/v2/locacoes", FalhaDeRede.SemConexao, vezes = 10)

        val r = assertIs<DomainResult.Error>(cliente(servidor).getJson("/v2/locacoes"))
        assertEquals(DomainResult.OFFLINE_CODE, r.code)
        assertEquals(3, servidor.tentativas.size, "1 + as 2 novas tentativas do default")

        servidor.limparFalhas()
        assertEquals(DomainResult.Success("[]"), cliente(servidor).getJson("/v2/locacoes"))
    }

    @Test
    fun gravou_e_a_resposta_se_perdeu_roda_a_rota_e_o_app_recebe_falha_de_conexao() = runTest {
        val servidor = ServidorDeTeste()
        var gravou = 0
        servidor.rota(HttpMethod.Post, caminho) { gravou++; json("""{"status":"CANCELADA"}""") }
        servidor.falhar(HttpMethod.Post, caminho, FalhaDeRede.GravouERespostaSePerdeu)

        val r = assertIs<DomainResult.Error>(cliente(servidor).postJson("/v2/locacoes/l1/cancelar", "{}"))

        assertEquals(DomainResult.OFFLINE_CODE, r.code)
        assertEquals(1, gravou, "o servidor GRAVOU")
        assertEquals(1, servidor.chamadas.size)

        // A próxima chamada (a tela conferindo o estado) já não falha.
        assertEquals(DomainResult.Success("""{"status":"CANCELADA"}"""), cliente(servidor).postJson("/v2/locacoes/l1/cancelar", "{}"))
    }

    @Test
    fun corpo_cortado_num_get_e_repetido_pela_lib() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/v2/clientes") { json("""{"data":[{"id":"c1"}]}""") }
        servidor.falhar(HttpMethod.Get, "/v2/clientes", FalhaDeRede.CorpoCortado(bytes = 5))

        assertEquals(DomainResult.Success("""{"data":[{"id":"c1"}]}"""), cliente(servidor).getJson("/v2/clientes"))
        assertEquals(2, servidor.chamadas.size, "a 1ª chegou (corpo cortado), a 2ª veio inteira")
    }

    @Test
    fun corpo_cortado_num_post_que_grava_vira_erro_e_nao_e_repetido() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, "/v2/locacoes") { json("""{"id":"l9"}""", HttpStatusCode.Created) }
        servidor.falhar(HttpMethod.Post, "/v2/locacoes", FalhaDeRede.CorpoCortado())

        val r = assertIs<DomainResult.Error>(cliente(servidor).postJson("/v2/locacoes", "{}"))
        assertEquals(DomainResult.OFFLINE_CODE, r.code)
        assertEquals(1, servidor.chamadas.size, "POST que grava nunca é repetido (duplicaria)")
    }

    @Test
    fun corpo_cortado_num_post_idempotent_e_repetido_como_o_get() = runTest {
        // 2.381.0, caso LocAki 1.8.0: a simulação (POST que só consulta) com 200 e o corpo cortado.
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, "/v2/locacoes/simulacao") { json("""{"total":"120.00"}""") }
        servidor.falhar(HttpMethod.Post, "/v2/locacoes/simulacao", FalhaDeRede.CorpoCortado(bytes = 3))

        val r = cliente(servidor).postJson("/v2/locacoes/simulacao", "{}", idempotent = true)

        assertEquals(DomainResult.Success("""{"total":"120.00"}"""), r)
        assertEquals(2, servidor.chamadas.size)
    }

    @Test
    fun corpo_cortado_direto_no_httpClient_lanca_na_leitura() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/bruto") { responder("0123456789") }
        servidor.falhar(HttpMethod.Get, "/bruto", FalhaDeRede.CorpoCortado(bytes = 4), vezes = 10)
        val http = servidor.httpClient(StandardTestDispatcher(testScheduler))
        assertFailsWith<Exception> { http.get("https://servidor-de-teste/bruto").bodyAsText() }
        http.close()
    }

    @Test
    fun demora_alem_do_teto_vira_erro_em_tempo_virtual_e_demora_curta_passa() = runTest {
        val servidor = ServidorDeTeste()
        var gravou = 0
        servidor.rota(HttpMethod.Post, "/v2/lento") { gravou++; json("{}") }
        servidor.falhar(HttpMethod.Post, "/v2/lento", FalhaDeRede.Demora(ms = 120_000))

        val r = assertIs<DomainResult.Error>(cliente(servidor).postJson("/v2/lento", "{}"))
        assertEquals(DomainResult.OFFLINE_CODE, r.code)
        assertEquals(1, gravou)
        assertTrue(currentTime in 30_000L until 120_000L, "parou no teto de 30 s do cliente, em tempo virtual: $currentTime")

        servidor.falhar(HttpMethod.Post, "/v2/lento", FalhaDeRede.Demora(ms = 200))
        assertEquals(DomainResult.Success("{}"), cliente(servidor).postJson("/v2/lento", "{}"))
    }

    @Test
    fun recusa_chega_com_o_codigo_do_servidor_e_a_frase_no_campo() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, caminho) {
            recusa(HttpStatusCode.Conflict, "LOCACAO_JA_DEVOLVIDA", "A locação já foi devolvida.")
        }
        servidor.rota(HttpMethod.Post, "/v2/clientes") { recusaNosCampos(mapOf("telefone" to "Informe o WhatsApp")) }
        servidor.rota(HttpMethod.Put, "/v2/clientes/{id}") {
            recusa(HttpStatusCode.UnprocessableEntity, "INVALIDO", details = buildJsonObject { put("nome", "Curto demais") })
        }
        val api = cliente(servidor)

        val conflito = assertIs<DomainResult.Error>(api.postJson("/v2/locacoes/l1/cancelar", "{}"))
        assertEquals(409, conflito.code)
        assertEquals("LOCACAO_JA_DEVOLVIDA", conflito.serverCode)
        assertEquals("A locação já foi devolvida.", conflito.serverMessage)

        val campo = assertIs<DomainResult.Error>(api.postJson("/v2/clientes", "{}"))
        assertEquals(400, campo.code)
        assertEquals("VALIDATION_ERROR", campo.serverCode)
        assertEquals("Informe o WhatsApp", campo.fieldError("telefone"))

        val put = assertIs<DomainResult.Error>(api.putJson("/v2/clientes/c1", "{}"))
        assertEquals("Curto demais", put.fieldError("nome"))
    }

    @Test
    fun cota_esgotada_chega_como_DomainResult_Quota() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, "/v2/locacoes") { cotaEsgotada("locacoes", limite = 10, usado = 10, upgradeUrl = "https://x/p") }

        val r = assertIs<DomainResult.Quota>(cliente(servidor).postJson("/v2/locacoes", "{}"))

        assertEquals("locacoes", r.quota.feature)
        assertEquals(10, r.quota.limite)
        assertEquals(10, r.quota.contagem)
        assertEquals("https://x/p", r.upgradeUrl)
        assertEquals("QUOTA_EXCEEDED", r.serverCode)
    }

    @Test
    fun assinatura_bloqueada_chega_como_erro_de_bloqueio() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/v2/locacoes") { assinaturaBloqueada() }
        servidor.rota(HttpMethod.Get, "/v2/clientes") { assinaturaBloqueada("SUBSCRIPTION_REQUIRED") }

        val r = assertIs<DomainResult.Error>(cliente(servidor).getJson("/v2/locacoes"))
        assertEquals(402, r.code)
        assertTrue(r.isSubscriptionBlocked)
        assertEquals("TRIAL_EXPIRED", r.serverCode)
        val s = assertIs<DomainResult.Error>(cliente(servidor).getJson("/v2/clientes"))
        assertEquals("SUBSCRIPTION_REQUIRED", s.serverCode)

        assertFailsWith<IllegalArgumentException> { RespostaScope(io.ktor.client.engine.mock.MockRequestHandleScope(kotlin.coroutines.EmptyCoroutineContext)).assinaturaBloqueada("OUTRO") }
    }

    @Test
    fun nao_autorizado_leva_o_cliente_a_renovar_o_token_e_repetir() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Get, "/v2/me") { c ->
            if (c.cabecalhos[HttpHeaders.Authorization] == "Bearer velho") naoAutorizado() else json("""{"id":"u1"}""")
        }

        val r = cliente(servidor, token = "velho", renovado = "novo").getJson("/v2/me")

        assertEquals(DomainResult.Success("""{"id":"u1"}"""), r)
        assertEquals(listOf("Bearer velho", "Bearer novo"), servidor.chamadas.map { it.cabecalhos[HttpHeaders.Authorization] })
    }

    @Test
    fun vazio_responde_204_e_delete_termina_bem() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Delete, "/v2/clientes/{id}") { vazio() }
        val r = cliente(servidor).delete("/v2/clientes/c1")
        assertIs<DomainResult.Success<*>>(r)
        assertEquals("c1", servidor.chamadas(HttpMethod.Delete, "/v2/clientes/{id}").single().parametros["id"])
    }

    @Test
    fun falha_consumida_por_tentativa_e_so_na_rota_que_casa() = runTest {
        val servidor = ServidorDeTeste()
        servidor.rota(HttpMethod.Post, "/v2/a") { json("a") }
        servidor.rota(HttpMethod.Post, "/v2/b") { json("b") }
        servidor.falhar(HttpMethod.Post, "/v2/a", FalhaDeRede.SemConexao, vezes = 2)
        val api = cliente(servidor)

        assertEquals(DomainResult.Success("b"), api.postJson("/v2/b", "{}"))
        assertIs<DomainResult.Error>(api.postJson("/v2/a", "{}"))
        assertIs<DomainResult.Error>(api.postJson("/v2/a", "{}"))
        assertEquals(DomainResult.Success("a"), api.postJson("/v2/a", "{}"))
    }

    @Test
    fun parametros_invalidos_sao_recusados() {
        val servidor = ServidorDeTeste()
        assertFailsWith<IllegalArgumentException> { servidor.falhar(HttpMethod.Get, "/x", FalhaDeRede.SemConexao, vezes = 0) }
        assertFailsWith<IllegalArgumentException> { servidor.rota(HttpMethod.Get, "sem-barra") { json("") } }
        assertFailsWith<IllegalArgumentException> { FalhaDeRede.CorpoCortado(-1) }
        assertFailsWith<IllegalArgumentException> { FalhaDeRede.Demora(-1) }
    }

    @Test
    fun modelo_de_caminho_casa_pedacos_e_ignora_barra_final() {
        val m = ModeloDeCaminho("/v2/locacoes/{id}/itens/{item}")
        assertEquals(mapOf("id" to "l1", "item" to "i2"), m.casar("/v2/locacoes/l1/itens/i2/"))
        assertNull(m.casar("/v2/locacoes/l1/itens"))
        assertNull(m.casar("/v2/locacoes//itens/i2"))
        assertNull(m.casar("/v2/outra/l1/itens/i2"))
        assertEquals(emptyMap(), ModeloDeCaminho("/").casar("/"))
    }
}
