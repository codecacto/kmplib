package br.com.codecacto.kmplib.monetization.purchase

/**
 * Configuracao para inicializar o sistema de compras (RevenueCat).
 *
 * @param androidApiKey API key do RevenueCat para Google Play
 * @param iosApiKey API key do RevenueCat para App Store
 * @param entitlementId ID do entitlement no dashboard do RevenueCat
 * @param products Lista de produtos disponiveis para compra
 * @param debugMode Se true, usa LogLevel.DEBUG no RevenueCat
 */
data class PurchaseConfig(
    val androidApiKey: String,
    val iosApiKey: String,
    val entitlementId: String = "premium",
    val products: List<ProductConfig> = emptyList(),
    val debugMode: Boolean = false
)

/**
 * Configuracao de um produto individual.
 *
 * @param id ID do produto nas lojas (deve ser o mesmo no Google Play e App Store)
 * @param period Periodo da assinatura
 */
data class ProductConfig(
    val id: String,
    val period: SubscriptionPeriod
)

enum class SubscriptionPeriod {
    MONTHLY,
    SEMI_ANNUAL,
    ANNUAL
}
