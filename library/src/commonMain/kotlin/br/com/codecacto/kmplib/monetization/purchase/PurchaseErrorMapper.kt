package br.com.codecacto.kmplib.monetization.purchase

import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode

/**
 * **Classificacao do erro de compra pelo codigo TIPADO do SDK.**
 *
 * Regra da casa, sem excecao: o motivo sai de [PurchasesErrorCode], **nunca** do texto do erro. A
 * mensagem do RevenueCat e localizada pelo aparelho — num celular em pt-BR nenhuma busca por
 * `"network"`/`"declined"`/`"already owned"` casa, e todo erro de compra colapsa em
 * [PurchaseErrorCode.UNKNOWN]. Pior: dois desses textos **nao existem nem em ingles** (nao ha codigo
 * "declined"; "ja possui" e *"This product is already active for the user"*), entao aquele mapeamento
 * estava errado em qualquer idioma. Consequencia real: o alerta de pagamento chegava ao Discord como
 * `UNKNOWN` indistinguivel, e a UI mostrava a mesma mensagem para "cartao recusado" (o usuario
 * resolve) e "sem internet" (so tentar de novo).
 *
 * Por isso a assinatura de [toPurchaseErrorCode] recebe **so o codigo**: classificar por texto deixa
 * de ser possivel sem mudar a API. A mensagem segue viajando (em [PurchaseFailure.Failed.message])
 * apenas como diagnostico tecnico.
 *
 * Referencia: documentacao oficial de *Error Codes* do RevenueCat + a `description` de cada entrada
 * do enum no SDK (`purchases-kmp-models`).
 */
