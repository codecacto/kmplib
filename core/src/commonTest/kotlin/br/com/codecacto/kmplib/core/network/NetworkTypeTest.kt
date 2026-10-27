package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTypeTest {

    private class FakeSource(var atual: NetworkType? = null) : NetworkTypeSource {
        var callback: ((NetworkType) -> Unit)? = null
        var starts = 0
        var stops = 0
        override fun start(onChange: (NetworkType) -> Unit) { starts++; callback = onChange }
        override fun stop() { stops++; callback = null }
        override fun currentType(): NetworkType? = atual
        fun emitir(t: NetworkType) { callback?.invoke(t) }
    }

    @Test
    fun `classificacao do caminho do iOS - modo dados reduzidos conta como tarifada`() {
        assertEquals(NetworkType.NONE, classifyNetworkPath(satisfied = false, expensive = true, constrained = true))
        assertEquals(NetworkType.UNMETERED, classifyNetworkPath(satisfied = true, expensive = false, constrained = false))
        assertEquals(NetworkType.METERED, classifyNetworkPath(satisfied = true, expensive = true, constrained = false))
        assertEquals(NetworkType.METERED, classifyNetworkPath(satisfied = true, expensive = false, constrained = true))
    }

    @Test
    fun `classificacao das capacidades do Android`() {
        assertEquals(NetworkType.NONE, classifyNetworkCapabilities(hasInternet = false, notMetered = true, temporarilyNotMetered = false))
        assertEquals(NetworkType.UNMETERED, classifyNetworkCapabilities(hasInternet = true, notMetered = true, temporarilyNotMetered = false))
        assertEquals(NetworkType.UNMETERED, classifyNetworkCapabilities(hasInternet = true, notMetered = false, temporarilyNotMetered = true))
        assertEquals(NetworkType.METERED, classifyNetworkCapabilities(hasInternet = true, notMetered = false, temporarilyNotMetered = false))
    }

    @Test
    fun `propriedades do enum`() {
        assertFalse(NetworkType.NONE.isConnected)
        assertTrue(NetworkType.METERED.isConnected)
        assertTrue(NetworkType.UNMETERED.isUnmetered)
        assertFalse(NetworkType.METERED.isUnmetered)
    }

    private fun TestScope.observer(fonte: FakeSource) =
        ConnectivityObserver(fonte, 2.seconds, TestScope(StandardTestDispatcher(testScheduler)))

    @Test
    fun `antes de iniciar - online e tarifada (nunca supoe Wi-Fi)`() = runTest {
        val obs = observer(FakeSource())
        assertTrue(obs.isOnline.value)
        assertEquals(NetworkType.METERED, obs.networkType.value)
    }

    @Test
    fun `semeia o tipo corrente no start e segue as trocas de rede na hora`() = runTest {
        val fonte = FakeSource(atual = NetworkType.UNMETERED)
        val sc = TestScope(StandardTestDispatcher(testScheduler))
        val obs = ConnectivityObserver(fonte, 2.seconds, sc)
        obs.start()
        assertEquals(NetworkType.UNMETERED, obs.networkType.value)

        fonte.emitir(NetworkType.METERED)
        sc.runCurrent()
        assertEquals(NetworkType.METERED, obs.networkType.value, "Wi-Fi -> 4G é imediato")
        assertTrue(obs.isOnline.value)

        fonte.emitir(NetworkType.UNMETERED)
        sc.runCurrent()
        assertEquals(NetworkType.UNMETERED, obs.networkType.value)
    }

    @Test
    fun `queda e amortecida nos dois fluxos e a volta cancela`() = runTest {
        val fonte = FakeSource(atual = NetworkType.UNMETERED)
        val sc = TestScope(StandardTestDispatcher(testScheduler))
        val obs = ConnectivityObserver(fonte, 2.seconds, sc)
        obs.start()
        sc.runCurrent()

        fonte.emitir(NetworkType.NONE)
        sc.runCurrent()
        sc.advanceTimeBy(1_500.milliseconds)
        assertEquals(NetworkType.UNMETERED, obs.networkType.value, "queda ainda não confirmada")
        fonte.emitir(NetworkType.METERED)
        sc.runCurrent()
        assertEquals(NetworkType.METERED, obs.networkType.value)

        fonte.emitir(NetworkType.NONE)
        sc.runCurrent()
        sc.advanceTimeBy(2_100.milliseconds)
        assertEquals(NetworkType.NONE, obs.networkType.value)
        assertFalse(obs.isOnline.value)
        obs.stop()
        assertEquals(1, fonte.stops)
    }

    @Test
    fun `refresh relê sem amortecer e preserva quando a plataforma nao sabe`() = runTest {
        val fonte = FakeSource(atual = null)
        val obs = observer(fonte)
        obs.refresh()
        assertEquals(NetworkType.METERED, obs.networkType.value)
        fonte.atual = NetworkType.NONE
        obs.refresh()
        assertEquals(NetworkType.NONE, obs.networkType.value)
        assertFalse(obs.isOnline.value)
    }
}
