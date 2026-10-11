package br.com.codecacto.kmplib.core.util

import platform.Foundation.NSLog
import kotlin.concurrent.Volatile

actual object AppLogger {
    @Volatile
    private var minLevel: Level = Level.DEBUG

    actual fun d(tag: String, message: String) {
        if (isEnabled(Level.DEBUG)) {
            log("[$tag] DEBUG: $message")
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled(Level.ERROR)) {
            val errorMessage = throwable?.message?.let { " - $it" } ?: ""
            log("[$tag] ERROR: $message$errorMessage")
        }
    }

    actual fun i(tag: String, message: String) {
        if (isEnabled(Level.INFO)) {
            log("[$tag] INFO: $message")
        }
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (isEnabled(Level.WARN)) {
            val warningMessage = throwable?.message?.let { " - $it" } ?: ""
            log("[$tag] WARN: $message$warningMessage")
        }
    }

    actual fun setMinLevel(level: Level) {
        minLevel = level
    }

    actual fun getMinLevel(): Level = minLevel

    /**
     * O texto vai como o FORMATO do `NSLog` (passar `String` do Kotlin como argumento `%@` derruba o app no K/N),
     * então todo `%` vira `%%` (2.290.0): antes, uma mensagem com "%@" ou "%d" — uma URL com `%20`, um
     * percentual — fazia o `NSLog` ler argumento que não existe (lixo no log ou EXC_BAD_ACCESS).
     */
    private fun log(text: String) {
        NSLog(text.replace("%", "%%"))
    }

    private fun isEnabled(level: Level): Boolean = level.ordinal >= minLevel.ordinal
}
