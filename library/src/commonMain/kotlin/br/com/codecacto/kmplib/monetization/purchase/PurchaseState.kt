package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.datetime.Instant

/**
 * Estado atual da assinatura do usuario.
 */
data class SubscriptionInfo(
    val isActive: Boolean,
    val productId: String? = null,
    val expirationDate: Instant? = null,
    val willRenew: Boolean = false
)

/**
 * Produto disponivel para compra.
 */
data class PurchaseProduct(
    val id: String,
    val title: String,
    val description: String,
    val price: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val subscriptionPeriod: SubscriptionPeriod? = null
)

/**
 * Pacote de assinatura da camada uniforme do RevenueCat (**Offering -> Package**), gold-standard.
 *
 * O app compra por [packageId] ([PurchaseRepository.purchasePackage]) — nunca pelo ID cru de produto
 * da loja (que fica absorvido no `Package`). [durationMonths] e a chave de correlacao com o `Plan` do
 * catalogo (admin-api) e de ordenacao Mensal(1) -> Semestral(6) -> Anual(12).
 *
 * @param packageId identificador do `Package` no offering (chave de compra/selecao).
 * @param packageType tipo padronizado do pacote (ver [PurchasePackageType]).
 * @param storeProductId ID do produto na loja subjacente (informativo/telemetria; NAO usar p/ compra).
 * @param priceLabel preco JA FORMATADO pela loja (ex.: "R$ 9,90") — a lib NUNCA calcula preco.
 * @param priceAmountMicros preco em micros (1_000_000 = 1 unidade da moeda).
 * @param currencyCode codigo ISO da moeda (ex.: "BRL").
 * @param durationMonths duracao em meses (1/6/12) quando derivavel; `null` p/ vitalicio/indeterminado.
 */
data class PurchasePackage(
    val packageId: String,
    val packageType: PurchasePackageType,
    val storeProductId: String,
    val priceLabel: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val durationMonths: Int? = null
)

/** Tipo padronizado de um [PurchasePackage] (subconjunto estavel do `PackageType` do RevenueCat). */
enum class PurchasePackageType {
    MONTHLY,
    SIX_MONTH,
    ANNUAL,
    LIFETIME,
    OTHER
}

/**
 * Resultado de uma operacao de compra.
 */
sealed class PurchaseResult {
    data class Success(val subscriptionInfo: SubscriptionInfo) : PurchaseResult()

    /**
     * Falha real da compra (**cancelamento do usuario NAO chega aqui** — vem em [Cancelled]).
     *
     * @param message texto TECNICO do SDK, para log/diagnostico. **Nao exiba ao usuario**: e a
     *   mensagem do fornecedor, localizada e sem acao clara. O texto de tela sai de
     *   [PurchaseErrorCode.userMessage].
     * @param code motivo TIPADO, lido do `PurchasesErrorCode` do SDK (nunca da mensagem).
     */
    data class Error(val message: String, val code: PurchaseErrorCode) : PurchaseResult()

    data object Cancelled : PurchaseResult()
}

/**
 * Resultado de uma compra CONSUMIVEL (pay-per-action; nao-assinatura).
 *
 * Diferente de [PurchaseResult], nao depende de entitlement: devolve a transacao da loja
 * para o app enviar a admin-api validar e liberar AQUELA acao no Firestore.
 */
sealed class ConsumablePurchaseResult {
    /** transactionId = id da transacao na loja (para validacao server-side / vinculo com a acao). */
    data class Success(
        val transactionId: String,
        val productId: String,
        val store: String, // "play_store" | "app_store"
    ) : ConsumablePurchaseResult()

    /** Ver [PurchaseResult.Error]: [message] e tecnica; o texto de tela sai do [code]. */
    data class Error(val message: String, val code: PurchaseErrorCode) : ConsumablePurchaseResult()

    data object Cancelled : ConsumablePurchaseResult()
}

/**
 * Resultado de uma restauracao de compras.
 */
sealed class RestoreResult {
    data class Success(val subscriptionInfo: SubscriptionInfo) : RestoreResult()

    /**
     * Falha da restauracao.
     *
     * @param message texto TECNICO do SDK (log/diagnostico), nao para a tela.
     * @param code motivo TIPADO (2.90.0). Existe porque `RestauracaoFalhou` e alerta de pagamento —
     *   e queda de rede num restore nao pode virar incidente igual a loja quebrada
     *   ([PurchaseErrorCode.isPaymentIncident]). **Cancelamento** do usuario chega como
     *   [PurchaseErrorCode.USER_CANCELLED] (este selado nao tem branch `Cancelled`, e criar um
     *   quebraria todo `when` exaustivo dos apps).
     */
    data class Error(
        val message: String,
        val code: PurchaseErrorCode = PurchaseErrorCode.UNKNOWN,
    ) : RestoreResult()

