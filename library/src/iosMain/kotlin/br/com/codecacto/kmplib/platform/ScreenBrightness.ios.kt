package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIScreen

/**
 * **Padrão-ouro do iOS: `UIScreen.brightness` (0..1).**
 *
 * Diferença essencial em relação ao Android: aqui **não existe** "override da janela". O brilho é do
 * **aparelho**, a mudança é imediata e **o iOS não devolve o valor anterior quando o app sai** — a
 * Apple documenta que o valor persiste enquanto o app estiver rodando e cabe a ele restaurar. Por
 * isso o [ScreenBrightnessSession] roda em [BrightnessRestoreMode.RestorePrevious]: o brilho lido
 * antes do primeiro override é guardado e reposto no `restore()`/`release()`.
 *
 * Escrever `brightness` **não** requer permissão nem entrada no Info.plist.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** Kotlin/Native para iOS não compila no servidor Linux —
 * este `actual` foi escrito, revisado e coberto pela suíte comum (a regra de restauração está em
 * `commonTest`), mas não foi compilado. Registrado em `docs/backlog.md`.
 */
internal class IosScreenBrightnessController : ScreenBrightnessController {

    private val session = ScreenBrightnessSession(
        restoreMode = BrightnessRestoreMode.RestorePrevious,
        readPlatform = { UIScreen.mainScreen.brightness.toFloat() },
        writePlatform = { level ->
            // SYSTEM/UNKNOWN não tem correspondente no iOS: sem valor anterior legível, o certo é
            // não escrever nada em vez de chutar um brilho para o aparelho.
            if (ScreenBrightnessLevel.isOverride(level)) {
                UIScreen.mainScreen.brightness =
                    level.coerceIn(ScreenBrightnessLevel.MIN, ScreenBrightnessLevel.MAX).toDouble()
            }
        },
    )

    override val state: StateFlow<ScreenBrightnessState> = session.state

    init {
        session.refresh()
    }

    override fun current(): Float = session.current()

    override fun setBrightness(level: Float) = session.set(level)

    override fun restore() = session.restore()

    override fun release() = session.release()
}

actual fun createScreenBrightnessController(): ScreenBrightnessController =
    IosScreenBrightnessController()

@Composable
actual fun rememberScreenBrightnessController(): ScreenBrightnessController {
    val controller = remember { IosScreenBrightnessController() }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}
