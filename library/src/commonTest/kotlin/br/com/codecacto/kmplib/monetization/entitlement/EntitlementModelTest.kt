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
    fun parseQuotaExceeded_parsesCanonicalEnvelope_withStringNumbers() {
        // Formato real do admin-api: envelope { ok:false, error:{ details: {...com strings...} } }
        val body = """
            {"ok":false,"error":{"code":"QUOTA_EXCEEDED","message":"cota",
            "details":{"feature":"active_loans","limite":"5","contagem":"5",
            "janela":"LIFETIME","upgradeUrl":"https://x/upgrade"}}}
        """.trimIndent()
        val q = parseQuotaExceeded(body)
        assertEquals("active_loans", q?.feature)
        assertEquals(5, q?.limite)
        assertEquals(5, q?.contagem)
        assertEquals("https://x/upgrade", q?.upgradeUrl)
    }

    @Test
    fun parseQuotaExceeded_envelope_numericDetails() {
        val body = """{"ok":false,"error":{"details":{"feature":"os","limite":3,"contagem":3}}}"""
        val q = parseQuotaExceeded(body)
        assertEquals("os", q?.feature)
        assertEquals(3, q?.limite)
        assertEquals(3, q?.contagem)
        assertNull(q?.upgradeUrl)
    }

    @Test
    fun quotaExceeded_toUsageSnapshot() {
        val q = QuotaExceeded(feature = "recibos", limite = 5, contagem = 5)
        val snap = q.toUsageSnapshot()
        assertEquals("recibos", snap.feature)
        assertTrue(snap.isExhausted)
    }

    // --- Mapeamento DTO -> Entitlement: a autoridade do direito e active/status, NAO o plan. ---

    @Test
    fun toModel_activePremium_mapsToPremium() {
        val dto = EntitlementDto(
            active = true,
            status = "ACTIVE",
            plan = PlanDto(
                code = "premium_monthly",
                name = "Premium",
                limits = listOf(PlanLimitDto(feature = "active_loans", limit = -1))
            )
        )
        val ent = dto.toModel()
        assertEquals("premium_monthly", ent.plano)
        assertFalse(ent.isFree)
        assertTrue(ent.isPremium)
        assertTrue(ent.hasFeature("active_loans"))
    }

    @Test
    fun toModel_inactivePremium_isDowngradedToFree() {
        // Servidor devolve `plan` premium mesmo para entitlement EXPIRED/CANCELED — NAO autopromover.
        val dto = EntitlementDto(
            active = false,
            status = "EXPIRED",
            plan = PlanDto(
                code = "premium_monthly",
                name = "Premium",
                limits = listOf(PlanLimitDto(feature = "active_loans", limit = -1))
            )
        )
        val ent = dto.toModel()
        assertEquals("free", ent.plano)
        assertTrue(ent.isFree)
        assertFalse(ent.isPremium)
        assertFalse(ent.hasFeature("active_loans"))
    }

    @Test
    fun toModel_canceledPremium_isDowngradedToFree() {
        val dto = EntitlementDto(
            active = false,
            status = "CANCELED",
            plan = PlanDto(code = "premium_yearly", name = "Premium Anual")
        )
        val ent = dto.toModel()
        assertTrue(ent.isFree)
        assertFalse(ent.isPremium)
    }

    @Test
    fun toModel_statusActive_butActiveFlagFalse_mapsToPremium() {
        // active/status sao a autoridade: status=ACTIVE honra o plano pago mesmo se a flag vier false.
        val dto = EntitlementDto(
            active = false,
            status = "ACTIVE",
            plan = PlanDto(code = "pro", name = "Pro")
        )
        val ent = dto.toModel()
        assertEquals("pro", ent.plano)
        assertTrue(ent.isPremium)
    }

    @Test
    fun toModel_noPlan_isFree() {
        val dto = EntitlementDto(active = true, status = "ACTIVE", plan = null)
        val ent = dto.toModel()
        assertEquals("free", ent.plano)
        assertTrue(ent.isFree)
        assertFalse(ent.isPremium)
    }

    @Test
    fun toModel_inactiveNoPlan_isFree() {
        val dto = EntitlementDto(active = false, status = null, plan = null)
        val ent = dto.toModel()
        assertTrue(ent.isFree)
    }
}
