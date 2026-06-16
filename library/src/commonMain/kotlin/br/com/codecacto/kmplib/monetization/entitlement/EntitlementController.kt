package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.RestoreResult
import kotlinx.coroutines.flow.first

/**
 * Reducer/orquestrador (NAO um ViewModel) que conecta [EntitlementRepository] (leitura, fonte de
 * verdade) e [PurchaseManager]/RevenueCat (compra) ao [EntitlementState] de uma tela MVI.
 *
 * Cada metodo recebe o estado atual e retorna o proximo — o ViewModel do app aplica o resultado via
 * `setState`. Reusa `MonetizationManager.isPremium` para o flag premium e
 * `PurchaseManager.repository?.purchase(storeProductId)` para a compra; nao recria billing.
 *
 * Padrao de uso no ViewModel:
 * ```kotlin
 * setState { copy(ent = controller.refresh(ent)) }
 * ```
 */
class EntitlementController(
    private val repository: EntitlementRepository,
) {
    /** Cache de planos para nao refazer a chamada a cada abertura do paywall. */
    private var plansCache: List<Plan>? = null

    /**
     * Recarrega o entitlement e o flag premium. Em caso de erro de rede, **mantem** o entitlement
     * anterior (leitura degradada) — nunca concede nada novo offline.
     */
    suspend fun refresh(current: EntitlementState): EntitlementState {
        val premium = currentPremium()
        return when (val res = repository.getEntitlement()) {
            is ApiResult.Success -> current.copy(entitlement = res.data, isPremium = premium)
            else -> current.copy(isPremium = premium)
        }
    }

    /** Recarrega o snapshot de uso de uma feature; em erro, mantem o estado. */
    suspend fun refreshUsage(current: EntitlementState, feature: String): EntitlementState =
        when (val res = repository.getUsage(feature)) {
            is ApiResult.Success -> current.withUsage(res.data)
            else -> current
        }

    /**
     * Retorna os planos disponiveis (cache em memoria apos a primeira leitura bem-sucedida).
     * Retorna lista vazia em caso de erro.
     */
    suspend fun plans(forceRefresh: Boolean = false): List<Plan> {
        if (!forceRefresh) plansCache?.let { return it }
        return when (val res = repository.getPlans()) {
            is ApiResult.Success -> res.data.also { plansCache = it }
            else -> plansCache ?: emptyList()
        }
    }

    /**
     * Dispara a compra de um [Plan] via RevenueCat (store), usando `plan.storeProductId`.
     *
     * Retorna o [PurchaseResult] cru para o app reagir (sucesso -> chamar [refresh] para refletir o
     * novo entitlement apos o webhook gravar no admin-api). Retorna `Cancelled` quando o plano nao
     * tem `storeProductId` (compra deve seguir por outro fluxo, ex.: web/AbacatePay).
     */
    suspend fun purchase(plan: Plan): PurchaseResult {
        val productId = plan.storeProductId ?: return PurchaseResult.Cancelled
        val repo = PurchaseManager.repository
            ?: return PurchaseResult.Error(
                message = "Compras nao disponiveis neste modo de monetizacao.",
                code = br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode.STORE_ERROR,
            )
        return repo.purchase(productId)
    }

    /** Restaura compras anteriores via RevenueCat. */
    suspend fun restore(): RestoreResult =
        PurchaseManager.repository?.restorePurchases()
            ?: RestoreResult.NoPurchasesToRestore

    private suspend fun currentPremium(): Boolean =
        runCatching { MonetizationManager.isPremium.first() }.getOrDefault(false)
}
