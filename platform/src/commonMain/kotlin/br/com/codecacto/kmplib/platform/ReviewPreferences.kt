package br.com.codecacto.kmplib.platform

/**
 * Preferências do pedido de avaliação (`review_prefs` no Android, `NSUserDefaults` no iOS).
 *
 * Chaves estáveis: `has_reviewed`, `completion_count` (desde sempre) e, desde a 2.335.0,
 * `prompt_count`, `last_prompt_at`, `config_json`, `config_fetched_at`.
 */
expect class ReviewPreferences() {
    fun hasReviewed(): Boolean
    fun markReviewed()
    fun getCompletionCount(): Int
    fun incrementCompletionCount(): Int

    /** Exibições automáticas do pedido (2.335.0). */
    fun getPromptCount(): Int

    /** Instante (epoch ms) da última exibição automática; 0 = nunca (2.335.0). */
    fun getLastPromptAt(): Long

    /** Soma uma exibição e grava o instante (2.335.0). */
    fun recordPrompt(atMillis: Long)

    /** JSON da última config remota; `null` = nunca lida (2.335.0). */
    fun getConfigJson(): String?

    /** Instante da última leitura da config remota; 0 = nunca (2.335.0). */
    fun getConfigFetchedAt(): Long

    /** Grava a config remota lida agora (2.335.0). */
    fun setConfig(json: String, fetchedAtMillis: Long)
}
