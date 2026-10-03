package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.monetization.PremiumStatus.FreeReason
import br.com.codecacto.kmplib.monetization.purchase.ConsumablePurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.FakePurchaseRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [PremiumStatus] — o premium que sabe dizer "ainda não sei" (2.249.0).
 *
 * O [MonetizationManager] coleta num escopo próprio (`Dispatchers.Default`) e o teto é um `delay`
 * real: estes testes esperam em tempo real (`runBlocking` + [aguardar]), não no relógio virtual.
 */
class PremiumStatusTest {

    private val purchaseConfig = PurchaseConfig(androidApiKey = "goog_x", iosApiKey = "appl_x")

    @BeforeTest
    fun antes() = MonetizationManager.reset()

    @AfterTest
    fun depois() = MonetizationManager.reset()

    // ------------------------------------------------------------------ regra pura

    @Test
    fun `marcador de partida nao resolve`() {
        assertEquals(
            PremiumStatus.Unknown,
            resolvePremiumStatus(PremiumStatus.Unknown, inativa(), SubscriptionReadState.PENDING),
        )
    }

    @Test
    fun `leitura resolve premium ou free confirmado`() {
        assertEquals(
            PremiumStatus.Premium,
            resolvePremiumStatus(PremiumStatus.Unknown, ativa(), SubscriptionReadState.READ),
        )
        assertEquals(
            PremiumStatus.Free(FreeReason.STORE),
            resolvePremiumStatus(PremiumStatus.Unknown, inativa(), SubscriptionReadState.READ),
        )
    }

    @Test
    fun `falha antes de qualquer leitura resolve como free presumido`() {
        val status = resolvePremiumStatus(PremiumStatus.Unknown, inativa(), SubscriptionReadState.FAILED)
        assertEquals(PremiumStatus.Free(FreeReason.STORE_FAILURE), status)
        assertTrue((status as PremiumStatus.Free).isAssumed)
    }

    @Test
    fun `falha depois de resolvido nao rebaixa`() {
        assertEquals(
            PremiumStatus.Premium,
            resolvePremiumStatus(PremiumStatus.Premium, inativa(), SubscriptionReadState.FAILED),
        )
    }

    @Test
    fun `assinatura que expira com o app aberto vira free`() {
        assertEquals(
            PremiumStatus.Free(FreeReason.STORE),
            resolvePremiumStatus(PremiumStatus.Premium, inativa(), SubscriptionReadState.READ),
        )
    }

    @Test
    fun `free presumido e corrigido pela leitura que chega depois`() {
        assertEquals(
            PremiumStatus.Premium,
            resolvePremiumStatus(PremiumStatus.Free(FreeReason.TIMEOUT), ativa(), SubscriptionReadState.READ),
        )
    }

    // ------------------------------------------------------------------ manager com loja real simulada

