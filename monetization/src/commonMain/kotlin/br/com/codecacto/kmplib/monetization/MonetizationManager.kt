package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.firebase.auth.User
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import br.com.codecacto.kmplib.monetization.purchase.AppUserIdCheck
import br.com.codecacto.kmplib.monetization.purchase.ConsumablePurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.ItemPurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.ItemRestoreResult
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentity
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentityError
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentityException
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityBinder
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityGateway
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityStatus
import br.com.codecacto.kmplib.monetization.purchase.StoreItemsOutcome
import br.com.codecacto.kmplib.monetization.purchase.StorePurchaseClaim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Orquestrador central de monetizacao.
 *
 * Ponto unico de entrada para configurar ads e/ou assinaturas.
 * Combina automaticamente o estado de Remote Config e premium
 * para decidir se ads devem ser exibidos.
 *
 * Uso no app:
 * ```kotlin
 * MonetizationManager.initialize(
 *     MonetizationConfig.FreemiumQuota(
 *         purchase = PurchaseConfig(...)
 *     )
 * )
 * ```
 *
 * A publicidade em si (house ads) e governada por `AdRouter`/`CustomAdManager`; aqui so se decide se
 * o usuario e premium. [shouldShowAds] = "deve exibir qualquer anuncio" (true quando NAO premium).
 *
 * A **postura** (tem ads? vende assinatura? tem tier gratuito?) e derivada do proprio
 * [MonetizationConfig], nao de `is`-check aqui: modo novo na lib obriga o compilador a responder as
 * tres perguntas, em vez de este objeto responder `false` em silencio.
 */
object MonetizationManager {
    private const val TAG = "MonetizationManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _config: MonetizationConfig? = null
    private val _initialized = MutableStateFlow(false)

    private val _isPremium = MutableStateFlow(false)
    private val _shouldShowAds = MutableStateFlow(false)

    /** Configuracao atual. */
    val config: MonetizationConfig? get() = _config

    /** Se o modo atual monetiza o tier gratuito com publicidade (house ads). */
    val hasAds: Boolean
        get() = _config?.showsAds == true

    /** Se o modo atual vende assinatura (tem paywall). */
    val hasPurchase: Boolean
        get() = _config?.sellsSubscription == true

    /**
     * Se o modo atual tem **tier gratuito** utilizavel.
     *
     * `false` so em `PremiumOnly` ("pague para usar"). Distinto de [hasPurchase]: um SaaS freemium
     * com limite de uso tem os dois `true` (ver [MonetizationConfig.FreemiumQuota]).
     */
    val hasFreeTier: Boolean
        get() = _config?.hasFreeTier == true

    /**
     * Se o usuario e premium.
     * - AdsOnly: sempre false (o modo nao vende assinatura)
     * - demais modos: segue o estado da assinatura
     */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /**
     * Se ads (house ads) devem ser exibidos — [MonetizationConfig.shouldShowAds] aplicado ao estado
     * corrente da assinatura.
     * - AdsOnly: sempre true (gratuito)
     * - PremiumOnly / FreemiumQuota: sempre false (modos sem publicidade)
     * - Freemium: !isPremium
     */
    val shouldShowAds: StateFlow<Boolean> = _shouldShowAds.asStateFlow()

    /**
     * Inicializa o sistema de monetizacao.
     *
     * @param config Modo de monetizacao ([MonetizationConfig.AdsOnly], [MonetizationConfig.PremiumOnly],
     *   [MonetizationConfig.Freemium] ou [MonetizationConfig.FreemiumQuota])
     * @param userId ID opcional do usuario para o RevenueCat
     */
    fun initialize(config: MonetizationConfig, userId: String? = null) {
        if (_initialized.value) {
            AppLogger.w(TAG, "MonetizationManager ja inicializado")
            return
        }

        _config = config
        _isPremium.value = false
        // Valor inicial, antes do primeiro estado de assinatura chegar: o modo decide.
        _shouldShowAds.value = config.shouldShowAds(isPremium = false)

        config.purchaseConfig?.let { purchase ->
            // O sujeito que o app já declarou ([bindIdentity]) entra na CONFIGURAÇÃO do SDK — é o
            // jeito recomendado pelo fornecedor, e evita nascer um id anônimo só para trocá-lo em
            // seguida. Ver `StoreIdentityBinder`.
            // Sujeito inválido para a loja (reservado, anônimo do SDK) não entra na configuração: o SDK
            // nasce anônimo, o `identify` da porta da compra recusa com alerta, e nada se vende.
            val declared = userId ?: storeIdentity.subject.value
                ?.let { (PurchaseIdentity.check(it) as? AppUserIdCheck.Valid)?.appUserId }
            PurchaseManager.initialize(purchase, declared)
            if (storeIdentity.isManaged) scope.launch { storeIdentity.reconcile() }
            PurchaseManager.subscriptionState.onEach { info ->
                _isPremium.value = info.isActive
                _shouldShowAds.value = config.shouldShowAds(info.isActive)
            }.launchIn(scope)
        }

        AppLogger.d(TAG, "Modo: ${config.modeName}")

        _initialized.value = true
    }

