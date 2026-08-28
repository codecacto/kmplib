package br.com.codecacto.kmplib.monetization.purchase

import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Caminho do dinheiro sem cobertura até a 2.90.0 — e onde a classificação por SUBSTRING da mensagem
 * (localizada) fazia todo erro de compra virar `UNKNOWN` no aparelho em pt-BR.
 */
class PurchaseErrorMapperTest {

    // --- 1. Classificação pelo código tipado -----------------------------------------------------

    @Test
    fun `codigos de rede viram NETWORK_ERROR`() {
        listOf(
            PurchasesErrorCode.NetworkError,
            PurchasesErrorCode.OfflineConnectionError,
            PurchasesErrorCode.ProductRequestTimedOut,
            PurchasesErrorCode.ApiEndpointBlocked,
        ).forEach {
            assertEquals(PurchaseErrorCode.NETWORK_ERROR, it.toPurchaseErrorCode(), "código $it")
        }
    }

    @Test
    fun `problema de loja e de backend do fornecedor viram STORE_ERROR`() {
        listOf(
            PurchasesErrorCode.StoreProblemError,
            PurchasesErrorCode.UnknownBackendError,
            PurchasesErrorCode.UnexpectedBackendResponseError,
            PurchasesErrorCode.InvalidReceiptError,
            PurchasesErrorCode.SignatureVerificationError,
        ).forEach {
            assertEquals(PurchaseErrorCode.STORE_ERROR, it.toPurchaseErrorCode(), "código $it")
        }
    }

