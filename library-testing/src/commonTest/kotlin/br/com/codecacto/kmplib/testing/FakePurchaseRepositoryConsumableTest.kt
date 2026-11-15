package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.ConsumableItemPurchaseResult
import br.com.codecacto.kmplib.monetization.purchase.ConsumableTransactionIdSource
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Cenários de compra consumível por id do dublê (2.365.0). */
class FakePurchaseRepositoryConsumableTest {

    @Test
    fun `por default o duble nao vende consumivel por id`() = runTest {
        val r = FakePurchaseRepository().purchaseConsumableItem("faixa_1")
        assertIs<ConsumableItemPurchaseResult.Failed>(r)
        assertEquals(PurchaseErrorCode.CONFIGURATION_ERROR, r.code)
    }

    @Test
    fun `compra que da certo devolve recibo novo a cada compra com a conta da loja`() = runTest {
        val loja = FakePurchaseRepository.compraConsumivelQueDaCerto(appUserId = "cliente-9")
        val a = loja.purchaseConsumableItem("faixa_1")
        val b = loja.purchaseConsumableItem("faixa_1")
        assertIs<ConsumableItemPurchaseResult.Success>(a)
        assertIs<ConsumableItemPurchaseResult.Success>(b)
        assertNotEquals(a.purchase.transactionId, b.purchase.transactionId)
        assertEquals(ConsumableTransactionIdSource.STORE, a.purchase.transactionIdSource)
        assertEquals("cliente-9", a.purchase.appUserId)
        assertEquals(listOf("faixa_1", "faixa_1"), loja.consumiveisComprados)
    }

    @Test
    fun `compra que termina pendente nao conta como comprada`() = runTest {
        val loja = FakePurchaseRepository.compraConsumivelQueTermina(ConsumableItemPurchaseResult.Pending("faixa_1"))
        assertEquals(ConsumableItemPurchaseResult.Pending("faixa_1"), loja.purchaseConsumableItem("faixa_1"))
        assertTrue(loja.consumiveisComprados.isEmpty())
    }
}
