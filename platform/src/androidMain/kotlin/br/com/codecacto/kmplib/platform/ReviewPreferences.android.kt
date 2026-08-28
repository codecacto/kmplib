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
}
