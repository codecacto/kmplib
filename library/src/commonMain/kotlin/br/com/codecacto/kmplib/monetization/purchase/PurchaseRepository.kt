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

    /** Retorna os produtos disponiveis para compra. */
    suspend fun getProducts(): Result<List<PurchaseProduct>>

    /** Compra um produto pelo ID. */
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
