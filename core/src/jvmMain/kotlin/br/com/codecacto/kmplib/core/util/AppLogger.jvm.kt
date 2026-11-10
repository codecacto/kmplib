package br.com.codecacto.kmplib.core.util

import java.util.logging.Logger
import java.util.logging.Level as JulLevel

/**
 * Desktop: `java.util.logging` (o logger padrão da JVM, sem dependência) com o nome `kmplib.<tag>`.
 * Quem usa SLF4J/Logback no app liga a ponte `jul-to-slf4j` e os logs da lib entram no mesmo arquivo.
 */
actual object AppLogger {
    @Volatile
    private var minLevel: Level = Level.DEBUG

    actual fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)

    actual fun e(tag: String, message: String, throwable: Throwable?) = log(Level.ERROR, tag, message, throwable)

    actual fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)

    actual fun w(tag: String, message: String, throwable: Throwable?) = log(Level.WARN, tag, message, throwable)

    actual fun setMinLevel(level: Level) {
        minLevel = level
    }

    actual fun getMinLevel(): Level = minLevel

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        val jul = when (level) {
            Level.DEBUG -> JulLevel.FINE
            Level.INFO -> JulLevel.INFO
            Level.WARN -> JulLevel.WARNING
            Level.ERROR -> JulLevel.SEVERE
        }
        Logger.getLogger("kmplib.$tag").log(jul, message, throwable)
    }
}
