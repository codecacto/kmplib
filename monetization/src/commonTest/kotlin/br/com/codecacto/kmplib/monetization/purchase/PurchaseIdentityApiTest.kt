package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contrato observável de identidade de cobrança: [PurchaseRepository.identify]/[resetIdentity]/
 * [currentAppUserId] e a sua propagação por [PurchaseManager] → [MonetizationManager] → entitlement.
 *
 * O adapter real ([RevenueCatPurchaseRepository]) fala com o SDK nativo e não roda em unit test; o
 * [FakePurchaseRepository] reproduz o contrato prometido pela lib.
 */
class PurchaseIdentityApiTest {

    private val purchaseConfig = PurchaseConfig(androidApiKey = "goog_x", iosApiKey = "appl_x")

    @BeforeTest
    fun limpar() {
        MonetizationManager.reset()
    }

    @AfterTest
    fun limparDepois() {
        MonetizationManager.reset()
    }

    // ---------------------------------------------------------------- defaults da interface

    @Test
    fun `repositorio sem identidade falha explicito e nao em silencio`() = runTest {
        val repo = IdentityUnawarePurchaseRepository()

        val identify = repo.identify("org-123").exceptionOrNull()
        assertIs<PurchaseIdentityException>(identify)
        assertEquals(PurchaseIdentityError.UNSUPPORTED, identify.reason)

        val reset = repo.resetIdentity().exceptionOrNull()
        assertIs<PurchaseIdentityException>(reset)
        assertEquals(PurchaseIdentityError.UNSUPPORTED, reset.reason)

        assertNull(repo.currentAppUserId())
    }

    // ---------------------------------------------------------------- PurchaseManager sem loja

    @Test
    fun `sem purchase configurado identify falha como NOT_CONFIGURED e logout e sucesso`() = runTest {
        PurchaseManager.reset()

        val erro = PurchaseManager.identify("org-123").exceptionOrNull()
        assertIs<PurchaseIdentityException>(erro)
        assertEquals(PurchaseIdentityError.NOT_CONFIGURED, erro.reason)

        // Logout do app não pode falhar por causa de uma loja que nem existe neste build.
        assertTrue(PurchaseManager.resetIdentity().isSuccess)
        assertNull(PurchaseManager.currentAppUserId())
    }

    // ---------------------------------------------------------------- efeito no entitlement

    @Test
    fun `identificar a organizacao publica o entitlement dela e derruba o catalogo em cache`() = runTest {
        val repo = FakePurchaseRepository(premiumFor = setOf("org-premium"))
        repo.cachedOfferings = listOf(pacoteMensal())

        assertFalse(repo.subscriptionState.first().isActive)
        assertTrue(repo.identify("org-premium").isSuccess)

        assertTrue(repo.subscriptionState.first().isActive, "premium do novo sujeito deve valer")
        assertEquals("org-premium", repo.currentAppUserId())
        assertTrue(
            repo.cachedOfferings.isEmpty(),
            "oferta pode ser personalizada por app user — cache do sujeito anterior não vale"
        )
    }

    @Test
    fun `trocar para sujeito sem assinatura nao herda o premium do anterior`() = runTest {
        val repo = FakePurchaseRepository(premiumFor = setOf("org-premium"))

        repo.identify("org-premium")
        assertTrue(repo.subscriptionState.first().isActive)

        repo.identify("org-free")
        assertFalse(repo.subscriptionState.first().isActive, "entitlement não pode vazar entre tenants")
    }

    @Test
    fun `logout anonimiza devolve a Free e repetir e no-op de sucesso`() = runTest {
        val repo = FakePurchaseRepository(premiumFor = setOf("org-premium"))
        repo.identify("org-premium")
        assertTrue(repo.subscriptionState.first().isActive)

        assertTrue(repo.resetIdentity().isSuccess)
        assertFalse(repo.subscriptionState.first().isActive)
        assertTrue(PurchaseIdentity.isAnonymous(repo.currentAppUserId()))

        // Já anônimo: o SDK devolveria LogOutWithAnonymousUserError; a lib promete sucesso no-op,
        // para o logout de quem nunca foi identificado não virar alerta de pagamento.
        assertTrue(repo.resetIdentity().isSuccess)
        assertEquals(2, repo.resetCalls)
    }

    @Test
    fun `id invalido e recusado antes de tocar a loja`() = runTest {
        val repo = FakePurchaseRepository()

        val erro = repo.identify("  ").exceptionOrNull()
        assertIs<PurchaseIdentityException>(erro)
        assertEquals(PurchaseIdentityError.INVALID_APP_USER_ID, erro.reason)
        assertTrue(PurchaseIdentity.isAnonymous(repo.currentAppUserId()), "nada foi identificado")
    }

    @Test
    fun `falha de rede e transitoria e preserva o entitlement corrente`() = runTest {
        val repo = FakePurchaseRepository(premiumFor = setOf("org-premium"))
        repo.identify("org-premium")

        repo.nextIdentityFailure = PurchaseIdentityError.NETWORK
        val erro = repo.identify("org-outra").exceptionOrNull()
        assertIs<PurchaseIdentityException>(erro)
        assertEquals(PurchaseIdentityError.NETWORK, erro.reason)

        assertTrue(repo.subscriptionState.first().isActive, "falha não pode corromper o estado")
        assertEquals("org-premium", repo.currentAppUserId())
    }

    // ---------------------------------------------------------------- fachada pública do app

    @Test
    fun `MonetizationManager identify reflete o premium do sujeito identificado`() = runTest {
        val repo = FakePurchaseRepository(premiumFor = setOf("org-premium"))
        PurchaseManager.initializeWith(repo)
        MonetizationManager.initialize(MonetizationConfig.FreemiumQuota(purchase = purchaseConfig))

        assertFalse(MonetizationManager.isPremium.value)

        assertTrue(MonetizationManager.identify("org-premium").isSuccess)
        assertTrue(aguardarPremium(true), "isPremium deveria refletir o entitlement da organização")
        assertEquals("org-premium", MonetizationManager.currentAppUserId())

        assertTrue(MonetizationManager.resetIdentity().isSuccess)
        assertTrue(aguardarPremium(false), "logout deve devolver o app a Free")
    }

    private suspend fun aguardarPremium(esperado: Boolean): Boolean =
        // O MonetizationManager coleta o estado da assinatura num escopo próprio (Dispatchers.Default):
        // esperar em tempo real, não no relógio virtual do runTest.
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(3_000) { MonetizationManager.isPremium.first { it == esperado } } != null
        }

    private fun pacoteMensal() = PurchasePackage(
        packageId = "\$rc_monthly",
        packageType = PurchasePackageType.MONTHLY,
        storeProductId = "premium_mensal_tattoostudio",
        priceLabel = "R$ 49,90",
        priceAmountMicros = 49_900_000,
        currencyCode = "BRL",
        durationMonths = 1,
    )
}
