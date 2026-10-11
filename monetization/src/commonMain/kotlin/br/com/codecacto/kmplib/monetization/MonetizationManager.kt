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
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.hasStoreApiKey
import br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityBinder
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityGateway
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityStatus
import br.com.codecacto.kmplib.monetization.purchase.StoreItemsOutcome
import br.com.codecacto.kmplib.monetization.purchase.StorePurchaseClaim
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionReadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

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
@OptIn(ExperimentalCoroutinesApi::class)
object MonetizationManager {
    private const val TAG = "MonetizationManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var _config: MonetizationConfig? = null
    private val _initialized = MutableStateFlow(false)

    private val _isPremium = MutableStateFlow(false)
    private val _shouldShowAds = MutableStateFlow(false)

    private val _premiumStatus = MutableStateFlow<PremiumStatus>(PremiumStatus.Unknown)

    /**
     * Escopo `Unconfined` só para derivar [isPremiumResolved]: o `map` roda na thread de quem mudou o
     * [premiumStatus], então o derivado acompanha na mesma hora e nunca diverge dele.
     */
    private val derivedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Coleta, primeira leitura e teto de espera — guardados para o [reset] os cancelar. */
    private var subscriptionJob: Job? = null
    private var firstReadJob: Job? = null
    private var resolutionTimeoutJob: Job? = null

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
     *
     * ⚠️ Nasce `false` e **não distingue "não é assinante" de "a loja ainda não respondeu"**. Para
     * decidir um **gate premium** (bloquear tela, abrir paywall), leia [premiumStatus] ou espere
     * [awaitPremiumResolved] — senão o assinante é tratado como grátis na abertura do app.
     */
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /**
     * **Premium com resolução explícita** (2.249.0): [PremiumStatus.Unknown] até a loja responder,
     * depois [PremiumStatus.Premium] ou [PremiumStatus.Free] — e nunca volta a `Unknown` (exceto na troca
     * de repositório da loja, que é uma geração nova — ver `applyStoreSnapshot`).
     *
     * É o que um **gate premium** deve ler: [isPremium] nasce `false` e não distingue "não é
     * assinante" de "a loja ainda não respondeu", então o gate decidido na abertura do app trata o
     * assinante como grátis. Com este, o gate espera (`Unknown` = carregando) e decide só com
     * resposta. Sai de `Unknown`:
     * - no primeiro estado de assinatura **lido da loja** (cache do SDK ou rede) — o [initialize] já
     *   pede essa leitura, sem o app chamar nada;
     * - na leitura que **falhou** → `Free(STORE_FAILURE)`;
     * - no **teto** (`premiumResolutionTimeout` do [initialize]) → `Free(TIMEOUT)`;
     * - **na hora** em modo sem assinatura (`AdsOnly` → `Free(NOT_SOLD)`) ou com
     *   [declareNotMonetized];
     * - com dublê da loja (`kmplib-testing`), na instalação — o estado dele já nasce lido.
     *
     * Free presumido (falha/timeout) **é corrigido** quando a leitura chega — a RevenueCat avisa pelo
     * listener de `CustomerInfo` (ao voltar ao primeiro plano, após transações): gate que observa reabre.
     * Para decidir uma vez, numa função suspensa, use [awaitPremiumResolved]/[awaitPremiumStatus].
     */
    val premiumStatus: StateFlow<PremiumStatus> = _premiumStatus.asStateFlow()

    /** `true` enquanto [premiumStatus] está resolvido — derivado dele, nunca diverge (2.249.0). */
    val isPremiumResolved: StateFlow<Boolean> =
        _premiumStatus.map { it.isResolved }.stateIn(derivedScope, SharingStarted.Eagerly, false)

