package br.com.codecacto.kmplib.platform

import android.content.Context
import android.content.SharedPreferences
import br.com.codecacto.kmplib.core.context.AndroidAppContext

actual class ReviewPreferences {
    private val prefs: SharedPreferences

    actual constructor() {
        val context = AndroidAppContext.get()
            ?: throw IllegalStateException("KmpLib não foi inicializado. Chame KmpLib.init(context) no Application.onCreate()")
        prefs = context.getSharedPreferences("review_prefs", Context.MODE_PRIVATE)
    }

    actual fun hasReviewed(): Boolean = prefs.getBoolean("has_reviewed", false)

    actual fun markReviewed() {
        prefs.edit().putBoolean("has_reviewed", true).apply()
    }

    actual fun getCompletionCount(): Int = prefs.getInt("completion_count", 0)

    actual fun incrementCompletionCount(): Int {
        val newCount = getCompletionCount() + 1
        prefs.edit().putInt("completion_count", newCount).apply()
        return newCount
    }

    actual fun getPromptCount(): Int = prefs.getInt("prompt_count", 0)

    actual fun getLastPromptAt(): Long = prefs.getLong("last_prompt_at", 0L)

    actual fun recordPrompt(atMillis: Long) {
        prefs.edit()
            .putInt("prompt_count", getPromptCount() + 1)
            .putLong("last_prompt_at", atMillis)
            .apply()
    }

    actual fun getConfigJson(): String? = prefs.getString("config_json", null)

    actual fun getConfigFetchedAt(): Long = prefs.getLong("config_fetched_at", 0L)

    actual fun setConfig(json: String, fetchedAtMillis: Long) {
        prefs.edit()
            .putString("config_json", json)
            .putLong("config_fetched_at", fetchedAtMillis)
            .apply()
    }
}
