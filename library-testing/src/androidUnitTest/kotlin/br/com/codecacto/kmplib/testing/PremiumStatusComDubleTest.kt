package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.PremiumStatus
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * O caminho que os apps usam em teste instrumentado e na build de QA: `PurchaseTestHooks.instalar`
 * **antes** do `MonetizationManager.initialize`. O `premiumStatus` (2.249.0) tem de resolver com o
 * estado do dublê — `jaAssinante` (Super 8: instrumentado e `qaPremium` do iOS) abre Premium, sem
 * passar pelo teto de espera.
 */
class PremiumStatusComDubleTest {

    private val config = MonetizationConfig.Freemium(PurchaseConfig(androidApiKey = "goog_x", iosApiKey = "appl_x"))

    @BeforeTest
    fun antes() {
        MonetizationManager.reset()
    }

    @AfterTest
    fun depois() {
        PurchaseTestHooks.limpar()
        MonetizationManager.reset()
    }

    @Test
    fun `jaAssinante resolve Premium sem esperar o teto`() = runBlocking<Unit> {
        PurchaseTestHooks.instalar(FakePurchaseRepository.jaAssinante())
        MonetizationManager.initialize(config, premiumResolutionTimeout = 30.seconds)

        assertTrue(MonetizationManager.awaitPremiumResolved(timeout = 3.seconds))
        assertEquals(PremiumStatus.Premium, MonetizationManager.premiumStatus.value)
    }

    @Test
    fun `comOfertas resolve Free confirmado`() = runBlocking<Unit> {
        PurchaseTestHooks.instalar(FakePurchaseRepository.comOfertas())
        MonetizationManager.initialize(config, premiumResolutionTimeout = 30.seconds)

        val status = MonetizationManager.awaitPremiumStatus(timeout = 3.seconds)
        assertEquals(PremiumStatus.Free(PremiumStatus.FreeReason.STORE), status)
        assertFalse(MonetizationManager.isPremium.value)
    }
}
