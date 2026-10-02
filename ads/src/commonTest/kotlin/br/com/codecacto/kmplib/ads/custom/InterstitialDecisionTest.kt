package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.ads.AdLoadState
import br.com.codecacto.kmplib.ads.router.AdProvider
import br.com.codecacto.kmplib.ads.router.AdRouting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A corrida do intersticial de abertura (Piadaria, 02/out/2026): o pedido chega no primeiro frame,
 * com a lista do apps-api ainda vazia. A decisão tem de ESPERAR a primeira carga, com teto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterstitialDecisionTest {

    private val interstitial = CustomAd(id = "i1", imageUrl = "https://x/i.png", format = CustomAd.FORMAT_INTERSTITIAL)
    private val banner = CustomAd(id = "b1", imageUrl = "https://x/b.png", format = CustomAd.FORMAT_BANNER)
    private val timeout = 5.seconds

    @Test
    fun `lista ainda carregando - espera e exibe quando o criativo chega`() = runTest {
        val showAds = MutableStateFlow(true)
        val ads = MutableStateFlow(emptyList<CustomAd>())
        val load = MutableStateFlow(AdLoadState.LOADING)

        val decision = async { decideInterstitial(showAds, ads, load, timeout) }
        advanceTimeBy(1_500)
        runCurrent()
        assertFalse(decision.isCompleted, "com a lista carregando, a decisão não pode sair no primeiro frame")

        ads.value = listOf(interstitial)
        load.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Show(interstitial), decision.await())
    }

    @Test
    fun `criativo ja carregado - exibe na hora sem esperar`() = runTest {
        val decision = decideInterstitial(
            MutableStateFlow(true), MutableStateFlow(listOf(interstitial)), MutableStateFlow(AdLoadState.READY), timeout,
        )
        assertEquals(InterstitialDecision.Show(interstitial), decision)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `carga chegou sem intersticial - pula`() = runTest {
        val ads = MutableStateFlow(emptyList<CustomAd>())
        val load = MutableStateFlow(AdLoadState.LOADING)
        val decision = async { decideInterstitial(MutableStateFlow(true), ads, load, timeout) }
        runCurrent()

        ads.value = listOf(banner)
        load.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.NO_AD), decision.await())
    }

    @Test
    fun `carga falhou - segue sem anuncio sem esperar o teto`() = runTest {
        val load = MutableStateFlow(AdLoadState.LOADING)
        val decision = async { decideInterstitial(MutableStateFlow(true), MutableStateFlow(emptyList()), load, timeout) }
        runCurrent()

        load.value = AdLoadState.FAILED
        runCurrent()

        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.NO_AD), decision.await())
        assertTrue(currentTime < timeout.inWholeMilliseconds)
    }

    @Test
    fun `carga nao volta - desiste no teto`() = runTest {
        val decision = decideInterstitial(
            MutableStateFlow(true), MutableStateFlow(emptyList()), MutableStateFlow(AdLoadState.LOADING), timeout,
        )
        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.TIMEOUT), decision)
        assertEquals(timeout.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `criativo que chega depois do teto nao e exibido`() = runTest {
        val ads = MutableStateFlow(emptyList<CustomAd>())
        val load = MutableStateFlow(AdLoadState.LOADING)
        val decision = async { decideInterstitial(MutableStateFlow(true), ads, load, timeout) }
        advanceTimeBy(timeout.inWholeMilliseconds + 1)
        runCurrent()
        ads.value = listOf(interstitial)
        load.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.TIMEOUT), decision.await())
    }

    @Test
    fun `timeout zero decide na hora`() = runTest {
        val decision = decideInterstitial(
            MutableStateFlow(true), MutableStateFlow(emptyList()), MutableStateFlow(AdLoadState.LOADING), Duration.ZERO,
        )
        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.TIMEOUT), decision)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `fonte nunca inicializada nao e esperada`() = runTest {
        val decision = decideInterstitial(
            MutableStateFlow(true), MutableStateFlow(emptyList()), MutableStateFlow(AdLoadState.IDLE), timeout,
        )
        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.NO_AD), decision)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `premium nunca exibe - nem com criativo pronto`() = runTest {
        val decision = decideInterstitial(
            MutableStateFlow(false), MutableStateFlow(listOf(interstitial)), MutableStateFlow(AdLoadState.READY), timeout,
        )
        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.ADS_DISABLED), decision)
    }

    @Test
    fun `virou premium durante a espera - pula mesmo que o criativo chegue`() = runTest {
        val showAds = MutableStateFlow(true)
        val ads = MutableStateFlow(emptyList<CustomAd>())
        val load = MutableStateFlow(AdLoadState.LOADING)
        val decision = async { decideInterstitial(showAds, ads, load, timeout) }
        runCurrent()

        showAds.value = false
        runCurrent()
        ads.value = listOf(interstitial)
        load.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.ADS_DISABLED), decision.await())
    }

    @Test
    fun `roteamento ainda carregando - espera o painel antes de exibir`() = runTest {
        val routing = MutableStateFlow(AdRouting.ALL_CUSTOM) // defaults do app
        val routingLoad = MutableStateFlow(AdLoadState.LOADING)
        val decision = async {
            decideInterstitial(
                MutableStateFlow(true), MutableStateFlow(listOf(interstitial)), MutableStateFlow(AdLoadState.READY),
                timeout, routing, routingLoad,
            )
        }
        runCurrent()
        assertFalse(decision.isCompleted, "com o criativo pronto, ainda falta saber se o painel desligou")

        routingLoad.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Show(interstitial), decision.await())
    }

    @Test
    fun `painel publicou off - respeita mesmo com defaults CUSTOM e criativo pronto`() = runTest {
        val routing = MutableStateFlow(AdRouting.ALL_CUSTOM)
        val routingLoad = MutableStateFlow(AdLoadState.LOADING)
        val decision = async {
            decideInterstitial(
                MutableStateFlow(true), MutableStateFlow(listOf(interstitial)), MutableStateFlow(AdLoadState.READY),
                timeout, routing, routingLoad,
            )
        }
        runCurrent()

        routing.value = AdRouting(banner = AdProvider.CUSTOM, interstitial = AdProvider.OFF)
        routingLoad.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Skip(InterstitialSkipReason.ROUTING_OFF), decision.await())
    }

    @Test
    fun `roteamento OFF do default chegando CUSTOM do servidor - exibe`() = runTest {
        // O caso da corrida do roteador: defaults OFF, painel CUSTOM. Antes, OFF no 1º frame = descarte.
        val routing = MutableStateFlow(AdRouting.OFF)
        val routingLoad = MutableStateFlow(AdLoadState.LOADING)
        val ads = MutableStateFlow(emptyList<CustomAd>())
        val adsLoad = MutableStateFlow(AdLoadState.LOADING)
        val decision = async {
            decideInterstitial(MutableStateFlow(true), ads, adsLoad, timeout, routing, routingLoad)
        }
        runCurrent()

        routing.value = AdRouting.ALL_CUSTOM
        routingLoad.value = AdLoadState.READY
        runCurrent()
        assertFalse(decision.isCompleted)

        ads.value = listOf(interstitial)
        adsLoad.value = AdLoadState.READY
        runCurrent()

        assertEquals(InterstitialDecision.Show(interstitial), decision.await())
    }

    @Test
    fun `roteamento falhou - vale o defaults do app`() = runTest {
        val decision = decideInterstitial(
            MutableStateFlow(true), MutableStateFlow(listOf(interstitial)), MutableStateFlow(AdLoadState.READY),
            timeout, MutableStateFlow(AdRouting.ALL_CUSTOM), MutableStateFlow(AdLoadState.FAILED),
        )
        assertEquals(InterstitialDecision.Show(interstitial), decision)
    }
}