    @Test
    fun `credencial e oferta mal configuradas viram CONFIGURATION_ERROR`() {
        listOf(
            PurchasesErrorCode.ConfigurationError,
            PurchasesErrorCode.InvalidCredentialsError,
            PurchasesErrorCode.InvalidAppUserIdError,
            PurchasesErrorCode.InvalidPromotionalOfferError,
        ).forEach {
            assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, it.toPurchaseErrorCode(), "código $it")
        }
    }

    /** O caso que motivou a entrega: recusa de pagamento precisa chegar como recusa. */
    @Test
    fun `PurchaseInvalidError vira PAYMENT_DECLINED`() {
        assertEquals(
            PurchaseErrorCode.PAYMENT_DECLINED,
            PurchasesErrorCode.PurchaseInvalidError.toPurchaseErrorCode(),
        )
    }

    @Test
    fun `pagamento pendente nao e recusa`() {
        val code = PurchasesErrorCode.PaymentPendingError.toPurchaseErrorCode()
        assertEquals(PurchaseErrorCode.PAYMENT_PENDING, code)
        assertNotEquals(PurchaseErrorCode.PAYMENT_DECLINED, code)
    }

    @Test
    fun `ja assinante distingue conta propria de conta alheia`() {
        assertEquals(
            PurchaseErrorCode.ALREADY_OWNED,
            PurchasesErrorCode.ProductAlreadyPurchasedError.toPurchaseErrorCode(),
        )
        listOf(
            PurchasesErrorCode.ReceiptAlreadyInUseError,
            PurchasesErrorCode.ReceiptInUseByOtherSubscriberError,
            PurchasesErrorCode.PurchaseBelongsToOtherUser,
        ).forEach {
            assertEquals(
                PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER,
                it.toPurchaseErrorCode(),
                "código $it",
            )
        }
    }

    @Test
    fun `restricao do aparelho produto indisponivel elegibilidade e compra em andamento`() {
        assertEquals(
            PurchaseErrorCode.PURCHASE_NOT_ALLOWED,
            PurchasesErrorCode.PurchaseNotAllowedError.toPurchaseErrorCode(),
        )
        assertEquals(
            PurchaseErrorCode.PURCHASE_NOT_ALLOWED,
            PurchasesErrorCode.InsufficientPermissionsError.toPurchaseErrorCode(),
        )
        assertEquals(
            PurchaseErrorCode.PRODUCT_NOT_FOUND,
            PurchasesErrorCode.ProductNotAvailableForPurchaseError.toPurchaseErrorCode(),
        )
        assertEquals(
            PurchaseErrorCode.INELIGIBLE,
            PurchasesErrorCode.IneligibleError.toPurchaseErrorCode(),
        )
        assertEquals(
            PurchaseErrorCode.PURCHASE_IN_PROGRESS,
            PurchasesErrorCode.OperationAlreadyInProgressError.toPurchaseErrorCode(),
        )
    }

    // --- 2. A MENSAGEM NÃO CLASSIFICA (regressão do defeito) -------------------------------------

    /**
     * Trava a volta do `mapErrorCode(message)`: cada texto abaixo casaria com uma das cinco
     * substrings antigas (`network`/`store`/`pending`/`declined`/`already owned`), mas o código
     * tipado diz outra coisa — e é o código que vale.
     */
    @Test
    fun `mensagem enganosa nao muda a classificacao`() {
        val casos = listOf(
            Triple(PurchasesErrorCode.ProductAlreadyPurchasedError, "network failure", PurchaseErrorCode.ALREADY_OWNED),
            Triple(PurchasesErrorCode.NetworkError, "problem with the store", PurchaseErrorCode.NETWORK_ERROR),
            Triple(PurchasesErrorCode.PurchaseInvalidError, "payment is pending", PurchaseErrorCode.PAYMENT_DECLINED),
            Triple(PurchasesErrorCode.ConfigurationError, "card declined", PurchaseErrorCode.CONFIGURATION_ERROR),
            Triple(PurchasesErrorCode.StoreProblemError, "already owned by network", PurchaseErrorCode.STORE_ERROR),
        )
        casos.forEach { (sdkCode, texto, esperado) ->
            val falha = assertIs<PurchaseFailure.Failed>(
                PurchasesError(sdkCode, texto).toPurchaseFailure(userCancelled = false),
                "esperava falha real para $sdkCode",
            )
            assertEquals(esperado, falha.code, "texto '$texto' não pode influenciar o código")
        }
    }

    /**
     * O outro lado da mesma regressão: mensagem **em pt-BR** (o aparelho real do usuário), onde
     * nenhuma substring em inglês casa. Antes disto tudo virava `UNKNOWN` — inclusive recusa de
     * pagamento, que é o erro mais comum de todos.
     */
    @Test
    fun `mensagem localizada em pt-BR ainda classifica pelo codigo`() {
        val falha = assertIs<PurchaseFailure.Failed>(
            PurchasesError(PurchasesErrorCode.PurchaseInvalidError, "Pagamento recusado pela operadora")
                .toPurchaseFailure(userCancelled = false)
        )
        assertEquals(PurchaseErrorCode.PAYMENT_DECLINED, falha.code)
        assertNotEquals(PurchaseErrorCode.UNKNOWN, falha.code)
        // A mensagem técnica do SDK segue disponível para log/diagnóstico — só não classifica nada.
        assertTrue(falha.message.isNotBlank())
    }

    // --- 3. Cancelamento não é erro --------------------------------------------------------------

    @Test
    fun `cancelamento pelo flag do SDK nao vira falha`() {
        val falha = PurchasesError(PurchasesErrorCode.PurchaseCancelledError, "Purchase was cancelled.")
            .toPurchaseFailure(userCancelled = true)
        assertEquals(PurchaseFailure.Cancelled, falha)
    }

    /** Cinto e suspensório: se o flag vier `false`, o código de cancelamento ainda manda. */
    @Test
    fun `cancelamento pelo codigo nao vira falha mesmo sem o flag`() {
        val falha = PurchasesError(PurchasesErrorCode.PurchaseCancelledError, "cancelado")
            .toPurchaseFailure(userCancelled = false)
        assertEquals(PurchaseFailure.Cancelled, falha)
    }

    /** E o flag também manda sozinho, ainda que o código venha genérico. */
    @Test
    fun `flag de cancelamento vence codigo generico`() {
        val falha = PurchasesError(PurchasesErrorCode.UnknownError, "qualquer coisa")
            .toPurchaseFailure(userCancelled = true)
        assertEquals(PurchaseFailure.Cancelled, falha)
    }

    @Test
    fun `cancelamento nunca e incidente de pagamento`() {
        assertEquals(
            PurchaseErrorCode.USER_CANCELLED,
            PurchasesErrorCode.PurchaseCancelledError.toPurchaseErrorCode(),
        )
        assertFalse(PurchaseErrorCode.USER_CANCELLED.isPaymentIncident)
    }

    // --- 4. Decisão de alerta e mensagem de tela -------------------------------------------------

    @Test
    fun `so falha do sistema e incidente`() {
        listOf(
            PurchaseErrorCode.CONFIGURATION_ERROR,
            PurchaseErrorCode.PRODUCT_NOT_FOUND,
            PurchaseErrorCode.STORE_ERROR,
            PurchaseErrorCode.UNKNOWN,
        ).forEach { assertTrue(it.isPaymentIncident, "$it deveria alertar") }

        listOf(
            PurchaseErrorCode.NETWORK_ERROR,
            PurchaseErrorCode.PAYMENT_PENDING,
            PurchaseErrorCode.PAYMENT_DECLINED,
            PurchaseErrorCode.PURCHASE_NOT_ALLOWED,
            PurchaseErrorCode.ALREADY_OWNED,
            PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER,
            PurchaseErrorCode.PURCHASE_IN_PROGRESS,
            PurchaseErrorCode.INELIGIBLE,
            PurchaseErrorCode.USER_CANCELLED,
        ).forEach { assertFalse(it.isPaymentIncident, "$it não pode virar alerta no Discord") }
    }

    @Test
    fun `todo codigo tem mensagem de tela propria e acionavel`() {
        val mensagens = PurchaseErrorCode.entries.map { it.userMessage() }
        assertTrue(mensagens.none { it.isBlank() }, "nenhum código pode ficar sem mensagem")
        assertEquals(
            PurchaseErrorCode.entries.size,
            mensagens.toSet().size,
            "mensagens repetidas apagam a distinção que o enum existe para dar",
        )
    }

    @Test
    fun `mensagem de tela e customizavel pelo app`() {
        val texts = PurchaseErrorTexts(paymentDeclined = "Cartão recusado, tente outro.")
        assertEquals("Cartão recusado, tente outro.", PurchaseErrorCode.PAYMENT_DECLINED.userMessage(texts))
        // O que o app não sobrescreveu continua no default da lib.
        assertEquals(
            PurchaseErrorTexts().networkError,
            PurchaseErrorCode.NETWORK_ERROR.userMessage(texts),
        )
    }

    // --- 5. Guarda contra código novo do SDK -----------------------------------------------------

    /**
     * `toPurchaseErrorCode` usa `else -> UNKNOWN` para que um bump do RevenueCat não quebre o build
     * da lib. O preço disso seria um código novo cair calado em `UNKNOWN` (= alerta genérico no
     * Discord), então a checagem vive aqui: ao atualizar o SDK, este teste falha listando o que
     * apareceu, e a classificação passa a ser uma decisão consciente.
     */
    @Test
    fun `nenhum codigo novo do SDK cai calado em UNKNOWN`() {
        val naoClassificados = PurchasesErrorCode.entries
            .filter { it.toPurchaseErrorCode() == PurchaseErrorCode.UNKNOWN }
            .map { it.name }
            .toSet()

        assertEquals(
            SEM_ACAO_PROPRIA,
            naoClassificados,
            "Código do RevenueCat sem classificação. Decida em PurchaseErrorMapper.kt: se a UI ou " +
                "o alerta de pagamento agem diferente por causa dele, mapeie; se não, some aqui.",
        )
    }

    private companion object {
        /**
         * Códigos que caem em `UNKNOWN` de propósito: nem a UI nem o alerta fazem algo diferente por
         * causa deles (atributos de assinante, refund, StoreKit, Test Store, erro genérico).
         */
        val SEM_ACAO_PROPRIA = setOf(
            "UnknownError",
            "InvalidSubscriberAttributesError",
            "LogOutWithAnonymousUserError",
            "UnsupportedError",
            "EmptySubscriberAttributesError",
            "SystemInfoError",
            "BeginRefundRequestError",
            "FeatureNotAvailableInCustomEntitlementsComputationMode",
            "FeatureNotSupportedWithStoreKit1",
            "TestStoreSimulatedPurchaseError",
        )
    }
}
