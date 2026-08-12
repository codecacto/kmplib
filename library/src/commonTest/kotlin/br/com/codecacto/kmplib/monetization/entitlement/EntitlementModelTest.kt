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
    fun parseQuotaExceeded_parsesBacklibErrorResponse_withTopLevelDetails() {
        // Formato do ErrorResponse da backlib (todo backend proprio do ecossistema): `details` no
        // TOPO do corpo, com os numeros como STRING (details e um Map<String, String>).
        val body = """
            {"message":"Limite do plano gratuito atingido","code":"QUOTA_EXCEEDED",
             "timestamp":"2026-08-11T12:00:00Z","path":"/v1/items","traceId":"abc-123",
             "details":{"feature":"items","limite":"50","contagem":"50",
                        "upgradeUrl":"https://acervo.codecacto.com.br/premium"}}
        """.trimIndent()
        val q = parseQuotaExceeded(body)
        assertEquals("items", q?.feature)
        assertEquals(50, q?.limite)
        assertEquals(50, q?.contagem)
        assertEquals("https://acervo.codecacto.com.br/premium", q?.upgradeUrl)
    }

    @Test
    fun parseQuotaExceeded_topLevelDetails_numericValues_andErrorAsString() {
        // `error` como STRING (nao objeto) nao pode atrapalhar a leitura do `details` do topo.
        val body = """{"error":"QUOTA_EXCEEDED","details":{"feature":"fotos","limite":3,"contagem":3}}"""
        val q = parseQuotaExceeded(body)
        assertEquals("fotos", q?.feature)
        assertEquals(3, q?.limite)
        assertEquals(3, q?.contagem)
        assertNull(q?.upgradeUrl)
    }

    @Test
    fun parseQuotaExceeded_canonicalEnvelope_winsOverTopLevelDetails() {
        // Precedencia explicita: com os dois presentes, o envelope canonico do admin-api ganha.
        val body = """
            {"details":{"feature":"do_topo","limite":1,"contagem":1},
             "error":{"code":"QUOTA_EXCEEDED",
                      "details":{"feature":"do_envelope","limite":9,"contagem":9}}}
        """.trimIndent()
        val q = parseQuotaExceeded(body)
        assertEquals("do_envelope", q?.feature)
        assertEquals(9, q?.limite)
    }

    @Test
    fun parseQuotaExceeded_returnsNull_whenNoFeatureAnywhere() {
        // Caso negativo: 402 de outro motivo (sem payload de paywall) NAO pode virar QuotaExceeded.
        assertNull(parseQuotaExceeded("""{"message":"Pagamento pendente","code":"PAYMENT_REQUIRED"}"""))
        assertNull(parseQuotaExceeded("""{"details":{"limite":"5","contagem":"5"}}"""))
        assertNull(parseQuotaExceeded("""{"error":{"details":{"feature":"x"}}}"""))
        assertNull(parseQuotaExceeded("""["nao","e","objeto"]"""))
    }

    @Test
    fun quotaExceeded_toUsageSnapshot() {
        val q = QuotaExceeded(feature = "recibos", limite = 5, contagem = 5)
        val snap = q.toUsageSnapshot()
        assertEquals("recibos", snap.feature)
        assertTrue(snap.isExhausted)
    }

    // --- Mapeamento DTO (contrato pt do admin-api /me) -> Entitlement.
    //     A autoridade do direito vigente e `ativo`, NUNCA a mera presenca de um plano pago. ---

    @Test
    fun toModel_activePremium_mapsToPremium() {
        val dto = EntitlementDto(
            plano = "premium_monthly",
            features = listOf("active_loans", "export_pdf"),
            validoAte = "2026-12-31T23:59:59Z",
            fonte = "REVENUECAT",
            atualizadoEm = "2026-07-01T00:00:00Z",
            ativo = true
        )
        val ent = dto.toModel()
        assertEquals("premium_monthly", ent.plano)
        assertFalse(ent.isFree)
        assertTrue(ent.isPremium)
        assertTrue(ent.hasFeature("active_loans"))
        assertEquals("revenuecat", ent.fonte)
    }

    @Test
    fun toModel_inactivePremium_isDowngradedToFree() {
        // Servidor devolve `plano` premium mesmo para entitlement EXPIRED/CANCELED (ativo=false) — NAO autopromover.
        val dto = EntitlementDto(
            plano = "premium_monthly",
            features = listOf("active_loans"),
            ativo = false
        )
        val ent = dto.toModel()
        assertEquals("free", ent.plano)
        assertTrue(ent.isFree)
        assertFalse(ent.isPremium)
        assertFalse(ent.hasFeature("active_loans"))
    }

    @Test
    fun toModel_freeDefault_isFree() {
        // Usuario sem grant: admin-api responde 200 com o default free (plano="free", ativo=false, fonte="NONE").
        val dto = EntitlementDto(
            plano = "free",
            features = emptyList(),
            validoAte = null,
            fonte = "NONE",
            atualizadoEm = "",
            ativo = false
        )
        val ent = dto.toModel()
        assertEquals("free", ent.plano)
        assertTrue(ent.isFree)
        assertFalse(ent.isPremium)
    }
}
