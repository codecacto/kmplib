package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseException
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentityError
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentityException
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackageType
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.RestoreResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * O dublê de teste também é testado — e não é zelo excessivo.
 *
 * Um dublê que mente é pior que nenhum: ele faz a suíte de pagamento ficar **verde** sobre o trecho
 * onde mora o pior modo de falha do produto. A cópia que existia no Super 8 devolvia
 * `purchasePackage()` → `Cancelled` num arquivo cujo comentário dizia que ele substituía a
 * RevenueCat; ninguém percebeu porque nada exercitava o dublê.
 */
class FakePurchaseRepositoryTest {

    @Test
    fun `catalogo padrao tem os tres planos na ordem canonica`() = runTest {
        val planos = FakePurchaseRepository.comOfertas().getOfferings().getOrThrow()

        assertEquals(
            listOf(
                PurchasePackageType.MONTHLY,
                PurchasePackageType.SIX_MONTH,
                PurchasePackageType.ANNUAL,
            ),
            planos.map { it.packageType },
            "a ordem é Mensal → Semestral → Anual em todo lugar (regra do ecossistema)",
        )
        assertEquals(listOf(1, 6, 12), planos.map { it.durationMonths })
    }

    @Test
    fun `catalogo padrao nao oferece trimestral`() = runTest {
        val planos = FakePurchaseRepository.comOfertas().getOfferings().getOrThrow()

        // Trimestral não existe no padrão da fábrica. Um dublê que o ofereça ensina o app a tratar
        // um caso que não deveria existir — e foi exatamente um `$rc_three_month` vazio, sobrando na
        // RevenueCat do Super 8, que o preflight apontou como plano que aparece e não compra.
        assertTrue(planos.none { it.durationMonths == 3 })
    }

    @Test
    fun `comOfertas lista planos mas nao conclui compra`() = runTest {
        val loja = FakePurchaseRepository.comOfertas()

        assertEquals(PurchaseResult.Cancelled, loja.purchasePackage(FakePurchaseRepository.PACOTE_MENSAL))
        assertFalse(loja.isPremium())
        assertEquals(emptyList(), loja.pacotesComprados)
    }

    @Test
    fun `compraQueDaCerto ativa a assinatura e publica o novo estado`() = runTest {
        val loja = FakePurchaseRepository.compraQueDaCerto()

        assertFalse(loja.subscriptionState.first().isActive)

        val resultado = loja.purchasePackage(FakePurchaseRepository.PACOTE_MENSAL)

        assertIs<PurchaseResult.Success>(resultado)
        assertTrue(resultado.subscriptionInfo.isActive)
        assertTrue(loja.isPremium())
        assertTrue(loja.subscriptionState.first().isActive, "o Flow tem de refletir a compra")
    }

    @Test
    fun `registra QUAL pacote foi comprado`() = runTest {
        val loja = FakePurchaseRepository.compraQueDaCerto()

        loja.purchasePackage(FakePurchaseRepository.PACOTE_ANUAL)

        // O assert que pega o pior erro silencioso da automação de paywall: clicar no botão do card
        // errado. "Virou premium" fica verde nos dois casos; isto, não.
        assertEquals(listOf(FakePurchaseRepository.PACOTE_ANUAL), loja.pacotesComprados)
    }

    @Test
    fun `compraCancelada nao libera nada`() = runTest {
        val loja = FakePurchaseRepository.compraCancelada()

        assertEquals(PurchaseResult.Cancelled, loja.purchasePackage(FakePurchaseRepository.PACOTE_MENSAL))
        assertFalse(loja.isPremium())
        assertFalse(loja.subscriptionState.first().isActive)
    }

    @Test
    fun `compraQueFalha devolve o codigo pedido e nao ativa nada`() = runTest {
        val loja = FakePurchaseRepository.compraQueFalha(PurchaseErrorCode.CONFIGURATION_ERROR)

        val resultado = loja.purchasePackage(FakePurchaseRepository.PACOTE_MENSAL)

        assertIs<PurchaseResult.Error>(resultado)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, resultado.code)
        assertFalse(loja.isPremium())
    }

    @Test
    fun `jaAssinante abre com assinatura ativa e restaura`() = runTest {
        val loja = FakePurchaseRepository.jaAssinante()

        assertTrue(loja.isPremium())
        assertTrue(loja.subscriptionState.first().isActive)
        assertIs<RestoreResult.Success>(loja.restorePurchases())
    }

    @Test
    fun `semOfertas devolve catalogo vazio sem falhar`() = runTest {
        val loja = FakePurchaseRepository.semOfertas()

        // Paywall vazio é o caso perverso: a chamada tem SUCESSO e a tela fica sem nada para
        // comprar. Se o dublê falhasse aqui, o app seria testado no caminho de erro, que é o
        // caminho que ele provavelmente trata — e não neste, que é o que ninguém trata.
        assertEquals(emptyList(), loja.getOfferings().getOrThrow())
    }

    @Test
    fun `ofertasQueFalham devolve PurchaseException tipada`() = runTest {
        val loja = FakePurchaseRepository.ofertasQueFalham(PurchaseErrorCode.STORE_ERROR)

        val erro = loja.getOfferings().exceptionOrNull()

        assertIs<PurchaseException>(erro)
        assertEquals(PurchaseErrorCode.STORE_ERROR, erro.code)
    }

    @Test
    fun `comprar pacote fora do catalogo nao vira sucesso`() = runTest {
        val loja = FakePurchaseRepository.compraQueDaCerto(FakePurchaseRepository.SO_MENSAL)

        val resultado = loja.purchasePackage(FakePurchaseRepository.PACOTE_ANUAL)

        assertIs<PurchaseResult.Error>(resultado)
        assertEquals(PurchaseErrorCode.PRODUCT_NOT_FOUND, resultado.code)
        assertFalse(loja.isPremium())
    }

    @Test
    fun `conta as leituras do catalogo`() = runTest {
        val loja = FakePurchaseRepository.comOfertas()

        loja.getOfferings()
        loja.getOfferings()

        assertEquals(2, loja.leiturasDoCatalogo)
    }

    @Test
    fun `identify recusa id invalido antes de qualquer efeito`() = runTest {
        val loja = FakePurchaseRepository()

        val falha = loja.identify("  ").exceptionOrNull()

        assertIs<PurchaseIdentityException>(falha)
        assertEquals(PurchaseIdentityError.INVALID_APP_USER_ID, falha.reason)
    }

    @Test
    fun `identify traz o premium do sujeito identificado`() = runTest {
        val loja = FakePurchaseRepository(premiumFor = setOf("org-42"))

        assertFalse(loja.isPremium())

        assertTrue(loja.identify("org-42").isSuccess)

        // É o caso do produto multi-tenant em que quem assina é a ORGANIZAÇÃO: identificar a org
        // certa é o que faz o entitlement aparecer. Errar o sujeito é o modo de falha em que o
        // cliente paga e continua bloqueado.
        assertTrue(loja.isPremium())
        assertEquals("org-42", loja.currentAppUserId())
    }

    @Test
    fun `resetIdentity devolve o app a nao-assinante`() = runTest {
        val loja = FakePurchaseRepository(premiumFor = setOf("org-42"))
        loja.identify("org-42")

        assertTrue(loja.resetIdentity().isSuccess)

        assertFalse(loja.isPremium(), "o próximo usuário do aparelho não herda o entitlement")
    }
}