    @Test
    fun `nasce Unknown e resolve Premium no primeiro CustomerInfo`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)

        delay(100)
        assertEquals(PremiumStatus.Unknown, MonetizationManager.premiumStatus.value)
        assertFalse(MonetizationManager.isPremiumResolved.value)
        assertEquals(1, loja.syncCalls, "o initialize pede a primeira leitura sozinho")

        loja.responder(ativa())

        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Premium })
        assertTrue(MonetizationManager.isPremiumResolved.value)
        assertTrue(MonetizationManager.isPremium.value, "isPremium continua valendo como antes")
    }

    @Test
    fun `nasce Unknown e resolve Free quando a loja diz que nao assina`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)

        loja.responder(inativa())

        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Free(FreeReason.STORE) })
    }

    @Test
    fun `leitura que falha resolve Free presumido sem esperar o teto`() = runBlocking<Unit> {
        val loja = LojaControlada(primeiraLeituraFalha = true)
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)

        assertTrue(aguardar {
            MonetizationManager.premiumStatus.value == PremiumStatus.Free(FreeReason.STORE_FAILURE)
        })
    }

    @Test
    fun `sem resposta no teto resolve Free por timeout e a leitura tardia corrige`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(
            MonetizationConfig.Freemium(purchaseConfig),
            premiumResolutionTimeout = 150.milliseconds,
        )

        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Free(FreeReason.TIMEOUT) })
        assertTrue(MonetizationManager.isPremiumResolved.value)

        loja.responder(ativa())
        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Premium })
    }

    @Test
    fun `resolvido nunca volta a Unknown`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)
        loja.responder(ativa())
        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Premium })

        // Uma implementação que voltasse o sinal a PENDING (nenhuma deveria) não desfaz a resposta.
        loja.leitura.value = SubscriptionReadState.PENDING
        loja.estado.value = inativa()
        delay(200)

        assertTrue(MonetizationManager.premiumStatus.value.isResolved)
        assertTrue(MonetizationManager.isPremiumResolved.value)
    }

    @Test
    fun `a guarda nunca deixa voltar a Unknown`() {
        MonetizationManager.initialize(MonetizationConfig.AdsOnly)
        assertTrue(MonetizationManager.premiumStatus.value.isResolved)

        MonetizationManager.updatePremiumStatus { PremiumStatus.Unknown }

        assertEquals(PremiumStatus.Free(FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
        assertTrue(MonetizationManager.isPremiumResolved.value)
    }

    @Test
    fun `isPremiumResolved acompanha o premiumStatus na mesma hora`() {
        assertFalse(MonetizationManager.isPremiumResolved.value)
        MonetizationManager.declareNotMonetized()
        assertTrue(MonetizationManager.isPremiumResolved.value)
        MonetizationManager.reset()
        assertFalse(MonetizationManager.isPremiumResolved.value)
    }

    @Test
    fun `refreshSubscriptionState rele pela loja e o default delega ao sync`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)
        assertTrue(aguardar { loja.syncCalls == 1 })

        MonetizationManager.refreshSubscriptionState()

        assertEquals(2, loja.syncCalls)
    }

    @Test
    fun `reset cancela a primeira leitura em voo`() = runBlocking<Unit> {
        val loja = LojaControlada(leituraLenta = true)
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)
        assertTrue(aguardar { loja.syncCalls == 1 })

        MonetizationManager.reset()
        delay(400)

        assertFalse(loja.leituraConcluida, "a leitura do processo anterior não pode concluir depois do reset")
    }

    // ------------------------------------------------------------------ modos sem loja

    @Test
    fun `AdsOnly resolve na hora como NOT_SOLD`() {
        MonetizationManager.initialize(MonetizationConfig.AdsOnly)

        assertEquals(PremiumStatus.Free(FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
        assertTrue(MonetizationManager.isPremiumResolved.value)
    }

    @Test
    fun `app sem monetizacao declara e resolve na hora`() = runBlocking<Unit> {
        MonetizationManager.declareNotMonetized()

        assertEquals(PremiumStatus.Free(FreeReason.NOT_SOLD), MonetizationManager.premiumStatus.value)
        // E quem espera não paga o teto.
        val inicio = kotlin.time.TimeSource.Monotonic.markNow()
        assertFalse(MonetizationManager.awaitPremiumResolved(timeout = 5.seconds))
        assertTrue(inicio.elapsedNow() < 1.seconds)
    }

    // ------------------------------------------------------------------ dublês

    @Test
    fun `dubles sem o sinal novo resolvem na primeira emissao`() = runBlocking<Unit> {
        // `FakePurchaseRepository` não sobrescreve `subscriptionReadState`: o default (READ) faz o
        // estado dele valer como resposta — é o caso dos dublês dos apps e do `kmplib-testing`.
        val duble = FakePurchaseRepository(premiumFor = setOf("org-premium"))
        PurchaseManager.initializeWith(duble)
        MonetizationManager.initialize(MonetizationConfig.FreemiumQuota(purchaseConfig), premiumResolutionTimeout = 30.seconds)

        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Free(FreeReason.STORE) })

        duble.identify("org-premium")
        assertTrue(aguardar { MonetizationManager.premiumStatus.value == PremiumStatus.Premium })
    }

    // ------------------------------------------------------------------ espera suspensa

    @Test
    fun `awaitPremiumResolved devolve true quando a loja confirma`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)

        val espera = async(Dispatchers.Default) { MonetizationManager.awaitPremiumResolved(timeout = 5.seconds) }
        delay(100)
        loja.responder(ativa())

        assertTrue(espera.await())
    }

    @Test
    fun `awaitPremiumStatus estoura o teto como Free TIMEOUT sem mexer no estado global`() = runBlocking<Unit> {
        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)

        val status = withContext(Dispatchers.Default) { MonetizationManager.awaitPremiumStatus(150.milliseconds) }

        assertEquals(PremiumStatus.Free(FreeReason.TIMEOUT), status)
        assertEquals(PremiumStatus.Unknown, MonetizationManager.premiumStatus.value)
    }

    @Test
    fun `espera iniciada antes do initialize acompanha a inicializacao tardia`() = runBlocking<Unit> {
        val espera = async(Dispatchers.Default) { MonetizationManager.awaitPremiumStatus(5.seconds) }
        delay(100)

        val loja = LojaControlada()
        PurchaseManager.initializeWith(loja)
        MonetizationManager.initialize(MonetizationConfig.Freemium(purchaseConfig), premiumResolutionTimeout = 30.seconds)
        loja.responder(ativa())

        assertIs<PremiumStatus.Premium>(espera.await())
    }

    @Test
    fun `reset volta a Unknown e cancela o teto pendente`() = runBlocking<Unit> {
        PurchaseManager.initializeWith(LojaControlada())
        MonetizationManager.initialize(
            MonetizationConfig.Freemium(purchaseConfig),
            premiumResolutionTimeout = 150.milliseconds,
        )
        MonetizationManager.reset()

        delay(300)
        assertEquals(PremiumStatus.Unknown, MonetizationManager.premiumStatus.value)
        assertFalse(MonetizationManager.isPremiumResolved.value)
    }

    // ------------------------------------------------------------------ apoio

    private fun ativa() = SubscriptionInfo(isActive = true, productId = "premium_mensal")
    private fun inativa() = SubscriptionInfo(isActive = false)

    private suspend fun aguardar(condicao: () -> Boolean): Boolean = withContext(Dispatchers.Default) {
        withTimeoutOrNull(3_000) {
            while (!condicao()) delay(10)
            true
        } ?: false
    }

    /**
     * Loja que se comporta como o adaptador da RevenueCat: nasce com o marcador (`PENDING`) e só
     * responde quando o teste manda.
     */
    private class LojaControlada(
        private val primeiraLeituraFalha: Boolean = false,
        private val leituraLenta: Boolean = false,
    ) : PurchaseRepository {
        val estado = MutableStateFlow(SubscriptionInfo(isActive = false))
        val leitura = MutableStateFlow(SubscriptionReadState.PENDING)
        var syncCalls = 0
            private set
        var leituraConcluida = false
            private set

        override val subscriptionState: Flow<SubscriptionInfo> = estado.asStateFlow()
        override val subscriptionReadState: Flow<SubscriptionReadState> = leitura.asStateFlow()

        fun responder(info: SubscriptionInfo) {
            estado.value = info
            leitura.value = SubscriptionReadState.READ
        }

        override suspend fun syncSubscriptionState() {
            syncCalls++
            if (leituraLenta) delay(200)
            leituraConcluida = true
            if (primeiraLeituraFalha) leitura.compareAndSet(SubscriptionReadState.PENDING, SubscriptionReadState.FAILED)
        }

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
