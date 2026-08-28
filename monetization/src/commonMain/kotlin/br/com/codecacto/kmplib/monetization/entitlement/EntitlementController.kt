package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import kotlin.time.TimeSource

/**
 * Resultado da leitura da oferta do projeto ([EntitlementController.plansResult]).
 *
 * Separar "leitura ok" de "leitura falhou" e o que permite ao paywall reagir: com [Unavailable] o app
 * cai no fallback da loja e ALERTA; com [Available] vazio, o projeto realmente nao tem plano ativo (e
 * mostrar tela vazia esta correto).
 */
sealed interface PlansResult {
    /** A leitura funcionou. [plans] pode estar vazia — significa "nenhum plano ativo", de verdade. */
    data class Available(val plans: List<Plan>, val fromCache: Boolean) : PlansResult

    /** A leitura falhou (rede, 401, 5xx). [message] e a causa tecnica, quando houver. */
    data class Unavailable(val message: String?) : PlansResult
}

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
    private val repository: EntitlementRepository,
    /**
     * TTL do cache em memoria dos planos (default 60s, igual ao [AdminApiEntitlementRepository]).
     * `0` desabilita o cache.
     *
     * Antes da 2.79.0 este cache **nao expirava**: a lista era guardada para sempre dentro da sessao
     * do app e so `forceReload` a derrubava — por isso ligar/desligar um plano no admin central so
     * aparecia com swipe-refresh ou matando o app.
     */
    private val plansCacheTtlMillis: Long = DEFAULT_PLANS_CACHE_TTL_MILLIS
) {

    private val timeSource = TimeSource.Monotonic

    /** Plano(s) do catalogo do projeto (para o Paywall). Cache leve com TTL ([plansCacheTtlMillis]). */
    private var cachedPlans: List<Plan>? = null
    private var cachedPlansMark: TimeSource.Monotonic.ValueTimeMark? = null

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

    /**
     * Le os planos do projeto (cache com TTL). **Nao distingue falha de "nenhum plano ativo"** — as
     * duas coisas chegam como lista vazia. Para decidir fallback de paywall, use [plansResult].
     */
    suspend fun plans(forceReload: Boolean = false): List<Plan> =
        when (val res = plansResult(forceReload)) {
            is PlansResult.Available -> res.plans
            is PlansResult.Unavailable -> emptyList()
        }

    /**
     * Le os planos do projeto dizendo **se a leitura funcionou**.
     *
     * Existe porque `emptyList()` e ambiguo e a ambiguidade custa dinheiro: "o admin-api recusou por
     * falta de token" e "este projeto nao tem plano ativo" produzem a MESMA tela vazia (docs/16 §A-24
     * do Super 8). Com [PlansResult.Unavailable] o app pode cair no fallback da loja
     * (`toPaywallPlansFromStore`) e **alertar** (`PaymentAlertKind.OfertaCentralIndisponivel`) em vez
     * de mostrar um paywall morto.
     *
     * Cache: uma leitura bem-sucedida vale por [plansCacheTtlMillis]; falha **nao** e cacheada, mas um
     * cache ainda valido e servido em vez do erro (degradacao segura — nunca concede nada, so exibe).
     */
    suspend fun plansResult(forceReload: Boolean = false): PlansResult {
        if (!forceReload) freshCachedPlans()?.let { return PlansResult.Available(it, fromCache = true) }
        return when (val res = repository.getPlans()) {
            is ApiResult.Success -> {
                cachedPlans = res.data
                cachedPlansMark = timeSource.markNow()
                PlansResult.Available(res.data, fromCache = false)
            }
            is ApiResult.Error ->
                freshCachedPlans()?.let { PlansResult.Available(it, fromCache = true) }
                    ?: PlansResult.Unavailable(res.message)
            ApiResult.Loading -> PlansResult.Unavailable(message = null)
        }
    }

    /** Descarta o cache de planos (ex.: apos mudanca de plano no admin ou pull-to-refresh). */
    fun invalidatePlansCache() {
        cachedPlans = null
        cachedPlansMark = null
    }

    private fun freshCachedPlans(): List<Plan>? {
        if (plansCacheTtlMillis <= 0L) return null
        val plans = cachedPlans ?: return null
        val mark = cachedPlansMark ?: return null
        return plans.takeIf { mark.elapsedNow().inWholeMilliseconds <= plansCacheTtlMillis }
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

    companion object {
        /** TTL default do cache de planos: 60s, o mesmo do [AdminApiEntitlementRepository]. */
        const val DEFAULT_PLANS_CACHE_TTL_MILLIS: Long = 60_000L
    }
}
