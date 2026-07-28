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

    /**
     * **Identifica o app user do RevenueCat** (`Purchases.logIn`) — a forma OFICIAL do fornecedor de
     * amarrar a compra a um sujeito conhecido depois do `configure`.
     *
     * Existe porque o `appUserId` do [PurchaseInitializer] só pode ser informado no **bootstrap**, e
     * o sujeito real da assinatura muitas vezes só é conhecido **depois do login** (ex.: produto
     * multi-tenant em que quem assina é a ORGANIZAÇÃO, resolvida por `GET /me`). Sem este passo o
     * webhook chega à central com o id anônimo/do usuário e o entitlement vai para o tenant errado —
     * a organização paga e continua bloqueada.
     *
     * Idempotente por natureza: chamar com o id já corrente é no-op do lado do SDK. Atualiza
     * [subscriptionState] com o `customerInfo` devolvido pelo login (o entitlement do novo sujeito).
     *
     * Tem implementação default (falha explícita) para não quebrar fakes/impls existentes — a
     * implementação real é a do RevenueCat.
     *
     * @param appUserId identificador estável do sujeito da assinatura (nunca um id anônimo/rotativo).
     */
    suspend fun identify(appUserId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("identify nao suportado por este repository"))

    /**
     * Volta o RevenueCat para um app user **anônimo** (`Purchases.logOut`). Chamar no logout, para o
     * próximo usuário do mesmo aparelho não herdar o entitlement de quem saiu.
     */
    suspend fun resetIdentity(): Result<Unit> =
        Result.failure(UnsupportedOperationException("resetIdentity nao suportado por este repository"))

    /** App user id corrente no SDK (diagnóstico/log). `null` quando o SDK não está configurado. */
    fun currentAppUserId(): String? = null

    /** Retorna a info atual da assinatura. */
    suspend fun getSubscriptionInfo(): SubscriptionInfo

    /** Sincroniza o estado com o backend. Chamar no app launch e ao voltar do background. */
    suspend fun syncSubscriptionState()
}
