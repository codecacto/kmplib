package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * `View.keepScreenOn` — a forma recomendada pelo Android de manter a tela acesa a partir da UI.
 *
 * O flag vive na janela enquanto a View estiver anexada; o `onDispose` o devolve ao normal. Não usa
 * `PowerManager.WakeLock` de propósito: aquele exige a permissão `WAKE_LOCK`, sobrevive à saída da
 * tela e é a origem clássica do vazamento "a tela nunca mais apagou".
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = previous }
    }
}
