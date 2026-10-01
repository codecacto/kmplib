package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.alert.PaymentAlertKind
import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementProvider
import br.com.codecacto.kmplib.monetization.entitlement.OfferingsOutcome
import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.entitlement.PlansResult
import br.com.codecacto.kmplib.monetization.entitlement.PurchaseOutcome
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialPeriod
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialUnit
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import br.com.codecacto.kmplib.monetization.purchase.TrialEligibility
import br.com.codecacto.kmplib.observability.CrashLevel
import br.com.codecacto.kmplib.observability.CrashReporter
import br.com.codecacto.kmplib.observability.CrashReporterConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Loja de mentira: devolve o que o teste manda e conta as chamadas. */
private class FakeStore(
    var offerings: OfferingsOutcome = OfferingsOutcome.Indisponivel,
    premium: Boolean = false,
    var purchaseResult: PurchaseOutcome = PurchaseOutcome.Ativado,
    var restoreResult: PurchaseOutcome = PurchaseOutcome.NadaParaRestaurar,
    var subscription: SubscriptionInfo? = null,
) : EntitlementProvider {
    private val _isPremium = MutableStateFlow(premium)
    override val isPremium: StateFlow<Boolean> = _isPremium
    var purchasedPackageId: String? = null
    var loadCount = 0
    /** Segura a leitura da loja até o teste soltar — para observar o estado no meio dela. */
    var gate: CompletableDeferred<Unit>? = null
    /** Roda no meio da compra (a folha da loja fechando dispara um ON_RESUME). */
    var duringPurchase: (() -> Unit)? = null

    override suspend fun refresh() = Unit
    override suspend fun offerings(): List<PurchasePackage> = offerings.pacotes
    override suspend fun loadOfferings(): OfferingsOutcome {
        loadCount++
        gate?.await()
        return offerings
    }
    override suspend fun subscriptionInfo(): SubscriptionInfo? = subscription
    override suspend fun purchasePackage(packageId: String): PurchaseOutcome {
        purchasedPackageId = packageId
        duringPurchase?.invoke()
        if (purchaseResult == PurchaseOutcome.Ativado) _isPremium.value = true
        return purchaseResult
    }
    override suspend fun restore(): PurchaseOutcome = restoreResult

    /** Offerings por id — o "sem trial" da opção B do iOS. */
    var offeringsById: Map<String, OfferingsOutcome> = emptyMap()
    val requestedOfferings = mutableListOf<String>()
    var purchasedWithoutFreeTrial: Boolean? = null

    override suspend fun loadOfferings(offeringId: String): OfferingsOutcome {
        requestedOfferings += offeringId
        return offeringsById[offeringId] ?: OfferingsOutcome.Vazio
    }

    override suspend fun purchasePackage(packageId: String, withoutFreeTrial: Boolean): PurchaseOutcome {
        purchasedWithoutFreeTrial = withoutFreeTrial
        return purchasePackage(packageId)
    }
}

/** Oferta central de mentira: registra o `forceReload` de cada leitura. */
private class FakeCentral(var result: PlansResult) {
    val forceReloads = mutableListOf<Boolean>()
    val source = PaywallOfferSource.CentralWithStoreFallback { force ->
        forceReloads += force
        result
    }
}

/** Reporter ativo que guarda o `tipo` de cada alerta enviado. */
private class RecordingCrashReporter : CrashReporter {
    val alertTypes = mutableListOf<String>()
    override val isActive: Boolean = true
    override fun init(config: CrashReporterConfig) = Unit
    override fun captureException(throwable: Throwable, tags: Map<String, String>) = Unit
    override fun captureMessage(
        message: String,
        level: CrashLevel,
        tags: Map<String, String>,
        fingerprint: List<String>,
    ) {
        tags["tipo"]?.let(alertTypes::add)
    }
    override fun addBreadcrumb(message: String, category: String?, level: CrashLevel) = Unit
    override fun setUser(id: String?) = Unit
    override fun clearUser() = Unit
    override fun setTag(key: String, value: String) = Unit
}

