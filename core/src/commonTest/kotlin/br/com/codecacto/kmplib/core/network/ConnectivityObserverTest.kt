package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Contrato de ciclo de vida do [ConnectivityObserver] (2.69.0).
 *
 * A política vive no [ActivationRefCounter] (commonMain puro), então é testada aqui de ponta a
 * ponta sem precisar de `Context`/`NWPathMonitor`: `onActivate`/`onDeactivate` representam o
 * registro/desregistro do callback nativo (`ConnectivityManager.NetworkCallback` / `NWPathMonitor`).
 */
class ConnectivityObserverTest {

    private class Recorder {
        var activations = 0
        var deactivations = 0
        val counter = ActivationRefCounter(
            onActivate = { activations++ },
            onDeactivate = { deactivations++ },
        )
    }

    @Test
    fun `dois start registram um unico callback nativo`() {
        val r = Recorder()

        r.counter.acquire() // ConnectivityGate
        r.counter.acquire() // RestCrudSyncEngine (auto-sync)

        assertEquals(1, r.activations, "callback nativo deve ser registrado uma única vez")
        assertEquals(0, r.deactivations)
        assertEquals(2, r.counter.active)
    }

    @Test
    fun `stop de um consumidor nao derruba o observer do outro`() {
        val r = Recorder()
        r.counter.acquire() // auto-sync (vida longa)
        r.counter.acquire() // gate (vida curta)

        r.counter.release() // onDispose do ConnectivityGate

        assertEquals(0, r.deactivations, "o observer do auto-sync deve continuar vivo")
        assertTrue(r.counter.isActive)
        assertEquals(1, r.counter.active)
    }

    @Test
    fun `ultimo consumidor a sair desregistra o callback`() {
        val r = Recorder()
        r.counter.acquire()
        r.counter.acquire()

        r.counter.release()
        r.counter.release()

        assertEquals(1, r.activations)
        assertEquals(1, r.deactivations)
        assertFalse(r.counter.isActive)
        assertEquals(0, r.counter.active)
    }

    @Test
    fun `stop sobrando e no-op e nunca deixa a contagem negativa`() {
        val r = Recorder()

        r.counter.release() // stop() sem start()
        assertEquals(0, r.deactivations)
        assertEquals(0, r.counter.active)

        r.counter.acquire()
        r.counter.release()
        r.counter.release() // stop() duplicado
        r.counter.release()

        assertEquals(1, r.activations)
        assertEquals(1, r.deactivations, "callback nativo desregistrado exatamente uma vez")
        assertEquals(0, r.counter.active)
    }

    @Test
    fun `reativa apos o ciclo completo`() {
        val r = Recorder()
        r.counter.acquire()
        r.counter.release()
        r.counter.acquire()

        assertEquals(2, r.activations)
        assertEquals(1, r.deactivations)
        assertTrue(r.counter.isActive)
    }

    @Test
    fun `observer comeca sem consumidores e conta start-stop pareados`() {
        val observer = ConnectivityObserver()
        assertEquals(0, observer.activeConsumers)
        assertFalse(observer.isObserving)

        observer.start()
        observer.start()
        assertEquals(2, observer.activeConsumers)
        assertTrue(observer.isObserving)

        observer.stop()
        assertEquals(1, observer.activeConsumers)
        assertTrue(observer.isObserving, "ainda há um consumidor (auto-sync)")

        observer.stop()
        assertEquals(0, observer.activeConsumers)
        assertFalse(observer.isObserving)

        observer.stop() // desemparelhado: no-op
        assertEquals(0, observer.activeConsumers)
    }

    @Test
    fun `isOnline comeca otimista e refresh sem plataforma preserva o valor`() {
        val observer = ConnectivityObserver()
        // Sem Context (unit test) o monitor devolve status desconhecido: nunca inventa "offline".
        assertTrue(observer.isOnline.value)
        observer.refresh()
        assertTrue(observer.isOnline.value)
    }
}

/**
 * O amortecimento da QUEDA (2.209.0) — a política que impede o "sem internet" de piscar na volta do
 * segundo plano no iOS e na troca de Wi-Fi para dados móveis.
 *
 * Exercita a mesma composição que o [ConnectivityObserver] monta (`collectLatest` + `delay`) sobre
 * um flow controlado, porque o observer real instancia o monitor NATIVO no construtor — e não há
 * `NWPathMonitor` nem `Context` num teste de `commonTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmortecimentoDaQuedaTest {

    /** Monta o mesmo laço do observer e devolve o que a UI enxergaria. */
    private fun kotlinx.coroutines.CoroutineScope.amortecer(
        sistema: MutableStateFlow<Boolean>,
        visto: MutableStateFlow<Boolean>,
        prazo: kotlin.time.Duration,
    ) = launch {
        sistema.collectLatest { online ->
            if (online) visto.value = true else { delay(prazo); visto.value = false }
        }
    }

    @Test
    fun `queda que se desfaz antes do prazo nunca chega a tela`() = runTest {
        val sistema = MutableStateFlow(true)
        val visto = MutableStateFlow(true)
        val laco = amortecer(sistema, visto, 2.seconds)

        // O app volta do segundo plano: o NWPathMonitor empurra `unsatisfied`…
        sistema.value = false
        advanceTimeBy(300.milliseconds)
        runCurrent()
        assertTrue(visto.value, "300ms de queda não podem derrubar a tela")

        // …e, logo em seguida, o estado verdadeiro.
        sistema.value = true
        runCurrent()
        assertTrue(visto.value)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertTrue(visto.value, "a espera do `false` tinha de ter sido cancelada pela volta")
        laco.cancel()
    }

    @Test
    fun `queda de verdade chega depois do prazo`() = runTest {
        val sistema = MutableStateFlow(true)
        val visto = MutableStateFlow(true)
        val laco = amortecer(sistema, visto, 2.seconds)

        sistema.value = false
        advanceTimeBy(2.seconds + 100.milliseconds)
        runCurrent()

        assertFalse(visto.value, "sem rede de verdade, o aviso precisa aparecer")
        laco.cancel()
    }

    @Test
    fun `voltar a ficar online e imediato`() = runTest {
        val sistema = MutableStateFlow(true)
        val visto = MutableStateFlow(true)
        val laco = amortecer(sistema, visto, 2.seconds)

        sistema.value = false
        advanceTimeBy(3.seconds)
        runCurrent()
        assertFalse(visto.value)

        sistema.value = true
        runCurrent()
        assertTrue(visto.value, "a volta não passa pelo atraso — ele é só da queda")
        laco.cancel()
    }
}
