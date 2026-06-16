package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeEntitlementRepository(
    var entitlement: ApiResult<Entitlement> = ApiResult.Success(Entitlement.FREE),
    var usage: ApiResult<UsageSnapshot> = ApiResult.Success(UsageSnapshot("recibos", 0, 5)),
    var plans: ApiResult<List<Plan>> = ApiResult.Success(emptyList()),
    var assertResult: AssertResult = AssertResult.Allowed,
) : EntitlementRepository {
    var plansCalls = 0
    var lastAssert: Triple<String, Int, Int>? = null
    override suspend fun getEntitlement() = entitlement
    override suspend fun getUsage(feature: String) = usage
    override suspend fun getPlans(): ApiResult<List<Plan>> {
        plansCalls++
        return plans
    }
    override suspend fun assertUsage(feature: String, currentCount: Int, amount: Int): AssertResult {
        lastAssert = Triple(feature, currentCount, amount)
        return assertResult
    }
}

class EntitlementControllerTest {

    @Test
    fun refresh_success_updatesEntitlement_clearsError() = runTest {
        val repo = FakeEntitlementRepository(
            entitlement = ApiResult.Success(Entitlement(plano = "pro", features = setOf("pdf")))
        )
        val controller = EntitlementController(repo)
        val next = controller.refresh(EntitlementState(error = "antigo"))
        assertEquals("pro", next.plano)
        assertTrue(next.hasFeature("pdf"))
        assertNull(next.error)
        assertEquals(false, next.isLoading)
    }

    @Test
    fun refresh_error_keepsPreviousEntitlement_setsError() = runTest {
        val repo = FakeEntitlementRepository(
            entitlement = ApiResult.Error(code = 500, message = "boom")
        )
        val controller = EntitlementController(repo)
        val previous = EntitlementState(entitlement = Entitlement(plano = "pro"))
        val next = controller.refresh(previous)
        assertEquals("pro", next.plano) // mantem o anterior (estado degradado)
        assertEquals("boom", next.error)
    }

    @Test
    fun refreshUsage_appliesSnapshot() = runTest {
        val repo = FakeEntitlementRepository(
            usage = ApiResult.Success(UsageSnapshot("recibos", 4, 5))
        )
        val controller = EntitlementController(repo)
        val next = controller.refreshUsage(EntitlementState(), "recibos")
        assertEquals(4, next.usageOf("recibos")?.contagem)
    }

    @Test
    fun plans_areCached_untilForceReload() = runTest {
        val repo = FakeEntitlementRepository(
            plans = ApiResult.Success(listOf(Plan(plano = "pro", nome = "Pro", preco = "9.90")))
        )
        val controller = EntitlementController(repo)
        controller.plans()
        controller.plans()
        assertEquals(1, repo.plansCalls)
        controller.plans(forceReload = true)
        assertEquals(2, repo.plansCalls)
    }

    @Test
    fun plans_error_returnsEmpty() = runTest {
        val repo = FakeEntitlementRepository(plans = ApiResult.Error(message = "x"))
        val controller = EntitlementController(repo)
        assertTrue(controller.plans().isEmpty())
    }

    @Test
    fun assertUsage_allowed_passesArgs() = runTest {
        val repo = FakeEntitlementRepository(assertResult = AssertResult.Allowed)
        val controller = EntitlementController(repo)
        val res = controller.assertUsage("active_loans", currentCount = 2, amount = 1)
        assertTrue(res.isAllowed)
        assertEquals(Triple("active_loans", 2, 1), repo.lastAssert)
    }

    @Test
    fun assertUsageInto_allowed_keepsState_returnsTrue() = runTest {
        val repo = FakeEntitlementRepository(assertResult = AssertResult.Allowed)
        val controller = EntitlementController(repo)
        val (state, allowed) = controller.assertUsageInto(EntitlementState(), "f", 0)
        assertTrue(allowed)
        assertNull(state.paywall)
    }

    @Test
    fun assertUsageInto_denied_opensPaywall_returnsFalse() = runTest {
        val quota = QuotaExceeded(feature = "active_loans", limite = 5, contagem = 5, upgradeUrl = "u")
        val repo = FakeEntitlementRepository(assertResult = AssertResult.Denied(quota))
        val controller = EntitlementController(repo)
        val (state, allowed) = controller.assertUsageInto(EntitlementState(), "active_loans", 5)
        assertEquals(false, allowed)
        assertEquals(quota, state.paywall)
        assertTrue(state.isPaywallOpen)
    }

    @Test
    fun assertUsageInto_failed_setsError_returnsFalse() = runTest {
        val repo = FakeEntitlementRepository(assertResult = AssertResult.Failed(code = 500, message = "boom"))
        val controller = EntitlementController(repo)
        val (state, allowed) = controller.assertUsageInto(EntitlementState(), "f", 0)
        assertEquals(false, allowed)
        assertEquals("boom", state.error)
    }
}
