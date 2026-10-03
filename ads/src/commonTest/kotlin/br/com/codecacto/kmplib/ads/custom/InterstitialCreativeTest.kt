package br.com.codecacto.kmplib.ads.custom

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Piadaria, 02/out/2026: o intersticial "ao abrir" entrava PRETO, com só o "X", enquanto a arte
 * baixava. O diálogo só pode abrir com a arte pronta; sem ela, a exibição é pulada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InterstitialCreativeTest {

    private val timeout = 5.seconds

    @Test
    fun `arte carregou dentro do teto - exibe`() = runTest {
        val ok = awaitInterstitialCreative("https://x/i.webp", timeout) { delay(1_500); true }
        assertTrue(ok)
        assertEquals(1_500, currentTime, "espera só o tempo da carga")
    }

    @Test
    fun `arte falhou - pula`() = runTest {
        assertFalse(awaitInterstitialCreative("https://x/i.webp", timeout) { false })
    }

    @Test
    fun `carga lancou excecao - pula em vez de quebrar`() = runTest {
        assertFalse(awaitInterstitialCreative("https://x/i.webp", timeout) { error("rede caiu") })
    }

    @Test
    fun `arte nao chegou no teto - pula no teto`() = runTest {
        val ok = awaitInterstitialCreative("https://x/i.webp", timeout) { delay(60_000); true }
        assertFalse(ok)
        assertEquals(5_000, currentTime)
    }

    @Test
    fun `url em branco - pula sem tentar`() = runTest {
        var tentou = false
        assertFalse(awaitInterstitialCreative("  ", timeout) { tentou = true; true })
        assertFalse(tentou)
    }
}
