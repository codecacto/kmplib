package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import br.com.codecacto.kmplib.monetization.PremiumStatus.FreeReason
import br.com.codecacto.kmplib.monetization.entitlement.AssertResult
import br.com.codecacto.kmplib.monetization.entitlement.CentralAccessConfig
import br.com.codecacto.kmplib.monetization.entitlement.CentralAccessState
import br.com.codecacto.kmplib.monetization.entitlement.Entitlement
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementCategory
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementRepository
import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.entitlement.PreferencesCentralAccessMemory
import br.com.codecacto.kmplib.monetization.entitlement.RememberedCentralAccess
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import br.com.codecacto.kmplib.monetization.entitlement.combinePremiumStatus
import br.com.codecacto.kmplib.monetization.purchase.ConsumablePurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchaseProduct
import br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.RestoreResult
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionReadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Acesso liberado na central (2.374.0): premium = loja OU admin global. O manager coleta em
 * `Dispatchers.Default` — espera em tempo real, como o [PremiumStatusTest].
 */
class CentralAccessTest {

    private val purchaseConfig = PurchaseConfig(androidApiKey = "goog_x", iosApiKey = "appl_x")

    @BeforeTest
    fun antes() = MonetizationManager.reset()

    @AfterTest
    fun depois() = MonetizationManager.reset()

    // ------------------------------------------------------------------ regra pura

    @Test
    fun `central liberada vence ate loja sem resposta`() {
        assertEquals(
            PremiumStatus.Premium,
            combinePremiumStatus(PremiumStatus.Unknown, PremiumStatus.Unknown, CentralAccessState.GRANTED),
        )
        assertEquals(
            PremiumStatus.Premium,
            combinePremiumStatus(PremiumStatus.Unknown, PremiumStatus.Free(FreeReason.STORE), CentralAccessState.GRANTED),
        )
    }

    @Test
    fun `a central nunca derruba a loja`() {
        for (c in CentralAccessState.entries) {
            assertEquals(PremiumStatus.Premium, combinePremiumStatus(PremiumStatus.Unknown, PremiumStatus.Premium, c))
        }
    }

    @Test
    fun `central pendente segura o gate so enquanto nada foi publicado`() {
        val free = PremiumStatus.Free(FreeReason.STORE)
        assertEquals(PremiumStatus.Unknown, combinePremiumStatus(PremiumStatus.Unknown, free, CentralAccessState.PENDING))
        assertEquals(free, combinePremiumStatus(free, free, CentralAccessState.PENDING))
        assertEquals(free, combinePremiumStatus(PremiumStatus.Unknown, free, CentralAccessState.NOT_GRANTED))
        assertEquals(free, combinePremiumStatus(PremiumStatus.Unknown, free, CentralAccessState.UNAVAILABLE))
        assertEquals(free, combinePremiumStatus(PremiumStatus.Unknown, free, CentralAccessState.DISABLED))
        assertEquals(
            PremiumStatus.Unknown,
            combinePremiumStatus(PremiumStatus.Premium, PremiumStatus.Unknown, CentralAccessState.NOT_GRANTED),
        )
    }

    // ------------------------------------------------------------------ manager

