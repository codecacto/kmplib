package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppReviewManagerTest {

    // ====== onCompletion: trigger N ======

    @Test
    fun `onCompletion retorna false antes de atingir trigger`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 3, store = store)

        assertFalse(manager.onCompletion())  // 1
        assertFalse(manager.onCompletion())  // 2
        assertEquals(2, store.getCompletionCount())
    }

    @Test
    fun `onCompletion retorna true exatamente no trigger`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 3, store = store)

        manager.onCompletion()  // 1
        manager.onCompletion()  // 2
        assertTrue(manager.onCompletion())  // 3 — trigger
    }

    @Test
    fun `onCompletion continua retornando true apos o trigger se nao foi marcado`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 2, store = store)

        manager.onCompletion()  // 1
        assertTrue(manager.onCompletion())  // 2
        assertTrue(manager.onCompletion())  // 3 — ainda passa
    }

    @Test
    fun `onCompletion retorna false depois de markShown`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 1, store = store)

        assertTrue(manager.onCompletion())
        manager.markShown()
        assertFalse(manager.onCompletion())
        assertFalse(manager.onCompletion())
    }

    @Test
    fun `onCompletion nao incrementa apos hasReviewed ser true`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 5, store = store)

        store.setReviewed(true)
        manager.onCompletion()
        manager.onCompletion()
        assertEquals(0, store.getCompletionCount())
    }

    // ====== shouldShow (passivo) ======

    @Test
    fun `shouldShow false quando nao atingiu trigger`() {
        val store = FakeReviewStore()
        store.setCompletionCount(2)
        val manager = AppReviewManager(triggerCount = 3, store = store)

        assertFalse(manager.shouldShow())
    }

    @Test
    fun `shouldShow true quando atingiu trigger e nao avaliou`() {
        val store = FakeReviewStore()
        store.setCompletionCount(3)
        val manager = AppReviewManager(triggerCount = 3, store = store)

        assertTrue(manager.shouldShow())
    }

    @Test
    fun `shouldShow false quando ja avaliou mesmo com count alto`() {
        val store = FakeReviewStore()
        store.setCompletionCount(10)
        store.setReviewed(true)
        val manager = AppReviewManager(triggerCount = 3, store = store)

        assertFalse(manager.shouldShow())
    }

    @Test
    fun `shouldShow nao incrementa contador`() {
        val store = FakeReviewStore()
        store.setCompletionCount(5)
        val manager = AppReviewManager(triggerCount = 3, store = store)

        manager.shouldShow()
        manager.shouldShow()
        assertEquals(5, store.getCompletionCount())
    }

    // ====== Acessores ======

    @Test
    fun `completionCount delega para o store`() {
        val store = FakeReviewStore()
        store.setCompletionCount(7)
        val manager = AppReviewManager(store = store)
        assertEquals(7, manager.completionCount())
    }

    @Test
    fun `hasReviewed delega para o store`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(store = store)
        assertFalse(manager.hasReviewed())
        store.setReviewed(true)
        assertTrue(manager.hasReviewed())
    }

    @Test
    fun `markShown propaga para o store`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(store = store)

        manager.markShown()
        assertTrue(store.hasReviewed())
    }

    // ====== Default values ======

    @Test
    fun `triggerCount padrao e 3`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(store = store)

        assertFalse(manager.onCompletion())  // 1
        assertFalse(manager.onCompletion())  // 2
        assertTrue(manager.onCompletion())   // 3
    }

    @Test
    fun `triggerCount customizado e respeitado`() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 5, store = store)

        repeat(4) { assertFalse(manager.onCompletion()) }
        assertTrue(manager.onCompletion())  // 5
    }
}
