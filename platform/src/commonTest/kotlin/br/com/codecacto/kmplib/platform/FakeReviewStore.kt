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

    var prompts = 0
    var lastPromptAt = 0L
    var cachedConfig: CachedReviewConfig? = null

    override fun getPromptCount(): Int = prompts
    override fun getLastPromptAtMillis(): Long = lastPromptAt
    override fun recordPrompt(atMillis: Long) {
        prompts++
        lastPromptAt = atMillis
    }

    override fun readCachedConfig(): CachedReviewConfig? = cachedConfig
    override fun writeCachedConfig(config: ReviewPromptConfig, fetchedAtMillis: Long) {
        cachedConfig = CachedReviewConfig(config, fetchedAtMillis)
    }

    /** Helpers para setup direto em testes. */
    fun setReviewed(value: Boolean) { reviewed = value }
    fun setCompletionCount(value: Int) { completionCount = value }
}
