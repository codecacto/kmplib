package br.com.codecacto.kmplib.appupdate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppServiceGateStateTest {

    private val soft1 = AppServiceStatus(
        update = AppUpdateStatus.Soft(storeUrl = null, message = "nova", latestVersionName = "1.1.0"),
    )
    private val soft2 = AppServiceStatus(
        update = AppUpdateStatus.Soft(storeUrl = null, message = "nova", latestVersionName = "1.2.0"),
    )
    private val hard = AppServiceStatus(update = AppUpdateStatus.Hard(storeUrl = null, message = null))
    private val manutencao = AppServiceStatus(maintenance = MaintenanceNotice("janela", null))

    // Regressão da 2.203.0: reconsultar zerava a dispensa e o diálogo reaparecia.
    @Test
    fun dispensaSobreviveAReconsultaComAMesmaVersao() = runTest {
        val state = AppServiceGateState()
        state.refresh { soft1 }
        assertNotNull(state.softUpdateToOffer)

        state.dismissSoft()
        assertNull(state.softUpdateToOffer)

        state.refresh { soft1 }
        assertNull(state.softUpdateToOffer)
    }

    @Test
    fun dispensaSobreviveAUmaRespostaSemAtualizacaoNoMeio() = runTest {
        val state = AppServiceGateState()
        state.refresh { soft1 }
        state.dismissSoft()
        state.refresh { AppServiceStatus() }
        state.refresh { soft1 }
        assertNull(state.softUpdateToOffer)
    }

    @Test
    fun versaoNovaRecomendadaReabreOAviso() = runTest {
        val state = AppServiceGateState()
        state.refresh { soft1 }
        state.dismissSoft()
        state.refresh { soft2 }
        assertEquals("1.2.0", state.softUpdateToOffer?.latestVersionName)
    }

    @Test
    fun semNomeDeVersaoAIdentidadeEOProprioAviso() = runTest {
        val semVersao = AppServiceStatus(update = AppUpdateStatus.Soft(null, "recomendada", null))
        val outraMensagem = AppServiceStatus(update = AppUpdateStatus.Soft(null, "outra", null))
        val state = AppServiceGateState()
        state.refresh { semVersao }
        state.dismissSoft()
        state.refresh { semVersao }
        assertNull(state.softUpdateToOffer)

        state.refresh { outraMensagem }
        assertNotNull(state.softUpdateToOffer)
    }

    // O conteúdo não pode ser desmontado enquanto a reconsulta não responde.
    @Test
    fun reconsultaEmAndamentoMantemOUltimoStatus() = runTest {
        val state = AppServiceGateState()
        state.refresh { soft1 }

        val resposta = CompletableDeferred<AppServiceStatus>()
        val job = launch { state.refresh { resposta.await() } }
        testScheduler.runCurrent()
        assertEquals(soft1, state.status)

        resposta.complete(manutencao)
        job.join()
        assertEquals(manutencao, state.status)
    }

    // Consulta dupla na abertura: o 2º gatilho chega com a 1ª consulta em voo.
    @Test
    fun pedidoDuranteConsultaEmVooNaoAbreOutra() = runTest {
        val state = AppServiceGateState()
        var chamadas = 0
        val resposta = CompletableDeferred<AppServiceStatus>()
        val primeira = launch { state.refresh { chamadas++; resposta.await() } }
        testScheduler.runCurrent()

        val abriuOutra = state.refresh { chamadas++; AppServiceStatus() }
        assertFalse(abriuOutra)

        resposta.complete(soft1)
        primeira.join()
        assertEquals(1, chamadas)
        assertEquals(soft1, state.status)

        assertTrue(state.refresh { chamadas++; AppServiceStatus() })
        assertEquals(2, chamadas)
    }

    @Test
    fun falhaNaConsultaLiberaEmVezDeDerrubarOApp() = runTest {
        val state = AppServiceGateState()
        state.refresh { manutencao }
        assertTrue(state.isBlocking)

        state.refresh { error("sem rede") }
        assertFalse(state.isBlocking)
        assertEquals(AppServiceStatus(), state.status)
    }

    @Test
    fun cancelamentoPropagaESoltaATrava() = runTest {
        val state = AppServiceGateState()
        assertFailsWith<CancellationException> {
            state.refresh { throw CancellationException("saiu da tela") }
        }
        assertTrue(state.refresh { soft1 })
    }

    @Test
    fun bloqueioNaoOfereceAtualizacaoOpcional() = runTest {
        val state = AppServiceGateState()
        state.refresh { hard }
        assertTrue(state.isBlocking)
        assertNull(state.softUpdateToOffer)

        state.refresh { manutencao.copy(update = soft1.update) }
        assertTrue(state.isBlocking)
        assertNull(state.softUpdateToOffer)
    }

    @Test
    fun dispensarSemAtualizacaoOpcionalNaoFazNada() = runTest {
        val state = AppServiceGateState()
        state.dismissSoft()
        state.refresh { soft1 }
        assertNotNull(state.softUpdateToOffer)
    }

    @Test
    fun primeiroStartDaAberturaNaoReconsulta() {
        val recheck = ForegroundRecheck()
        assertFalse(recheck.onStart())
    }

    @Test
    fun voltaDoSegundoPlanoReconsultaUmaVez() {
        val recheck = ForegroundRecheck()
        recheck.onStart()
        recheck.onStop()
        assertTrue(recheck.onStart())
        assertFalse(recheck.onStart())
    }
}
