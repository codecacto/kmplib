package br.com.codecacto.kmplib.core.util

import android.util.Log

actual object AppLogger {
    @Volatile
    private var minLevel: Level = Level.DEBUG

    actual fun d(tag: String, message: String) {
        if (isEnabled(Level.DEBUG)) {
            Log.d(tag, message)
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled(Level.ERROR)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    actual fun i(tag: String, message: String) {
        if (isEnabled(Level.INFO)) {
            Log.i(tag, message)
        }
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled(Level.WARN)) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    actual fun setMinLevel(level: Level) {
        minLevel = level
    }

    actual fun getMinLevel(): Level = minLevel

    private fun isEnabled(level: Level): Boolean = level.ordinal >= minLevel.ordinal
}
