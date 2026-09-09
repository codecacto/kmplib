package br.com.codecacto.kmplib.monetization.purchase

/**
 * **Um item que a loja diz que a pessoa possui.**
 *
 * Não é permissão. É o que o aparelho **alega**, e a alegação viaja para o nosso servidor dentro de
 * um [StorePurchaseClaim] para ele conferir com o fornecedor e conceder (ou não) o acesso. Ver o
 * KDoc de [StorePurchaseClaim] §"quem decide o acesso".
 *
 * @param productId id do produto na loja — a chave para achar o item no nosso catálogo.
 * @param transactionId id da transação na loja. É a **chave de idempotência** da concessão no
 *   backend (`EntitlementService.grant(sourceRef = …)`): o mesmo recibo entregue duas vezes concede
 *   uma vez só. `null` quando a loja não informou — acontece em transação restaurada de
 *   plataformas antigas, e nesse caso o servidor precisa conciliar por `appUserId + productId`.
 * @param purchasedAtMillis instante da compra em epoch millis (UTC), como a loja informou. **É
 *   informativo**: relógio de cliente não vale como data de compra, e quem grava a data do direito é
 *   o servidor.
 */
data class OwnedStoreItem(
    val productId: String,
    val transactionId: String? = null,
    val purchasedAtMillis: Long? = null,
)

/**
 * Resultado da **verificação de assinatura criptográfica** da resposta do fornecedor (*Trusted
 * Entitlements* do RevenueCat).
 *
 * O SDK verifica a integridade dos dados de entitlement contra uma assinatura do servidor dele; o
 * resultado é **informativo** e nunca bloqueia nada sozinho — a própria documentação do fornecedor
 * diz isso com todas as letras ("Enabling Trusted Entitlements does not automatically protect your
 * app").
 *
 * Na fábrica ele serve a um propósito só, e não é liberar tela: [FAILED] é **sinal de adulteração**
 * (um MiTM devolvendo entitlement inventado) e vira alerta de pagamento. Quem decide o acesso é o
 * servidor, que não olha para este campo porque não fala com o aparelho — ele fala com o
 * fornecedor.
 */
enum class StoreVerification {
    /** Verificação não pedida (desligada na configuração, ou plataforma sem suporte). */
    NOT_REQUESTED,

    /** Dados verificados contra o servidor do fornecedor. */
    VERIFIED,

    /** Dados criados e verificados **no aparelho** (StoreKit 2). */
    VERIFIED_ON_DEVICE,

    /** **Verificação falhou** — possível adulteração da resposta. Alertar. */
    FAILED,
}

/**
 * **O que o app INFORMA ao nosso servidor** sobre as compras que a loja diz existir — e nada além
 * disso.
 *
 * ## Quem decide o acesso
 *
 * O servidor. Sempre. Este objeto é uma **alegação de cliente**, e cliente é o aparelho de quem
 * pode ter interesse em mentir: um APK modificado devolve o que quiser aqui. O caminho correto,
 * que é também o recomendado pelo fornecedor, tem duas pontas e a lib mobile não é nenhuma delas:
 *
 * 1. o **webhook** do RevenueCat chega ao nosso backend e concede o direito
 *    (`backlib-entitlement`, `EntitlementSource.IAP_APPLE`/`IAP_GOOGLE`, `sourceRef` =
 *    [OwnedStoreItem.transactionId]);
 * 2. quando o app manda este claim, o backend **consulta o fornecedor** pelo [appUserId] (REST API
 *    `GET /subscribers/{app_user_id}`) e só então concede.
 *
 * O claim existe porque o webhook pode atrasar, cair ou nunca ter existido (compra feita antes de o
 * produto entrar na nossa base) — é a **rede de segurança**, não a fonte da verdade. Mandá-lo é
 * seguro justamente porque não é acreditado: no pior caso o servidor confere e não concede nada.
 *
 * **Nunca** libere conteúdo por causa deste objeto. Ele não tem, de propósito, nenhum
 * `temAcesso(item)`: a pergunta "pode abrir?" é do servidor (`hasAccess`), e um booleano aqui seria
 * a porta que todo app acabaria usando.
 *
 * ## Como usar
 *
 * ```kotlin
 * when (val r = monetization.purchaseItem(curso.productId)) {
 *     is ItemPurchaseResult.Success -> api.conciliarCompras(r.claim)   // servidor concede
 *     is ItemPurchaseResult.Pending -> ui.avisar(textos.paymentPending) // NÃO libere nada
 *     is ItemPurchaseResult.AlreadyOwned -> api.conciliarCompras(monetization.restoreItems())
 *     ItemPurchaseResult.Cancelled -> Unit
 *     is ItemPurchaseResult.Failed -> ui.erro(r.code.userMessage())
 * }
 * ```
 *
 * @param appUserId identidade na loja (`Purchases.logIn`, ver [PurchaseRepository.identify]) — é a
 *   chave pela qual o servidor consulta o fornecedor. **Precisa ser opaca**: ela trafega para
 *   webhook e painel de terceiro, e e-mail/CPF aqui é vazamento evitável
 *   ([PurchaseIdentity.looksLikePersonalData]).
 * @param store a loja **deste aparelho**. Dica de conciliação, não certeza: uma compra restaurada
 *   pode ter nascido noutra plataforma, e quem sabe disso é o fornecedor.
 * @param items o que a loja diz que a pessoa possui. Pode vir vazio.
 * @param verification ver [StoreVerification]. `FAILED` = alertar, nunca "bloquear o usuário" —
 *   quem não tem direito já não recebe nada do servidor.
 */
