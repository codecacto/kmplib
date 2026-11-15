package br.com.codecacto.kmplib.monetization.purchase

/**
 * De onde saiu o [ConsumablePurchase.transactionId] (2.365.0).
 *
 * - [STORE] — o id da **própria loja**: o `orderId` do Google Play (`GPA.…`) ou o
 *   `transactionIdentifier` do StoreKit. É o preferido: é o que aparece no extrato da loja e no
 *   webhook da RevenueCat como `store_transaction_id`.
 * - [REVENUECAT] — a loja não informou id (acontece no Play com código promocional e em algumas
 *   compras de teste) e o id veio da transação que a RevenueCat registrou para esta compra
 *   (`CustomerInfo.nonSubscriptionTransactions`). O backend concilia pela REST da RevenueCat.
 */
enum class ConsumableTransactionIdSource { STORE, REVENUECAT }

/**
 * **O recibo de uma compra CONSUMÍVEL** (2.365.0) — o que o app manda ao NOSSO servidor para ele
 * conferir com a RevenueCat e creditar aquela compra (uma vez só por [transactionId]).
 *
 * A lib **não credita nada**: este objeto é alegação de cliente. Quem credita é o backend, avisado
 * pelo webhook da RevenueCat e, como rede de segurança, por este recibo. A chave de idempotência do
 * crédito é o [transactionId].
 *
 * @param productId o produto que a loja cobrou (o da transação; o pedido, se ela não informou).
 * @param transactionId o id da transação — o da loja quando houver, senão o da RevenueCat
 *   ([transactionIdSource]). Nunca em branco.
 * @param storeTransactionId o id da loja, cru (`null` quando ela não informou).
 * @param revenueCatTransactionId o id da transação na RevenueCat, quando já veio no `CustomerInfo`
 *   desta compra (`null` se não veio).
 * @param store a loja deste aparelho.
 * @param appUserId o app user da loja no momento da compra — a conta logada (a compra só segue com a
 *   identidade ligada, ver [br.com.codecacto.kmplib.monetization.MonetizationManager.purchaseConsumableItem]).
 * @param purchasedAtMillis instante informado pela loja (epoch millis, UTC). Informativo.
 */
data class ConsumablePurchase(
    val productId: String,
    val transactionId: String,
    val transactionIdSource: ConsumableTransactionIdSource,
    val storeTransactionId: String?,
    val revenueCatTransactionId: String?,
    val store: PurchaseStore,
    val appUserId: String,
    val purchasedAtMillis: Long?,
)

/**
 * **Resultado da compra CONSUMÍVEL por id** (2.365.0) —
 * [br.com.codecacto.kmplib.monetization.MonetizationManager.purchaseConsumableItem].
 *
 * | Desfecho | O que o app faz |
 * |---|---|
 * | [Success] | manda [Success.purchase] ao servidor e espera o crédito |
 * | [Pending] | avisa "pagamento em análise" e **não libera nada** — o crédito chega pelo webhook |
 * | [CompletedWithoutReceipt] | a loja cobrou e não devolveu id: pede ao servidor conciliar pela conta; **não cobre de novo** |
 * | [IdentityNotBound] | a loja não está ligada à conta logada: nada foi cobrado; "não foi possível vincular a compra à sua conta" |
 * | [Cancelled] | nada — desistência não é falha nem alerta |
 * | [Failed] | mostra [PurchaseErrorCode.userMessage]; alerta se [PurchaseErrorCode.isPaymentIncident] |
 *
 * Novo selado de propósito (o [ConsumablePurchaseResult] antigo não tem `Pending` e acrescentar um
 * ramo quebraria o `when` exaustivo de quem já o usa).
 */
sealed interface ConsumableItemPurchaseResult {

    /** A loja cobrou e confirmou; [purchase] é o recibo a conciliar. */
    data class Success(val purchase: ConsumablePurchase) : ConsumableItemPurchaseResult

    /**
     * Pagamento em análise pela loja (aprovação parental no Play, boleto, Ask to Buy). Não há recibo
     * e **não houve falha**. O webhook da RevenueCat avisa o servidor quando a loja confirmar.
     */
    data class Pending(val productId: String) : ConsumableItemPurchaseResult

    /**
     * A loja confirmou a compra mas **nenhum id de transação** veio — nem da loja nem da RevenueCat.
     * Raro; a cobrança EXISTE. O servidor concilia pela conta ([appUserId]) na REST da RevenueCat.
     */
    data class CompletedWithoutReceipt(
        val productId: String,
        val appUserId: String,
        val store: PurchaseStore,
    ) : ConsumableItemPurchaseResult

