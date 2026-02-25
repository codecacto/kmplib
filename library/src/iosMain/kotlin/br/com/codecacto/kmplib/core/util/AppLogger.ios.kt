package br.com.codecacto.kmplib.core.util

import platform.Foundation.NSLog
import kotlin.concurrent.Volatile

actual object AppLogger {
    @Volatile
    private var minLevel: Level = Level.DEBUG

    actual fun d(tag: String, message: String) {
        if (isEnabled(Level.DEBUG)) {
            NSLog("[$tag] DEBUG: $message")
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled(Level.ERROR)) {
            val errorMessage = throwable?.message?.let { " - $it" } ?: ""
            NSLog("[$tag] ERROR: $message$errorMessage")
        }
    }

    actual fun i(tag: String, message: String) {
        if (isEnabled(Level.INFO)) {
            NSLog("[$tag] INFO: $message")
        }
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled(Level.WARN)) {
            val warningMessage = throwable?.message?.let { " - $it" } ?: ""
            NSLog("[$tag] WARN: $message$warningMessage")
        }
    }

    actual fun setMinLevel(level: Level) {
        minLevel = level
    }

    actual fun getMinLevel(): Level = minLevel

    private fun isEnabled(level: Level): Boolean = level.ordinal >= minLevel.ordinal
}