data class StorePurchaseClaim(
    val appUserId: String,
    val store: PurchaseStore,
    val items: List<OwnedStoreItem> = emptyList(),
    val verification: StoreVerification = StoreVerification.NOT_REQUESTED,
) {
    /**
     * A loja não sabe quem é esta pessoa (app user anônimo do próprio SDK).
     *
     * Conciliar assim **não amarra a compra a ninguém** no nosso lado, e na volta — celular novo,
     * reinstalação — não há como devolver o acesso. É o caso que a documentação do fornecedor marca
     * em vermelho para produto de compra única: consumível e não-renovável só se restauram com
     * App User ID próprio. Chame [PurchaseRepository.identify] **antes** de vender.
     *
     * Id **em branco** conta como anônimo, e não como "identificado com um id vazio": as duas
     * situações têm exatamente a mesma consequência — o servidor não tem por quem perguntar ao
     * fornecedor —, e distingui-las só produziria um claim que parece conciliável e não é.
     */
    val isAnonymousAppUser: Boolean
        get() = appUserId.isBlank() || PurchaseIdentity.isAnonymous(appUserId)

    /** Ids de produto possuídos — para pintar "Comprado" no catálogo. **Não é permissão.** */
    val productIds: List<String> get() = items.map { it.productId }.distinct()

    /** Nada a conciliar. */
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * **Resultado da compra de um item não-consumível.**
 *
 * Os cinco desfechos são distintos porque o app **age diferente** em cada um — e os três primeiros
 * não são erro nenhum, que é justamente o que se perde quando o retorno é "sucesso ou exceção":
 *
 * | Desfecho | O que o app faz |
 * |---|---|
 * | [Success] | manda o [Success.claim] ao servidor e espera a concessão |
 * | [Pending] | avisa que o pagamento está em análise e **não libera nada** |
 * | [AlreadyOwned] | concilia ([PurchaseRepository.restoreItems]) em vez de cobrar de novo |
 * | [Cancelled] | nada — o usuário desistiu, e desistência não é falha nem alerta |
 * | [Failed] | mostra [PurchaseErrorCode.userMessage] e, se [PurchaseErrorCode.isPaymentIncident], alerta |
 */
sealed interface ItemPurchaseResult {

    /**
     * A loja cobrou e confirmou.
     *
     * @param item o que acabou de ser comprado (com o recibo desta transação).
     * @param claim **tudo** o que a loja diz que a pessoa possui, incluindo [item]. Mandar o claim
     *   inteiro, e não só o item novo, é de propósito: a concessão no backend é idempotente pela
     *   chave da origem, então reenviar o que já foi concedido não custa nada — e conserta, de
     *   graça, a compra antiga cujo webhook se perdeu.
     */
    data class Success(val item: OwnedStoreItem, val claim: StorePurchaseClaim) : ItemPurchaseResult

    /**
     * **Pagamento em análise pela loja** — aprovação parental no Google Play, boleto, meio de
     * pagamento que confirma depois. Não há recibo ainda e **não houve falha**.
     *
     * O acesso chega pelo webhook quando a loja confirmar; o app avisa e segue. Liberar aqui é
     * entregar o produto para uma cobrança que pode nunca ser aprovada.
     */
    data class Pending(val productId: String) : ItemPurchaseResult

    /**
     * A pessoa **já possui** este item nesta conta de loja — item não-consumível não se compra duas
     * vezes, e a loja recusa antes de cobrar.
     *
     * Quase sempre significa que a compra existe e o **nosso** lado não sabe dela (webhook perdido,
     * app reinstalado, conta nova no nosso sistema com a mesma conta de loja). O caminho é
     * conciliar, não insistir na cobrança.
     */
    data class AlreadyOwned(val productId: String) : ItemPurchaseResult

    /** O usuário fechou a sheet da loja. Não é erro, não alerta, não mostra mensagem de falha. */
    data object Cancelled : ItemPurchaseResult

    /**
     * Falha real.
     *
     * @param code motivo TIPADO, lido do código do SDK — nunca do texto (a mensagem do fornecedor é
     *   localizada pelo aparelho; ver [toPurchaseErrorCode]).
     * @param message texto técnico do SDK, para log. **Não exiba**: a frase de tela sai de
     *   [PurchaseErrorCode.userMessage].
     */
    data class Failed(val code: PurchaseErrorCode, val message: String) : ItemPurchaseResult

    companion object {
        /**
         * Classifica uma falha do SDK nos desfechos acima — **a regra que separa "não é erro" de
         * "é erro"**, em função pura para poder ser testada sem loja.
         *
         * `PAYMENT_PENDING` e `ALREADY_OWNED` chegam do SDK como *erro* e não são: o primeiro é uma
         * cobrança em andamento, o segundo é um direito que já existe. Tratá-los como falha faz o
         * app mostrar "não foi possível concluir a compra" para quem acabou de pagar — e, no caso do
         * `ALREADY_OWNED`, para quem **já pagou antes** e continua sem acesso.
         *
         * `ALREADY_OWNED_BY_OTHER_USER` **não** vira [AlreadyOwned]: a compra é de outra conta de
         * loja, restaurar não resolve, e sugerir "restaurar compras" ali só produz uma segunda
         * frustração.
         */
        fun fromFailure(
            code: PurchaseErrorCode,
            message: String,
            productId: String,
        ): ItemPurchaseResult = when (code) {
            PurchaseErrorCode.PAYMENT_PENDING -> Pending(productId)
            PurchaseErrorCode.ALREADY_OWNED -> AlreadyOwned(productId)
            PurchaseErrorCode.USER_CANCELLED -> Cancelled
            else -> Failed(code, message)
        }
    }
}

/**
 * **Resultado da restauração de compras avulsas — N itens, não um booleano.**
 *
 * É a diferença de desenho entre vender assinatura e vender item: [RestoreResult] responde "tem
 * assinatura ativa: sim/não", e essa pergunta não serve a quem comprou seis cursos e trocou de
 * celular. Aqui a resposta é a **lista**, com o recibo de cada item, que é o que o servidor precisa
 * para reconstruir os seis direitos.
 *
 * Uma chamada cobre os dois modelos: a mesma restauração devolve os itens **e** o estado da
 * assinatura ([Restored.subscription]), porque a loja mostra um diálogo do sistema a cada
 * restauração e pedir isso duas vezes é pedir a senha da Apple duas vezes.
 *
 * ⚠️ **Só a pedido do usuário.** A documentação do fornecedor é explícita: `restorePurchases` pode
 * abrir prompt de login do sistema operacional e **não** deve ser disparado por conta própria. Para
 * conciliar sozinho, na abertura do app, existe [PurchaseRepository.ownedItems], que lê sem
 * incomodar ninguém.
 */
sealed interface ItemRestoreResult {

    /**
     * Havia o que restaurar.
     *
     * @param claim os itens encontrados, prontos para ir ao servidor conciliar.
     * @param subscription o estado da assinatura na mesma leitura — pode estar inativo num produto
     *   que só vende avulso, e é isso mesmo.
     */
    data class Restored(
        val claim: StorePurchaseClaim,
        val subscription: SubscriptionInfo,
    ) : ItemRestoreResult

    /**
     * A loja respondeu e **não há nada** nesta conta: nem item, nem assinatura.
     *
     * A mensagem de tela precisa dizer o que fazer ("entre com a conta usada na compra"), porque a
     * causa mais comum não é "nunca comprou" — é estar logado na conta de loja errada.
     */
    data object NothingToRestore : ItemRestoreResult

    /** Não deu para restaurar. Ver [ItemPurchaseResult.Failed] para a régua de código × mensagem. */
    data class Failed(val code: PurchaseErrorCode, val message: String) : ItemRestoreResult
}
