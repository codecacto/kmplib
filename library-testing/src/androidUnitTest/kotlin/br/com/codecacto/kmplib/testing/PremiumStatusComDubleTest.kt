package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.PremiumStatus
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    fun `instalar jaAssinante DEPOIS do initialize vira Premium`() = runBlocking<Unit> {
        PurchaseTestHooks.instalar(FakePurchaseRepository.comOfertas())
        MonetizationManager.initialize(config, premiumResolutionTimeout = 30.seconds)
        assertEquals(PremiumStatus.Free(PremiumStatus.FreeReason.STORE), MonetizationManager.awaitPremiumStatus(3.seconds))

        PurchaseTestHooks.instalar(FakePurchaseRepository.jaAssinante())

        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Premium })
        assertTrue(MonetizationManager.isPremium.value)
    }

    @Test
    fun `instalar comOfertas depois de jaAssinante vira Free e limpar resolve sem loja`() = runBlocking<Unit> {
        PurchaseTestHooks.instalar(FakePurchaseRepository.jaAssinante())
        MonetizationManager.initialize(config, premiumResolutionTimeout = 30.seconds)
        assertTrue(MonetizationManager.awaitPremiumResolved(3.seconds))

        PurchaseTestHooks.instalar(FakePurchaseRepository.comOfertas())
        assertTrue(aguardar {
            MonetizationManager.premiumStatus.value == PremiumStatus.Free(PremiumStatus.FreeReason.STORE)
        })
        assertFalse(MonetizationManager.isPremium.value)

        PurchaseTestHooks.limpar()
        assertTrue(aguardar {
            MonetizationManager.premiumStatus.value == PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD)
        })
    }

    private suspend fun aguardar(condicao: () -> Boolean): Boolean = withContext(Dispatchers.Default) {
        withTimeoutOrNull(3_000) {
            while (!condicao()) delay(10)
            true
        } ?: false
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
