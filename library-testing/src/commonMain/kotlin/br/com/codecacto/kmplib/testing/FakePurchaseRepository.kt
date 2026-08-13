package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.AppUserIdCheck
import br.com.codecacto.kmplib.monetization.purchase.ConsumablePurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseException
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentity
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentityError
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentityException
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.PurchaseProduct
import br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.RestoreResult
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * Dublê de [PurchaseRepository] com **cenários prontos** — a loja simulada dos testes de pagamento.
 *
 * Instale-o com [PurchaseTestHooks] e o app passa a comprar sem tocar em Google Play, App Store ou
 * RevenueCat:
 *
 * ```kotlin
 * @Before fun antes() = PurchaseTestHooks.instalar(FakePurchaseRepository.compraQueDaCerto())
 * @After  fun depois() = PurchaseTestHooks.limpar()
 * ```
 *
 * ## Por que os cenários são construtores nomeados, e não flags
 *
 * A versão anterior deste dublê (uma cópia por app, a mais completa no Super 8) devolvia
 * `getOfferings()` **vazio** e `purchasePackage()` → `Cancelled`. Ou seja: **nunca simulava compra
 * bem-sucedida** — justamente o único caminho que a suíte de pagamento precisa exercitar. Um dublê
 * genérico com sete parâmetros booleanos teria o mesmo destino, porque ninguém lembra qual
 * combinação representa "a compra deu certo".
 *
 * Cada cenário abaixo é um estado do mundo que o produto tem de saber tratar, com nome que diz qual:
 *
 * | Cenário | O que exercita |
 * |---|---|
 * | [comOfertas] | o paywall lista os planos (preço formatado pela loja) |
 * | [compraQueDaCerto] | `purchasePackage` → `Success` e a assinatura vira ativa |
 * | [compraCancelada] | o usuário desiste — o app **não pode** liberar nada |
 * | [compraQueFalha] | erro da loja, por código ([PurchaseErrorCode]) |
 * | [jaAssinante] | o app abre com assinatura ativa (selo, sem CTA de assinar) |
 * | [semOfertas] | **paywall vazio** — o pior incidente de monetização |
 * | [ofertasQueFalham] | a leitura do catálogo falha (rede/loja) |
 *
 * ## O que ele registra, e por que isso importa
 *
 * [pacotesComprados] guarda os `packageId` na ordem em que foram comprados. Não é enfeite: todo
 * paywall da fábrica tem **um botão "Assinar" por plano**, e um teste que seleciona pelo elemento
 * errado compra o plano errado e **passa** — o dinheiro certo, no plano errado, é uma falha que
 * nenhum assert de "virou premium" pega. Assertar `pacotesComprados == listOf("\$rc_monthly")` pega.
 *
 * O estado é por instância, então cada teste cria o seu; o que vaza entre testes é o estado global
 * do `PurchaseManager`, e é para isso que existe o `PurchaseTestHooks.limpar()`.
 */
