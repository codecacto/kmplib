package br.com.codecacto.kmplib.monetization.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A alegação que o app manda ao servidor — e o que ela **deliberadamente não tem**.
 *
 * Não existe `temAcesso(item)` aqui, e é de propósito: a pergunta "pode abrir?" é do servidor
 * (`backlib-entitlement.hasAccess`). Um booleano neste objeto seria a porta que todo app acabaria
 * usando, e ela abre com um APK modificado.
 */
class StorePurchaseClaimTest {

    @Test
    fun `app user anonimo e sinalizado - compra que nao volta em celular novo`() {
        val anonimo = StorePurchaseClaim(
            appUserId = PurchaseIdentity.ANONYMOUS_ID_PREFIX + "abc",
            store = PurchaseStore.PLAY_STORE,
        )
        val identificado = StorePurchaseClaim("aluno-1", PurchaseStore.PLAY_STORE)

        assertTrue(anonimo.isAnonymousAppUser)
        assertFalse(identificado.isAnonymousAppUser)
    }

    @Test
    fun `id em branco conta como anonimo - o claim nao seria conciliavel de qualquer forma`() {
        assertTrue(StorePurchaseClaim("", PurchaseStore.PLAY_STORE).isAnonymousAppUser)
        assertTrue(StorePurchaseClaim("   ", PurchaseStore.PLAY_STORE).isAnonymousAppUser)
    }

    @Test
    fun `productIds nao repete - a loja pode listar mais de uma transacao do mesmo produto`() {
        val claim = StorePurchaseClaim(
            appUserId = "aluno-1",
            store = PurchaseStore.APP_STORE,
            items = listOf(
                OwnedStoreItem("curso_a", "txn_1"),
                OwnedStoreItem("curso_a", "txn_2"),
                OwnedStoreItem("curso_b", "txn_3"),
            ),
        )

        assertEquals(listOf("curso_a", "curso_b"), claim.productIds)
        // Os recibos NÃO são deduplicados: cada um é chave de idempotência de uma concessão.
        assertEquals(3, claim.items.size)
    }

    @Test
    fun `claim sem item e vazio e nao tem nada a conciliar`() {
        assertTrue(StorePurchaseClaim("aluno-1", PurchaseStore.PLAY_STORE).isEmpty)
        assertFalse(
            StorePurchaseClaim("aluno-1", PurchaseStore.PLAY_STORE, listOf(OwnedStoreItem("c"))).isEmpty
        )
    }

    @Test
    fun `a loja vai e volta pelo valor de fio que o backend ja conhece`() {
        assertEquals("play_store", PurchaseStore.PLAY_STORE.wireValue)
        assertEquals("app_store", PurchaseStore.APP_STORE.wireValue)
        assertEquals(PurchaseStore.APP_STORE, PurchaseStore.fromWire("app_store"))
        assertEquals(PurchaseStore.UNKNOWN, PurchaseStore.fromWire("amazon"))
        assertEquals(PurchaseStore.UNKNOWN, PurchaseStore.fromWire(null))
    }

    @Test
    fun `recibo ausente e representavel - transacao restaurada nem sempre traz id`() {
        // Nesse caso o servidor concilia por appUserId + productId; fingir um id aqui produziria
        // uma chave de idempotência falsa e o direito seria concedido duas vezes.
        val item = OwnedStoreItem("curso_a")

        assertEquals(null, item.transactionId)
        assertEquals(null, item.purchasedAtMillis)
    }
}
