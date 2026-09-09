package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.monetization.MonetizationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **Coexistência**: a venda avulsa entrou aditiva, e quem vende assinatura não pode ter sentido
 * nada.
 *
 * Os quatro métodos novos têm implementação default na interface justamente por isto — há dublê de
 * loja dentro dos apps (`LojaDeDemonstracao` das cascas de QA) implementando
 * [PurchaseRepository], e um método abstrato novo quebraria o build de todos eles. O default
 * precisa ser **honesto**: "esta loja não vende avulso", nunca um sucesso vazio.
 */
class ItemSaleApiTest {

    @BeforeTest
    fun limpar() = MonetizationManager.reset()

    @AfterTest
    fun limparDepois() = MonetizationManager.reset()

    // ---------------------------------------------------------------- defaults da interface

    @Test
    fun `repositorio so de assinatura responde que nao vende avulso, sem fingir sucesso`() = runTest {
        val repo = IdentityUnawarePurchaseRepository()

        assertIs<StoreItemsOutcome.Unavailable>(repo.getStoreItems(listOf("curso_a")))

        val compra = repo.purchaseItem("curso_a")
        assertIs<ItemPurchaseResult.Failed>(compra)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, compra.code)

        val restore = repo.restoreItems()
        assertIs<ItemRestoreResult.Failed>(restore)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, restore.code)

        val possuidos = repo.ownedItems().exceptionOrNull()
        assertIs<PurchaseException>(possuidos)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, possuidos.code)
    }

    @Test
    fun `o caminho de ASSINATURA continua identico depois da venda avulsa entrar`() = runTest {
        val repo = FakePurchaseRepository()
        repo.cachedOfferings = listOf(pacoteMensal())

        assertEquals(1, repo.getOfferings().getOrThrow().size)
        assertIs<RestoreResult.NoPurchasesToRestore>(repo.restorePurchases())
        assertFalse(repo.subscriptionState.first().isActive)
        assertFalse(repo.isPremium())
    }

    // ---------------------------------------------------------------- sem loja configurada

    @Test
    fun `sem purchase configurado o catalogo de itens e Unavailable e nao Empty`() = runTest {
        PurchaseManager.reset()

        // Empty diria "a loja respondeu e não tem nada", que dispararia alerta de incidente. Aqui a
        // loja nem foi consultada: o defeito, se houver, é de build (chave ausente).
        val r = MonetizationManager.getStoreItems(listOf("curso_a"))
        assertIs<StoreItemsOutcome.Unavailable>(r)
        assertFalse(r.incident)
    }

    @Test
    fun `sem purchase configurado comprar item falha como CONFIGURATION_ERROR`() = runTest {
        PurchaseManager.reset()

        val compra = MonetizationManager.purchaseItem("curso_a")
        assertIs<ItemPurchaseResult.Failed>(compra)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, compra.code)
        assertTrue(compra.code.isPaymentIncident, "ninguém consegue comprar — é incidente da fábrica")

        val restore = MonetizationManager.restoreItems()
        assertIs<ItemRestoreResult.Failed>(restore)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, restore.code)

        assertTrue(MonetizationManager.ownedItems().isFailure)
    }

    // ---------------------------------------------------------------- fachada → repositório

    @Test
    fun `MonetizationManager delega ao repositorio instalado`() = runTest {
        PurchaseManager.initializeWith(LojaDeItensDeTeste())

        val catalogo = MonetizationManager.getStoreItems(listOf("curso_a"))
        assertIs<StoreItemsOutcome.Available>(catalogo)
        assertEquals(listOf("curso_a"), catalogo.items.map { it.productId })

        val compra = MonetizationManager.purchaseItem("curso_a")
        assertIs<ItemPurchaseResult.Success>(compra)
        assertEquals("txn_curso_a", compra.item.transactionId)
        assertTrue(
            compra.claim.items.any { it.productId == "curso_a" },
            "o claim precisa conter o item recém-comprado — é ele que o servidor concede",
        )
    }

    private fun pacoteMensal() = PurchasePackage(
        packageId = "\$rc_monthly",
        packageType = PurchasePackageType.MONTHLY,
        storeProductId = "premium_mensal",
        priceLabel = "R$ 19,90",
        priceAmountMicros = 19_900_000,
        currencyCode = "BRL",
        durationMonths = 1,
    )

    /** Loja mínima que vende um item — só o suficiente para provar a delegação da fachada. */
    private class LojaDeItensDeTeste : PurchaseRepository by IdentityUnawarePurchaseRepository() {
        override suspend fun getStoreItems(productIds: List<String>): StoreItemsOutcome =
            StoreItemsOutcome.from(
                productIds,
                listOf(
                    StoreItem(
                        productId = "curso_a",
                        title = "Curso A",
                        description = "…",
                        priceLabel = "R$ 149,90",
                        priceAmountMicros = 149_900_000,
                        currencyCode = "BRL",
                    )
                ),
            )

        override suspend fun purchaseItem(productId: String): ItemPurchaseResult {
            val item = OwnedStoreItem(productId, "txn_$productId", 1_700_000_000_000)
            return ItemPurchaseResult.Success(
                item = item,
                claim = StorePurchaseClaim("aluno-1", PurchaseStore.PLAY_STORE, listOf(item)),
            )
        }
    }
}
