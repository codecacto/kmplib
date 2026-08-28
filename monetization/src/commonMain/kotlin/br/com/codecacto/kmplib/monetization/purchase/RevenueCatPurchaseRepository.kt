package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.CacheFetchPolicy
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.StoreProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class RevenueCatPurchaseRepository(
    private val config: PurchaseConfig
) : PurchaseRepository {

    private val _subscriptionState = MutableStateFlow(SubscriptionInfo(isActive = false))
    override val subscriptionState: Flow<SubscriptionInfo> = _subscriptionState.asStateFlow()

    private var cachedProducts: List<StoreProduct> = emptyList()

    /** Cache dos `Package` do offering, por `identifier`, para o [purchasePackage]. */
    private var cachedPackages: Map<String, Package> = emptyMap()

    override suspend fun isPremium(): Boolean {
        return getSubscriptionInfo().isActive
    }

    override suspend fun getOfferings(): Result<List<PurchasePackage>> {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getOfferings(
                onError = { error ->
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao buscar offerings [$code]: ${error.message}")
                    continuation.resume(Result.failure(PurchaseException(code, error.message)))
                },
                onSuccess = { offerings ->
                    // Offering configurado (config.offeringId) com fallback para o `current`.
                    val offering = offerings.all[config.offeringId] ?: offerings.current
                    if (offering == null) {
                        AppLogger.w(TAG, "Offering '${config.offeringId}' ausente e sem `current`")
                        cachedPackages = emptyMap()
                        continuation.resume(Result.success(emptyList()))
                    } else {
                        val packages = offering.availablePackages
                        cachedPackages = packages.associateBy { it.identifier }
                        continuation.resume(Result.success(packages.map { it.toPurchasePackage() }))
                    }
                }
            )
        }
    }

    override suspend fun purchasePackage(packageId: String): PurchaseResult {
        // Recarrega os offerings se o pacote nao esta em cache (ex.: primeira compra sem getOfferings).
        val pkg = cachedPackages[packageId]
            ?: run {
                getOfferings()
                cachedPackages[packageId]
            }
            ?: return PurchaseResult.Error(
                message = "Pacote nao encontrado: $packageId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                packageToPurchase = pkg,
                onError = { error, userCancelled ->
                    continuation.resume(error.toPurchaseResult(userCancelled))
                },
                onSuccess = { _, customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    _subscriptionState.value = subscriptionInfo
                    continuation.resume(PurchaseResult.Success(subscriptionInfo))
                }
            )
        }
    }

    @Deprecated(
        "Assinaturas usam getOfferings() (Offerings/Packages). getProducts() so p/ consumiveis.",
        ReplaceWith("getOfferings()")
    )
    override suspend fun getProducts(): Result<List<PurchaseProduct>> {
        val productIds = config.products.map { it.id }
        if (productIds.isEmpty()) return Result.success(emptyList())

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getProducts(
                productIds = productIds,
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao buscar produtos: ${error.message}")
                    continuation.resume(Result.failure(IllegalStateException(error.message)))
                },
                onSuccess = { products ->
                    cachedProducts = products
                    continuation.resume(Result.success(products.map { it.toPurchaseProduct() }))
                }
            )
        }
    }

    @Deprecated(
        "Assinaturas usam purchasePackage(packageId) via getOfferings() (Offerings/Packages)."
    )
    @Suppress("DEPRECATION")
    override suspend fun purchase(productId: String): PurchaseResult {
        if (cachedProducts.none { it.id == productId }) {
            getProducts()
        }

        val product = cachedProducts.find { it.id == productId }
            ?: return PurchaseResult.Error(
                message = "Produto nao encontrado: $productId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                storeProduct = product,
                onError = { error, userCancelled ->
                    continuation.resume(error.toPurchaseResult(userCancelled))
                },
                onSuccess = { _, customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    _subscriptionState.value = subscriptionInfo
                    continuation.resume(PurchaseResult.Success(subscriptionInfo))
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult {
        if (cachedProducts.none { it.id == productId }) {
            getProducts()
        }

        val product = cachedProducts.find { it.id == productId }
            ?: return ConsumablePurchaseResult.Error(
                message = "Produto nao encontrado: $productId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                storeProduct = product,
                onError = { error, userCancelled ->
                    continuation.resume(
                        when (val failure = error.toPurchaseFailure(userCancelled)) {
                            is PurchaseFailure.Cancelled -> ConsumablePurchaseResult.Cancelled
                            is PurchaseFailure.Failed -> ConsumablePurchaseResult.Error(
                                message = failure.message,
                                code = failure.code,
                            )
                        }
                    )
                },
                onSuccess = { storeTransaction, _ ->
                    val transactionId = storeTransaction.transactionId
                    val resolvedProductId =
                        storeTransaction.productIds.firstOrNull() ?: productId

                    if (transactionId.isNullOrBlank()) {
                        continuation.resume(
                            ConsumablePurchaseResult.Error(
                                message = "transacao sem id",
                                code = PurchaseErrorCode.UNKNOWN
                            )
                        )
                    } else {
                        continuation.resume(
                            ConsumablePurchaseResult.Success(
                                transactionId = transactionId,
                                productId = resolvedProductId,
                                store = currentStore()
                            )
                        )
                    }
                }
            )
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.restorePurchases(
                onError = { error ->
                    // `restorePurchases` não informa "cancelou"; o código tipado é a única forma de
                    // não contar uma desistência como falha de restauração (que é alerta).
                    val code = error.code.toPurchaseErrorCode()
                    AppLogger.e(TAG, "Erro ao restaurar compras [$code]: ${error.message}")
                    continuation.resume(RestoreResult.Error(error.message, code))
                },
                onSuccess = { customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    _subscriptionState.value = subscriptionInfo

                    if (subscriptionInfo.isActive) {
                        continuation.resume(RestoreResult.Success(subscriptionInfo))
                    } else {
                        continuation.resume(RestoreResult.NoPurchasesToRestore)
                    }
                }
            )
        }
    }

    override suspend fun identify(appUserId: String): Result<Unit> {
        val id = when (val check = PurchaseIdentity.check(appUserId)) {
            is AppUserIdCheck.Invalid -> {
                // Contrato quebrado de quem chama: a partir daqui nenhuma compra cai no tenant certo.
                AppLogger.e(TAG, "identify recusado: ${check.reason}", null)
                return identityFailure(PurchaseIdentityError.INVALID_APP_USER_ID, check.reason)
            }

            is AppUserIdCheck.Valid -> check.appUserId
        }
        if (PurchaseIdentity.looksLikePersonalData(id)) {
            // Não bloqueia (sem identidade a compra iria para o tenant errado, que é pior), mas o id
            // trafega para webhook/dashboard de terceiro: dado pessoal aqui é vazamento evitável.
            AppLogger.w(TAG, "appUserId parece dado pessoal — use um id opaco e estavel (LGPD)")
        }
        if (!Purchases.isConfigured) {
            AppLogger.w(TAG, "identify ignorado: RevenueCat nao configurado")
            return identityFailure(PurchaseIdentityError.NOT_CONFIGURED, "RevenueCat nao configurado")
        }
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logIn(
                newAppUserID = id,
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao identificar app user: ${error.message}", null)
                    continuation.resume(
                        identityFailure(error.code.toIdentityError(), error.message)
                    )
                },
                onSuccess = { customerInfo, created ->
                    onIdentityChanged(customerInfo)
                    AppLogger.d(TAG, "App user identificado no RevenueCat (novo=$created)")
                    continuation.resume(Result.success(Unit))
                }
            )
        }
    }

    override suspend fun resetIdentity(): Result<Unit> {
        if (!Purchases.isConfigured) return Result.success(Unit)
        // Anonimizar quem já é anônimo é o estado desejado — o SDK devolveria
        // `LogOutWithAnonymousUserError`, um falso incidente de pagamento no logout de todo usuário
        // que nunca chegou a ser identificado.
        if (Purchases.sharedInstance.isAnonymous) {
            AppLogger.d(TAG, "resetIdentity no-op: app user ja anonimo")
            return Result.success(Unit)
        }
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.logOut(
                onError = { error ->
                    AppLogger.w(TAG, "Erro ao anonimizar app user: ${error.message}")
                    continuation.resume(
                        identityFailure(error.code.toIdentityError(), error.message)
                    )
                },
                onSuccess = { customerInfo ->
                    onIdentityChanged(customerInfo)
                    continuation.resume(Result.success(Unit))
                }
            )
        }
    }

    override fun currentAppUserId(): String? =
        if (Purchases.isConfigured) Purchases.sharedInstance.appUserID else null

    /**
     * Efeito colateral obrigatório de toda troca de sujeito (login/logout na loja):
     *
     * 1. **derruba o catálogo em cache** — a oferta do RevenueCat pode ser personalizada por app user
     *    (Targeting/Experiments), e cada `Package`/`StoreProduct` carrega o contexto de offering que
     *    atribui a compra; comprar um objeto buscado para o sujeito anterior atribui a receita errado;
     * 2. **republica o entitlement** do novo sujeito, para a UI não continuar mostrando o premium de
     *    quem saiu (nem esconder o de quem entrou).
     */
    private fun onIdentityChanged(
        customerInfo: com.revenuecat.purchases.kmp.models.CustomerInfo
    ) {
        cachedPackages = emptyMap()
        cachedProducts = emptyList()
        _subscriptionState.value = customerInfo.toSubscriptionInfo()
    }

    private fun identityFailure(reason: PurchaseIdentityError, message: String): Result<Unit> =
        Result.failure(PurchaseIdentityException(reason, message))

    /**
     * Mapeia o código TIPADO do SDK (não a mensagem, que é localizada) para o motivo da lib. O caller
     * usa isso para decidir o que vira alerta de pagamento e o que é só transitório.
     */
    private fun PurchasesErrorCode.toIdentityError(): PurchaseIdentityError = when (this) {
        PurchasesErrorCode.NetworkError,
        PurchasesErrorCode.OfflineConnectionError -> PurchaseIdentityError.NETWORK

        PurchasesErrorCode.InvalidAppUserIdError -> PurchaseIdentityError.INVALID_APP_USER_ID

        PurchasesErrorCode.ConfigurationError,
        PurchasesErrorCode.InvalidCredentialsError -> PurchaseIdentityError.NOT_CONFIGURED

        PurchasesErrorCode.StoreProblemError,
        PurchasesErrorCode.UnknownBackendError,
        PurchasesErrorCode.UnexpectedBackendResponseError -> PurchaseIdentityError.STORE

        else -> PurchaseIdentityError.UNKNOWN
    }

    override suspend fun getSubscriptionInfo(): SubscriptionInfo {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.getCustomerInfo(
                fetchPolicy = CacheFetchPolicy.CACHED_OR_FETCHED,
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao buscar subscription info: ${error.message}")
                    continuation.resume(SubscriptionInfo(isActive = false))
                },
                onSuccess = { customerInfo ->
                    continuation.resume(customerInfo.toSubscriptionInfo())
                }
            )
        }
    }

    override suspend fun syncSubscriptionState() {
        val info = getSubscriptionInfo()
        _subscriptionState.value = info
        AppLogger.d(TAG, "Subscription state synced: active=${info.isActive}")
    }

    private fun com.revenuecat.purchases.kmp.models.CustomerInfo.toSubscriptionInfo(): SubscriptionInfo {
        val premiumEntitlement = entitlements.active[config.entitlementId]

        return if (premiumEntitlement != null) {
            SubscriptionInfo(
                isActive = true,
                productId = premiumEntitlement.productIdentifier,
                expirationDate = null,
                willRenew = premiumEntitlement.willRenew
            )
        } else {
            SubscriptionInfo(isActive = false)
        }
    }

    /** Mapeia um `Package` do offering para o DTO uniforme [PurchasePackage] da lib. */
    private fun Package.toPurchasePackage(): PurchasePackage {
        val product = storeProduct
        val type = packageType.toPurchasePackageType()
        return PurchasePackage(
            packageId = identifier,
            packageType = type,
            storeProductId = product.id,
            priceLabel = product.price.formatted,
            priceAmountMicros = product.price.amountMicros,
            currencyCode = product.price.currencyCode,
            durationMonths = resolveDurationMonths(type, product)
        )
    }

    private fun PackageType.toPurchasePackageType(): PurchasePackageType = when (this) {
        PackageType.MONTHLY -> PurchasePackageType.MONTHLY
        PackageType.SIX_MONTH -> PurchasePackageType.SIX_MONTH
        PackageType.ANNUAL -> PurchasePackageType.ANNUAL
        PackageType.LIFETIME -> PurchasePackageType.LIFETIME
        // WEEKLY / TWO_MONTH / THREE_MONTH / CUSTOM / UNKNOWN
        else -> PurchasePackageType.OTHER
    }

    /**
     * Deriva a duracao em meses do pacote: direta para os tipos padronizados; para [OTHER]/CUSTOM
     * tenta o periodo de assinatura do produto (`period.valueInMonths`); vitalicio/indeterminado -> null.
     */
    private fun resolveDurationMonths(
        type: PurchasePackageType,
        product: StoreProduct
    ): Int? = when (type) {
        PurchasePackageType.MONTHLY -> 1
        PurchasePackageType.SIX_MONTH -> 6
        PurchasePackageType.ANNUAL -> 12
        PurchasePackageType.LIFETIME -> null
        PurchasePackageType.OTHER -> product.period?.valueInMonths?.toInt()?.takeIf { it > 0 }
    }

    private fun StoreProduct.toPurchaseProduct(): PurchaseProduct {
        val periodConfig = config.products.find { it.id == id }?.period
        return PurchaseProduct(
            id = id,
            title = title,
            description = localizedDescription ?: title,
            price = price.formatted,
            priceAmountMicros = price.amountMicros,
            currencyCode = price.currencyCode,
            subscriptionPeriod = periodConfig
        )
    }

    /**
     * Traduz o erro do SDK no resultado de compra da lib. Desistência vira [PurchaseResult.Cancelled]
     * (nunca `Error`), e o motivo da falha real sai do **código tipado** — ver [toPurchaseFailure].
     */
    private fun PurchasesError.toPurchaseResult(
        userCancelled: Boolean
    ): PurchaseResult = when (val failure = toPurchaseFailure(userCancelled)) {
        is PurchaseFailure.Cancelled -> PurchaseResult.Cancelled
        is PurchaseFailure.Failed -> {
            AppLogger.e(TAG, "Compra falhou [${failure.code}]: ${failure.message}")
            PurchaseResult.Error(message = failure.message, code = failure.code)
        }
    }

    companion object {
        private const val TAG = "RevenueCatPurchaseRepo"
    }
}
