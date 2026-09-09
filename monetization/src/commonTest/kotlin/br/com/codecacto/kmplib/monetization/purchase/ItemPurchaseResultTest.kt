package br.com.codecacto.kmplib.monetization.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A régua que separa **"não é erro"** de **"é erro"** na compra de item avulso.
 *
 * Dois códigos chegam do SDK como *erro* e não são: `PAYMENT_PENDING` (a cobrança está em
 * andamento) e `ALREADY_OWNED` (o direito já existe). Tratá-los como falha faz o app dizer "não foi
 * possível concluir a compra" a quem acabou de pagar — e, no segundo caso, a quem **já pagou antes**
 * e continua sem acesso porque o webhook se perdeu.
 */
class ItemPurchaseResultTest {

    @Test
    fun `pagamento em analise vira Pending e nao falha`() {
        val r = ItemPurchaseResult.fromFailure(
            PurchaseErrorCode.PAYMENT_PENDING,
            "pending",
            "curso_a",
        )

        assertIs<ItemPurchaseResult.Pending>(r)
        assertEquals("curso_a", r.productId)
    }

    @Test
    fun `ja possuido vira AlreadyOwned - o caminho e conciliar, nao cobrar de novo`() {
        val r = ItemPurchaseResult.fromFailure(PurchaseErrorCode.ALREADY_OWNED, "owned", "curso_a")

        assertIs<ItemPurchaseResult.AlreadyOwned>(r)
        assertEquals("curso_a", r.productId)
    }

    @Test
    fun `compra de OUTRA conta de loja continua sendo falha`() {
        // Restaurar não resolve: a compra é de outra conta. Sugerir "restaurar compras" aqui só
        // produz uma segunda frustração.
        val r = ItemPurchaseResult.fromFailure(
            PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER,
            "other user",
            "curso_a",
        )

        assertIs<ItemPurchaseResult.Failed>(r)
        assertEquals(PurchaseErrorCode.ALREADY_OWNED_BY_OTHER_USER, r.code)
    }

    @Test
    fun `desistencia vira Cancelled e nunca Failed`() {
        val r = ItemPurchaseResult.fromFailure(PurchaseErrorCode.USER_CANCELLED, "cancel", "curso_a")

        assertIs<ItemPurchaseResult.Cancelled>(r)
    }

    @Test
    fun `todo codigo restante vira Failed preservando codigo e mensagem tecnica`() {
        val naoSaoFalha = setOf(
            PurchaseErrorCode.PAYMENT_PENDING,
            PurchaseErrorCode.ALREADY_OWNED,
            PurchaseErrorCode.USER_CANCELLED,
        )

        // Guarda de exaustividade: código novo no enum passa por aqui e precisa de decisão
        // consciente — o default silencioso é o que faz um caso de dinheiro cair em UNKNOWN.
        PurchaseErrorCode.entries.filterNot { it in naoSaoFalha }.forEach { code ->
            val r = ItemPurchaseResult.fromFailure(code, "tecnico:${code.name}", "curso_a")
            val falha = assertIs<ItemPurchaseResult.Failed>(r, "faltou classificar $code")
            assertEquals(code, falha.code)
            assertEquals("tecnico:${code.name}", falha.message)
        }
    }

    @Test
    fun `os codigos que nao sao falha tambem nao sao incidente de pagamento`() {
        assertFalse(PurchaseErrorCode.PAYMENT_PENDING.isPaymentIncident)
        assertFalse(PurchaseErrorCode.ALREADY_OWNED.isPaymentIncident)
        assertFalse(PurchaseErrorCode.USER_CANCELLED.isPaymentIncident)
        assertTrue(PurchaseErrorCode.CONFIGURATION_ERROR.isPaymentIncident)
    }
}
