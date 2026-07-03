package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.coroutines.flow.Flow

/**
 * Interface para operacoes de compra e assinatura.
 */
interface PurchaseRepository {

    /** Flow que emite o estado atual da assinatura. */
    val subscriptionState: Flow<SubscriptionInfo>

    /** Verifica se o usuario tem assinatura premium ativa. */
    suspend fun isPremium(): Boolean

    /**
     * Retorna os pacotes de assinatura da camada uniforme do RevenueCat (Offering -> Packages),
     * gold-standard. Le o offering [PurchaseConfig.offeringId] (fallback: offering `current`) e
     * mapeia cada `Package` disponivel para [PurchasePackage] (preco JA formatado pela loja). O app
     * compra por [purchasePackage] — nunca pelo ID cru de produto. Falha de rede/loja -> [Result.failure].
     */
    suspend fun getOfferings(): Result<List<PurchasePackage>>

    /**
     * Compra um pacote de assinatura pelo [PurchasePackage.packageId] (camada Offering/Package do
     * RevenueCat). Atualiza [subscriptionState] a partir do `customerInfo` retornado. Chame
     * [getOfferings] antes (para popular o cache de pacotes); se o pacote nao estiver em cache, o
     * repositorio tenta recarregar os offerings automaticamente.
     */
    suspend fun purchasePackage(packageId: String): PurchaseResult

    /**
     * Retorna os produtos disponiveis para compra.
     *
     * @deprecated Assinaturas agora usam [getOfferings] (Offerings/Packages do RevenueCat). Permanece
     *   funcional apenas para consumiveis/pay-per-action ([purchaseConsumable]).
     */
    @Deprecated(
        "Assinaturas usam getOfferings() (Offerings/Packages). getProducts() so p/ consumiveis.",
        ReplaceWith("getOfferings()")
    )
    suspend fun getProducts(): Result<List<PurchaseProduct>>

    /**
     * Compra um produto pelo ID cru.
     *
     * @deprecated Assinaturas agora usam [purchasePackage] (Offerings/Packages do RevenueCat).
     *   Permanece funcional apenas para fluxos consumiveis legados.
     */
    // Sem ReplaceWith: productId (id cru da loja) != packageId (identifier do Package);
    // um quick-fix automatico geraria chamada incorreta. Migrar manualmente para getOfferings()+purchasePackage.
    @Deprecated(
        "Assinaturas usam purchasePackage(packageId) via getOfferings() (Offerings/Packages)."
    )
    suspend fun purchase(productId: String): PurchaseResult

    /**
     * Compra um produto CONSUMIVEL (one-time / pay-per-action). Diferente de [purchase], nao depende
     * de entitlement: devolve a transacao da loja (transactionId/productId) para o app enviar a
     * admin-api, que valida e libera a acao. NAO altera [subscriptionState].
     */
    suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult

    /** Restaura compras anteriores. */
    suspend fun restorePurchases(): RestoreResult

    /** Retorna a info atual da assinatura. */
    suspend fun getSubscriptionInfo(): SubscriptionInfo

    /** Sincroniza o estado com o backend. Chamar no app launch e ao voltar do background. */
    suspend fun syncSubscriptionState()
}
