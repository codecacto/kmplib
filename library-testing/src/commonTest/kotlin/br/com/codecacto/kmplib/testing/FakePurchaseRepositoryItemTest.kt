package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.ItemPurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.ItemRestoreResult
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseException
import br.com.codecacto.kmplib.monetization.purchase.PurchaseIdentity
import br.com.codecacto.kmplib.monetization.purchase.PurchaseStore
import br.com.codecacto.kmplib.monetization.purchase.StoreItemsOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * O dublê da loja no modo **venda avulsa** (item não-consumível).
 *
 * O cenário que este arquivo existe para travar é o do **celular novo**: o aluno comprou seis
 * cursos e trocou de aparelho. Uma restauração que responda "tem assinatura: sim/não" não devolve
 * curso nenhum — a resposta certa é a lista, com o recibo de cada item, que é o que o servidor
 * precisa para reconstruir os seis direitos.
 */
class FakePurchaseRepositoryItemTest {

    private val catalogo = listOf(
        FakePurchaseRepository.itemDaLoja("curso_saque"),
        FakePurchaseRepository.itemDaLoja("curso_voleio"),
    )

    @Test
    fun `compra de item devolve recibo e passa a constar como possuido`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueDaCerto(catalogo, appUserId = "aluno-1")

        val r = loja.purchaseItem("curso_saque")

        val ok = assertIs<ItemPurchaseResult.Success>(r)
        assertEquals("curso_saque", ok.item.productId)
        assertEquals("txn_curso_saque", ok.item.transactionId)
        assertEquals(listOf("curso_saque"), loja.itensComprados)

        // O claim é o que vai para o servidor conciliar — e sai identificado, não anônimo.
        assertEquals("aluno-1", ok.claim.appUserId)
        assertFalse(ok.claim.isAnonymousAppUser)
        assertEquals(listOf("curso_saque"), ok.claim.productIds)
    }

    @Test
    fun `item nao-consumivel nao se compra duas vezes - a segunda e AlreadyOwned`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueDaCerto(catalogo)

        assertIs<ItemPurchaseResult.Success>(loja.purchaseItem("curso_saque"))
        val segunda = loja.purchaseItem("curso_saque")

        val jaTem = assertIs<ItemPurchaseResult.AlreadyOwned>(segunda)
        assertEquals("curso_saque", jaTem.productId)
        assertEquals(1, loja.itensComprados.size, "não pode contar como uma segunda venda")
    }

    @Test
    fun `comprar item fora do catalogo nao passa`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueDaCerto(catalogo)

        val r = loja.purchaseItem("curso_que_nao_existe")

        val falha = assertIs<ItemPurchaseResult.Failed>(r)
        assertEquals(PurchaseErrorCode.PRODUCT_NOT_FOUND, falha.code)
    }

    @Test
    fun `restaurar em celular novo devolve os SEIS itens, nao um booleano`() = runTest {
        val comprados = (1..6).map { FakePurchaseRepository.itemComprado("curso_$it") }
        val loja = FakePurchaseRepository.itensJaComprados(comprados, appUserId = "aluno-1")

        val r = loja.restoreItems()

        val restaurado = assertIs<ItemRestoreResult.Restored>(r)
        assertEquals(6, restaurado.claim.items.size)
        assertEquals(
            (1..6).map { "txn_curso_$it" },
            restaurado.claim.items.map { it.transactionId },
            "cada recibo é a chave de idempotência de uma concessão no servidor",
        )
        assertFalse(
            restaurado.subscription.isActive,
            "produto que só vende avulso não tem assinatura ativa — e isso não é erro",
        )
    }

    @Test
    fun `conta de loja sem nada devolve NothingToRestore`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueDaCerto(catalogo)

        assertIs<ItemRestoreResult.NothingToRestore>(loja.restoreItems())
    }

    @Test
    fun `ownedItems le sem prompt e reflete o que foi comprado`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueDaCerto(catalogo, loja = PurchaseStore.APP_STORE)

        assertTrue(loja.ownedItems().getOrThrow().isEmpty)
        loja.purchaseItem("curso_voleio")

        val claim = loja.ownedItems().getOrThrow()
        assertEquals(listOf("curso_voleio"), claim.productIds)
        assertEquals(PurchaseStore.APP_STORE, claim.store)
    }

    @Test
    fun `catalogo de itens marca o id que a loja nao tem`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueDaCerto(catalogo)

        val r = loja.getStoreItems(listOf("curso_saque", "curso_fantasma"))

        val disponivel = assertIs<StoreItemsOutcome.Available>(r)
        assertEquals(listOf("curso_fantasma"), disponivel.missingProductIds)
        assertTrue(disponivel.incident)
        assertEquals(1, loja.leiturasDoCatalogoDeItens)
    }

    @Test
    fun `pagamento em analise nao libera nada e nao conta como compra`() = runTest {
        val loja = FakePurchaseRepository.compraDeItemQueTermina(
            desfecho = ItemPurchaseResult.Pending("curso_saque"),
            itens = catalogo,
        )

        assertIs<ItemPurchaseResult.Pending>(loja.purchaseItem("curso_saque"))
        assertTrue(loja.itensComprados.isEmpty())
        assertTrue(loja.ownedItems().getOrThrow().isEmpty, "nada possuído até a loja confirmar")
    }

    @Test
    fun `falha na leitura do catalogo de itens vem tipada`() = runTest {
        val loja = FakePurchaseRepository.catalogoDeItensQueFalha(PurchaseErrorCode.NETWORK_ERROR)

        val r = loja.getStoreItems(listOf("curso_1"))

        val falha = assertIs<StoreItemsOutcome.Failed>(r)
        assertEquals(PurchaseErrorCode.NETWORK_ERROR, falha.code)
        assertFalse(falha.incident, "usuário sem rede não é incidente da fábrica")
    }

    @Test
    fun `dublê de ASSINATURA nao passa a vender avulso sem ninguem pedir`() = runTest {
        val soAssinatura = FakePurchaseRepository.compraQueDaCerto()

        assertIs<StoreItemsOutcome.Unavailable>(soAssinatura.getStoreItems(listOf("curso_saque")))
        val erro = soAssinatura.ownedItems().exceptionOrNull()
        assertIs<PurchaseException>(erro)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, erro.code)
    }

    @Test
    fun `compra com app user anonimo e sinalizada no claim`() = runTest {
        // É a compra que não volta: sem App User ID próprio, item avulso não se restaura em
        // celular novo. O dublê não recusa a venda — sinaliza, como a lib real.
        val loja = FakePurchaseRepository(
            ofertas = emptyList(),
            itensDaLoja = catalogo,
            initialAppUserId = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "fake",
        )

        val ok = assertIs<ItemPurchaseResult.Success>(loja.purchaseItem("curso_saque"))
        assertTrue(ok.claim.isAnonymousAppUser)
    }
}