internal fun PurchasesErrorCode.toPurchaseErrorCode(): PurchaseErrorCode = when (this) {
    // --- Transitorio: rede/conectividade. Nunca alerta. ------------------------------------------
    PurchasesErrorCode.NetworkError,
    PurchasesErrorCode.OfflineConnectionError,
    // "SKProductsRequest took too long to complete" — timeout de rede, nao defeito da oferta.
    PurchasesErrorCode.ProductRequestTimedOut,
    // "Requests to RevenueCat are being blocked" (DNS/VPN/firewall do usuario).
    PurchasesErrorCode.ApiEndpointBlocked,
    -> PurchaseErrorCode.NETWORK_ERROR

    // --- Loja / backend do fornecedor: fora do alcance do usuario e da fabrica. -------------------
    PurchasesErrorCode.StoreProblemError,
    PurchasesErrorCode.UnknownBackendError,
    PurchasesErrorCode.UnexpectedBackendResponseError,
    PurchasesErrorCode.CustomerInfoError,
    PurchasesErrorCode.InvalidReceiptError,
    PurchasesErrorCode.MissingReceiptFileError,
    PurchasesErrorCode.SignatureVerificationError,
    PurchasesErrorCode.InvalidWebPurchaseToken,
    PurchasesErrorCode.ExpiredWebPurchaseToken,
    -> PurchaseErrorCode.STORE_ERROR

    // --- Configuracao da monetizacao quebrada: NINGUEM compra ate a fabrica corrigir. -------------
    PurchasesErrorCode.ConfigurationError,
    PurchasesErrorCode.InvalidCredentialsError,
    // Id do assinante recusado pelo backend: a compra nao cairia no tenant certo (ver PurchaseIdentity).
    PurchasesErrorCode.InvalidAppUserIdError,
    PurchasesErrorCode.InvalidAppleSubscriptionKeyError,
    PurchasesErrorCode.InvalidPromotionalOfferError,
    PurchasesErrorCode.ProductDiscountMissingIdentifierError,
    PurchasesErrorCode.ProductDiscountMissingSubscriptionGroupIdentifierError,
    -> PurchaseErrorCode.CONFIGURATION_ERROR

    // Produto existe no catalogo mas a loja nao o vende (regiao/produto nao liberado). Para a UI e o
    // mesmo que "plano indisponivel" — por isso reusa o codigo historico em vez de inflar o enum.
    PurchasesErrorCode.ProductNotAvailableForPurchaseError -> PurchaseErrorCode.PRODUCT_NOT_FOUND

    PurchasesErrorCode.PaymentPendingError -> PurchaseErrorCode.PAYMENT_PENDING

    // Unico codigo que Play/App Store devolvem quando o pagamento nao passa (cartao recusado,
    // expirado, forma de pagamento nao aceita). O RevenueCat o descreve de forma generica
    // ("One or more of the arguments provided are invalid"), mas a acao recomendada e sempre a
    // mesma: pedir ao usuario que revise a forma de pagamento.
    PurchasesErrorCode.PurchaseInvalidError -> PurchaseErrorCode.PAYMENT_DECLINED

    PurchasesErrorCode.PurchaseNotAllowedError,
    PurchasesErrorCode.InsufficientPermissionsError,
    -> PurchaseErrorCode.PURCHASE_NOT_ALLOWED

    PurchasesErrorCode.ProductAlreadyPurchasedError -> PurchaseErrorCode.ALREADY_OWNED

    // Pago, porem em OUTRA conta da loja: restaurar nao resolve.
    PurchasesErrorCode.ReceiptAlreadyInUseError,
    PurchasesErrorCode.ReceiptInUseByOtherSubscriberError,
    PurchasesErrorCode.PurchaseBelongsToOtherUser,
    -> PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER

    PurchasesErrorCode.OperationAlreadyInProgressError -> PurchaseErrorCode.PURCHASE_IN_PROGRESS

    PurchasesErrorCode.IneligibleError -> PurchaseErrorCode.INELIGIBLE

    // Desistencia do usuario. Nos fluxos de compra isto vira `Cancelled` antes de chegar aqui
    // (ver [toPurchaseFailure]); no restore/offerings e este codigo que impede um cancelamento de
    // ser contado como incidente de pagamento.
    PurchasesErrorCode.PurchaseCancelledError -> PurchaseErrorCode.USER_CANCELLED

    // Demais codigos (atributos de assinante, refund, StoreKit, Test Store, `UnknownError`) nao tem
    // acao propria para a UI nem para o alerta. `else` em vez de `when` exaustivo de proposito: um
    // bump do SDK nao pode quebrar o build da lib — quem avisa e o teste-guarda
    // `PurchaseErrorMapperTest`, que falha listando o codigo novo para classificacao consciente.
    else -> PurchaseErrorCode.UNKNOWN
}

/**
 * Resultado da leitura de um erro do SDK: **desistencia do usuario** ou **falha de verdade**.
 *
 * Cancelamento e o falso-positivo mais provavel deste caminho (irmao do `LogOutWithAnonymousUserError`
 * tratado em `resetIdentity`): tratar "o usuario fechou a tela da loja" como erro enche o Discord de
 * alerta sem incidente algum e ainda mostra mensagem de falha a quem so mudou de ideia.
 */
internal sealed interface PurchaseFailure {
    /** Usuario desistiu — nao e erro, nao alerta, nao exibe mensagem de falha. */
    data object Cancelled : PurchaseFailure

    /** Falha real, com motivo tipado e mensagem tecnica do SDK (diagnostico, nao tela). */
    data class Failed(val code: PurchaseErrorCode, val message: String) : PurchaseFailure
}

/**
 * Le um erro do SDK no fluxo de compra.
 *
 * [userCancelled] e o flag que o proprio `Purchases.purchase` entrega; o codigo
 * `PurchaseCancelledError` e checado **junto** porque as duas fontes nem sempre concordam (o flag
 * chega de wrappers de plataforma diferentes) e basta uma delas dizer "cancelou" para nao existir
 * incidente.
 */
internal fun PurchasesError.toPurchaseFailure(userCancelled: Boolean): PurchaseFailure =
    if (userCancelled || code == PurchasesErrorCode.PurchaseCancelledError) {
        PurchaseFailure.Cancelled
    } else {
        PurchaseFailure.Failed(code = code.toPurchaseErrorCode(), message = message)
    }
