package br.com.codecacto.kmplib.monetization.quota

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.prefs.FakeAppPreferences
import br.com.codecacto.kmplib.monetization.entitlement.AssertResult
import br.com.codecacto.kmplib.monetization.entitlement.Entitlement
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementProvider
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementRepository
import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.entitlement.PurchaseOutcome
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val FEATURE = "partidas_diarias"
private const val SLUG = "app-teste"

/** Repositório fake: encena a resposta do admin-api por chamada. */
private class FakeEntitlementRepository(
    var next: () -> AssertResult = { AssertResult.Allowed },
) : EntitlementRepository {
    val asserts = mutableListOf<Triple<String, Int, Int>>()

    override suspend fun getEntitlement(): ApiResult<Entitlement> = ApiResult.Success(Entitlement.FREE)
    override suspend fun getUsage(feature: String): ApiResult<UsageSnapshot> =
        ApiResult.Success(UsageSnapshot(feature, 0, 0))

    override suspend fun getPlans(): ApiResult<List<Plan>> = ApiResult.Success(emptyList())

    override suspend fun assertUsage(feature: String, currentCount: Int, amount: Int): AssertResult {
        asserts += Triple(feature, currentCount, amount)
        return next()
    }
}

/**
 * Provider que reproduz a corrida real do RevenueCat: o `StateFlow` só reflete o premium **depois**
 * do primeiro `refresh()` (antes disso vale `false`, como num cold start).
 */
private class ColdStartEntitlementProvider(private val premiumAfterRefresh: Boolean) : EntitlementProvider {
    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium
    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount++
        _isPremium.value = premiumAfterRefresh
    }

    override suspend fun offerings(): List<PurchasePackage> = emptyList()
    override suspend fun subscriptionInfo(): SubscriptionInfo? = null
    override suspend fun purchasePackage(packageId: String): PurchaseOutcome = PurchaseOutcome.Indisponivel
    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Indisponivel
}

class OfflineQuotaGateTest {

    private fun store(prefs: FakeAppPreferences, nowMillis: () -> Long = { 1_783_512_000_000L }) =
        DailyQuotaStore(prefs, SLUG, FEATURE, nowMillis, TimeZone.UTC)

    // ---- Regras puras -----------------------------------------------------

    @Test
    fun rules_premium_and_unlimited_always_allow() {
        assertTrue(QuotaRules.allows(isPremium = true, currentCount = 999, freeLimit = 3))
        assertTrue(QuotaRules.allows(isPremium = false, currentCount = 999, freeLimit = QUOTA_UNLIMITED))
        assertFalse(QuotaRules.allows(isPremium = false, currentCount = 3, freeLimit = 3))
        assertEquals(null, QuotaRules.remaining(true, 1, 3))
        assertEquals(2, QuotaRules.remaining(false, 1, 3))
        assertEquals(0, QuotaRules.remaining(false, 9, 3))
    }

    // ---- Corrida do premium (bug MundoBandeiras AppModule.kt:90) ----------

    @Test
    fun premium_source_awaits_first_refresh_before_reading_flow() = runTest {
        val provider = ColdStartEntitlementProvider(premiumAfterRefresh = true)
        // Snapshot cru (o bug): antes de refresh(), o flow diz Free.
        assertFalse(provider.isPremium.value)

        val source = provider.asPremiumSource()
        assertTrue(source.isPremium(), "Pro tratado como Free antes do 1º refresh")
        assertEquals(1, provider.refreshCount)

        // Priming acontece UMA vez por processo.
        assertTrue(source.isPremium())
        assertEquals(1, provider.refreshCount)
    }