class FakePurchaseRepository(
    /** Catálogo que [getOfferings] devolve (quando [falhaDoCatalogo] é `null`). */
    private val ofertas: List<PurchasePackage> = PLANOS_PADRAO,
    /** Resultado de [purchasePackage], por `packageId`. */
    private val resultadoDaCompra: (String) -> PurchaseResult = { packageId ->
        PurchaseResult.Success(assinaturaAtiva(produtoDoPacote(packageId)))
    },
    /** Quando não-nulo, [getOfferings] falha com este código em vez de devolver [ofertas]. */
    private val falhaDoCatalogo: PurchaseErrorCode? = null,
    /** Estado inicial da assinatura — `jaAssinante` entra por aqui. */
    estadoInicial: SubscriptionInfo = SubscriptionInfo(isActive = false),
    /** Resultado de [restorePurchases]. */
    private val resultadoDaRestauracao: RestoreResult = RestoreResult.NoPurchasesToRestore,
    /** Resultado de [purchaseConsumable] (pay-per-action). */
    private val resultadoDoConsumivel: ConsumablePurchaseResult = ConsumablePurchaseResult.Cancelled,
    /** App user id inicial na "loja" (anônimo, como no SDK real). */
    initialAppUserId: String = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "fake",
    /** Sujeitos (app user ids) que já têm assinatura ativa — usado por [identify]. */
    private val premiumFor: Set<String> = emptySet(),
) : PurchaseRepository {

    private val _subscriptionState = MutableStateFlow(estadoInicial)
    override val subscriptionState: Flow<SubscriptionInfo> = _subscriptionState.asStateFlow()

    private var appUserId: String = initialAppUserId

    /** `packageId` de cada compra tentada com sucesso, na ordem. Ver o KDoc da classe. */
    val pacotesComprados: List<String> get() = _pacotesComprados.toList()
    private val _pacotesComprados = mutableListOf<String>()

    /** Quantas vezes [getOfferings] foi chamado (pega paywall que recarrega o catálogo em loop). */
    var leiturasDoCatalogo: Int = 0
        private set

    /** Falha determinística a devolver na próxima troca de identidade (`null` = sucesso). */
    var proximaFalhaDeIdentidade: PurchaseIdentityError? = null

    override suspend fun isPremium(): Boolean = _subscriptionState.value.isActive

    override suspend fun getOfferings(): Result<List<PurchasePackage>> {
        leiturasDoCatalogo++
        falhaDoCatalogo?.let { codigo ->
            return Result.failure(PurchaseException(codigo, "catálogo indisponível (dublê de teste)"))
        }
        return Result.success(ofertas)
    }

    override suspend fun purchasePackage(packageId: String): PurchaseResult {
        // Comprar pacote que não está no catálogo é o que acontece quando o teste (ou o app) usa um
        // id que a loja não conhece. Devolver `PRODUCT_NOT_FOUND` em vez de sucesso é o que impede
        // um teste de "comprar" um plano inexistente e passar.
        if (ofertas.isNotEmpty() && ofertas.none { it.packageId == packageId }) {
            return PurchaseResult.Error(
                "packageId '$packageId' não está no catálogo do dublê",
                PurchaseErrorCode.PRODUCT_NOT_FOUND,
            )
        }
        val resultado = resultadoDaCompra(packageId)
        if (resultado is PurchaseResult.Success) {
            _pacotesComprados += packageId
            _subscriptionState.value = resultado.subscriptionInfo
        }
        return resultado
    }

    @Deprecated("Assinaturas usam getOfferings() (Offerings/Packages).", ReplaceWith("getOfferings()"))
    override suspend fun getProducts(): Result<List<PurchaseProduct>> = Result.success(emptyList())

    @Deprecated("Assinaturas usam purchasePackage(packageId) via getOfferings().")
    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Error(
            "fluxo legado por id de produto não é simulado — use purchasePackage",
            PurchaseErrorCode.PRODUCT_NOT_FOUND,
        )

    override suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
        resultadoDoConsumivel

    override suspend fun restorePurchases(): RestoreResult {
        if (resultadoDaRestauracao is RestoreResult.Success) {
            _subscriptionState.value = resultadoDaRestauracao.subscriptionInfo
        }
        return resultadoDaRestauracao
    }

    /**
     * Reproduz o **contrato de identidade** que a lib promete (ver [PurchaseRepository.identify]):
     * valida o id antes de qualquer efeito, publica o entitlement do novo sujeito e é idempotente.
     *
     * Não é zelo: é o contrato que impede o entitlement de ir para o tenant errado num produto em
     * que quem assina é a organização — o modo de falha em que o cliente paga e continua bloqueado.
     */
    override suspend fun identify(appUserId: String): Result<Unit> {
        val id = when (val check = PurchaseIdentity.check(appUserId)) {
            is AppUserIdCheck.Invalid ->
                return Result.failure(
                    PurchaseIdentityException(PurchaseIdentityError.INVALID_APP_USER_ID, check.reason)
                )

            is AppUserIdCheck.Valid -> check.appUserId
        }
        proximaFalhaDeIdentidade?.let { motivo ->
            proximaFalhaDeIdentidade = null
            return Result.failure(PurchaseIdentityException(motivo, "falha simulada"))
        }
        this.appUserId = id
        _subscriptionState.value =
            if (id in premiumFor) assinaturaAtiva() else SubscriptionInfo(isActive = false)
        return Result.success(Unit)
    }

    override suspend fun resetIdentity(): Result<Unit> {
        if (PurchaseIdentity.isAnonymous(appUserId)) return Result.success(Unit)
        appUserId = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "novo"
        _subscriptionState.value = SubscriptionInfo(isActive = false)
        return Result.success(Unit)
    }

    override fun currentAppUserId(): String = appUserId

    override suspend fun getSubscriptionInfo(): SubscriptionInfo = _subscriptionState.value

    /** No-op: o dublê não tem servidor com quem sincronizar; o estado já é o que ele publicou. */
    override suspend fun syncSubscriptionState() = Unit

    companion object {
        /** `packageId` padrão do RevenueCat para o plano mensal. */
        const val PACOTE_MENSAL: String = "\$rc_monthly"

        /** `packageId` padrão do RevenueCat para o plano semestral. */
        const val PACOTE_SEMESTRAL: String = "\$rc_six_month"

        /** `packageId` padrão do RevenueCat para o plano anual. */
        const val PACOTE_ANUAL: String = "\$rc_annual"

        /**
         * Os **três** planos do padrão da fábrica, na ordem canônica (Mensal → Semestral → Anual).
         *
         * Não existe trimestral no padrão (`CLAUDE.md`), e é de propósito que ele não esteja aqui:
         * dublê que oferece plano fora do padrão ensina o app a tratar um caso que não deveria
         * existir.
         */
        val PLANOS_PADRAO: List<PurchasePackage> = listOf(
            plano(PACOTE_MENSAL, PurchasePackageType.MONTHLY, "R$ 19,90", 19_900_000, 1),
            plano(PACOTE_SEMESTRAL, PurchasePackageType.SIX_MONTH, "R$ 99,90", 99_900_000, 6),
            plano(PACOTE_ANUAL, PurchasePackageType.ANNUAL, "R$ 179,90", 179_900_000, 12),
        )

        /** Só o mensal — para o app cujo catálogo tem um plano só. */
        val SO_MENSAL: List<PurchasePackage> = listOf(PLANOS_PADRAO.first())

        /** Um [PurchasePackage] avulso, para montar catálogo fora do padrão. */
        fun plano(
            packageId: String,
            tipo: PurchasePackageType,
            precoFormatado: String,
            precoEmMicros: Long,
            duracaoEmMeses: Int?,
            idDoProdutoNaLoja: String = produtoDoPacote(packageId),
            moeda: String = "BRL",
        ): PurchasePackage = PurchasePackage(
            packageId = packageId,
            packageType = tipo,
            storeProductId = idDoProdutoNaLoja,
            priceLabel = precoFormatado,
            priceAmountMicros = precoEmMicros,
            currencyCode = moeda,
            durationMonths = duracaoEmMeses,
        )

        /**
         * O paywall lista os planos, mas **nenhuma compra é simulada** (`purchasePackage` cancela).
         *
         * É o cenário do teste que só quer o usuário NÃO assinante — o caso dos 33 testes
         * instrumentados do Super 8, que não falam de pagamento e só precisam que o app não ache que
         * já é premium.
         */
        fun comOfertas(planos: List<PurchasePackage> = PLANOS_PADRAO): FakePurchaseRepository =
            FakePurchaseRepository(ofertas = planos, resultadoDaCompra = { PurchaseResult.Cancelled })

        /** A compra fecha: `Success` + [subscriptionState] vira ativo (com o produto comprado). */
        fun compraQueDaCerto(
            planos: List<PurchasePackage> = PLANOS_PADRAO,
            expiraEm: Instant? = null,
        ): FakePurchaseRepository = FakePurchaseRepository(
            ofertas = planos,
            resultadoDaCompra = { packageId ->
                PurchaseResult.Success(assinaturaAtiva(produtoDoPacote(packageId), expiraEm))
            },
        )

        /**
         * O usuário desiste na sheet da loja.
         *
         * O assert que importa aqui é **negativo**: nada liberado, nenhum alerta de pagamento
         * disparado (cancelamento não é incidente — ver `PurchaseErrorCode.isPaymentIncident`).
         */
        fun compraCancelada(planos: List<PurchasePackage> = PLANOS_PADRAO): FakePurchaseRepository =
            FakePurchaseRepository(ofertas = planos, resultadoDaCompra = { PurchaseResult.Cancelled })

        /**
         * A loja recusa a compra com [codigo] — inclusive
         * [PurchaseErrorCode.CONFIGURATION_ERROR], que é o incidente mais grave depois do paywall
         * vazio (ninguém consegue comprar).
         */
        fun compraQueFalha(
            codigo: PurchaseErrorCode,
            planos: List<PurchasePackage> = PLANOS_PADRAO,
        ): FakePurchaseRepository = FakePurchaseRepository(
            ofertas = planos,
            resultadoDaCompra = { PurchaseResult.Error("falha simulada: ${codigo.name}", codigo) },
        )

        /** O app abre com assinatura ATIVA (selo premium, sem CTA de assinar). */
        fun jaAssinante(
            idDoProdutoNaLoja: String = produtoDoPacote(PACOTE_MENSAL),
            expiraEm: Instant? = null,
            renovaAutomaticamente: Boolean = true,
            planos: List<PurchasePackage> = PLANOS_PADRAO,
        ): FakePurchaseRepository = FakePurchaseRepository(
            ofertas = planos,
            estadoInicial = SubscriptionInfo(
                isActive = true,
                productId = idDoProdutoNaLoja,
                expirationDate = expiraEm,
                willRenew = renovaAutomaticamente,
            ),
            resultadoDaRestauracao = RestoreResult.Success(
                assinaturaAtiva(idDoProdutoNaLoja, expiraEm)
            ),
        )

        /**
         * **Paywall vazio**: a loja responde, e o catálogo vem sem nenhum plano.
         *
         * É o pior incidente de monetização — a tela abre, não há o que comprar, e nada falha. Um
         * app bem-feito mostra erro em vez de uma tela em branco, e é isto que este cenário cobra.
         */
        fun semOfertas(): FakePurchaseRepository = FakePurchaseRepository(ofertas = emptyList())

        /** A leitura do catálogo FALHA (rede, loja fora, configuração) — o app tem de tratar. */
        fun ofertasQueFalham(
            codigo: PurchaseErrorCode = PurchaseErrorCode.NETWORK_ERROR,
        ): FakePurchaseRepository =
            FakePurchaseRepository(ofertas = emptyList(), falhaDoCatalogo = codigo)

        /**
         * `packageId` → id de produto na loja. O `$rc_` é convenção do RevenueCat para o pacote;
         * o produto por trás tem nome próprio, e é ele que aparece em
         * [SubscriptionInfo.productId] — distinção que já causou teste assertando o id errado.
         */
        internal fun produtoDoPacote(packageId: String): String = when (packageId) {
            PACOTE_MENSAL -> "premium_mensal_fake"
            PACOTE_SEMESTRAL -> "premium_semestral_fake"
            PACOTE_ANUAL -> "premium_anual_fake"
            else -> packageId.removePrefix("\$rc_")
        }

        private fun assinaturaAtiva(
            idDoProdutoNaLoja: String = produtoDoPacote(PACOTE_MENSAL),
            expiraEm: Instant? = null,
        ): SubscriptionInfo = SubscriptionInfo(
            isActive = true,
            productId = idDoProdutoNaLoja,
            expirationDate = expiraEm,
            willRenew = true,
        )
    }
}
