package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Gerenciador central de compras/assinaturas.
 *
 * Nao deve ser usado diretamente pelo app — use [MonetizationManager] como ponto de entrada.
 */
object PurchaseManager {
    private const val TAG = "PurchaseManager"

    private val _currentRepository = MutableStateFlow<PurchaseRepository?>(null)
    private val _repository: PurchaseRepository? get() = _currentRepository.value
    private val _initialized = MutableStateFlow(false)

    /**
     * O repositório **corrente**, observável (2.251.0). [initializeWith] (o `PurchaseTestHooks`) e
     * [reset] trocam o repositório depois da inicialização; quem assinou o `subscriptionState` de um
     * repositório fixo continuaria ouvindo o ANTIGO. O `MonetizationManager` assina este fluxo e
     * troca junto (`flatMapLatest`).
     */
    val currentRepository: StateFlow<PurchaseRepository?> = _currentRepository.asStateFlow()

    /** Repository para operacoes de compra. Null se o modo nao inclui purchase. */
    val repository: PurchaseRepository?
        get() = _repository

    /** Flow do estado da assinatura. */
    val subscriptionState: Flow<SubscriptionInfo>
        get() = _repository?.subscriptionState
            ?: MutableStateFlow(SubscriptionInfo(isActive = false)).asStateFlow()

    /**
     * Se o [subscriptionState] corrente já é leitura da loja (2.249.0) — ver
     * [PurchaseRepository.subscriptionReadState]. Sem repositório (build sem billing) responde
     * [SubscriptionReadState.FAILED]: não há loja a esperar.
     */
    val subscriptionReadState: Flow<SubscriptionReadState>
        get() = _repository?.subscriptionReadState
            ?: MutableStateFlow(SubscriptionReadState.FAILED).asStateFlow()

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
        replaceRepository(RevenueCatPurchaseRepository(config))
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


    /**
     * Catálogo de itens NÃO-CONSUMÍVEIS (venda avulsa). Sem loja configurada devolve
     * [StoreItemsOutcome.Unavailable] — build sem billing não é "a loja está vazia".
     */
    internal suspend fun getStoreItems(productIds: List<String>): StoreItemsOutcome =
        _repository?.getStoreItems(productIds) ?: StoreItemsOutcome.Unavailable

    /** Compra um item NÃO-CONSUMÍVEL (ver [PurchaseRepository.purchaseItem]). */
    internal suspend fun purchaseItem(productId: String): ItemPurchaseResult =
        _repository?.purchaseItem(productId)
            ?: ItemPurchaseResult.Failed(
                PurchaseErrorCode.CONFIGURATION_ERROR,
                "purchase nao inicializado",
            )

    /** Restaura as compras avulsas — N itens (ver [PurchaseRepository.restoreItems]). */
    internal suspend fun restoreItems(): ItemRestoreResult =
        _repository?.restoreItems()
            ?: ItemRestoreResult.Failed(
                PurchaseErrorCode.CONFIGURATION_ERROR,
                "purchase nao inicializado",
            )

    /** Itens que a loja já sabe que a pessoa possui, sem prompt (ver [PurchaseRepository.ownedItems]). */
    internal suspend fun ownedItems(): Result<StorePurchaseClaim> =
        _repository?.ownedItems()
            ?: Result.failure(
                PurchaseException(
                    PurchaseErrorCode.CONFIGURATION_ERROR,
                    "purchase nao inicializado",
                )
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
        replaceRepository(repository)
        _initialized.value = true
    }

    internal fun reset() {
        replaceRepository(null)
        _initialized.value = false
    }

    /**
     * Troca o repositório corrente. O que sai é **desligado** — no adaptador da RevenueCat, o listener
     * de `CustomerInfo` deixa de publicar e devolve o delegate anterior —, para não vazar estado entre
     * repositórios.
     */
    private fun replaceRepository(repository: PurchaseRepository?) {
        val previous = _currentRepository.value
        if (previous === repository) return
        (previous as? RevenueCatPurchaseRepository)?.detach()
        _currentRepository.value = repository
    }
}
