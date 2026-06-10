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
) : EntitlementRepository {
    var plansCalls = 0
    override suspend fun getEntitlement() = entitlement
    override suspend fun getUsage(feature: String) = usage
    override suspend fun getPlans(): ApiResult<List<Plan>> {
        plansCalls++
        return plans
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
}
