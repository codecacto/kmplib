package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.PremiumStatus
import br.com.codecacto.kmplib.monetization.purchase.FakePurchaseRepository
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.hasStoreApiKey
import br.com.codecacto.kmplib.monetization.purchase.isStoreApiKeyConfigured
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A trava da chave da loja na FUNDAÇÃO (2.288.0): chave de verdade → loja; chave em branco ou
 * `PLACEHOLDER_*` → stub fail-closed, **a menos que** uma loja já esteja instalada no processo (o
 * dublê do build de QA) — aí o paywall fala com ela. Antes isto morava (copiado) em cada app.
 */
class StoreAvailabilityTest {

    private fun config(key: String) = PurchaseConfig(androidApiKey = key, iosApiKey = key)
    private val placeholder = config("PLACEHOLDER_REVENUECAT_KEY")
    private val real = config("goog_abc123")

    @BeforeTest
    fun antes() = MonetizationManager.reset()

    @AfterTest
    fun depois() = MonetizationManager.reset()

    private fun instalarDuble(): FakePurchaseRepository =
        FakePurchaseRepository().also {
            it.cachedOfferings = listOf(
                PurchasePackage(
                    packageId = "\$rc_monthly",
                    packageType = PurchasePackageType.MONTHLY,
                    storeProductId = "premium_mensal_x",
                    priceLabel = "R$ 9,90",
                    priceAmountMicros = 9_900_000L,
                    currencyCode = "BRL",
                ),
            )
            PurchaseManager.initializeWith(it)
        }

    @Test
    fun `chave configurada e so a que nao e branca nem PLACEHOLDER`() {
        assertTrue(isStoreApiKeyConfigured("goog_abc"))
        assertTrue(isStoreApiKeyConfigured("appl_abc"))
        assertFalse(isStoreApiKeyConfigured(null))
        assertFalse(isStoreApiKeyConfigured(""))
        assertFalse(isStoreApiKeyConfigured("   "))
        assertFalse(isStoreApiKeyConfigured("PLACEHOLDER_REVENUECAT_ANDROID"))
        assertFalse(isStoreApiKeyConfigured(" placeholder"))
        assertTrue(real.hasStoreApiKey)
        assertFalse(placeholder.hasStoreApiKey)
    }

    @Test
    fun `sem chave e sem loja instalada o provider e o stub fail-closed`() {
        assertIs<StubEntitlementProvider>(createEntitlementProvider(purchaseConfig = null))
        // Até a 2.287.0 este caso configurava o SDK com a chave de mentira.
        assertIs<StubEntitlementProvider>(createEntitlementProvider(purchaseConfig = placeholder))
        assertFalse(MonetizationManager.hasStore(placeholder))
        assertFalse(MonetizationManager.hasStore(null))
        assertTrue(MonetizationManager.hasStore(real))
    }

    @Test
    fun `chave PLACEHOLDER com o duble de QA instalado fala com o duble`() = runTest {
        instalarDuble()
        assertTrue(PurchaseManager.hasInstalledStore)
        assertTrue(MonetizationManager.hasStore(placeholder))

        val provider = createEntitlementProvider(purchaseConfig = placeholder)
        assertIs<PurchaseManagerEntitlementProvider>(provider)
        val catalogo = assertIs<OfferingsOutcome.Disponivel>(provider.loadOfferings())
        assertEquals("\$rc_monthly", catalogo.pacotes.single().packageId)

        assertIs<PurchaseManagerEntitlementProvider>(createEntitlementProvider(purchaseConfig = null))
    }

    @Test
    fun `initializeWhenStoreAvailable sem config declara que nao monetiza`() {
        assertFalse(MonetizationManager.initializeWhenStoreAvailable(null))
        assertEquals(PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
    }

    @Test
    fun `initializeWhenStoreAvailable sem loja degrada para gratuito sem configurar o SDK`() {
        assertFalse(MonetizationManager.initializeWhenStoreAvailable(MonetizationConfig.FreemiumQuota(placeholder)))
        assertFalse(MonetizationManager.hasPurchase)
        assertFalse(PurchaseManager.hasInstalledStore)
        assertEquals(PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
    }

    @Test
    fun `initializeWhenStoreAvailable com o duble de QA liga o modo sobre ele`() {
        val duble = instalarDuble()
        assertTrue(MonetizationManager.initializeWhenStoreAvailable(MonetizationConfig.FreemiumQuota(placeholder)))
        assertTrue(MonetizationManager.hasPurchase)
        assertTrue(PurchaseManager.repository === duble, "o initialize não troca o dublê pelo SDK")
    }

    @Test
    fun `modo so de anuncio inicializa sem loja`() {
        assertTrue(MonetizationManager.initializeWhenStoreAvailable(MonetizationConfig.AdsOnly))
        assertFalse(MonetizationManager.hasPurchase)
    }
}