    data object NoPurchasesToRestore : RestoreResult()
}

/**
 * Motivo TIPADO de falha no caminho do dinheiro, derivado do `PurchasesErrorCode` do RevenueCat
 * (**nunca** da mensagem — ver [toPurchaseErrorCode]).
 *
 * Serve a dois consumidores, e por isso cada valor so existe se **alguem age diferente** por causa
 * dele:
 * 1. **a UI**, que precisa distinguir o que o usuario resolve (cartao recusado, restricao do
 *    aparelho, ja assina) do que so cabe esperar (rede, loja) — ver [userMessage];
 * 2. **o alerta de pagamento** (`monetization/alert` → GlitchTip → Discord), que precisa separar
 *    incidente da fabrica de ruido do ambiente — ver [isPaymentIncident].
 *
 * **Historico (2.90.0):** ate a 2.89.0 a classificacao era por **substring da mensagem** do SDK
 * (`contains("network")`, `contains("declined")`…). A mensagem do RevenueCat e localizada, e varios
 * desses textos **nao existem em idioma nenhum** (nao ha codigo "declined"; "ja possui" e
 * *"This product is already active for the user"*): na pratica quase todo erro de compra virava
 * [UNKNOWN], inclusive pagamento recusado — o alerta chegava ao Discord sem informar nada.
 *
 * Os 7 primeiros valores mantem a ordem historica (ordinal estavel); os novos entram no fim.
 */
enum class PurchaseErrorCode {
    /**
     * Rede/conectividade: offline, timeout, requisicoes bloqueadas (DNS/firewall/VPN).
     * **Transitorio** — a acao e "tentar de novo", nunca alerta.
     */
    NETWORK_ERROR,

    /**
     * Problema na loja ou no backend do fornecedor (App Store/Play/RevenueCat fora do ar, recibo
     * invalido, resposta inesperada). Nao ha o que o usuario faca alem de tentar mais tarde.
     */
    STORE_ERROR,

    /**
     * Pacote/produto ausente no offering, ou existente mas **nao disponivel para compra** (regiao,
     * produto nao liberado na loja). E defeito de configuracao da oferta — **incidente**.
     */
    PRODUCT_NOT_FOUND,

    /**
     * Pagamento em analise pela loja (ex.: boleto/aprovacao parental no Google Play). **Nao e
     * falha**: o acesso e liberado quando a loja confirmar. Nunca alertar.
     */
    PAYMENT_PENDING,

    /**
     * Pagamento recusado/invalido — cartao sem limite, expirado, forma de pagamento nao aceita.
     * **O usuario resolve**, e a mensagem precisa dizer isso (era o caso que mais colapsava em
     * [UNKNOWN] antes da 2.90.0).
     */
    PAYMENT_DECLINED,

    /** O usuario **ja possui** esta assinatura ativa nesta conta — o caminho e restaurar compras. */
    ALREADY_OWNED,

    /** Qualquer outra falha, inclusive codigo novo do SDK ainda nao classificado. **Incidente.** */
    UNKNOWN,

    /**
     * Configuracao da monetizacao quebrada neste build: chave/credencial invalida, entitlement ou
     * oferta promocional mal configurados, app user id recusado. **Ninguem consegue comprar** — e o
     * incidente mais grave depois do paywall vazio.
     */
    CONFIGURATION_ERROR,

    /**
     * O aparelho ou a conta **nao tem permissao** para comprar (restricao parental, perfil
     * gerenciado, permissao de app ausente). O usuario resolve nas configuracoes da loja.
     */
    PURCHASE_NOT_ALLOWED,

    /**
     * A compra existe, mas pertence a **outra conta** da loja (recibo em uso por outro assinante).
     * Restaurar nao resolve: e preciso entrar com a conta usada na compra, ou falar com o suporte.
     * Distinto de [ALREADY_OWNED], onde o acesso e do proprio usuario.
     */
    ALREADY_OWNED_BY_OTHER_USER,

    /**
     * Ja existe uma compra em andamento (tipicamente toque duplo no botao). A UI pede para aguardar;
     * jamais e incidente.
     */
    PURCHASE_IN_PROGRESS,

    /** A conta nao esta elegivel para esta oferta (trial/promocao ja utilizada). */
    INELIGIBLE,

    /**
     * **O usuario desistiu** — nao e erro e NUNCA vira alerta.
     *
     * Em [PurchaseResult]/[ConsumablePurchaseResult] o cancelamento chega no branch `Cancelled` do
     * proprio selado, entao este valor **nao aparece** ali. Ele existe para os caminhos que so tem
     * "sucesso ou erro" ([RestoreResult.Error], falha de [PurchaseRepository.getOfferings]), onde
     * antes um cancelamento seria indistinguivel de uma falha real.
     */
    USER_CANCELLED,
}