private fun pkg(
    id: String,
    months: Int?,
    type: PurchasePackageType,
    micros: Long,
    currency: String = "BRL",
) = PurchasePackage(
    packageId = id,
    packageType = type,
    storeProductId = "premium_${id}_teste",
    priceLabel = "R$ ${micros / 1_000_000}",
    priceAmountMicros = micros,
    currencyCode = currency,
    durationMonths = months,
)

private val MENSAL = pkg("\$rc_monthly", 1, PurchasePackageType.MONTHLY, 10_000_000L)
private val SEMESTRAL = pkg("\$rc_six_month", 6, PurchasePackageType.SIX_MONTH, 50_000_000L)
private val ANUAL = pkg("\$rc_annual", 12, PurchasePackageType.ANNUAL, 90_000_000L)

private fun centralPlan(nome: String, months: Int, ativo: Boolean = true, destaques: List<String> = emptyList()) =
    Plan(
        plano = nome.lowercase(),
        nome = nome,
        preco = null,
        storeProductId = "premium_${nome.lowercase()}_teste",
        durationMonths = months,
        tipo = null,
        ativo = ativo,
        destaques = destaques,
    )

/**
 * O ViewModel do paywall é a lógica de venda de ~15 apps. Trava: ordem e selo, oferta central com
 * fallback (ilegível ≠ vazia), compra, restauração, a virada para "assinatura ativa", a economia e a
 * régua de alertas (nem alerta falso, nem alerta perdido).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var crash: RecordingCrashReporter

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        crash = RecordingCrashReporter()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        store: FakeStore,
        source: PaywallOfferSource = PaywallOfferSource.Store,
        savings: PaywallSavings? = null,
        afterActivation: PaywallAfterActivation = PaywallAfterActivation.ShowActive,
        storeHighlights: (Int) -> List<String> = { emptyList() },
        onActivated: suspend () -> Unit = {},
        trialPolicy: PaywallTrialPolicy = PaywallTrialPolicy(),
    ) = PaywallViewModel(
        entitlementProvider = store,
        config = PaywallConfig(
            termsUrl = "https://exemplo.test/termos",
            privacyUrl = "https://exemplo.test/privacidade",
            offerSource = source,
            storeHighlights = storeHighlights,
            savings = savings,
            afterActivation = afterActivation,
            onPremiumActivated = onActivated,
            trialPolicy = trialPolicy,
        ),
        paymentAlerts = PaymentAlertReporter(crash, projeto = "teste", umaVezPorSessao = false),
        loadMessages = { PaywallMessages() },
    )

    private val PaywallViewModel.paywall get() = state.value.paywall

    // ------------------------------------------------------------ só loja

    @Test
    fun `nasce carregando sem plano`() {
        val p = viewModel(FakeStore()).paywall
        assertTrue(p.isLoadingPlans)
        assertTrue(p.plans.isEmpty())
    }

    @Test
    fun `planos saem Mensal Semestral Anual com selo no de maior duracao`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(ANUAL, MENSAL, SEMESTRAL))))
        vm.onAction(PaywallHostAction.Load)

        val p = vm.paywall
        assertFalse(p.isLoadingPlans)
        assertEquals(listOf(1, 6, 12), p.plans.map { it.durationMonths })
        assertEquals(listOf("Mensal", "Semestral", "Anual"), p.plans.map { it.name })
        assertEquals(listOf(ANUAL.packageId), p.plans.filter { it.isRecommended }.map { it.id })
        assertEquals(ANUAL.packageId, p.selectedPlanId)
        assertNull(p.error)
        assertTrue(crash.alertTypes.isEmpty())
    }

    @Test
    fun `sem anual o selo migra para o semestral`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, SEMESTRAL))))
        vm.onAction(PaywallHostAction.Load)
        assertEquals(listOf(SEMESTRAL.packageId), vm.paywall.plans.filter { it.isRecommended }.map { it.id })
    }

    @Test
    fun `beneficios do card vindo da loja saem da config`() {
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))),
            storeHighlights = { listOf("Sem limite") },
        )
        vm.onAction(PaywallHostAction.Load)
        assertEquals(listOf("Sem limite"), vm.paywall.plans.single().highlights)
    }

    @Test
    fun `loja sem pacote alerta PaywallSemPlano`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Vazio))
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.plans.isEmpty())
        assertEquals(listOf(PaymentAlertKind.PaywallSemPlano.slug), crash.alertTypes)
    }

    @Test
    fun `loja com pacote mas nada vendavel alerta PaywallSemPlano`() {
        val trimestral = pkg("\$rc_three_month", 3, PurchasePackageType.OTHER, 25_000_000L)
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(trimestral))))
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.plans.isEmpty())
        assertEquals(listOf(PaymentAlertKind.PaywallSemPlano.slug), crash.alertTypes)
    }

    @Test
    fun `build sem chave da loja nao alerta — e defeito de build nao de runtime`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Indisponivel))
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.plans.isEmpty())
        assertFalse(vm.paywall.isLoadingPlans)
        assertTrue(crash.alertTypes.isEmpty())
    }

    @Test
    fun `sem rede mostra o erro ao usuario mas nao alerta a fabrica`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Falha("offline", PurchaseErrorCode.NETWORK_ERROR)))
        vm.onAction(PaywallHostAction.Load)
        assertEquals(PaywallMessages().purchaseErrors.networkError, vm.paywall.error)
        assertTrue(crash.alertTypes.isEmpty())
    }

    @Test
    fun `falha de loja que e incidente alerta LojaIndisponivel`() {
        viewModel(FakeStore(offerings = OfferingsOutcome.Falha("config", PurchaseErrorCode.CONFIGURATION_ERROR)))
            .onAction(PaywallHostAction.Load)
        assertEquals(listOf(PaymentAlertKind.LojaIndisponivel.slug), crash.alertTypes)
    }

    @Test
    fun `releitura que falha nao apaga a vitrine ja desenhada`() {
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, ANUAL)))
        val vm = viewModel(store)
        vm.onAction(PaywallHostAction.Load)
        store.offerings = OfferingsOutcome.Falha("offline", PurchaseErrorCode.NETWORK_ERROR)
        vm.onAction(PaywallHostAction.Load)

        assertEquals(2, vm.paywall.plans.size)
        assertNull(vm.paywall.error)
        assertFalse(vm.paywall.isLoadingPlans)
    }

    @Test
    fun `releitura com vitrine na tela nao volta ao esqueleto`() {
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)))
        val vm = viewModel(store)
        vm.onAction(PaywallHostAction.Load)
        store.gate = CompletableDeferred()
        store.offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, ANUAL))
        vm.onAction(PaywallHostAction.Load)

        // No meio da releitura: a vitrine antiga continua, sem esqueleto.
        assertFalse(vm.paywall.isLoadingPlans)
        assertEquals(1, vm.paywall.plans.size)

        store.gate!!.complete(Unit)
        assertEquals(2, vm.paywall.plans.size)
    }

    @Test
    fun `puxar para atualizar liga o indicador so durante a leitura`() {
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)))
        val vm = viewModel(store)
        store.gate = CompletableDeferred()
        vm.onAction(PaywallHostAction.Refresh)
        assertTrue(vm.state.value.isRefreshing)
        assertFalse(vm.paywall.isLoadingPlans)
        store.gate!!.complete(Unit)
        assertFalse(vm.state.value.isRefreshing)
    }

    // ------------------------------------------------------------ quem já assina

    @Test
    fun `quem ja assina ve a assinatura ativa com o bloco gerenciar`() {
        val sub = SubscriptionInfo(isActive = true, productId = "premium_mensal_teste", willRenew = true)
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)), premium = true, subscription = sub),
        )
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.isPremium)
        assertEquals(sub, vm.paywall.subscription)
    }

    @Test
    fun `assinatura ativa lida agora vence o StateFlow atrasado`() {
        val sub = SubscriptionInfo(isActive = true, productId = "premium_anual_teste", willRenew = true)
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)), premium = false, subscription = sub),
        )
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.isPremium)
    }

    @Test
    fun `assinatura inativa nao aparece no estado`() {
        val vm = viewModel(
            FakeStore(
                offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)),
                subscription = SubscriptionInfo(isActive = false),
            ),
        )
        vm.onAction(PaywallHostAction.Load)
        assertFalse(vm.paywall.isPremium)
        assertNull(vm.paywall.subscription)
    }

    // ------------------------------------------------------------ compra

    @Test
    fun `compra concluida vira assinatura ativa na mesma tela`() {
        val sub = SubscriptionInfo(isActive = true, productId = "premium_anual_teste", willRenew = true)
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, ANUAL)), subscription = sub)
        var activated = 0
        val vm = viewModel(store, onActivated = { activated++ })
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(ANUAL.packageId)))

        assertEquals(ANUAL.packageId, store.purchasedPackageId)
        val p = vm.paywall
        assertTrue(p.isPremium)
        assertEquals(sub, p.subscription)
        assertFalse(p.isPurchasing)
        assertNull(p.purchasingPlanId)
        assertEquals(1, activated)
    }

    @Test
    fun `com afterActivation Close a compra fecha a tela`() = runTest {
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))),
            afterActivation = PaywallAfterActivation.Close,
        )
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(PaywallHostEffect.Close, vm.effect.first())
    }

    @Test
    fun `falha no onPremiumActivated nao desfaz a compra`() {
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))),
            onActivated = { error("cache") },
        )
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertTrue(vm.paywall.isPremium)
        assertNull(vm.paywall.error)
    }

    @Test
    fun `compra cancelada nao vira erro`() {
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)), purchaseResult = PurchaseOutcome.Cancelado),
        )
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))

        val p = vm.paywall
        assertFalse(p.isPremium)
        assertFalse(p.isPurchasing)
        assertNull(p.error)
        assertTrue(crash.alertTypes.isEmpty())
    }

    @Test
    fun `cartao recusado mostra a mensagem da lib e nao alerta`() {
        val vm = viewModel(
            FakeStore(
                offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)),
                purchaseResult = PurchaseOutcome.Falha("sdk", PurchaseErrorCode.PAYMENT_DECLINED),
            ),
        )
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))

        assertEquals(PaywallMessages().purchaseErrors.paymentDeclined, vm.paywall.error)
        assertTrue(crash.alertTypes.isEmpty())
    }

    @Test
    fun `falha de compra que e incidente alerta CompraFalhou`() {
        val vm = viewModel(
            FakeStore(
                offerings = OfferingsOutcome.Disponivel(listOf(MENSAL)),
                purchaseResult = PurchaseOutcome.Falha("sdk", PurchaseErrorCode.STORE_ERROR),
            ),
        )
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(listOf(PaymentAlertKind.CompraFalhou.slug), crash.alertTypes)
    }

    @Test
    fun `build sem billing responde assinatura indisponivel`() {
        val vm = viewModel(FakeStore(purchaseResult = PurchaseOutcome.Indisponivel))
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(PaywallMessages().unavailable, vm.paywall.error)
    }

    @Test
    fun `releitura durante a compra e ignorada`() {
        val store = FakeStore(purchaseResult = PurchaseOutcome.Cancelado)
        val vm = viewModel(store)
        store.duringPurchase = { vm.onAction(PaywallHostAction.Load) }
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(0, store.loadCount)
    }

    @Test
    fun `segundo toque durante a compra nao compra de novo`() {
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, ANUAL)))
        val vm = viewModel(store)
        vm.onAction(PaywallHostAction.Load)
        store.duringPurchase = {
            store.duringPurchase = null
            vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(ANUAL.packageId)))
        }
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(MENSAL.packageId, store.purchasedPackageId)
    }

    // ------------------------------------------------------------ restaurar

    @Test
    fun `restaurar sem compra avisa por mensagem`() = runTest {
        val vm = viewModel(FakeStore(restoreResult = PurchaseOutcome.NadaParaRestaurar))
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.Restore))

        assertEquals(PaywallHostEffect.ShowMessage(PaywallMessages().nothingToRestore), vm.effect.first())
        assertFalse(vm.paywall.isPurchasing)
        assertNull(vm.paywall.error)
    }

    @Test
    fun `restaurar com assinatura ativa vira premium`() {
        val vm = viewModel(FakeStore(restoreResult = PurchaseOutcome.Ativado))
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.Restore))
        assertTrue(vm.paywall.isPremium)
    }

    @Test
    fun `restauracao que falha por incidente alerta RestauracaoFalhou`() {
        val vm = viewModel(FakeStore(restoreResult = PurchaseOutcome.Falha("sdk", PurchaseErrorCode.UNKNOWN)))
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.Restore))
        assertEquals(listOf(PaymentAlertKind.RestauracaoFalhou.slug), crash.alertTypes)
        assertEquals(PaywallMessages().purchaseErrors.unknown, vm.paywall.error)
    }

    // ------------------------------------------------------------ navegação

    @Test
    fun `gerenciar assinatura abre a loja da plataforma`() = runTest {
        val vm = viewModel(FakeStore())
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.ManageSubscription))
        assertEquals(PaywallHostEffect.OpenSubscriptionManagement, vm.effect.first())
    }

    @Test
    fun `termos e privacidade abrem as URLs legais do app`() = runTest {
        val vm = viewModel(FakeStore())
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.Terms))
        assertEquals(PaywallHostEffect.OpenUrl("https://exemplo.test/termos"), vm.effect.first())
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.Privacy))
        assertEquals(PaywallHostEffect.OpenUrl("https://exemplo.test/privacidade"), vm.effect.first())
    }

    @Test
    fun `voltar e ajuda viram navegacao`() = runTest {
        val vm = viewModel(FakeStore())
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.Back))
        assertEquals(PaywallHostEffect.Close, vm.effect.first())
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.OpenDeveloper))
        assertEquals(PaywallHostEffect.OpenDeveloper, vm.effect.first())
    }

    @Test
    fun `dispensar o erro sem plano na tela tenta a loja de novo`() {
        val store = FakeStore(offerings = OfferingsOutcome.Falha("offline", PurchaseErrorCode.NETWORK_ERROR))
        val vm = viewModel(store)
        vm.onAction(PaywallHostAction.Load)
        store.offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.DismissError))

        assertEquals(2, store.loadCount)
        assertEquals(1, vm.paywall.plans.size)
        assertNull(vm.paywall.error)
    }

    @Test
    fun `medidor de uso entra e sai do estado`() {
        val vm = viewModel(FakeStore())
        val usage = UsageSnapshot(feature = "locacoes", contagem = 5, limite = 5)
        vm.onAction(PaywallHostAction.ShowUsage(usage))
        assertEquals(usage, vm.paywall.usage)
        vm.onAction(PaywallHostAction.ShowUsage(null))
        assertNull(vm.paywall.usage)
    }

    @Test
    fun `puxar para atualizar desliga o indicador ao fim`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))))
        vm.onAction(PaywallHostAction.Refresh)
        assertFalse(vm.state.value.isRefreshing)
        assertEquals(1, vm.paywall.plans.size)
    }

    // ------------------------------------------------------------ oferta central + fallback

    @Test
    fun `oferta central correlaciona com a loja e mantem a ordem e o selo`() {
        val central = FakeCentral(
            PlansResult.Available(
                listOf(centralPlan("Anual", 12), centralPlan("Mensal", 1, destaques = listOf("Tudo"))),
                fromCache = false,
            ),
        )
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, SEMESTRAL, ANUAL))),
            source = central.source,
        )
        vm.onAction(PaywallHostAction.Load)

        val p = vm.paywall
        // O semestral está na loja mas DESLIGADO na central: não aparece.
        assertEquals(listOf(MENSAL.packageId, ANUAL.packageId), p.plans.map { it.id })
        assertEquals(listOf("Tudo"), p.plans.first().highlights)
        assertEquals(listOf(ANUAL.packageId), p.plans.filter { it.isRecommended }.map { it.id })
        assertTrue(crash.alertTypes.isEmpty())
        assertEquals(listOf(false), central.forceReloads)
    }

    @Test
    fun `oferta central ilegivel cai para a loja e alerta OfertaCentralIndisponivel`() {
        val central = FakeCentral(PlansResult.Unavailable("HTTP 401"))
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, SEMESTRAL))),
            source = central.source,
            storeHighlights = { listOf("Ilimitado") },
        )
        vm.onAction(PaywallHostAction.Load)

        val p = vm.paywall
        assertEquals(listOf(1, 6), p.plans.map { it.durationMonths })
        assertEquals(listOf("Mensal", "Semestral"), p.plans.map { it.name })
        assertEquals(listOf("Ilimitado"), p.plans.first().highlights)
        assertEquals(listOf(PaymentAlertKind.OfertaCentralIndisponivel.slug), crash.alertTypes)
    }

    @Test
    fun `oferta central VAZIA nao e fallback — paywall vazio e PaywallSemPlano`() {
        val central = FakeCentral(PlansResult.Available(emptyList(), fromCache = false))
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))),
            source = central.source,
        )
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.plans.isEmpty())
        assertEquals(listOf(PaymentAlertKind.PaywallSemPlano.slug), crash.alertTypes)
    }

    @Test
    fun `sem rede nem a oferta central alerta — e o usuario offline`() {
        val central = FakeCentral(PlansResult.Unavailable("timeout"))
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Falha("offline", PurchaseErrorCode.NETWORK_ERROR)),
            source = central.source,
        )
        vm.onAction(PaywallHostAction.Load)
        assertTrue(crash.alertTypes.isEmpty())
        assertEquals(PaywallMessages().purchaseErrors.networkError, vm.paywall.error)
    }

    @Test
    fun `puxar para atualizar rele a oferta central sem cache`() {
        val central = FakeCentral(PlansResult.Available(listOf(centralPlan("Mensal", 1)), fromCache = false))
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))), source = central.source)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Refresh)
        assertEquals(listOf(false, true), central.forceReloads)
    }

    @Test
    fun `leitura central que lanca excecao nao trava a tela em carregando`() {
        val source = PaywallOfferSource.CentralWithStoreFallback { error("boom") }
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL))), source = source)
        vm.onAction(PaywallHostAction.Load)
        assertFalse(vm.paywall.isLoadingPlans)
        assertEquals(PaywallMessages().purchaseErrors.unknown, vm.paywall.error)
    }

    // ------------------------------------------------------------ economia

    @Test
    fun `economia sobre o mensal vai no selo do plano de destaque`() {
        val vm = viewModel(
            FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, SEMESTRAL, ANUAL))),
            savings = PaywallSavings(pricePerMonthLabel = { micros, cur -> "$cur ${micros / 10_000}/mês" }),
        )
        vm.onAction(PaywallHostAction.Load)

        val byId = vm.paywall.plans.associateBy { it.id }
        // Mensal 10,00/mês · Semestral 8,33/mês (16%) · Anual 7,50/mês (25%)
        assertNull(byId.getValue(MENSAL.packageId).badgeLabel)
        assertNull(byId.getValue(MENSAL.packageId).pricePerMonthLabel)
        assertEquals("Economize 16%", byId.getValue(SEMESTRAL.packageId).badgeLabel)
        assertEquals("Economize 25%", byId.getValue(ANUAL.packageId).badgeLabel)
        assertEquals("BRL 750/mês", byId.getValue(ANUAL.packageId).pricePerMonthLabel)
    }

    @Test
    fun `economia desligada nao mexe nos cards`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(MENSAL, ANUAL))))
        vm.onAction(PaywallHostAction.Load)
        assertTrue(vm.paywall.plans.all { it.badgeLabel == null && it.pricePerMonthLabel == null })
    }

    // ------------------------------------------------------------ teste grátis pela loja (2.231.0)

    private fun comTrial(p: PurchasePackage, eligibility: TrialEligibility, canSkip: Boolean = true) =
        p.copy(freeTrial = FreeTrialPeriod(1, FreeTrialUnit.WEEK), trialEligibility = eligibility, canSkipFreeTrial = canSkip)

    @Test
    fun `trial confirmado pela loja vira 7 dias gratis no card com o termo de cobranca`() {
        val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, TrialEligibility.ELIGIBLE)))))
        vm.onAction(PaywallHostAction.Load)

        val plano = vm.paywall.plans.single()
        assertEquals("7 dias", plano.trial?.periodLabel)
        assertEquals("Começar 7 dias grátis", PaywallTexts().ctaLabel(plano))
        assertEquals(
            "Grátis por 7 dias, depois R$ 10/mês. Renova automaticamente; cancele quando quiser.",
            PaywallTexts().trialTerms(plano),
        )
    }

    @Test
    fun `elegibilidade desconhecida ou negada nunca promete trial`() {
        listOf(TrialEligibility.UNKNOWN, TrialEligibility.INELIGIBLE, TrialEligibility.NO_OFFER).forEach { e ->
            val vm = viewModel(FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, e)))))
            vm.onAction(PaywallHostAction.Load)
            val plano = vm.paywall.plans.single()
            assertNull(plano.trial, "elegibilidade $e")
            assertEquals("Assinar", PaywallTexts().ctaLabel(plano))
            assertNull(PaywallTexts().trialTerms(plano))
        }
    }

    @Test
    fun `trial ja usado no backend - Android compra o plano base e mostra Assinar`() {
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, TrialEligibility.ELIGIBLE))))
        val vm = viewModel(store, trialPolicy = PaywallTrialPolicy(alreadyUsed = { true }))
        vm.onAction(PaywallHostAction.Load)

        assertNull(vm.paywall.plans.single().trial)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(true, store.purchasedWithoutFreeTrial)
    }

    @Test
    fun `trial ja usado - iOS sem offering alternativo (opcao A) mostra o que a Apple vai dar`() {
        val store = FakeStore(
            offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, TrialEligibility.ELIGIBLE, canSkip = false))),
        )
        val vm = viewModel(store, trialPolicy = PaywallTrialPolicy(alreadyUsed = { true }))
        vm.onAction(PaywallHostAction.Load)

        assertEquals("7 dias", vm.paywall.plans.single().trial?.periodLabel)
        assertTrue(store.requestedOfferings.isEmpty())
    }

    @Test
    fun `trial ja usado - iOS com offering sem trial (opcao B) vende o offering alternativo`() {
        val semTrial = MENSAL.copy(packageId = "\$rc_monthly", storeProductId = "premium_mensal_teste_sem_trial")
        val store = FakeStore(
            offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, TrialEligibility.ELIGIBLE, canSkip = false))),
        ).apply { offeringsById = mapOf("sem-trial" to OfferingsOutcome.Disponivel(listOf(semTrial))) }
        val vm = viewModel(store, trialPolicy = PaywallTrialPolicy(alreadyUsed = { true }, offeringWithoutTrial = "sem-trial"))
        vm.onAction(PaywallHostAction.Load)

        assertEquals(listOf("sem-trial"), store.requestedOfferings)
        assertNull(vm.paywall.plans.single().trial)
    }

    @Test
    fun `offering sem trial vazio volta ao catalogo normal - nunca tela vazia`() {
        val store = FakeStore(
            offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, TrialEligibility.ELIGIBLE, canSkip = false))),
        )
        val vm = viewModel(store, trialPolicy = PaywallTrialPolicy(alreadyUsed = { true }, offeringWithoutTrial = "sem-trial"))
        vm.onAction(PaywallHostAction.Load)

        assertEquals(1, vm.paywall.plans.size)
        assertEquals("7 dias", vm.paywall.plans.single().trial?.periodLabel)
    }

    @Test
    fun `falha ao ler o trial usado deixa a loja decidir`() {
        val store = FakeStore(offerings = OfferingsOutcome.Disponivel(listOf(comTrial(MENSAL, TrialEligibility.ELIGIBLE))))
        val vm = viewModel(store, trialPolicy = PaywallTrialPolicy(alreadyUsed = { error("sem rede") }))
        vm.onAction(PaywallHostAction.Load)

        assertEquals("7 dias", vm.paywall.plans.single().trial?.periodLabel)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan(MENSAL.packageId)))
        assertEquals(false, store.purchasedWithoutFreeTrial)
    }
}