    /**
     * Relê a assinatura **ignorando o cache da loja** (2.250.0) e atualiza [isPremium]/[premiumStatus].
     * Use depois de uma compra feita FORA da lib — a ponte `onNativePurchaseCompleted` do iOS
     * (`SubscriptionStoreView`): a leitura comum (`syncSubscriptionState`) tende a voltar com o cache
     * de antes da compra. Sem loja configurada, não faz nada.
     */
    suspend fun refreshSubscriptionState() {
        runCatching { PurchaseManager.repository?.refreshSubscriptionState() }
            .onFailure { AppLogger.w(TAG, "Releitura da assinatura falhou: ${it.message}") }
    }

    /**
     * Espera a resposta da loja e devolve o [PremiumStatus] **resolvido** (2.249.0). Se [timeout]
     * passar sem resposta, devolve `Free(TIMEOUT)` — nunca prende quem chamou — sem alterar o
     * [premiumStatus] global (que tem o próprio teto, a partir do [initialize]).
     *
     * Chamado **antes** do [initialize] (app que inicializa a loja depois do login, como o Super 8),
     * espera a inicialização também, dentro do mesmo [timeout].
     */
    suspend fun awaitPremiumStatus(timeout: Duration = PremiumStatus.DEFAULT_TIMEOUT): PremiumStatus =
        withTimeoutOrNull(timeout) { premiumStatus.first { it.isResolved } }
            ?: PremiumStatus.Free(PremiumStatus.FreeReason.TIMEOUT)

    /**
     * `true` se a loja disser que é assinante dentro de [timeout]; `false` se disser que não, se a
     * leitura falhar ou se o teto passar (fail-closed). Ver [awaitPremiumStatus].
     *
     * ```kotlin
     * if (!MonetizationManager.awaitPremiumResolved()) abrirPaywall()
     * ```
     */
    suspend fun awaitPremiumResolved(timeout: Duration = PremiumStatus.DEFAULT_TIMEOUT): Boolean =
        awaitPremiumStatus(timeout).isPremium

