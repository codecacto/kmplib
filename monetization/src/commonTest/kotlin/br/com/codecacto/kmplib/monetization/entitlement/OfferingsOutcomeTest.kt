package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.FakePurchaseRepository
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseException
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import br.com.codecacto.kmplib.monetization.purchase.isPaymentIncident
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **"Não consegui falar com a loja" ≠ "a loja não tem plano".**
 *
 * Até a 2.140.0 os dois chegavam ao app como `emptyList()`, e o Torneio de Pênalti alertava
 * `PaywallSemPlano` (Fatal) quando o usuário abria a tela de assinatura sem rede — queimando, de
 * quebra, o único alerta daquele tipo permitido na sessão. Estes testes travam a distinção.
 */
class OfferingsOutcomeTest {

    @BeforeTest
    fun limpar() = MonetizationManager.reset()

    @AfterTest
    fun limparDepois() = MonetizationManager.reset()

    // ------------------------------------------------------------------ mapeamento do Result cru

    @Test
    fun `catalogo lido com pacotes vira Disponivel`() {
        val outcome = OfferingsOutcome.deResultado(Result.success(listOf(pacoteMensal())))

        val disponivel = assertIs<OfferingsOutcome.Disponivel>(outcome)
        assertEquals(1, disponivel.pacotes.size)
        assertFalse(outcome.catalogoVazioConfirmado, "há plano para vender")
    }

    @Test
    fun `loja que responde sem pacote vira Vazio e confirma o catalogo vazio`() {
        val outcome = OfferingsOutcome.deResultado(Result.success(emptyList()))

        assertEquals(OfferingsOutcome.Vazio, outcome)
        assertTrue(outcome.catalogoVazioConfirmado, "é o único caso que autoriza alertar")
        assertTrue(outcome.pacotes.isEmpty())
    }

    @Test
    fun `falha de rede NAO confirma catalogo vazio e nao e incidente`() {
        val outcome = OfferingsOutcome.deResultado(
            Result.failure(PurchaseException(PurchaseErrorCode.NETWORK_ERROR, "sem conexão")),
        )

        val falha = assertIs<OfferingsOutcome.Falha>(outcome)
        assertEquals(PurchaseErrorCode.NETWORK_ERROR, falha.code)
        assertEquals("sem conexão", falha.mensagem)
        assertFalse(
            outcome.catalogoVazioConfirmado,
            "não saber o que a loja tem não é saber que ela não tem nada",
        )
        assertFalse(falha.incidente, "usuário sem rede não é incidente da fábrica")
        assertTrue(falha.pacotes.isEmpty())
    }

    @Test
    fun `falha de configuracao continua sendo incidente`() {
        val outcome = OfferingsOutcome.deResultado(
            Result.failure(PurchaseException(PurchaseErrorCode.CONFIGURATION_ERROR, "chave inválida")),
        )

        val falha = assertIs<OfferingsOutcome.Falha>(outcome)
        assertTrue(falha.incidente)
        assertTrue(falha.code.isPaymentIncident)
    }

    @Test
    fun `erro sem codigo tipado vira UNKNOWN — e UNKNOWN se investiga`() {
        val outcome = OfferingsOutcome.deResultado(Result.failure(IllegalStateException("boom")))

        val falha = assertIs<OfferingsOutcome.Falha>(outcome)
        assertEquals(PurchaseErrorCode.UNKNOWN, falha.code)
        assertEquals("boom", falha.mensagem)
        assertTrue(falha.incidente)
    }

    @Test
    fun `erro sem mensagem ainda descreve a causa no detalhe tecnico`() {
        val outcome = OfferingsOutcome.deResultado(Result.failure(IllegalStateException()))

        val falha = assertIs<OfferingsOutcome.Falha>(outcome)
        assertTrue(falha.mensagem.contains("IllegalStateException"), "detalhe: ${falha.mensagem}")
    }

    // ------------------------------------------------------------------ invariantes do tipo

    @Test
    fun `dePacotes nunca constroi Disponivel vazio`() {
        assertEquals(OfferingsOutcome.Vazio, OfferingsOutcome.dePacotes(emptyList()))
        assertIs<OfferingsOutcome.Disponivel>(OfferingsOutcome.dePacotes(listOf(pacoteMensal())))
    }

    @Test
    fun `Disponivel com lista vazia e recusado na construcao`() {
        assertFailsWith<IllegalArgumentException> { OfferingsOutcome.Disponivel(emptyList()) }
    }

