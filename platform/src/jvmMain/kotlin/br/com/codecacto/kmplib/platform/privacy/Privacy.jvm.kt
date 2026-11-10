package br.com.codecacto.kmplib.platform.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Desktop: a JVM não tira a janela do Alt+Tab nem a protege de captura (no Windows isso é
 * `SetWindowDisplayAffinity`, fora do Compose). `isSupported = false` — o app sabe que não há proteção.
 */
internal object DesktopPrivacyScreen : PrivacyScreen {
    override val isSupported: Boolean = false
    override val isHidden: Boolean = false
    override fun setHidden(hidden: Boolean) = Unit
}

actual fun getPrivacyScreen(): PrivacyScreen = DesktopPrivacyScreen

/** Desktop: o SO não informa gravação/espelhamento de tela à JVM — sempre `false`. */
@Composable
internal actual fun rememberScreenCaptured(): State<Boolean> = remember { mutableStateOf(false) }
