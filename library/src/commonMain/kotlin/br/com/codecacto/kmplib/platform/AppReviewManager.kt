package br.com.codecacto.kmplib.platform

/**
 * Storage abstrato para o estado de avaliação. Permite injetar fakes em testes.
 *
 * Implementação padrão é [PreferencesReviewStore], que persiste via [ReviewPreferences].
 */
interface ReviewStore {
    fun hasReviewed(): Boolean
    fun markReviewed()
    fun getCompletionCount(): Int
    fun incrementCompletionCount(): Int
}

/**
 * Adapter que delega para [ReviewPreferences] real.
 */
class PreferencesReviewStore(
    private val prefs: ReviewPreferences = ReviewPreferences()
) : ReviewStore {
    override fun hasReviewed(): Boolean = prefs.hasReviewed()
    override fun markReviewed() = prefs.markReviewed()
    override fun getCompletionCount(): Int = prefs.getCompletionCount()
    override fun incrementCompletionCount(): Int = prefs.incrementCompletionCount()
}

/**
 * Helper que decide quando mostrar o `AppReviewDialog` baseado em "completions".
 *
 * Mostra o dialog quando o usuário completou [triggerCount] ações relevantes e
 * ainda não avaliou. Persiste via [store] (default: [PreferencesReviewStore]).
 *
 * Uso típico:
 * ```
 * // No Application ou DI
 * val reviewManager = AppReviewManager(triggerCount = 3)
 *
 * // Ao final de uma ação relevante (ex.: salvar uma sessão, completar tarefa)
 * if (reviewManager.onCompletion()) {
 *     showReviewDialog = true
 * }
 *
 * // Quando o dialog for exibido (positivo ou negativo)
 * reviewManager.markShown()
 * ```
 *
 * Em testes, injete uma `ReviewStore` fake (in-memory):
 * ```
 * val store = FakeReviewStore()
 * val manager = AppReviewManager(triggerCount = 3, store = store)
 * ```
 *
 * @param triggerCount número de completions antes de mostrar o dialog.
 * @param store storage persistente. Default usa [PreferencesReviewStore].
 */
class AppReviewManager(
    private val triggerCount: Int = 3,
    private val store: ReviewStore = PreferencesReviewStore()
) {
    /**
     * Incrementa contador de completions e retorna `true` se for hora de mostrar
     * o dialog. Retorna `false` se o usuário já avaliou ou ainda não atingiu o
     * trigger.
     */
    fun onCompletion(): Boolean {
        if (store.hasReviewed()) return false
        val count = store.incrementCompletionCount()
        return count >= triggerCount
    }

    /**
     * Verifica se deve mostrar (sem incrementar). Útil para checagens passivas.
     */
    fun shouldShow(): Boolean =
        !store.hasReviewed() && store.getCompletionCount() >= triggerCount

    /** Marca como avaliado — não mostra mais o dialog. */
    fun markShown() {
        store.markReviewed()
    }

    /** Quantidade atual de completions registradas. */
    fun completionCount(): Int = store.getCompletionCount()

    /** Se o usuário já avaliou. */
    fun hasReviewed(): Boolean = store.hasReviewed()
}
