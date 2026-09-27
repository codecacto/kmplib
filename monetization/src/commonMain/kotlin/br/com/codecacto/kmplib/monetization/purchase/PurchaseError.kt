package br.com.codecacto.kmplib.monetization.purchase

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_network
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_store
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_product_not_found
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_pending
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_declined
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_already_owned
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_configuration
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_not_allowed
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_other_user
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_in_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_ineligible
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_cancelled
import br.com.codecacto.kmplib.generated.resources.kmplib_purchase_unknown

/**
 * Falha do fluxo de compra com **motivo tipado**, para os pontos em que a lib devolve `Result`
 * (ex.: [PurchaseRepository.getOfferings]).
 *
 * Existe porque `Result.failure(IllegalStateException(mensagem))` obrigava o app a adivinhar pelo
 * texto — o mesmo vicio que esta versao removeu do fluxo de compra. Continua sendo um `Throwable`
 * comum, entao quem so lia `exceptionOrNull()?.message` nao muda.
 */
class PurchaseException(
    val code: PurchaseErrorCode,
    message: String,
) : Exception(message) {
    override fun toString(): String = "PurchaseException(${code.name}): $message"
}

/**
 * **Este erro e um incidente da fabrica?**
 *
 * `true` = a falha e atribuivel ao **sistema** (configuracao da monetizacao, oferta, loja, codigo
 * desconhecido) e alguem daqui precisa agir → reportar via `PaymentAlertReporter`
 * (`PaymentAlertKind.CompraFalhou` / `RestauracaoFalhou`), com `detalhe = "codigo=${'$'}{code.name}"`,
 * que e justamente o que faltava no alerta antes da 2.90.0.
 *
 * `false` = a falha e do **usuario ou do ambiente dele** (sem rede, cartao recusado, restricao do
 * aparelho, ja assina, desistiu). A UI resolve com uma mensagem clara ([userMessage]); virar alerta
 * so produziria enxurrada no Discord e escondeira o incidente de verdade no meio do ruido.
 *
 * Nao decide nada sozinho — quem alerta e o app, com o seu contexto.
 */
val PurchaseErrorCode.isPaymentIncident: Boolean
    get() = when (this) {
        PurchaseErrorCode.CONFIGURATION_ERROR,
        PurchaseErrorCode.PRODUCT_NOT_FOUND,
        PurchaseErrorCode.STORE_ERROR,
        PurchaseErrorCode.UNKNOWN,
        -> true

        PurchaseErrorCode.NETWORK_ERROR,
        PurchaseErrorCode.PAYMENT_PENDING,
        PurchaseErrorCode.PAYMENT_DECLINED,
        PurchaseErrorCode.PURCHASE_NOT_ALLOWED,
        PurchaseErrorCode.ALREADY_OWNED,
        PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER,
        PurchaseErrorCode.PURCHASE_IN_PROGRESS,
        PurchaseErrorCode.INELIGIBLE,
        PurchaseErrorCode.USER_CANCELLED,
        -> false
    }

/**
 * Textos de tela por [PurchaseErrorCode] — i18n injetavel, defaults em pt-BR.
 *
 * A lib traz o texto porque a **acao** que cada codigo pede e a mesma em todo app do ecossistema
 * ("revise a forma de pagamento", "use restaurar compras", "tente de novo"), e ate a 2.89.0 cada app
 * reescrevia o proprio `when` — quando reescrevia: dois apps exibiam a mensagem crua do SDK, e um
 * exibia literalmente o nome do enum. O app so sobrescreve o que quiser.
 *
 * As mensagens dizem **o que fazer**, nunca so o que houve.
 */
data class PurchaseErrorTexts(
    val networkError: String =
        "Sem conexão com a internet. Verifique sua rede e tente novamente.",
    val storeError: String =
        "A loja está indisponível no momento. Tente novamente em alguns minutos.",
    val productNotFound: String =
        "Este plano não está disponível no momento.",
    val paymentPending: String =
        "Pagamento em análise pela loja. Seu acesso é liberado assim que for confirmado.",
    val paymentDeclined: String =
        "Pagamento recusado. Verifique a forma de pagamento cadastrada na loja e tente novamente.",
    val alreadyOwned: String =
        "Você já tem esta assinatura ativa. Use \"Restaurar compras\" para liberar o acesso.",
    val configurationError: String =
        "Não foi possível iniciar a compra agora. Já fomos avisados — tente novamente mais tarde.",
    val purchaseNotAllowed: String =
        "Este aparelho ou conta não tem permissão para fazer compras. Verifique as restrições nas " +
            "configurações da loja.",
    val alreadyOwnedByOtherUser: String =
        "Esta assinatura está vinculada a outra conta da loja. Entre com a conta usada na compra ou " +
            "fale com o suporte.",
    val purchaseInProgress: String =
        "Já existe uma compra em andamento. Aguarde a conclusão.",
    val ineligible: String =
        "Sua conta não está elegível para esta oferta.",
    /**
     * Cancelamento nao deveria virar mensagem de erro na tela — a UI simplesmente volta ao paywall.
     * O texto existe para log e para caminhos que so tem "sucesso ou erro" (restaurar compras).
     */
    val userCancelled: String =
        "Compra cancelada.",
    val unknown: String =
        "Não foi possível concluir a compra. Tente novamente.",
)

