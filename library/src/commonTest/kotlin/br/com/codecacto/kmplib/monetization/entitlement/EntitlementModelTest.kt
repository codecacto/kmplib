package br.com.codecacto.kmplib.monetization.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntitlementModelTest {

    @Test
    fun entitlement_free_default_hasNoFeatures() {
        val e = Entitlement.FREE
        assertTrue(e.isFree)
        assertFalse(e.hasFeature("recibos"))
    }

    @Test
    fun entitlement_hasFeature_isCaseSensitiveOnSet() {
        val e = Entitlement(plano = "pro", features = setOf("recibos", "pdf"))
        assertTrue(e.hasFeature("recibos"))
        assertTrue(e.hasFeature("pdf"))
        assertFalse(e.hasFeature("export"))
        assertFalse(e.isFree)
    }

    @Test
    fun usage_unlimited_whenLimitNegative() {
        val u = UsageSnapshot(feature = "recibos", contagem = 100, limite = -1)
        assertTrue(u.isUnlimited)
        assertEquals(Int.MAX_VALUE, u.remaining)
        assertFalse(u.isExhausted)
        assertEquals(0f, u.fraction)
    }

    @Test
    fun usage_remaining_derivedFromLimitMinusCount() {
        val u = UsageSnapshot(feature = "recibos", contagem = 3, limite = 5)
        assertEquals(2, u.remaining)
        assertFalse(u.isExhausted)
        assertEquals(0.6f, u.fraction)
    }

    @Test
    fun usage_exhausted_whenCountReachesLimit() {
        val u = UsageSnapshot(feature = "recibos", contagem = 5, limite = 5)
        assertEquals(0, u.remaining)
        assertTrue(u.isExhausted)
        assertEquals(1f, u.fraction)
    }

    @Test
    fun usage_remaining_neverNegative_andFractionClamped() {
        val u = UsageSnapshot(feature = "recibos", contagem = 9, limite = 5)
        assertEquals(0, u.remaining)
        assertTrue(u.isExhausted)
        assertEquals(1f, u.fraction)
    }

    @Test
    fun usage_explicitRestante_takesPrecedence() {
        val u = UsageSnapshot(feature = "recibos", contagem = 3, limite = 5, restante = 1)
        assertEquals(1, u.remaining)
    }

    @Test
    fun plan_free_detected() {
        assertTrue(Plan(plano = "free", nome = "Gratis").isFree)
        assertFalse(Plan(plano = "pro", nome = "Pro", preco = "9.90").isFree)
    }

    @Test
    fun parseQuotaExceeded_parsesContract() {
        val body = """{"feature":"recibos","limite":5,"contagem":5,"upgradeUrl":"https://x/u"}"""
        val q = parseQuotaExceeded(body)
        assertEquals("recibos", q?.feature)
        assertEquals(5, q?.limite)
        assertEquals(5, q?.contagem)
        assertEquals("https://x/u", q?.upgradeUrl)
    }

    @Test
    fun parseQuotaExceeded_ignoresUnknownKeys_andMissingUpgradeUrl() {
        val body = """{"feature":"os","limite":3,"contagem":3,"extra":true}"""
        val q = parseQuotaExceeded(body)
        assertEquals("os", q?.feature)
        assertNull(q?.upgradeUrl)
    }

    @Test
    fun parseQuotaExceeded_returnsNull_onGarbage() {
        assertNull(parseQuotaExceeded(null))
        assertNull(parseQuotaExceeded(""))
        assertNull(parseQuotaExceeded("not json"))
    }

    @Test
    fun quotaExceeded_toUsageSnapshot() {
        val q = QuotaExceeded(feature = "recibos", limite = 5, contagem = 5)
        val snap = q.toUsageSnapshot()
        assertEquals("recibos", snap.feature)
        assertTrue(snap.isExhausted)
    }
}
