package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.CacheFetchPolicy
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

    override suspend fun isPremium(): Boolean {
        return getSubscriptionInfo().isActive
    }

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

    override suspend fun purchase(productId: String): PurchaseResult {
        val product = cachedProducts.find { it.id == productId }
            ?: return PurchaseResult.Error(
                message = "Produto nao encontrado: $productId",
                code = PurchaseErrorCode.PRODUCT_NOT_FOUND
            )

        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.purchase(
                storeProduct = product,
                onError = { error, userCancelled ->
                    if (userCancelled) {
                        continuation.resume(PurchaseResult.Cancelled)
                    } else {
                        continuation.resume(
                            PurchaseResult.Error(
                                message = error.message,
                                code = mapErrorCode(error.message)
                            )
                        )
                    }
                },
                onSuccess = { _, customerInfo ->
                    val subscriptionInfo = customerInfo.toSubscriptionInfo()
                    _subscriptionState.value = subscriptionInfo
                    continuation.resume(PurchaseResult.Success(subscriptionInfo))
                }
            )
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        return suspendCancellableCoroutine { continuation ->
            Purchases.sharedInstance.restorePurchases(
                onError = { error ->
                    AppLogger.e(TAG, "Erro ao restaurar compras: ${error.message}")
                    continuation.resume(RestoreResult.Error(error.message))
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

    private fun mapErrorCode(message: String): PurchaseErrorCode {
        val lower = message.lowercase()
        return when {
            lower.contains("network") -> PurchaseErrorCode.NETWORK_ERROR
            lower.contains("store") -> PurchaseErrorCode.STORE_ERROR
            lower.contains("pending") -> PurchaseErrorCode.PAYMENT_PENDING
            lower.contains("declined") -> PurchaseErrorCode.PAYMENT_DECLINED
            lower.contains("already") && lower.contains("owned") -> PurchaseErrorCode.ALREADY_OWNED
            else -> PurchaseErrorCode.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "RevenueCatPurchaseRepo"
    }
}
