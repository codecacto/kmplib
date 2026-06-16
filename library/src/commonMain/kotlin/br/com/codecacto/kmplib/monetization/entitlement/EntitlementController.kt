package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult

/**
 * Orquestra a leitura de monetizacao para o MVI, combinando a fonte de verdade
 * ([EntitlementRepository] -> admin-api) com o sinal de assinatura da loja
 * ([MonetizationManager]/[PurchaseManager] -> RevenueCat).
 *
 * Pensado para o ViewModel manter um `EntitlementState` no seu State e atualiza-lo com os retornos
 * destes metodos (mantendo a tela stateless). Nao tem estado proprio observavel — devolve sempre o
 * proximo [EntitlementState] a partir do atual, no padrao reducer.
 *
 * Exemplo num BaseViewModel:
 * ```kotlin
 * class HomeViewModel(
 *     private val controller: EntitlementController
 * ) : BaseViewModel<HomeState, HomeAction, HomeEffect>(HomeState()) {
 *     fun load() = launch {
 *         updateState { it.copy(ent = controller.refresh(it.ent)) }
 *     }
 *     fun onCreateRecibo() = launch {
 *         // 1) chama a rota consumivel; se vier 402 -> quotaExceededOrNull()
 *         // 2) updateState { it.copy(ent = it.ent.showingPaywall(quota)) }
 *     }
 *     fun onUpgrade(plan: Plan) = launch {
 *         val r = controller.purchase(plan)
 *         if (r is PurchaseResult.Success) updateState { it.copy(ent = controller.refresh(it.ent)) }
 *     }
 * }
 * ```
 */
class EntitlementController(
    private val repository: EntitlementRepository
) {

    /** Plano(s) do catalogo do projeto (para o Paywall). Cache leve: recarregue quando precisar. */
    private var cachedPlans: List<Plan>? = null

    /**
     * Recarrega entitlement do admin-api e o sinal premium da loja, devolvendo o proximo estado.
     * Em falha de rede, mantem o entitlement anterior e seta `error` (estado degradado, R1 doc 03).
     */
    suspend fun refresh(current: EntitlementState): EntitlementState {
        val premium = PurchaseManager.repository?.isPremium() ?: false
        return when (val res = repository.getEntitlement()) {
            is ApiResult.Success -> current.copy(
                entitlement = res.data,
                isPremium = premium,
                isLoading = false,
                error = null
            )
            is ApiResult.Error -> current.copy(
                isPremium = premium,
                isLoading = false,
                error = res.message
            )
            ApiResult.Loading -> current.copy(isLoading = true)
        }
    }

    /** Recarrega o medidor de uma feature e o aplica ao estado. */
    suspend fun refreshUsage(current: EntitlementState, feature: String): EntitlementState =
        when (val res = repository.getUsage(feature)) {
            is ApiResult.Success -> current.withUsage(res.data)
            is ApiResult.Error -> current.copy(error = res.message)
            ApiResult.Loading -> current
        }

    /** Le os planos do projeto (com cache simples). */
    suspend fun plans(forceReload: Boolean = false): List<Plan> {
        cachedPlans?.takeIf { !forceReload }?.let { return it }
        return when (val res = repository.getPlans()) {
            is ApiResult.Success -> res.data.also { cachedPlans = it }
            else -> cachedPlans ?: emptyList()
        }
    }

    /**
     * Dispara a compra de um plano via RevenueCat ([PurchaseManager]). O entitlement efetivo so muda
     * quando o webhook RevenueCat -> admin-api grava (doc 03 §6); apos sucesso, chame [refresh].
     *
     * @return [PurchaseResult] (Success/Error/Cancelled). Lanca IllegalStateException se o plano nao
     *   tiver `storeProductId` ou se purchase nao estiver inicializado.
     */
    suspend fun purchase(plan: Plan): PurchaseResult {
        val productId = plan.storeProductId
            ?: error("Plano '${plan.plano}' sem storeProductId — nao da pra comprar via loja.")
        val repo = PurchaseManager.repository
            ?: error("PurchaseManager nao inicializado (MonetizationConfig sem purchase).")
        return repo.purchase(productId)
    }

    /** Restaura compras anteriores (loja). Apos sucesso, chame [refresh]. */
    suspend fun restore() = PurchaseManager.repository?.restorePurchases()

    /**
     * Verifica server-side (abordagem B) se o consumo de [feature] pode prosseguir, ANTES de
     * efetiva-lo. Delega ao admin-api (`POST /monet/{slug}/assert`) via [EntitlementRepository].
     *
     * Retorna o [AssertResult] cru (Allowed/Denied/Failed). Para ja refletir o paywall no estado,
     * use [assertUsageInto], que devolve o proximo [EntitlementState].
     */
    suspend fun assertUsage(feature: String, currentCount: Int, amount: Int = 1): AssertResult =
        repository.assertUsage(feature, currentCount, amount)

    /**
     * Igual a [assertUsage], mas ja embute o resultado no [EntitlementState]:
     * - [AssertResult.Denied] -> `current.showingPaywall(quota)` (a tela abre o Paywall).
     * - [AssertResult.Failed] -> `current.copy(error = ...)` (estado degradado).
     * - [AssertResult.Allowed] -> `current` inalterado.
     *
     * Devolve um par `(proximoEstado, permitido)`; o app so prossegue com o consumo se `permitido`.
     */
    suspend fun assertUsageInto(
        current: EntitlementState,
        feature: String,
        currentCount: Int,
        amount: Int = 1
    ): Pair<EntitlementState, Boolean> =
        when (val res = repository.assertUsage(feature, currentCount, amount)) {
            AssertResult.Allowed -> current to true
            is AssertResult.Denied -> current.showingPaywall(res.quota) to false
            is AssertResult.Failed -> current.copy(error = res.message) to false
        }
}
