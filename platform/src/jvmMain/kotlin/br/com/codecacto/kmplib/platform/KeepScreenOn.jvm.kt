package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import br.com.codecacto.kmplib.core.util.AppLogger
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desktop (2.353.0). Windows: `SetThreadExecutionState(ES_CONTINUOUS | ES_DISPLAY_REQUIRED |
 * ES_SYSTEM_REQUIRED)` — a API oficial para "não apagar a tela nem dormir" (é o que player de vídeo
 * usa). O estado é POR THREAD, por isso uma thread daemon própria segura o pedido; pedidos
 * simultâneos são contados e só o último a sair libera. macOS/Linux: sem API na JVM — aviso no log.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) DesktopScreenKeeper.acquire()
        onDispose { if (enabled) DesktopScreenKeeper.release() }
    }
}

internal object DesktopScreenKeeper {
    private val pedidos = AtomicInteger(0)
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "kmplib-keep-screen-on").apply { isDaemon = true } }
    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

    fun acquire() {
        if (pedidos.incrementAndGet() == 1) apply(true)
    }

    fun release() {
        if (pedidos.decrementAndGet() <= 0) {
            pedidos.set(0)
            apply(false)
        }
    }

    private fun apply(on: Boolean) {
        if (!isWindows) {
            if (on) AppLogger.w("KeepScreenOn", "manter a tela acesa não tem API na JVM fora do Windows")
            return
        }
        thread.execute {
            runCatching {
                val flags = if (on) {
                    WinBase.ES_CONTINUOUS or WinBase.ES_DISPLAY_REQUIRED or WinBase.ES_SYSTEM_REQUIRED
                } else {
                    WinBase.ES_CONTINUOUS
                }
                Kernel32.INSTANCE.SetThreadExecutionState(flags)
            }.onFailure { AppLogger.w("KeepScreenOn", "SetThreadExecutionState falhou: ${it::class.simpleName}") }
        }
    }
}