    @Test
    fun `so Vazio confirma catalogo vazio`() {
        val casos = listOf(
            OfferingsOutcome.Disponivel(listOf(pacoteMensal())) to false,
            OfferingsOutcome.Vazio to true,
            OfferingsOutcome.Falha("x", PurchaseErrorCode.STORE_ERROR) to false,
            OfferingsOutcome.Indisponivel to false,
        )
        casos.forEach { (outcome, esperado) ->
            assertEquals(esperado, outcome.catalogoVazioConfirmado, "caso: $outcome")
        }
    }

    // ------------------------------------------------------------------ provider real (RevenueCat)

    @Test
    fun `provider real distingue falha de leitura de catalogo vazio`() = runTest {
        val repo = FakePurchaseRepository()
        PurchaseManager.initializeWith(repo)
        val provider = RevenueCatEntitlementProvider(purchaseConfig = config)

        repo.offeringsFailure = PurchaseException(PurchaseErrorCode.NETWORK_ERROR, "offline")
        val falha = assertIs<OfferingsOutcome.Falha>(provider.loadOfferings())
        assertEquals(PurchaseErrorCode.NETWORK_ERROR, falha.code)
        // O caminho antigo continua igual ao que sempre foi: lista vazia, sem causa.
        assertTrue(provider.offerings().isEmpty())

        repo.offeringsFailure = null
        assertEquals(OfferingsOutcome.Vazio, provider.loadOfferings())

        repo.cachedOfferings = listOf(pacoteMensal())
        val disponivel = assertIs<OfferingsOutcome.Disponivel>(provider.loadOfferings())
        assertEquals(listOf(pacoteMensal()), disponivel.pacotes)
        assertEquals(listOf(pacoteMensal()), provider.offerings())
    }

    @Test
    fun `provider real sem repositorio reporta Indisponivel, nunca Vazio`() = runTest {
        PurchaseManager.initializeWith(FakePurchaseRepository())
        val provider = RevenueCatEntitlementProvider(purchaseConfig = config)

        PurchaseManager.reset()

        assertEquals(OfferingsOutcome.Indisponivel, provider.loadOfferings())
        assertTrue(provider.offerings().isEmpty(), "compatibilidade: a lista segue vazia")
    }

    // ------------------------------------------------------------------ compatibilidade

    @Test
    fun `stub sem billing reporta Indisponivel`() = runTest {
        val provider = StubEntitlementProvider()

        assertEquals(OfferingsOutcome.Indisponivel, provider.loadOfferings())
        assertFalse(
            provider.loadOfferings().catalogoVazioConfirmado,
            "build sem chave não é 'a loja está vazia'",
        )
    }

    @Test
    fun `provider proprio do app que so implementa offerings continua funcionando`() = runTest {
        assertEquals(OfferingsOutcome.Vazio, ProviderLegado(emptyList()).loadOfferings())

        val comPlano = ProviderLegado(listOf(pacoteMensal()))
        val disponivel = assertIs<OfferingsOutcome.Disponivel>(comPlano.loadOfferings())
        assertEquals(listOf(pacoteMensal()), disponivel.pacotes)
    }

    /** Provider escrito por um app antes da 2.141.0: implementa só o contrato antigo. */
    private class ProviderLegado(private val pacotes: List<PurchasePackage>) : EntitlementProvider {
        private val premium = MutableStateFlow(false)
        override val isPremium: StateFlow<Boolean> = premium.asStateFlow()
        override suspend fun refresh() = Unit
        override suspend fun offerings(): List<PurchasePackage> = pacotes
        override suspend fun subscriptionInfo(): SubscriptionInfo? = null
        override suspend fun purchasePackage(packageId: String): PurchaseOutcome =
            PurchaseOutcome.Indisponivel
        override suspend fun restore(): PurchaseOutcome = PurchaseOutcome.Indisponivel
    }

    private val config = PurchaseConfig(androidApiKey = "goog_teste", iosApiKey = "appl_teste")

    private fun pacoteMensal() = PurchasePackage(
        packageId = "\$rc_monthly",
        packageType = PurchasePackageType.MONTHLY,
        storeProductId = "premium_mensal_teste",
        priceLabel = "R$ 9,90",
        priceAmountMicros = 9_900_000L,
        currencyCode = "BRL",
        durationMonths = 1,
    )
}
