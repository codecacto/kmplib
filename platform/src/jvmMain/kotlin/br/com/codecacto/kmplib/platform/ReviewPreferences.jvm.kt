package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.storage.writeFileDurably
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Properties

/**
 * Desktop (2.353.0): `review_prefs.properties` na pasta de dados do app. API síncrona (como o
 * `SharedPreferences` do Android), gravação atômica e durável a cada mudança.
 */
actual class ReviewPreferences actual constructor() {

    private val arquivo = DesktopAppContext.appDataDirectory().resolve("review_prefs.properties")

    private fun load(): Properties = Properties().also { p ->
        runCatching { if (Files.isRegularFile(arquivo)) Files.newInputStream(arquivo).use(p::load) }
            .onFailure { AppLogger.w("ReviewPreferences", "arquivo ilegível: ${it::class.simpleName}") }
    }

    private fun edit(change: (Properties) -> Unit): Unit = synchronized(LOCK) {
        val p = load().also(change)
        val out = ByteArrayOutputStream()
        p.store(out, null)
        runCatching { writeFileDurably(arquivo, out.toByteArray()) }
            .onFailure { AppLogger.w("ReviewPreferences", "não gravou: ${it::class.simpleName}") }
        Unit
    }

    private fun long(key: String): Long = synchronized(LOCK) { load().getProperty(key)?.toLongOrNull() ?: 0L }

    actual fun hasReviewed(): Boolean = synchronized(LOCK) { load().getProperty(KEY_REVIEWED) == "true" }
    actual fun markReviewed(): Unit = edit { it.setProperty(KEY_REVIEWED, "true") }
    actual fun getCompletionCount(): Int = long(KEY_COMPLETIONS).toInt()
    actual fun incrementCompletionCount(): Int = synchronized(LOCK) {
        val novo = getCompletionCount() + 1
        edit { it.setProperty(KEY_COMPLETIONS, novo.toString()) }
        novo
    }
    actual fun getPromptCount(): Int = long(KEY_PROMPTS).toInt()
    actual fun getLastPromptAt(): Long = long(KEY_LAST_PROMPT)
    actual fun recordPrompt(atMillis: Long): Unit = synchronized(LOCK) {
        val prompts = getPromptCount() + 1
        edit {
            it.setProperty(KEY_PROMPTS, prompts.toString())
            it.setProperty(KEY_LAST_PROMPT, atMillis.toString())
        }
    }
    actual fun getConfigJson(): String? = synchronized(LOCK) { load().getProperty(KEY_CONFIG) }
    actual fun getConfigFetchedAt(): Long = long(KEY_CONFIG_AT)
    actual fun setConfig(json: String, fetchedAtMillis: Long): Unit = edit {
        it.setProperty(KEY_CONFIG, json)
        it.setProperty(KEY_CONFIG_AT, fetchedAtMillis.toString())
    }

    private companion object {
        val LOCK = Any()
        const val KEY_REVIEWED = "has_reviewed"
        const val KEY_COMPLETIONS = "completion_count"
        const val KEY_PROMPTS = "prompt_count"
        const val KEY_LAST_PROMPT = "last_prompt_at"
        const val KEY_CONFIG = "config_json"
        const val KEY_CONFIG_AT = "config_fetched_at"
    }
}
