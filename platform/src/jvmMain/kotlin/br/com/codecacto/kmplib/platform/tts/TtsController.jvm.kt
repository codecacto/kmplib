package br.com.codecacto.kmplib.platform.tts

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Desktop (2.353.0): a JVM não tem síntese de voz. `speak` vira [TtsState.Error]; idioma = `NotSupported`. */
internal class DesktopTtsController : TtsController {
    private val _state = MutableStateFlow(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    override suspend fun speak(text: String, langTag: String, rate: Float) {
        AppLogger.w("TtsController", "síntese de voz indisponível no desktop")
        _state.value = TtsState.Error
    }

    override fun stop() {
        _state.value = TtsState.Idle
    }

    override suspend fun isLanguageAvailable(langTag: String): TtsVoiceAvailability = TtsVoiceAvailability.NotSupported
    override fun setRate(rate: Float) = Unit
    override fun setVoiceGender(gender: TtsVoiceGender) = Unit
    override fun release() {
        _state.value = TtsState.Idle
    }
}

actual fun createTtsController(): TtsController = DesktopTtsController()
