package br.com.codecacto.kmplib.monetization.entitlement

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato da fachada de assinatura offline. O provider real ([RevenueCatEntitlementProvider])
 * depende do SDK RevenueCat/loja e não é unit-testável sem device; validamos o [StubEntitlementProvider]
 * (fail-closed) e a factory [createEntitlementProvider].
 */
class EntitlementProviderTest {

    @Test
    fun `factory sem config retorna stub fail-closed`() {
        val provider = createEntitlementProvider(purchaseConfig = null)
        assertTrue(provider is StubEntitlementProvider)
    }

    @Test
    fun `stub sempre reporta Free`() {
        val provider = StubEntitlementProvider()
        assertFalse(provider.isPremium.value)
    }

    @Test
    fun `stub nunca fabrica offerings nem assinatura`() = runTest {
        val provider = StubEntitlementProvider()
        provider.refresh()
        assertTrue(provider.offerings().isEmpty())
        assertEquals(null, provider.subscriptionInfo())
        assertFalse(provider.isPremium.value)
    }

    @Test
    fun `stub recusa compra e restauracao como indisponivel`() = runTest {
        val provider = StubEntitlementProvider()
        assertEquals(PurchaseOutcome.Indisponivel, provider.purchasePackage("premium_mensal"))
        assertEquals(PurchaseOutcome.Indisponivel, provider.restore())
    }
}
