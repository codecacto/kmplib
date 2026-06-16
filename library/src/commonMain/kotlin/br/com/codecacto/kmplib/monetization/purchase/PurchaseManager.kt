package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Gerenciador central de compras/assinaturas.
 *
 * Nao deve ser usado diretamente pelo app — use [MonetizationManager] como ponto de entrada.
 */
object PurchaseManager {
    private const val TAG = "PurchaseManager"

    private var _repository: PurchaseRepository? = null
    private val _initialized = MutableStateFlow(false)

    /** Repository para operacoes de compra. Null se o modo nao inclui purchase. */
    val repository: PurchaseRepository?
        get() = _repository

    /** Flow do estado da assinatura. */
    val subscriptionState: Flow<SubscriptionInfo>
        get() = _repository?.subscriptionState
            ?: MutableStateFlow(SubscriptionInfo(isActive = false)).asStateFlow()

    /** Flow que indica se o usuario e premium. */
    val isPremium: Flow<Boolean>
        get() = _repository?.subscriptionState?.map { it.isActive }
            ?: MutableStateFlow(false).asStateFlow()

    internal fun initialize(config: PurchaseConfig, userId: String? = null) {
        if (_initialized.value) {
            AppLogger.w(TAG, "PurchaseManager ja inicializado")
            return
        }

        PurchaseInitializer.initialize(config, userId)
        _repository = RevenueCatPurchaseRepository(config)
        _initialized.value = true

        AppLogger.d(TAG, "PurchaseManager inicializado com ${config.products.size} produtos")
    }

    /**
     * Compra um produto CONSUMIVEL (one-time / pay-per-action). Delega ao repository.
     * Nao altera o estado de assinatura.
     */
    internal suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
        _repository?.purchaseConsumable(productId)
            ?: ConsumablePurchaseResult.Error("purchase nao inicializado", PurchaseErrorCode.UNKNOWN)

    internal fun reset() {
        _repository = null
        _initialized.value = false
    }
}
