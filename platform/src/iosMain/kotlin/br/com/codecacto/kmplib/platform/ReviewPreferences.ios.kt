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
}