/**
 * Mensagem pronta para a tela a partir do motivo tipado.
 *
 * Use isto no lugar de `PurchaseResult.Error.message` (texto do SDK, localizado pelo aparelho e sem
 * acao para o usuario). Ex.: `state.copy(erro = r.code.userMessage())`.
 */
fun PurchaseErrorCode.userMessage(texts: PurchaseErrorTexts = PurchaseErrorTexts()): String =
    when (this) {
        PurchaseErrorCode.NETWORK_ERROR -> texts.networkError
        PurchaseErrorCode.STORE_ERROR -> texts.storeError
        PurchaseErrorCode.PRODUCT_NOT_FOUND -> texts.productNotFound
        PurchaseErrorCode.PAYMENT_PENDING -> texts.paymentPending
        PurchaseErrorCode.PAYMENT_DECLINED -> texts.paymentDeclined
        PurchaseErrorCode.ALREADY_OWNED -> texts.alreadyOwned
        PurchaseErrorCode.CONFIGURATION_ERROR -> texts.configurationError
        PurchaseErrorCode.PURCHASE_NOT_ALLOWED -> texts.purchaseNotAllowed
        PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER -> texts.alreadyOwnedByOtherUser
        PurchaseErrorCode.PURCHASE_IN_PROGRESS -> texts.purchaseInProgress
        PurchaseErrorCode.INELIGIBLE -> texts.ineligible
        PurchaseErrorCode.USER_CANCELLED -> texts.userCancelled
        PurchaseErrorCode.UNKNOWN -> texts.unknown
    }

/**
 * [PurchaseErrorTexts] no **idioma da tela** (pt-BR, en, es, pt-PT), para o ViewModel (2.219.0):
 * `state.copy(erro = r.code.userMessage(loadPurchaseErrorTexts()))`. Falha de leitura → pt-BR.
 */
suspend fun loadPurchaseErrorTexts(): PurchaseErrorTexts =
    try {
        PurchaseErrorTexts(
            networkError = getString(Res.string.kmplib_purchase_network),
            storeError = getString(Res.string.kmplib_purchase_store),
            productNotFound = getString(Res.string.kmplib_purchase_product_not_found),
            paymentPending = getString(Res.string.kmplib_purchase_pending),
            paymentDeclined = getString(Res.string.kmplib_purchase_declined),
            alreadyOwned = getString(Res.string.kmplib_purchase_already_owned),
            configurationError = getString(Res.string.kmplib_purchase_configuration),
            purchaseNotAllowed = getString(Res.string.kmplib_purchase_not_allowed),
            alreadyOwnedByOtherUser = getString(Res.string.kmplib_purchase_other_user),
            purchaseInProgress = getString(Res.string.kmplib_purchase_in_progress),
            ineligible = getString(Res.string.kmplib_purchase_ineligible),
            userCancelled = getString(Res.string.kmplib_purchase_cancelled),
            unknown = getString(Res.string.kmplib_purchase_unknown),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        PurchaseErrorTexts()
    }

/** [PurchaseErrorTexts] no idioma do aparelho, para quem monta a mensagem na composição. */
@Composable
fun rememberPurchaseErrorTexts(): PurchaseErrorTexts = PurchaseErrorTexts(
    networkError = stringResource(Res.string.kmplib_purchase_network),
    storeError = stringResource(Res.string.kmplib_purchase_store),
    productNotFound = stringResource(Res.string.kmplib_purchase_product_not_found),
    paymentPending = stringResource(Res.string.kmplib_purchase_pending),
    paymentDeclined = stringResource(Res.string.kmplib_purchase_declined),
    alreadyOwned = stringResource(Res.string.kmplib_purchase_already_owned),
    configurationError = stringResource(Res.string.kmplib_purchase_configuration),
    purchaseNotAllowed = stringResource(Res.string.kmplib_purchase_not_allowed),
    alreadyOwnedByOtherUser = stringResource(Res.string.kmplib_purchase_other_user),
    purchaseInProgress = stringResource(Res.string.kmplib_purchase_in_progress),
    ineligible = stringResource(Res.string.kmplib_purchase_ineligible),
    userCancelled = stringResource(Res.string.kmplib_purchase_cancelled),
    unknown = stringResource(Res.string.kmplib_purchase_unknown),
)
