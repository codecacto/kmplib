package br.com.codecacto.kmplib.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controlador inerte, devolvido quando não há janela para controlar (no Android, sem Activity
 * registrada).
 *
 * Reporta [ScreenBrightnessState.UNKNOWN] e ignora os comandos — nunca finge que forçou o brilho,
 * para o app não desenhar um slider que não move luz nenhuma.
 */
object UnavailableScreenBrightnessController : ScreenBrightnessController {

    private val _state = MutableStateFlow(ScreenBrightnessState.UNKNOWN)
    override val state: StateFlow<ScreenBrightnessState> = _state.asStateFlow()

    override fun current(): Float = ScreenBrightnessLevel.UNKNOWN

    override fun setBrightness(level: Float) = Unit

    override fun restore() = Unit

    override fun release() = Unit
}
