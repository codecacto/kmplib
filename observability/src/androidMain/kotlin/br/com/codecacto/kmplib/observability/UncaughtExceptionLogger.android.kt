package br.com.codecacto.kmplib.observability

/**
 * Android: o handler padrão do sistema já escreve `FATAL EXCEPTION` com tipo, mensagem e pilha no
 * logcat, e o Sentry Android encadeia o dele — não há o que instalar.
 */
actual fun installUncaughtExceptionLogger(includeStackTrace: Boolean): Boolean = false

internal actual fun ensureUncaughtExceptionLoggerInstalled() = Unit