    /**
     * Compra um produto CONSUMIVEL (one-time / pay-per-action).
     *
     * Para cobranca POR ACAO via loja (IAP nao-renovavel): devolve a transacao da loja
     * (transactionId/productId/store) para o app enviar a admin-api validar e liberar a acao.
     * NAO depende de entitlement e NAO altera [isPremium].
     */
    suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
        PurchaseManager.purchaseConsumable(productId)


    // ---------------------------------------------------------------------------------------------
    // Venda AVULSA — item não-consumível (compra única, acesso vitalício). Aditivo, 2.192.0.
    //
    // Existe porque o resto deste módulo é inteiro orientado a ASSINATURA (Offering → Package →
    // `PaywallScreen` → entitlement "premium"), e um catálogo de itens vendidos um a um — um curso,
    // um e-book, um evento — não cabe nesse desenho: os produtos nascem junto com o conteúdo, são
    // dezenas, e a pergunta da restauração deixa de ser "tem assinatura: sim/não" e passa a ser
    // "quais dos seis itens esta pessoa comprou".
    //
    // A regra que atravessa todos os métodos abaixo: **a lib não concede acesso**. Ela informa a
    // compra; quem concede é o servidor (`backlib-entitlement`), avisado pelo webhook do fornecedor
    // e, como rede de segurança, pelo claim que estes métodos devolvem.
    // ---------------------------------------------------------------------------------------------

    /**
     * Lê da loja os itens não-consumíveis de [productIds] (preço já formatado por ela).
     *
     * O resultado diz **o que aconteceu**, não só o que veio: id que a loja não conhece sai em
     * [StoreItemsOutcome.missingProductIds] — a loja omite o produto e responde com sucesso, então
     * sem esse campo o app mostraria um item a menos sem nada falhar. Ver
     * [StoreItemsOutcome.incident] para decidir o alerta de pagamento.
     */
    suspend fun getStoreItems(productIds: List<String>): StoreItemsOutcome =
        PurchaseManager.getStoreItems(productIds)

    /**
     * Compra o item não-consumível [productId].
     *
     * **Não libera nada**: manda o [ItemPurchaseResult.Success.claim] ao nosso servidor e espere a
     * concessão. Os cinco desfechos existem porque o app age diferente em cada um — em especial
     * [ItemPurchaseResult.Pending] (pagamento em análise: avisar, não liberar) e
     * [ItemPurchaseResult.AlreadyOwned] (já pagou antes: conciliar, não cobrar de novo).
     *
     * Chame [identify] **antes** de vender: compra feita com app user anônimo não volta em celular
     * novo.
     */
    suspend fun purchaseItem(productId: String): ItemPurchaseResult =
        PurchaseManager.purchaseItem(productId)

    /**
     * **Restaura as compras avulsas — a LISTA de itens**, com o recibo de cada um.
     *
     * ⚠️ Só a partir de um toque do usuário ("Restaurar compras"): pode abrir prompt de login do
     * sistema operacional. Para conciliar sozinho na abertura do app use [ownedItems].
     *
     * A mesma chamada traz o estado da assinatura e atualiza [isPremium] — pedir as duas coisas em
     * chamadas separadas pede a senha da loja duas vezes.
     */
    suspend fun restoreItems(): ItemRestoreResult = PurchaseManager.restoreItems()

    /**
     * O que a loja já sabe que a pessoa possui, **sem prompt nenhum** — para pintar "Comprado" no
     * catálogo e para conciliar na abertura do app.
     *
     * Nunca para liberar conteúdo: ver [StorePurchaseClaim] §"quem decide o acesso".
     */
    suspend fun ownedItems(): Result<StorePurchaseClaim> = PurchaseManager.ownedItems()

    /**
     * **Identifica quem assina** na loja (`Purchases.logIn`) — chamar assim que o app souber o
     * sujeito real da assinatura, tipicamente depois do login/`GET /me`.
     *
     * O `appUserId` do [initialize] só existe no bootstrap, quando muitas vezes ainda não se sabe
     * quem (ou qual organização) vai assinar. Num produto multi-tenant o sujeito é a ORGANIZAÇÃO:
     * sem este passo o webhook chega à central com o UID do usuário e **o entitlement vai para o
     * tenant errado** — a organização paga e continua bloqueada.
     *
     * Idempotente (mesmo id ⇒ no-op no SDK). Atualiza [isPremium] com o entitlement do novo sujeito.
     * **Nunca lança:** falha vem em `Result.failure(`[PurchaseIdentityException]`)`, com
     * [PurchaseIdentityError] tipado para o app decidir o que vira alerta de pagamento
     * (`PaymentAlertKind.IdentidadeAusente`) e o que é transitório (`NETWORK`). Sem purchase
     * configurado ⇒ `NOT_CONFIGURED` (estado válido de build sem chave, não é incidente).
     *
     * Ver [PurchaseRepository.identify] para o contrato completo.
     */
    suspend fun identify(appUserId: String): Result<Unit> = PurchaseManager.identify(appUserId)

