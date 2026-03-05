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

    /** Restaura compras anteriores. */
    suspend fun restorePurchases(): RestoreResult

    /** Retorna a info atual da assinatura. */
    suspend fun getSubscriptionInfo(): SubscriptionInfo

    /** Sincroniza o estado com o backend. Chamar no app launch e ao voltar do background. */
    suspend fun syncSubscriptionState()
}
