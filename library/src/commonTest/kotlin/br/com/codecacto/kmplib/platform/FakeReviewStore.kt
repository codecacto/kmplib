package br.com.codecacto.kmplib.platform

/**
 * Implementação in-memory de [ReviewStore] para uso em testes do [AppReviewManager].
 *
 * Não persiste entre instâncias — cada teste cria uma nova fake.
 *
 * ```kotlin
 * val store = FakeReviewStore()
 * val manager = AppReviewManager(triggerCount = 3, store = store)
 * ```
 */
class FakeReviewStore : ReviewStore {
    private var completionCount = 0
    private var reviewed = false

    override fun hasReviewed(): Boolean = reviewed

    override fun markReviewed() {
        reviewed = true
    }

    override fun getCompletionCount(): Int = completionCount

    override fun incrementCompletionCount(): Int {
        completionCount++
        return completionCount
    }

    /** Helpers para setup direto em testes. */
    fun setReviewed(value: Boolean) { reviewed = value }
    fun setCompletionCount(value: Int) { completionCount = value }
}