    /**
     * **App que não monetiza** (a `casca-mobile` em `MonetizationMode.NONE`): declara que não há loja
     * a esperar, e [premiumStatus] resolve na hora como `Free(NOT_SOLD)` (2.249.0). Sem isto, quem
     * lesse [awaitPremiumResolved] num app sem monetização esperaria o teto inteiro.
     *
     * Não marca o manager como inicializado: um [initialize] posterior funciona normalmente.
     */
    fun declareNotMonetized() {
        updatePremiumStatus { current ->
            if (current.isResolved) current else PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD)
        }
    }

    /**
     * Há loja com que falar? (2.288.0) — a chave pública **desta plataforma** configurada
     * ([PurchaseConfig.hasStoreApiKey]: não vazia, não `PLACEHOLDER_*`) **ou** uma loja já instalada
     * no processo ([PurchaseManager.hasInstalledStore] — no boot, só o dublê do build de QA instala).
     *
     * Fail-closed: sem nenhum dos dois, `false` — o app é gratuito e o paywall diz "indisponível",
     * nunca premium de graça.
     */
    fun hasStore(purchase: PurchaseConfig?): Boolean =
        PurchaseManager.hasInstalledStore || purchase?.hasStoreApiKey == true

    /**
     * **A inicialização da monetização com a trava da chave, num lugar só** (2.288.0) — o que cada app
     * (e a `casca-mobile`) escrevia à mão no `initMonetization`:
     *
     * - [config] `null` (app que não monetiza) → [declareNotMonetized], `false`;
     * - modo sem assinatura ([MonetizationConfig.AdsOnly]) → [initialize], `true` (não precisa de loja);
     * - modo que vende e [hasStore] → [initialize], `true`. Com o dublê de QA já instalado, o
     *   `initialize` **não** configura o SDK (o `PurchaseManager` já está inicializado) e só liga o
     *   modo, o [premiumStatus] e a identidade sobre ele — é o que faz o paywall da build de QA abrir
     *   com os planos do dublê mesmo com a chave `PLACEHOLDER_*`;
     * - modo que vende e **sem** loja → [declareNotMonetized], `false` (degrada para gratuito em vez
     *   de configurar o SDK com chave de mentira).
     *
     * ```kotlin
     * MonetizationManager.initializeWhenStoreAvailable(monetizationConfigFor(modo, purchaseConfig))
     * ```
     *
     * @return `true` se inicializou.
     */
    fun initializeWhenStoreAvailable(
        config: MonetizationConfig?,
        userId: String? = null,
        premiumResolutionTimeout: Duration = PremiumStatus.DEFAULT_TIMEOUT,
    ): Boolean {
        if (config == null) {
            declareNotMonetized()
            return false
        }
        val purchase = config.purchaseConfig
        if (purchase != null && !hasStore(purchase)) {
            AppLogger.w(TAG, "Sem chave da loja nesta plataforma e sem loja instalada: o app segue gratuito")
            declareNotMonetized()
            return false
        }
        initialize(config, userId, premiumResolutionTimeout)
        return true
    }

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
     * @param premiumResolutionTimeout teto de espera pela primeira resposta da loja; passado o teto
     *   sem resposta, [premiumStatus] resolve como `Free(TIMEOUT)` (2.249.0).
     */
    fun initialize(
        config: MonetizationConfig,
        userId: String? = null,
        premiumResolutionTimeout: Duration = PremiumStatus.DEFAULT_TIMEOUT,
    ) {
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
            // Assina o repositório CORRENTE (2.251.0), não o do instante da inicialização: o
            // `PurchaseTestHooks.instalar/limpar` (e `PurchaseManager.initializeWith/reset`) trocam o
            // repositório depois, e até a 2.250.0 o manager continuava ouvindo o antigo — `isPremium` e
            // `premiumStatus` paravam enquanto o paywall (que lê o `PurchaseManager`) via o novo.
            subscriptionJob = PurchaseManager.currentRepository
                .flatMapLatest { repo ->
                    if (repo == null) {
                        flowOf(StoreSnapshot(null, SubscriptionInfo(isActive = false), SubscriptionReadState.FAILED))
                    } else {
                        combine(repo.subscriptionState, repo.subscriptionReadState) { info, read ->
                            StoreSnapshot(repo, info, read)
                        }
                    }
                }
                .onEach { snapshot -> applyStoreSnapshot(config, snapshot, premiumResolutionTimeout) }
                .launchIn(scope)
        }

        if (config.purchaseConfig == null) {
            // Modo que não vende assinatura: não há loja a esperar.
            updatePremiumStatus { current ->
                if (current.isResolved) current else PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD)
            }
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

    /** O estado de uma loja (repositório) num instante — `repo == null` = nenhuma loja instalada. */
    private data class StoreSnapshot(
        val repo: PurchaseRepository?,
        val info: SubscriptionInfo,
        val read: SubscriptionReadState,
    )

    /** O repositório que o manager está ouvindo, para detectar a troca. Só a coleta escreve. */
    private var observedRepository: PurchaseRepository? = null
    private var hasObservedRepository = false

    /**
     * Aplica um estado de loja. Na **troca de repositório** começa uma geração nova: o
     * [premiumStatus] é REAVALIADO a partir de `Unknown` (resolve na hora se o novo já nasce lido,
     * como os dublês; senão espera a 1ª leitura dele, com teto próprio) — é a única exceção ao
     * "resolvido nunca volta a `Unknown`", e existe porque a resposta anterior era de OUTRA loja.
     * Sem loja (`repo == null`, `PurchaseTestHooks.limpar()`), resolve `Free(NOT_SOLD)`.
     */
    private fun applyStoreSnapshot(config: MonetizationConfig, snapshot: StoreSnapshot, timeout: Duration) {
        _isPremium.value = snapshot.info.isActive
        _shouldShowAds.value = config.shouldShowAds(snapshot.info.isActive)

        val switched = !hasObservedRepository || snapshot.repo !== observedRepository
        if (!switched) {
            updatePremiumStatus { current -> resolvePremiumStatus(current, snapshot.info, snapshot.read) }
            return
        }
        val firstRepository = !hasObservedRepository
        hasObservedRepository = true
        observedRepository = snapshot.repo
        firstReadJob?.cancel()
        resolutionTimeoutJob?.cancel()

        val repo = snapshot.repo
        val fresh = if (repo == null) {
            PremiumStatus.Free(PremiumStatus.FreeReason.NOT_SOLD)
        } else {
            resolvePremiumStatus(PremiumStatus.Unknown, snapshot.info, snapshot.read)
        }
        if (firstRepository) {
            // A primeira loja segue a regra de sempre: não desfaz uma resposta que já existia.
            updatePremiumStatus { current ->
                when {
                    repo != null -> resolvePremiumStatus(current, snapshot.info, snapshot.read)
                    current.isResolved -> current
                    else -> fresh
                }
            }
        } else {
            _premiumStatus.value = fresh
        }
        if (repo == null) return

        // A primeira leitura desta loja (cache do SDK ou rede) — o caminho recomendado pela RevenueCat
        // (`getCustomerInfo`). Identidade ANTES da leitura, no mesmo launch (2.250.0): a leitura em
        // paralelo com o `reconcile` podia responder depois do `logIn` com o CustomerInfo do sujeito
        // anterior; o adaptador também descarta leitura iniciada antes de uma troca de identidade.
        firstReadJob = scope.launch {
            if (storeIdentity.isManaged) storeIdentity.reconcile()
            runCatching { repo.syncSubscriptionState() }
                .onFailure { AppLogger.w(TAG, "Primeira leitura da assinatura falhou: ${it.message}") }
        }
        resolutionTimeoutJob = scope.launch {
            delay(timeout)
            updatePremiumStatus { current ->
                if (current.isResolved) current
                else PremiumStatus.Free(PremiumStatus.FreeReason.TIMEOUT).also {
                    AppLogger.w(TAG, "Loja sem resposta em $timeout: premium presumido FREE")
                }
            }
        }
    }

    /** Aplica a transição garantindo que um estado resolvido nunca volta a `Unknown`. */
    internal fun updatePremiumStatus(transition: (PremiumStatus) -> PremiumStatus) {
        _premiumStatus.update { current ->
            val candidate = transition(current)
            if (current.isResolved && !candidate.isResolved) current else candidate
        }
    }

    /** Reseta o estado (util para testes). */
    fun reset() {
        subscriptionJob?.cancel()
        subscriptionJob = null
        firstReadJob?.cancel()
        firstReadJob = null
        resolutionTimeoutJob?.cancel()
        resolutionTimeoutJob = null
        observedRepository = null
        hasObservedRepository = false
        _config = null
        _initialized.value = false
        _isPremium.value = false
        _shouldShowAds.value = false
        _premiumStatus.value = PremiumStatus.Unknown
        PurchaseManager.reset()
        storeIdentity.reset()
    }
}

/**
 * Transição do [PremiumStatus] a cada estado de assinatura (regra pura, testada).
 *
 * - `isActive = true` é sempre [PremiumStatus.Premium]: assinatura ativa só vem da loja.
 * - [SubscriptionReadState.READ] → [PremiumStatus.Free] confirmado.
 * - [SubscriptionReadState.FAILED] → `Free(STORE_FAILURE)` se ainda não havia resposta; se havia,
 *   mantém (falha numa releitura não rebaixa quem já foi lido).
 * - [SubscriptionReadState.PENDING] → mantém: o valor corrente é o marcador de partida.
 */
internal fun resolvePremiumStatus(
    current: PremiumStatus,
    info: SubscriptionInfo,
    read: SubscriptionReadState,
): PremiumStatus = when {
    info.isActive -> PremiumStatus.Premium
    read == SubscriptionReadState.READ -> PremiumStatus.Free(PremiumStatus.FreeReason.STORE)
    read == SubscriptionReadState.FAILED ->
        if (current.isResolved) current else PremiumStatus.Free(PremiumStatus.FreeReason.STORE_FAILURE)
    else -> current
}
