package br.com.codecacto.kmplib.monetization.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitlementStateTest {

    @Test
    fun initialState_isFree_notPremium_noPaywall() {
        val s = EntitlementState()
        assertEquals("free", s.plano)
        assertFalse(s.isPremium)
        assertFalse(s.isPaywallOpen)
    }

    @Test
    fun hasFeature_trueWhenPremium_evenWithoutEntitlementFeature() {
        val s = EntitlementState(isPremium = true)
        assertTrue(s.hasFeature("recibos"))
    }

    @Test
    fun hasFeature_trueWhenEntitlementContainsIt() {
        val s = EntitlementState(entitlement = Entitlement(plano = "pro", features = setOf("pdf")))
        assertTrue(s.hasFeature("pdf"))
        assertFalse(s.hasFeature("export"))
    }

    @Test
    fun withUsage_storesByFeatureKey() {
        val s = EntitlementState()
            .withUsage(UsageSnapshot("recibos", 2, 5))
            .withUsage(UsageSnapshot("os", 1, 3))
        assertEquals(2, s.usageOf("recibos")?.contagem)
        assertEquals(3, s.usageOf("os")?.limite)
        assertNull(s.usageOf("desconhecida"))
    }

    @Test
    fun paywall_openAndDismiss() {
        val q = QuotaExceeded("recibos", 5, 5)
        val opened = EntitlementState().showingPaywall(q)
        assertTrue(opened.isPaywallOpen)
        assertEquals("recibos", opened.paywall?.feature)
        val closed = opened.dismissingPaywall()
        assertFalse(closed.isPaywallOpen)
    }
}
