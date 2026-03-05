package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.datetime.Instant

/**
 * Estado atual da assinatura do usuario.
 */
data class SubscriptionInfo(
    val isActive: Boolean,
    val productId: String? = null,
    val expirationDate: Instant? = null,
    val willRenew: Boolean = false
)

/**
 * Produto disponivel para compra.
 */
data class PurchaseProduct(
    val id: String,
    val title: String,
    val description: String,
    val price: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val subscriptionPeriod: SubscriptionPeriod? = null
)

/**
 * Resultado de uma operacao de compra.
 */
sealed class PurchaseResult {
    data class Success(val subscriptionInfo: SubscriptionInfo) : PurchaseResult()
    data class Error(val message: String, val code: PurchaseErrorCode) : PurchaseResult()
    data object Cancelled : PurchaseResult()
}

/**
 * Resultado de uma restauracao de compras.
 */
sealed class RestoreResult {
    data class Success(val subscriptionInfo: SubscriptionInfo) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
    data object NoPurchasesToRestore : RestoreResult()
}

enum class PurchaseErrorCode {
    NETWORK_ERROR,
    STORE_ERROR,
    PRODUCT_NOT_FOUND,
    PAYMENT_PENDING,
    PAYMENT_DECLINED,
    ALREADY_OWNED,
    UNKNOWN
}
