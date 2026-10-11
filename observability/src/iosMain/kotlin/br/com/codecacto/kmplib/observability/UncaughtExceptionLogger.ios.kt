package br.com.codecacto.kmplib.observability

import platform.Foundation.NSLog
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.native.setUnhandledExceptionHook

/** O gancho foi instalado neste processo (uma vez só — reinstalar encadearia o gancho nele mesmo). */
@Volatile
private var installed: Boolean = false

/** Pilha ligada pelo app ([installUncaughtExceptionLogger]); o gancho lê no instante da exceção. */
@Volatile
private var stackTraceEnabled: Boolean = false

actual fun installUncaughtExceptionLogger(includeStackTrace: Boolean): Boolean {
    stackTraceEnabled = includeStackTrace
    ensureUncaughtExceptionLoggerInstalled()
    return true
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun ensureUncaughtExceptionLoggerInstalled() {
    if (installed) return
    installed = true
    var anterior: ((Throwable) -> Unit)? = null
    anterior = setUnhandledExceptionHook { erro ->
        // Nada aqui pode lançar: uma exceção dentro do gancho troca o relatório da original pelo dela.
        runCatching { logUncaught(erro) }
        anterior?.invoke(erro)
    }
}

@OptIn(ExperimentalNativeApi::class)
private fun logUncaught(erro: Throwable) {
    val depuracao = Platform.isDebugBinary
    // Pilha só em depuração: em release ela passaria pelo CoreSymbolication (lento) e não teria símbolo.
    val pilha = if (depuracao && stackTraceEnabled) runCatching { erro.stackTraceToString() }.getOrNull() else null
    uncaughtExceptionLogLines(erro, includeMessage = depuracao, stackTrace = pilha).forEach { linha ->
        NSLog(escapeLogFormat(linha))
    }
}
