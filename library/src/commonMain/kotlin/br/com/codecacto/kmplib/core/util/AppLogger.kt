package br.com.codecacto.kmplib.core.util

/**
 * Logger multiplataforma para Android e iOS.
 *
 * Uso:
 * ```kotlin
 * AppLogger.d("MinhaTag", "Mensagem de debug")
 * AppLogger.e("MinhaTag", "Erro", exception)
 * ```
 */
enum class Level {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

expect object AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun setMinLevel(level: Level)
    fun getMinLevel(): Level
}