    @Test
    fun pro_user_never_consumes_quota_on_cold_start() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(premiumAfterRefresh = true)
        val repo = FakeEntitlementRepository()
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 5, FEATURE, store(prefs), repo)

        val outcome = gate.tryConsume()

        assertEquals(QuotaOutcome.Allowed(remaining = null), outcome)
        assertEquals(0, store(prefs).count(), "Pro não pode ter cota consumida")
        assertTrue(repo.asserts.isEmpty(), "Pro não faz assert de cota")
    }

    // ---- Fail-open LIMITADO ----------------------------------------------

    @Test
    fun network_failure_allows_until_free_cap_then_blocks() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(premiumAfterRefresh = false)
        val repo = FakeEntitlementRepository { AssertResult.Failed(-1, "offline") }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 2, FEATURE, store(prefs), repo)

        assertEquals(QuotaOutcome.Allowed(1), gate.tryConsume())
        assertEquals(QuotaOutcome.Allowed(0), gate.tryConsume())

        // 3ª tentativa offline: NÃO libera infinito — aplica o teto Free localmente.
        val blocked = gate.tryConsume()
        assertIs<QuotaOutcome.Blocked>(blocked)
        assertEquals(QuotaExceeded(FEATURE, limite = 2, contagem = 2), blocked.quota)

        // Consumos offline entraram na fila de reconciliação.
        assertEquals(2, store(prefs).pendingOffline())
    }

    @Test
    fun network_failure_never_promotes_free_to_premium() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(premiumAfterRefresh = false)
        val repo = FakeEntitlementRepository { AssertResult.Failed(500, "boom") }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 1, FEATURE, store(prefs), repo)

        assertIs<QuotaOutcome.Allowed>(gate.tryConsume())
        assertIs<QuotaOutcome.Blocked>(gate.tryConsume())
        assertEquals(1, gate.usageSnapshot().limite, "limite Free preservado apesar da falha")
    }

    // ---- Servidor manda ---------------------------------------------------

    @Test
    fun server_allowed_increments_clean_without_offline_queue() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(false)
        val repo = FakeEntitlementRepository { AssertResult.Allowed }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 5, FEATURE, store(prefs), repo)

        assertEquals(QuotaOutcome.Allowed(4), gate.tryConsume())
        assertEquals(1, store(prefs).count())
        assertEquals(0, store(prefs).pendingOffline())
        assertEquals(Triple(FEATURE, 0, 1), repo.asserts.single())
    }

    @Test
    fun server_denied_overrides_local_count_and_blocks() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(false)
        val quota = QuotaExceeded(FEATURE, limite = 5, contagem = 5)
        val repo = FakeEntitlementRepository { AssertResult.Denied(quota) }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 5, FEATURE, store(prefs), repo)

        val outcome = gate.tryConsume()

        assertIs<QuotaOutcome.Blocked>(outcome)
        assertEquals(quota, outcome.quota)
        assertEquals(5, store(prefs).count(), "saldo do servidor sobrepõe o espelho local")
        assertEquals(0, store(prefs).pendingOffline())
    }

    // ---- Reconciliação ----------------------------------------------------

    @Test
    fun reconcile_clears_pending_when_server_accepts() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(false)
        val repo = FakeEntitlementRepository { AssertResult.Failed(-1, "offline") }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 5, FEATURE, store(prefs), repo)

        gate.tryConsume()
        gate.tryConsume()
        assertEquals(2, store(prefs).pendingOffline())

        repo.next = { AssertResult.Allowed }
        gate.reconcile()

        assertEquals(0, store(prefs).pendingOffline())
        assertEquals(2, store(prefs).count(), "contagem preservada; só a fila é limpa")
        assertEquals(Triple(FEATURE, 2, 0), repo.asserts.last())
    }

    @Test
    fun reconcile_takes_server_balance_when_denied() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(false)
        val repo = FakeEntitlementRepository { AssertResult.Failed(-1, "offline") }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 5, FEATURE, store(prefs), repo)
        gate.tryConsume()

        repo.next = { AssertResult.Denied(QuotaExceeded(FEATURE, 5, 5)) }
        gate.reconcile()

        assertEquals(5, store(prefs).count())
        assertEquals(0, store(prefs).pendingOffline())
    }

    @Test
    fun reconcile_keeps_queue_when_still_offline() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(false)
        val repo = FakeEntitlementRepository { AssertResult.Failed(-1, "offline") }
        val gate = OfflineQuotaGate(provider.asPremiumSource(), 5, FEATURE, store(prefs), repo)
        gate.tryConsume()

        gate.reconcile()

        assertEquals(1, store(prefs).pendingOffline())
    }

    // ---- App 100% offline (sem repository) --------------------------------

    @Test
    fun offline_only_app_gates_locally_and_resets_next_day() = runTest {
        val prefs = FakeAppPreferences()
        val provider = ColdStartEntitlementProvider(false)
        var now = 1_783_512_000_000L // 2026-07-08T12:00Z
        val gate = OfflineQuotaGate(
            premium = provider.asPremiumSource(),
            freeLimit = 3,
            feature = FEATURE,
            store = store(prefs) { now },
            repository = null,
        )

        repeat(3) { assertIs<QuotaOutcome.Allowed>(gate.tryConsume()) }
        assertIs<QuotaOutcome.Blocked>(gate.tryConsume())

        now += 24 * 60 * 60 * 1000L // vira o dia (UTC)
        assertEquals(QuotaOutcome.Allowed(2), gate.tryConsume())
    }

    // ---- Limite estrutural (ChamadaFacil / Esquecido) ---------------------

    @Test
    fun structural_gate_blocks_at_cap_and_frees_slot_when_count_drops() = runTest {
        val provider = ColdStartEntitlementProvider(false)
        val gate = OfflineQuotaGate(provider.asPremiumSource(), freeLimit = 2, feature = "turmas")

        assertEquals(QuotaOutcome.Allowed(1), gate.assertStructural(currentCount = 1))
        assertIs<QuotaOutcome.Blocked>(gate.assertStructural(currentCount = 2))
        // Arquivou uma turma → vaga liberada.
        assertEquals(QuotaOutcome.Allowed(1), gate.assertStructural(currentCount = 1))
    }

    @Test
    fun structural_gate_unlimited_for_pro_even_on_cold_start() = runTest {
        val provider = ColdStartEntitlementProvider(premiumAfterRefresh = true)
        val gate = OfflineQuotaGate(provider.asPremiumSource(), freeLimit = 2, feature = "checklists")

        assertEquals(QuotaOutcome.Allowed(null), gate.assertStructural(currentCount = 50))
        assertEquals(QUOTA_UNLIMITED, gate.usageSnapshot(currentCount = 50).limite)
    }
}
