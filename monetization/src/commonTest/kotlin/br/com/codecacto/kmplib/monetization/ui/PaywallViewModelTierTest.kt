package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.alert.PaymentAlertReporter
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementProvider
import br.com.codecacto.kmplib.monetization.entitlement.OfferingsOutcome
import br.com.codecacto.kmplib.monetization.entitlement.PurchaseOutcome
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialPeriod
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialUnit
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.PurchaseStore
import br.com.codecacto.kmplib.monetization.purchase.StoreIdentityStatus
import br.com.codecacto.kmplib.monetization.purchase.StoreReplacementMode
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionChange
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import br.com.codecacto.kmplib.monetization.purchase.TrialEligibility
import br.com.codecacto.kmplib.observability.CrashLevel
import br.com.codecacto.kmplib.observability.CrashReporter
import br.com.codecacto.kmplib.observability.CrashReporterConfig
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val APP = "rondaki"

private fun pacote(tier: String, cycle: String, months: Int, micros: Long, trial: Boolean = false) = PurchasePackage(
    packageId = "${tier}_$cycle",
    packageType = when (months) {
        1 -> PurchasePackageType.MONTHLY
        6 -> PurchasePackageType.SIX_MONTH
        else -> PurchasePackageType.ANNUAL
    },
    storeProductId = "premium_${tier}_${cycle}_$APP:$cycle",
    priceLabel = "R$ ${micros / 1_000_000}",
    priceAmountMicros = micros,
    currencyCode = "BRL",
    durationMonths = months,
    freeTrial = if (trial) FreeTrialPeriod(7, FreeTrialUnit.DAY) else null,
    trialEligibility = if (trial) TrialEligibility.ELIGIBLE else TrialEligibility.NO_OFFER,
)

/** A escada do RondAki: 3 degraus × 3 ciclos (9 pacotes na offering `default`). */
private val ESCADA = listOf("v3" to 30L, "v10" to 80L, "v25" to 150L).flatMap { (t, mensal) ->
    listOf(
        pacote(t, "mensal", 1, mensal * 1_000_000, trial = true),
        pacote(t, "semestral", 6, mensal * 5 * 1_000_000),
        pacote(t, "anual", 12, mensal * 9 * 1_000_000),
    )
}

private val DEGRAUS = listOf(
    PaywallTier("v3", "Até 3 vigias", 1),
    PaywallTier("v10", "Até 10 vigias", 2),
    PaywallTier("v25", "Até 25 vigias", 3),
)

private class TierStore(
    var offerings: OfferingsOutcome = OfferingsOutcome.Disponivel(ESCADA),
    var subscription: SubscriptionInfo? = null,
) : EntitlementProvider {
    private val _isPremium = MutableStateFlow(subscription?.isActive == true)
    override val isPremium: StateFlow<Boolean> = _isPremium
    var purchased: String? = null
    var changed: Pair<String, SubscriptionChange>? = null
    var changeResult: PurchaseOutcome = PurchaseOutcome.Ativado

    override suspend fun refresh() = Unit
    override suspend fun offerings(): List<PurchasePackage> = offerings.pacotes
    override suspend fun loadOfferings(): OfferingsOutcome = offerings
    override suspend fun subscriptionInfo(): SubscriptionInfo? = subscription
    override suspend fun purchasePackage(packageId: String): PurchaseOutcome {
        purchased = packageId
        _isPremium.value = true
        subscription = SubscriptionInfo(true, ESCADA.first { it.packageId == packageId }.storeProductId.substringBefore(':'), store = PurchaseStore.PLAY_STORE)
        return PurchaseOutcome.Ativado
    }
    override suspend fun changeSubscription(packageId: String, change: SubscriptionChange): PurchaseOutcome {
        changed = packageId to change
        return changeResult
    }
    override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.NadaParaRestaurar
}

private class AlertSink : CrashReporter {
    val tipos = mutableListOf<String>()
    val detalhes = mutableListOf<String>()
    override val isActive: Boolean = true
    override fun init(config: CrashReporterConfig) = Unit
    override fun captureException(throwable: Throwable, tags: Map<String, String>) = Unit
    override fun captureMessage(message: String, level: CrashLevel, tags: Map<String, String>, fingerprint: List<String>) {
        tags["tipo"]?.let(tipos::add)
        detalhes += message
    }
    override fun addBreadcrumb(message: String, category: String?, level: CrashLevel) = Unit
    override fun setUser(id: String?) = Unit
    override fun clearUser() = Unit
    override fun setTag(key: String, value: String) = Unit
}

