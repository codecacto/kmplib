package br.com.codecacto.kmplib.monetization.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsumablePurchaseResultTest {

    @Test
    fun `Success carrega transactionId productId e store`() {
        val r = ConsumablePurchaseResult.Success(
            transactionId = "GPA.1234-5678",
            productId = "solicitacao_avulsa",
            store = "play_store"
        )
        assertEquals("GPA.1234-5678", r.transactionId)
        assertEquals("solicitacao_avulsa", r.productId)
        assertEquals("play_store", r.store)
    }

    @Test
    fun `Error carrega mensagem e codigo`() {
        val r = ConsumablePurchaseResult.Error("transacao sem id", PurchaseErrorCode.UNKNOWN)
        assertEquals("transacao sem id", r.message)
        assertEquals(PurchaseErrorCode.UNKNOWN, r.code)
    }

    @Test
    fun `Cancelled e singleton`() {
        assertTrue(ConsumablePurchaseResult.Cancelled === ConsumablePurchaseResult.Cancelled)
    }

    @Test
    fun `is subtype de ConsumablePurchaseResult`() {
        val results: List<ConsumablePurchaseResult> = listOf(
            ConsumablePurchaseResult.Success("t", "p", "app_store"),
            ConsumablePurchaseResult.Error("x", PurchaseErrorCode.NETWORK_ERROR),
            ConsumablePurchaseResult.Cancelled
        )
        assertEquals(3, results.size)
    }
}
