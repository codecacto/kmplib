package br.com.codecacto.kmplib.platform

import platform.Foundation.NSUserDefaults

actual class ReviewPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun hasReviewed(): Boolean = defaults.boolForKey("has_reviewed")

    actual fun markReviewed() {
        defaults.setBool(true, forKey = "has_reviewed")
    }

    actual fun getCompletionCount(): Int = defaults.integerForKey("completion_count").toInt()

    actual fun incrementCompletionCount(): Int {
        val newCount = getCompletionCount() + 1
        defaults.setInteger(newCount.toLong(), forKey = "completion_count")
        return newCount
    }

    actual fun getPromptCount(): Int = defaults.integerForKey("prompt_count").toInt()

    // NSInteger é 64 bits nos alvos iOS suportados (arm64) — cabe o epoch em ms.
    actual fun getLastPromptAt(): Long = defaults.integerForKey("last_prompt_at")

    actual fun recordPrompt(atMillis: Long) {
        defaults.setInteger((getPromptCount() + 1).toLong(), forKey = "prompt_count")
        defaults.setInteger(atMillis, forKey = "last_prompt_at")
    }

    actual fun getConfigJson(): String? = defaults.stringForKey("config_json")

    actual fun getConfigFetchedAt(): Long = defaults.integerForKey("config_fetched_at")

    actual fun setConfig(json: String, fetchedAtMillis: Long) {
        defaults.setObject(json, forKey = "config_json")
        defaults.setInteger(fetchedAtMillis, forKey = "config_fetched_at")
    }
}
