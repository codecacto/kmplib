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
        // Build sem monetizacao configurada: e defeito de CONFIGURACAO, nao "erro desconhecido"
        // (ate a 2.89.0 saia UNKNOWN, indistinguivel de uma falha real da loja).
            ?: ConsumablePurchaseResult.Error(
                "purchase nao inicializado",
                PurchaseErrorCode.CONFIGURATION_ERROR,
            )

    /** Identifica o app user na loja (ver [PurchaseRepository.identify]). */
    internal suspend fun identify(appUserId: String): Result<Unit> =
        _repository?.identify(appUserId)
            ?: Result.failure(
                PurchaseIdentityException(
                    PurchaseIdentityError.NOT_CONFIGURED,
                    "monetizacao sem purchase configurado"
                )
            )

    /**
     * Volta o app user para anonimo (ver [PurchaseRepository.resetIdentity]).
     *
     * Sem purchase configurado devolve **sucesso**: nao ha identidade na loja para desfazer, e o
     * logout do app nao pode falhar por causa de uma loja que nem existe neste build.
     */
    internal suspend fun resetIdentity(): Result<Unit> =
        _repository?.resetIdentity() ?: Result.success(Unit)

    /** App user id corrente na loja (diagnostico). */
    internal fun currentAppUserId(): String? = _repository?.currentAppUserId()

    /**
     * Costura interna (testes / repositorio alternativo): injeta o [repository] sem passar pelo
     * [PurchaseInitializer], que toca o SDK nativo e nao roda em unit test. Nao e visivel aos apps.
     */
    internal fun initializeWith(repository: PurchaseRepository) {
        _repository = repository
        _initialized.value = true
    }

    internal fun reset() {
        _repository = null
        _initialized.value = false
    }
}
