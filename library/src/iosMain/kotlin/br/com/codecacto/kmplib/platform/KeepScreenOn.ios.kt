package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * `UIApplication.isIdleTimerDisabled` — a API oficial da Apple para segurar a tela acesa.
 *
 * É uma flag **global do app**: o `onDispose` restaura o valor anterior em vez de forçar `false`,
 * para não desfazer uma trava que outra tela ainda esteja mantendo.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** o build Kotlin/Native iOS não roda no servidor Linux.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        val application = UIApplication.sharedApplication
        val previous = application.idleTimerDisabled
        application.idleTimerDisabled = enabled
        onDispose { application.idleTimerDisabled = previous }
    }
}