    /**
     * Recusada **antes** da loja: ela não está ligada à conta logada ([status] ≠
     * [StoreIdentityStatus.BOUND]). Comprar assim creditaria outra pessoa ou ninguém.
     */
    data class IdentityNotBound(val status: StoreIdentityStatus) : ConsumableItemPurchaseResult

    /** O usuário fechou a folha da loja (ou o portão de pais não passou). */
    data object Cancelled : ConsumableItemPurchaseResult

    /** Falha real. [message] é técnica (log); o texto de tela sai do [code]. */
    data class Failed(val code: PurchaseErrorCode, val message: String) : ConsumableItemPurchaseResult

    companion object {
        /**
         * Classifica uma falha do SDK (regra pura, testada): `PAYMENT_PENDING` chega como erro e é
         * [Pending]; `USER_CANCELLED` é [Cancelled]; o resto é [Failed]. `ALREADY_OWNED` fica em
         * [Failed] — consumível não é "possuído"; no Play ele indica compra anterior ainda não
         * consumida, e o caminho é tentar de novo depois que a RevenueCat consumir.
         */
        fun fromFailure(
            code: PurchaseErrorCode,
            message: String,
            productId: String,
        ): ConsumableItemPurchaseResult = when (code) {
            PurchaseErrorCode.PAYMENT_PENDING -> Pending(productId)
            PurchaseErrorCode.USER_CANCELLED -> Cancelled
            else -> Failed(code, message)
        }
    }
}

/**
 * Janela de tolerância para aceitar a transação da RevenueCat como sendo DESTA compra (5 min antes do
 * instante informado pela loja). Sem ela, uma compra anterior do mesmo produto seria devolvida como
 * recibo da nova — e o servidor recusaria o crédito como duplicado.
 */
internal const val CONSUMABLE_RC_MATCH_TOLERANCE_MILLIS: Long = 5 * 60 * 1000L

/**
 * Monta o resultado de uma compra consumível bem-sucedida (regra pura, testada sem loja).
 *
 * @param requestedProductId o id pedido.
 * @param storeProductIds os ids da `StoreTransaction` (o primeiro vale; vazio cai no pedido).
 * @param storeTransactionId o id da loja (`StoreTransaction.transactionId`).
 * @param storePurchaseTimeMillis instante da `StoreTransaction`.
 * @param revenueCatTransactions as transações não-assinatura do `CustomerInfo` desta compra.
 */
internal fun consumablePurchaseResultOf(
    requestedProductId: String,
    storeProductIds: List<String>,
    storeTransactionId: String?,
    storePurchaseTimeMillis: Long?,
    revenueCatTransactions: List<OwnedStoreItem>,
    store: PurchaseStore,
    appUserId: String,
): ConsumableItemPurchaseResult {
    val productId = storeProductIds.firstOrNull { it.isNotBlank() } ?: requestedProductId
    val storeId = storeTransactionId?.takeIf { it.isNotBlank() }
    val floor = storePurchaseTimeMillis?.takeIf { it > 0 }?.minus(CONSUMABLE_RC_MATCH_TOLERANCE_MILLIS)
    val rcId = revenueCatTransactions
        .asSequence()
        .filter { it.productId == productId && !it.transactionId.isNullOrBlank() }
        // Sem instante da loja não há como separar esta compra de uma anterior: só aceita a da RC
        // quando ela é a ÚNICA transação daquele produto.
        .filter { tx -> floor == null || (tx.purchasedAtMillis ?: 0L) >= floor }
        .toList()
        .let { candidatos ->
            if (floor == null && candidatos.size > 1) emptyList() else candidatos
        }
        .maxByOrNull { it.purchasedAtMillis ?: 0L }
        ?.transactionId
    val resolved = storeId ?: rcId
        ?: return ConsumableItemPurchaseResult.CompletedWithoutReceipt(productId, appUserId, store)
    return ConsumableItemPurchaseResult.Success(
        ConsumablePurchase(
            productId = productId,
            transactionId = resolved,
            transactionIdSource = if (storeId != null) {
                ConsumableTransactionIdSource.STORE
            } else {
                ConsumableTransactionIdSource.REVENUECAT
            },
            storeTransactionId = storeId,
            revenueCatTransactionId = rcId,
            store = store,
            appUserId = appUserId,
            purchasedAtMillis = storePurchaseTimeMillis?.takeIf { it > 0 },
        )
    )
}