/**
 * GAP-PT-M09 — o paywall da lib conhece degraus: chips, degrau inicial a partir do atual (de fora ou
 * da loja), selo por degrau, e a TROCA de plano pela loja para quem já assina (Play com o produto
 * antigo e o modo de substituição; outra loja recusada antes de comprar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTierTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var alerts: AlertSink

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        alerts = AlertSink()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        store: TierStore,
        currentTier: suspend () -> String? = { null },
        platform: PurchaseStore = PurchaseStore.PLAY_STORE,
        initial: PaywallInitialTier = PaywallInitialTier.NEXT_ABOVE_CURRENT,
    ) = PaywallViewModel(
        entitlementProvider = store,
        config = PaywallConfig(
            termsUrl = "https://exemplo.test/termos",
            privacyUrl = "https://exemplo.test/privacidade",
            tiers = PaywallTierConfig(tiers = DEGRAUS.reversed(), currentTierId = currentTier, initialTier = initial),
        ),
        paymentAlerts = PaymentAlertReporter(alerts, projeto = "teste", umaVezPorSessao = false),
        loadMessages = { PaywallMessages() },
        identityGate = { StoreIdentityStatus.UNMANAGED },
        platformStore = { platform },
    )

    private val PaywallViewModel.p get() = state.value.paywall

    private fun assinante(produto: String, loja: PurchaseStore? = PurchaseStore.PLAY_STORE) =
        TierStore(subscription = SubscriptionInfo(isActive = true, productId = produto, willRenew = true, store = loja))

    @Test
    fun `sem assinatura abre no degrau mais baixo com chips na ordem e so os planos dele`() {
        val vm = vm(TierStore())
        vm.onAction(PaywallHostAction.Load)

        assertEquals(listOf("v3", "v10", "v25"), vm.p.tiers.map { it.id })
        assertEquals("v3", vm.p.selectedTierId)
        assertEquals(listOf("v3_mensal", "v3_semestral", "v3_anual"), vm.p.plans.map { it.id })
        assertEquals(listOf("v3_anual"), vm.p.plans.filter { it.isRecommended }.map { it.id })
        assertFalse(vm.p.canChangePlan)
        assertNull(vm.p.currentTierId)
        assertTrue(vm.p.plans.first().trial != null, "quem não assina vê o trial que a loja confirmou")
        assertTrue(alerts.tipos.isEmpty())
    }

    @Test
    fun `degrau atual vindo de fora abre no proximo acima e marca o chip`() {
        val vm = vm(TierStore(), currentTier = { "V3" })
        vm.onAction(PaywallHostAction.Load)

        assertEquals("v3", vm.p.currentTierId)
        assertEquals("v10", vm.p.selectedTierId)
        assertEquals(listOf(true, false, false), vm.p.tiers.map { it.isCurrent })
    }

    @Test
    fun `falha ao ler o degrau de fora nao derruba a vitrine`() {
        val vm = vm(TierStore(), currentTier = { error("rede") })
        vm.onAction(PaywallHostAction.Load)
        assertEquals("v3", vm.p.selectedTierId)
        assertEquals(3, vm.p.plans.size)
    }

    @Test
    fun `escolher o degrau troca os planos e sobrevive a releitura`() {
        val vm = vm(TierStore())
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.SelectTier("v25"))
        assertEquals("v25", vm.p.selectedTierId)
        assertEquals(listOf("v25_mensal", "v25_semestral", "v25_anual"), vm.p.plans.map { it.id })
        assertEquals("v25_anual", vm.p.selectedPlanId)

        vm.onAction(PaywallHostAction.Load)
        assertEquals("v25", vm.p.selectedTierId, "o ON_RESUME não devolve ao degrau inicial")

        vm.onAction(PaywallHostAction.SelectTier("inexistente"))
        assertEquals("v25", vm.p.selectedTierId)
    }

    @Test
    fun `assinante ve a troca com o plano atual marcado e sem trial`() {
        val vm = vm(assinante("premium_v3_mensal_$APP"))
        vm.onAction(PaywallHostAction.Load)

        assertTrue(vm.p.isPremium)
        assertTrue(vm.p.canChangePlan)
        assertEquals("v3", vm.p.currentTierId, "sem degrau de fora, vale o produto ativo na loja")
        assertEquals("v10", vm.p.selectedTierId)
        vm.onAction(PaywallHostAction.SelectTier("v3"))
        assertEquals(listOf(true, false, false), vm.p.plans.map { it.isCurrent })
        assertTrue(vm.p.plans.all { it.trial == null })
    }

    @Test
    fun `tocar no plano atual nao compra`() {
        val store = assinante("premium_v3_mensal_$APP")
        val vm = vm(store, initial = PaywallInitialTier.CURRENT)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v3_mensal")))
        assertNull(store.changed)
        assertNull(store.purchased)
        assertFalse(vm.p.isPurchasing)
    }

    @Test
    fun `subir de degrau troca pela loja com o produto antigo e cobranca proporcional`() {
        val store = assinante("premium_v3_mensal_$APP")
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v10_mensal")))

        val (pacote, troca) = store.changed!!
        assertEquals("v10_mensal", pacote)
        assertEquals("premium_v3_mensal_$APP", troca.oldProductId)
        assertEquals(StoreReplacementMode.CHARGE_PRORATED_PRICE, troca.replacementMode)
        assertNull(store.purchased, "troca nunca vira uma segunda compra")
    }

    @Test
    fun `descer de degrau fica para a renovacao e avisa`() = runTest {
        val store = assinante("premium_v10_anual_$APP")
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.SelectTier("v3"))
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v3_anual")))

        assertEquals(StoreReplacementMode.DEFERRED, store.changed!!.second.replacementMode)
        val efeito = vm.effect.first()
        assertIs<PaywallHostEffect.ShowMessage>(efeito)
        assertEquals(PaywallMessages().tierChangeScheduled, efeito.message)
    }

    @Test
    fun `trocar so o ciclo tambem fica para a renovacao`() {
        val store = assinante("premium_v10_mensal_$APP")
        val vm = vm(store, initial = PaywallInitialTier.CURRENT)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v10_anual")))
        assertEquals(StoreReplacementMode.DEFERRED, store.changed!!.second.replacementMode)
    }

    @Test
    fun `upgrade mais barato por mes usa WITH_TIME_PRORATION`() {
        // Na escada padrão todo degrau acima custa mais por mês; aqui o anual do v25 sai a 50/mês,
        // abaixo dos 80/mês do v10 mensal — o Play recusaria CHARGE_PRORATED_PRICE.
        val barato = ESCADA.map { if (it.packageId == "v25_anual") it.copy(priceAmountMicros = 600_000_000) else it }
        val store = TierStore(
            offerings = OfferingsOutcome.Disponivel(barato),
            subscription = SubscriptionInfo(true, "premium_v10_mensal_$APP", store = PurchaseStore.PLAY_STORE),
        )
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v25_anual"))) // 50/mês < 80/mês
        assertEquals(StoreReplacementMode.WITH_TIME_PRORATION, store.changed!!.second.replacementMode)
    }

    @Test
    fun `assinatura feita na outra loja nao troca aqui`() {
        val store = assinante("premium_v3_mensal_$APP", loja = PurchaseStore.APP_STORE)
        val vm = vm(store, platform = PurchaseStore.PLAY_STORE)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v10_mensal")))

        assertNull(store.changed)
        assertNull(store.purchased)
        assertEquals(PaywallMessages().tierChangeOtherStore, vm.p.error)
        assertFalse(vm.p.isPurchasing)
    }

    @Test
    fun `premium promocional compra normal porque nao ha o que substituir`() {
        val store = assinante("rc_promo_premium_lifetime", loja = PurchaseStore.UNKNOWN)
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v10_mensal")))
        assertNull(store.changed)
        assertEquals("v10_mensal", store.purchased)
    }

    @Test
    fun `troca que falha por incidente alerta e mostra o erro`() {
        val store = assinante("premium_v3_mensal_$APP").apply {
            changeResult = PurchaseOutcome.Falha("x", PurchaseErrorCode.STORE_ERROR)
        }
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v10_mensal")))
        assertTrue(vm.p.error != null)
        assertTrue("compra_falhou" in alerts.tipos)
    }

    @Test
    fun `primeira compra com degraus relê e vira a tela de troca`() {
        val store = TierStore()
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        vm.onAction(PaywallHostAction.Paywall(PaywallAction.SelectPlan("v3_anual")))
        assertEquals("v3_anual", store.purchased)
        assertTrue(vm.p.isPremium)
        assertTrue(vm.p.canChangePlan)
        assertEquals("v3", vm.p.currentTierId)
    }

    @Test
    fun `degrau sem pacote na loja some e alerta`() {
        val semV25 = ESCADA.filterNot { it.packageId.startsWith("v25_") }
        val vm = vm(TierStore(offerings = OfferingsOutcome.Disponivel(semV25)))
        vm.onAction(PaywallHostAction.Load)
        assertEquals(listOf("v3", "v10"), vm.p.tiers.map { it.id })
        assertTrue("paywall_sem_plano" in alerts.tipos)
    }

    @Test
    fun `releitura que falha mantem os degraus na tela`() {
        val store = TierStore()
        val vm = vm(store)
        vm.onAction(PaywallHostAction.Load)
        store.offerings = OfferingsOutcome.Falha("sem rede", PurchaseErrorCode.NETWORK_ERROR)
        vm.onAction(PaywallHostAction.Load)
        assertEquals(3, vm.p.tiers.size)
        assertEquals(3, vm.p.plans.size)
        assertNull(vm.p.error)
    }
}
