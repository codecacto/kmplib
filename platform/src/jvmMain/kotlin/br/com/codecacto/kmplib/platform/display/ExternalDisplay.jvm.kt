package br.com.codecacto.kmplib.platform.display

import androidx.compose.runtime.Composable

/** Desktop: a janela já é a tela grande — sem segunda tela gerenciada pela lib. */
@Composable
internal actual fun PlatformExternalDisplayHost(enabled: Boolean) = Unit

internal actual fun applyScreenOrientation(orientation: ScreenOrientation) = Unit
