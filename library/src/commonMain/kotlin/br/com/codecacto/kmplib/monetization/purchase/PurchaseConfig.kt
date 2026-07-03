package br.com.codecacto.kmplib.monetization.purchase

/**
 * Configuracao para inicializar o sistema de compras (RevenueCat).
 *
 * **Assinaturas (gold-standard RevenueCat):** o app le a camada uniforme de **Offerings/Packages**
 * (ver [PurchaseRepository.getOfferings]/[PurchaseRepository.purchasePackage]). O offering lido e
 * o [offeringId] (default `"default"`); os IDs de produto divergentes por loja ficam SO nas lojas e
 * sao absorvidos pelo `Package` — o app NUNCA ve o ID cru. Por isso [products] NAO e mais usado para
 * assinatura (so para consumiveis/pay-per-action; ver [ProductConfig]).
 *
 * @param androidApiKey API key do RevenueCat para Google Play
 * @param iosApiKey API key do RevenueCat para App Store
 * @param entitlementId ID do entitlement no dashboard do RevenueCat (default `"premium"`)
 * @param offeringId ID do offering de assinatura no dashboard do RevenueCat (default `"default"`);
 *   se ausente na resposta, o repositorio usa o offering `current`.
 * @param products Lista de produtos CONSUMIVEIS (pay-per-action). Assinaturas NAO usam esta lista.
 * @param debugMode Se true, usa LogLevel.DEBUG no RevenueCat
 */
data class PurchaseConfig(
    val androidApiKey: String,
    val iosApiKey: String,
    val entitlementId: String = "premium",
    val offeringId: String = "default",
    val products: List<ProductConfig> = emptyList(),
    val debugMode: Boolean = false
)

/**
 * Configuracao de um produto CONSUMIVEL (pay-per-action / one-time nao-renovavel).
 *
 * **NAO serve mais a assinaturas** — estas vem da camada de Offerings/Packages do RevenueCat
 * ([PurchaseRepository.getOfferings]), nunca de uma lista de IDs. Use [ProductConfig] apenas para
 * declarar os produtos consumiveis que o app compra via
 * [PurchaseRepository.purchaseConsumable] (cobranca por acao validada server-side na admin-api).
 *
 * @param id ID do produto consumivel nas lojas (mesmo id no Google Play e App Store).
 * @param period Legado; ignorado para consumiveis. Mantido para compatibilidade de assinatura de API.
 */
data class ProductConfig(
    val id: String,
    val period: SubscriptionPeriod? = null
)

enum class SubscriptionPeriod {
    MONTHLY,
    SEMI_ANNUAL,
    ANNUAL
}
