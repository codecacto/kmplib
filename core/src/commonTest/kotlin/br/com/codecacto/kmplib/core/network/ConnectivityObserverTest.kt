package br.com.codecacto.kmplib.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