    /**
     * Volta a identidade da loja para anônima (`Purchases.logOut`). Chamar no **logout**, para o
     * próximo usuário do mesmo aparelho não herdar o entitlement de quem saiu.
     *
     * Já anônimo (ou sem purchase configurado) ⇒ **sucesso no-op**: o logout do app não falha por
     * causa de uma identidade de loja que nunca existiu.
     */
    suspend fun resetIdentity(): Result<Unit> = PurchaseManager.resetIdentity()

    // ---------------------------------------------------------------------------------------------
    // Identidade da loja amarrada à sessão (2.233.0, GAP-MON-IDENT-01). Ver `StoreIdentityBinder`.
    // ---------------------------------------------------------------------------------------------

    /**
     * A instância única do processo — a loja é uma só, e a trava que serializa login/logout/compra
     * também tem de ser.
     */
    val storeIdentity: StoreIdentityBinder = StoreIdentityBinder(
        object : StoreIdentityGateway {
            override suspend fun identify(appUserId: String) = PurchaseManager.identify(appUserId)
            override suspend fun resetIdentity() = PurchaseManager.resetIdentity()
            override fun currentAppUserId() = PurchaseManager.currentAppUserId()
        },
    )

    /**
     * **Amarra a loja à sessão — o jeito oficial, uma linha na raiz do app.** Identifica no login (e
     * na sessão restaurada), anonimiza no logout (inclusive o forçado por refresh expirado), tudo
     * serializado. Suspende enquanto [auth] emitir: chame em `LaunchedEffect(Unit)` na raiz.
     *
     * ```kotlin
     * LaunchedEffect(Unit) { MonetizationManager.bindIdentity(authRepository, alerts = paymentAlerts) }
     * ```
     *
     * Com isto declarado, o `PaywallViewModel` da lib **recusa comprar/restaurar** quando a loja não
     * está com a conta logada ([ensureIdentityForPurchase]). Paywall próprio chama a porta à mão.
     *
     * @param subjectOf quem assina, a partir do usuário: a conta (`User.id`, default). Produto
     *   multi-tenant em que assina a ORGANIZAÇÃO usa a sobrecarga de `Flow` com o id da organização.
     * @param alerts alertas de pagamento (`IdentificacaoNaLojaFalhou`, `CompraSemIdentidade`).
     */
    suspend fun bindIdentity(
        auth: IAuthRepository,
        alerts: PaymentAlertReporter? = null,
        subjectOf: (User) -> String? = { it.id },
    ) = storeIdentity.bind(auth.currentUser.map { user -> user?.let(subjectOf) }, alerts)

    /**
     * Variante por [Flow] — o id de QUEM ASSINA (`null` = ninguém logado). É a forma do multi-tenant:
     * `bindIdentity(sessao.map { it?.organizacaoId })`. Ver [bindIdentity].
     */
    suspend fun bindIdentity(subjectIds: Flow<String?>, alerts: PaymentAlertReporter? = null) =
        storeIdentity.bind(subjectIds, alerts)

    /** Aplica um sujeito pontualmente (app que já observa a sessão). Ver `StoreIdentityBinder.sync`. */
    suspend fun syncIdentity(subjectId: String?) = storeIdentity.sync(subjectId)

    /**
     * **Porta da compra e da restauração**: tenta identificar de novo e responde se a loja pode vender
     * para a conta logada. Só [StoreIdentityStatus.allowsPurchase] segue; o resto vira mensagem na
     * tela ("não foi possível vincular a compra à sua conta"). O `PaywallViewModel` já chama sozinho.
     */
    suspend fun ensureIdentityForPurchase(): StoreIdentityStatus = storeIdentity.ensureForPurchase()

    /**
     * App user id corrente na loja — para log/diagnóstico ("identifiquei quem?"). `null` sem loja
     * configurada; anônimo devolve o id do próprio SDK (ver [PurchaseIdentity.isAnonymous]).
     */
    fun currentAppUserId(): String? = PurchaseManager.currentAppUserId()

    /** Reseta o estado (util para testes). */
    fun reset() {
        _config = null
        _initialized.value = false
        _isPremium.value = false
        _shouldShowAds.value = false
        PurchaseManager.reset()
        storeIdentity.reset()
    }
}
