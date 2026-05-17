package br.com.codecacto.kmplib.platform

/**
 * Helper que decide quando mostrar o `AppReviewDialog` baseado em "completions".
 *
 * Persiste via [ReviewPreferences]. Mostra o dialog quando o usuário completou
 * [triggerCount] ações relevantes e ainda não avaliou.
 *
 * Uso típico:
 * ```
 * // No Application ou DI
 * val reviewManager = AppReviewManager(triggerCount = 3)
 *
 * // Ao final de uma ação relevante (ex.: salvar uma sessão, completar tarefa)
 * if (reviewManager.onCompletion()) {
 *     // Mostrar AppReviewDialog
 *     showReviewDialog = true
 * }
 *
 * // Quando o dialog for exibido (independente do user clicar bem ou mal)
 * reviewManager.markShown()
 * ```
 *
 * @param triggerCount número de completions antes de mostrar o dialog.
 * @param prefs storage persistente. Default usa [ReviewPreferences].
 */
class AppReviewManager(
    private val triggerCount: Int = 3,
    private val prefs: ReviewPreferences = ReviewPreferences()
) {
    /**
     * Incrementa contador de completions e retorna `true` se for hora de mostrar
     * o dialog. Retorna `false` se o usuário já avaliou ou ainda não atingiu o
     * trigger.
     */
    fun onCompletion(): Boolean {
        if (prefs.hasReviewed()) return false
        val count = prefs.incrementCompletionCount()
        return count >= triggerCount
    }

    /**
     * Verifica se deve mostrar (sem incrementar). Útil para conferir em
     * `LaunchedEffect` ou outras checagens passivas.
     */
    fun shouldShow(): Boolean =
        !prefs.hasReviewed() && prefs.getCompletionCount() >= triggerCount

    /** Marca como avaliado — não mostra mais o dialog. */
    fun markShown() {
        prefs.markReviewed()
    }

    /** Quantidade atual de completions registradas. */
    fun completionCount(): Int = prefs.getCompletionCount()

    /** Se o usuário já avaliou. */
    fun hasReviewed(): Boolean = prefs.hasReviewed()
}
