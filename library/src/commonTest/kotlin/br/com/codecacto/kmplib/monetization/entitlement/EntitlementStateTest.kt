package br.com.codecacto.kmplib.monetization.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitlementStateTest {

    @Test
    fun `default e free sem features`() {
        val s = EntitlementState()
        assertTrue(s.entitlement.isFree)
        assertFalse(s.hasFeature("export_pdf"))
        assertFalse(s.showingPaywall)
    }

    @Test
    fun `hasFeature segue entitlement quando nao premium`() {
        val s = EntitlementState(
            entitlement = Entitlement(plano = "pro", features = setOf("export_pdf")),
        )
        assertTrue(s.hasFeature("export_pdf"))
        assertFalse(s.hasFeature("watermark_off"))
    }

    @Test
    fun `premium vence mesmo sem entitlement atualizado`() {
        val s = EntitlementState(
            entitlement = Entitlement.FREE,
            isPremium = true,
        )
        // entitlement nao tem a feature, mas premium libera tudo
        assertTrue(s.hasFeature("qualquer_coisa"))
    }

    @Test
    fun `withUsage mescla snapshot no mapa`() {
        val s = EntitlementState()
            .withUsage(UsageSnapshot(feature = "recibos", contagem = 1, limite = 5))
            .withUsage(UsageSnapshot(feature = "os", contagem = 2, limite = 3))
        assertEquals(2, s.usage.size)
        assertEquals(1L, s.usage["recibos"]?.contagem)
        // re-merge atualiza
        val s2 = s.withUsage(UsageSnapshot(feature = "recibos", contagem = 4, limite = 5))
        assertEquals(4L, s2.usage["recibos"]?.contagem)
        assertEquals(2, s2.usage.size)
    }

    @Test
    fun `showingPaywall e dismissingPaywall alternam o paywall`() {
        val quota = QuotaExceeded(feature = "recibos", limite = 5, contagem = 5)
        val opened = EntitlementState().showingPaywall(quota)
        assertTrue(opened.showingPaywall)
        assertEquals(quota, opened.paywall)

        val closed = opened.dismissingPaywall()
        assertFalse(closed.showingPaywall)
        assertNull(closed.paywall)
    }

    @Test
    fun `withEntitlement e withPremium retornam copias`() {
        val s = EntitlementState()
            .withEntitlement(Entitlement(plano = "pro"))
            .withPremium(true)
        assertEquals("pro", s.entitlement.plano)
        assertTrue(s.isPremium)
    }
}