    @Test
    fun `isento na central vira premium e some o anuncio`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = false))
        val central = CentralFalsa(resposta(isento()))
        MonetizationManager.enableCentralAccess(CentralAccessConfig(central))

        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Premium })
        assertTrue(MonetizationManager.isPremium.value)
        assertFalse(MonetizationManager.shouldShowAds.value)
        assertEquals(CentralAccessState.GRANTED, MonetizationManager.centralAccess.value)
        // Isento não é pagante.
        assertEquals(false, MonetizationManager.awaitPayingOrNull(1.seconds))
    }

    @Test
    fun `grant vitalicio conta como liberado e free nao`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = false))
        val central = CentralFalsa(resposta(Entitlement(plano = "premium", validoAte = null)))
        MonetizationManager.enableCentralAccess(CentralAccessConfig(central))
        assertTrue(aguardar { MonetizationManager.isPremium.value })

        MonetizationManager.disableCentralAccess()
        assertTrue(aguardar { !MonetizationManager.isPremium.value })
        assertTrue(MonetizationManager.shouldShowAds.value)

        MonetizationManager.enableCentralAccess(CentralAccessConfig(CentralFalsa(resposta(Entitlement.FREE))))
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.NOT_GRANTED })
        assertEquals(PremiumStatus.Free(FreeReason.STORE), MonetizationManager.premiumStatus.value)
        assertTrue(MonetizationManager.shouldShowAds.value)
    }

    @Test
    fun `sem ligar a central nada muda`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = false))
        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Free(FreeReason.STORE) })
        assertEquals(CentralAccessState.DISABLED, MonetizationManager.centralAccess.value)
        assertTrue(MonetizationManager.shouldShowAds.value)
    }

    @Test
    fun `assinante da loja continua premium com a central fora do ar`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = true))
        MonetizationManager.enableCentralAccess(
            CentralAccessConfig(CentralFalsa(ApiResult.Error(503, "fora")), firstReadTimeout = 100.milliseconds),
        )
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.UNAVAILABLE })
        assertEquals(PremiumStatus.Premium, MonetizationManager.premiumStatus.value)
        assertFalse(MonetizationManager.shouldShowAds.value)
    }

    @Test
    fun `falha na releitura mantem o liberado`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = false))
        val central = CentralFalsa(resposta(isento()))
        MonetizationManager.enableCentralAccess(CentralAccessConfig(central, minRefreshInterval = 0.milliseconds))
        assertTrue(aguardar { MonetizationManager.isPremium.value })

        central.resposta = ApiResult.Error(-1, "sem rede")
        MonetizationManager.refreshCentralAccess()
        assertEquals(2, central.chamadas)
        assertTrue(MonetizationManager.isPremium.value)
        assertEquals(CentralAccessState.GRANTED, MonetizationManager.centralAccess.value)

        // Revogada no admin: a próxima leitura boa tira.
        central.resposta = resposta(Entitlement.FREE)
        MonetizationManager.refreshCentralAccess()
        assertFalse(MonetizationManager.isPremium.value)
        assertTrue(MonetizationManager.shouldShowAds.value)
    }

    @Test
    fun `cache curto nao rele sem force`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = false))
        val central = CentralFalsa(resposta(Entitlement.FREE))
        MonetizationManager.enableCentralAccess(CentralAccessConfig(central, minRefreshInterval = 60.seconds))
        assertTrue(aguardar { central.chamadas == 1 && MonetizationManager.centralAccess.value == CentralAccessState.NOT_GRANTED })

        MonetizationManager.refreshCentralAccess()
        assertEquals(1, central.chamadas)
        central.resposta = resposta(isento())
        MonetizationManager.refreshCentralAccess(force = true)
        assertEquals(2, central.chamadas)
        assertTrue(MonetizationManager.isPremium.value)
    }

    @Test
    fun `primeira leitura pendente segura o gate e o teto solta`() = runBlocking<Unit> {
        val trava = CompletableDeferred<Unit>()
        val central = CentralFalsa(resposta(Entitlement.FREE), antes = { trava.await() })
        // Central ligada ANTES da loja responder: a loja diz "grátis", mas o gate espera a central.
        MonetizationManager.enableCentralAccess(CentralAccessConfig(central, firstReadTimeout = 1.seconds))
        iniciarSemEsperar(LojaLida(ativa = false))
        delay(300)
        assertEquals(PremiumStatus.Unknown, MonetizationManager.premiumStatus.value)

        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.UNAVAILABLE })
        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Free(FreeReason.STORE) })
        trava.complete(Unit)
    }

    @Test
    fun `troca de conta zera e resposta da conta anterior e descartada`() = runBlocking<Unit> {
        iniciarCom(LojaLida(ativa = false))
        val contas = MutableStateFlow<String?>("conta-a")
        val central = CentralFalsa(resposta(isento()))
        MonetizationManager.enableCentralAccess(CentralAccessConfig(central, accounts = contas))
        assertTrue(aguardar { MonetizationManager.isPremium.value })
        assertTrue(MonetizationManager.isPremiumForSubject("conta-a"))
        assertFalse(MonetizationManager.isPremiumForSubject("conta-b"))

        central.resposta = resposta(Entitlement.FREE)
        contas.value = "conta-b"
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.NOT_GRANTED })
        assertFalse(MonetizationManager.isPremium.value)

        contas.value = null
        delay(200)
        assertEquals(CentralAccessState.NOT_GRANTED, MonetizationManager.centralAccess.value)
        MonetizationManager.refreshCentralAccess(force = true)
        assertEquals(2, central.chamadas, "sem conta não lê")
    }

    @Test
    fun `liberacao lembrada vale sem rede na abertura seguinte`() = runBlocking<Unit> {
        val memoria = PreferencesCentralAccessMemory(InMemoryAppPreferences())
        memoria.save(RememberedCentralAccess("uid-1", granted = true, savedAtEpochMillis = agora()))
        iniciarCom(LojaLida(ativa = false))
        MonetizationManager.enableCentralAccess(
            CentralAccessConfig(
                CentralFalsa(ApiResult.Error(-1, "sem rede")),
                accounts = MutableStateFlow("uid-1"),
                memory = memoria,
            ),
        )
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.GRANTED })
        delay(100)
        assertTrue(MonetizationManager.isPremium.value)
    }

    @Test
    fun `lembranca de outra conta ou vencida nao vale`() = runBlocking<Unit> {
        val memoria = PreferencesCentralAccessMemory(InMemoryAppPreferences())
        memoria.save(RememberedCentralAccess("uid-1", granted = true, savedAtEpochMillis = agora() - 8.days.inWholeMilliseconds))
        iniciarCom(LojaLida(ativa = false))
        MonetizationManager.enableCentralAccess(
            CentralAccessConfig(
                CentralFalsa(ApiResult.Error(-1, "sem rede")),
                accounts = MutableStateFlow("uid-1"),
                memory = memoria,
                firstReadTimeout = 100.milliseconds,
            ),
        )
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.UNAVAILABLE })
        assertFalse(MonetizationManager.isPremium.value)

        MonetizationManager.reset()
        memoria.save(RememberedCentralAccess("uid-1", granted = true, savedAtEpochMillis = agora()))
        iniciarCom(LojaLida(ativa = false))
        MonetizationManager.enableCentralAccess(
            CentralAccessConfig(
                CentralFalsa(ApiResult.Error(-1, "sem rede")),
                accounts = MutableStateFlow("uid-2"),
                memory = memoria,
                firstReadTimeout = 100.milliseconds,
            ),
        )
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.UNAVAILABLE })
        assertFalse(MonetizationManager.isPremium.value)
    }

    @Test
    fun `leitura boa grava na memoria`() = runBlocking<Unit> {
        val memoria = PreferencesCentralAccessMemory(InMemoryAppPreferences())
        assertNull(memoria.load())
        iniciarCom(LojaLida(ativa = false))
        MonetizationManager.enableCentralAccess(
            CentralAccessConfig(CentralFalsa(resposta(isento())), accounts = MutableStateFlow("uid-9"), memory = memoria),
        )
        assertTrue(aguardar { runBlocking { memoria.load() }?.granted == true })
        assertEquals("uid-9", memoria.load()?.account)
        memoria.clear()
        assertNull(memoria.load())
    }

    @Test
    fun `AdsOnly ignora a central`() = runBlocking<Unit> {
        MonetizationManager.initialize(MonetizationConfig.AdsOnly)
        MonetizationManager.enableCentralAccess(CentralAccessConfig(CentralFalsa(resposta(isento()))))
        assertTrue(aguardar { MonetizationManager.centralAccess.value == CentralAccessState.GRANTED })
        assertFalse(MonetizationManager.isPremium.value)
        assertTrue(MonetizationManager.shouldShowAds.value)
    }

    @Test
    fun `central antes do initialize ja resolve o gate`() = runBlocking<Unit> {
        MonetizationManager.enableCentralAccess(CentralAccessConfig(CentralFalsa(resposta(isento()))))
        assertTrue(MonetizationManager.awaitPremiumResolved(2.seconds))
    }

    // ------------------------------------------------------------------ apoio

    private fun agora(): Long = br.com.codecacto.kmplib.core.util.currentTimeMillis()

    private fun isento() = Entitlement.FREE.copy(categoria = EntitlementCategory.EXEMPT)

    private fun resposta(e: Entitlement): ApiResult<Entitlement> = ApiResult.Success(e)

    /** Inicia com a loja e ESPERA a resposta dela (o dublê leva ~200 ms na JVM fria). */
    private suspend fun iniciarCom(loja: PurchaseRepository) {
        iniciarSemEsperar(loja)
        assertTrue(aguardar { MonetizationManager.premiumStatus.value.isResolved }, "a loja respondeu")
    }

    private fun iniciarSemEsperar(loja: PurchaseRepository) {
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)
    }

    private suspend fun aguardar(condicao: () -> Boolean): Boolean = withContext(Dispatchers.Default) {
        withTimeoutOrNull(3_000) {
            while (!condicao()) delay(10)
            true
        } ?: false
    }

    private class CentralFalsa(
        var resposta: ApiResult<Entitlement>,
        private val antes: suspend () -> Unit = {},
    ) : EntitlementRepository {
        var chamadas = 0
            private set

        override suspend fun getEntitlement(): ApiResult<Entitlement> {
            chamadas++
            antes()
            return resposta
        }

        override suspend fun getUsage(feature: String): ApiResult<UsageSnapshot> = ApiResult.Error(501, "n/a")
        override suspend fun getPlans(): ApiResult<List<Plan>> = ApiResult.Success(emptyList())
        override suspend fun assertUsage(feature: String, currentCount: Int, amount: Int): AssertResult =
            AssertResult.Failed(message = "n/a")
    }

    /** Loja que já nasce lida (como os dublês). */
    private class LojaLida(ativa: Boolean) : PurchaseRepository {
        private val estado = MutableStateFlow(SubscriptionInfo(isActive = ativa))
        override val subscriptionState: Flow<SubscriptionInfo> = estado.asStateFlow()
        override val subscriptionReadState: Flow<SubscriptionReadState> =
            MutableStateFlow(SubscriptionReadState.READ).asStateFlow()

        override suspend fun syncSubscriptionState() = Unit
        override suspend fun isPremium(): Boolean = estado.value.isActive
        override suspend fun getOfferings(): Result<List<PurchasePackage>> = Result.success(emptyList())
        override suspend fun purchasePackage(packageId: String): PurchaseResult = PurchaseResult.Cancelled

        @Deprecated("Assinaturas usam getOfferings()", ReplaceWith("getOfferings()"))
        override suspend fun getProducts(): Result<List<PurchaseProduct>> = Result.success(emptyList())

        @Deprecated("Assinaturas usam purchasePackage(packageId)")
        override suspend fun purchase(productId: String): PurchaseResult = PurchaseResult.Cancelled

        override suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult =
            ConsumablePurchaseResult.Error("n/a", PurchaseErrorCode.UNKNOWN)

        override suspend fun restorePurchases(): RestoreResult = RestoreResult.NoPurchasesToRestore
        override suspend fun getSubscriptionInfo(): SubscriptionInfo = estado.value
    }
}
