package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato da **postura de monetização**.
 *
 * O `MonetizationManager` é um `object` que fala com o SDK do RevenueCat na inicialização, então não
 * é unit-testável fora de device. Por isso a decisão vive em [MonetizationConfig] como regra pura —
 * e é ela que esta suíte cobre: as três perguntas de postura, a decisão `shouldShowAds` e a
 * invariante que amarra ads a tier gratuito.
 */
class MonetizationConfigTest {

    private val purchase = PurchaseConfig(
        androidApiKey = "goog_test",
        iosApiKey = "appl_test",
        entitlementId = "premium",
        offeringId = "default",
    )

    /**
     * Lista exaustiva dos modos. O `when` abaixo é o guarda: acrescentar um modo em
     * [MonetizationConfig] **quebra a compilação desta suíte** até que alguém declare a postura
     * esperada dele aqui.
     */
    private fun allModes(): List<MonetizationConfig> = listOf(
        MonetizationConfig.AdsOnly,
        MonetizationConfig.PremiumOnly(purchase),
        MonetizationConfig.Freemium(purchase),
        MonetizationConfig.FreemiumQuota(purchase),
    )

    /** Postura esperada por modo: (ads, assinatura, tier gratuito). */
    private fun expectedPosture(config: MonetizationConfig): Triple<Boolean, Boolean, Boolean> =
        when (config) {
            is MonetizationConfig.AdsOnly -> Triple(true, false, true)
            is MonetizationConfig.PremiumOnly -> Triple(false, true, false)
            is MonetizationConfig.Freemium -> Triple(true, true, true)
            is MonetizationConfig.FreemiumQuota -> Triple(false, true, true)
        }

    @Test
    fun `cada modo declara a postura esperada`() {
        allModes().forEach { config ->
            val (ads, subscription, freeTier) = expectedPosture(config)
            assertEquals(ads, config.showsAds, "showsAds de ${config.modeName}")
            assertEquals(subscription, config.sellsSubscription, "sellsSubscription de ${config.modeName}")
            assertEquals(freeTier, config.hasFreeTier, "hasFreeTier de ${config.modeName}")
        }
    }

    @Test
    fun `modo que exibe ads sempre tem tier gratuito`() {
        // Invariante da modelagem: anúncio só existe onde existe usuário não pagante. É o que
        // impede um modo novo nascer como "ads + pague para usar" (combinação sem sentido).
        allModes().filter { it.showsAds }.forEach { config ->
            assertTrue(config.hasFreeTier, "${config.modeName} exibe ads mas não declara tier gratuito")
        }
    }

    @Test
    fun `modo que vende assinatura carrega config de compra e o resto nao`() {
        allModes().forEach { config ->
            if (config.sellsSubscription) {
                assertNotNull(config.purchaseConfig, "${config.modeName} vende assinatura sem PurchaseConfig")
            } else {
                assertNull(config.purchaseConfig, "${config.modeName} não vende assinatura mas tem PurchaseConfig")
            }
        }
    }

    @Test
    fun `assinante nunca ve anuncio em modo nenhum`() {
        allModes().forEach { config ->
            assertFalse(config.shouldShowAds(isPremium = true), "${config.modeName} exibiu ads para assinante")
        }
    }

    @Test
    fun `AdsOnly exibe ads e nao vende assinatura`() {
        val config = MonetizationConfig.AdsOnly
        assertTrue(config.shouldShowAds(isPremium = false))
        assertFalse(config.sellsSubscription)
        assertNull(config.purchaseConfig)
    }

    @Test
    fun `PremiumOnly nunca exibe ads e nao tem tier gratuito`() {
        val config = MonetizationConfig.PremiumOnly(purchase)
        assertFalse(config.shouldShowAds(isPremium = false))
        assertFalse(config.shouldShowAds(isPremium = true))
        assertFalse(config.hasFreeTier)
        assertEquals(purchase, config.purchaseConfig)
    }

    @Test
    fun `Freemium exibe ads so para nao assinante`() {
        val config = MonetizationConfig.Freemium(purchase)
        assertTrue(config.shouldShowAds(isPremium = false))
        assertFalse(config.shouldShowAds(isPremium = true))
    }

    @Test
    fun `FreemiumQuota nunca exibe ads mas declara tier gratuito`() {
        // O ponto do modo: mesmo COMPORTAMENTO de ads do PremiumOnly, VERDADE diferente sobre o
        // plano gratuito. Configurar SaaS freemium como PremiumOnly funcionava por acidente.
        val config = MonetizationConfig.FreemiumQuota(purchase)
        assertFalse(config.shouldShowAds(isPremium = false))
        assertFalse(config.shouldShowAds(isPremium = true))
        assertTrue(config.hasFreeTier)
        assertTrue(config.sellsSubscription)
        assertEquals(purchase, config.purchaseConfig)
    }

    @Test
    fun `FreemiumQuota e PremiumOnly so diferem no tier gratuito`() {
        val quota = MonetizationConfig.FreemiumQuota(purchase)
        val premium = MonetizationConfig.PremiumOnly(purchase)
        assertEquals(premium.showsAds, quota.showsAds)
        assertEquals(premium.sellsSubscription, quota.sellsSubscription)
        assertEquals(premium.purchaseConfig, quota.purchaseConfig)
        assertFalse(premium.hasFreeTier)
        assertTrue(quota.hasFreeTier)
    }

    @Test
    fun `modeName e estavel e unico por modo`() {
        val names = allModes().map { it.modeName }
        assertEquals(
            listOf("ADS_ONLY", "PREMIUM_ONLY", "FREEMIUM", "FREEMIUM_QUOTA"),
            names,
        )
        assertEquals(names.size, names.toSet().size, "modeName duplicado entre modos")
    }
}
