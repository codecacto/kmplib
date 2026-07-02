package br.com.codecacto.kmplib.platform.tts

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVSpeechBoundaryImmediate
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.darwin.NSObject

/**
 * Implementação iOS do [TtsController] usando `AVSpeechSynthesizer` (AVFoundation).
 *
 * A disponibilidade de idioma é resolvida por `AVSpeechSynthesisVoice(language:)` (`nil` →
 * indisponível). O [state] é atualizado por um `AVSpeechSynthesizerDelegateProtocol`. Best-effort:
 * nada lança; voz ausente apenas não fala.
 *
 * Observação sobre a velocidade: a `rate` multiplataforma (`1f` = natural, [MIN_TTS_RATE]..
 * [MAX_TTS_RATE]) é convertida para a escala da `AVSpeechUtterance` ancorando `1f` em
 * `AVSpeechUtteranceDefaultSpeechRate` e clampando no intervalo válido `[Min, Max]` do sistema.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTtsController : TtsController {

    private companion object {
        const val TAG = "TtsController"
    }

    private val _state = MutableStateFlow(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentRate: Float = 1f
    private var released = false

    private val synthesizer = AVSpeechSynthesizer()

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didStartSpeechUtterance: AVSpeechUtterance
        ) {
            _state.value = TtsState.Speaking
        }

        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance
        ) {
            if (_state.value != TtsState.Error) _state.value = TtsState.Idle
        }

        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance
        ) {
            if (_state.value != TtsState.Error) _state.value = TtsState.Idle
        }
    }

    init {
        synthesizer.delegate = delegate
    }

    override suspend fun speak(text: String, langTag: String, rate: Float) {
        if (released) return
        val effectiveRate = resolveTtsSpeakRate(rate, currentRate)
        currentRate = effectiveRate
        try {
            val normalized = normalizeTtsLangTag(langTag)
            val voice = AVSpeechSynthesisVoice.voiceWithLanguage(normalized)
            if (voice == null) {
                // Voz indisponível NUNCA bloqueia: apenas não fala.
                if (_state.value == TtsState.Speaking) _state.value = TtsState.Idle
                return
            }
            if (synthesizer.speaking) {
                synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
            }
            val utterance = AVSpeechUtterance(string = text)
            utterance.voice = voice
            utterance.rate = iosRate(effectiveRate)
            synthesizer.speakUtterance(utterance)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao falar: ${e.message}")
            _state.value = TtsState.Error
        }
    }

    override fun stop() {
        try {
            if (synthesizer.speaking) {
                synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao parar TTS: ${e.message}")
        }
        if (_state.value != TtsState.Error) _state.value = TtsState.Idle
    }

    override suspend fun isLanguageAvailable(langTag: String): TtsVoiceAvailability {
        return try {
            val normalized = normalizeTtsLangTag(langTag)
            if (AVSpeechSynthesisVoice.voiceWithLanguage(normalized) != null) {
                TtsVoiceAvailability.Available
            } else {
                // O iOS não distingue "dados ausentes" de "não suportado": reporta NotSupported.
                TtsVoiceAvailability.NotSupported
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao checar idioma: ${e.message}")
            TtsVoiceAvailability.NotSupported
        }
    }

    override fun setRate(rate: Float) {
        currentRate = clampTtsRate(rate)
    }

    override fun release() {
        released = true
        try {
            if (synthesizer.speaking) {
                synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
            }
            synthesizer.delegate = null
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao liberar TTS: ${e.message}")
        }
        _state.value = TtsState.Idle
    }

    /**
     * Converte a `rate` multiplataforma (1f = natural) para a escala da `AVSpeechUtterance`,
     * ancorando 1f no `AVSpeechUtteranceDefaultSpeechRate` e clampando no intervalo válido do sistema.
     */
    private fun iosRate(rate: Float): Float {
        val anchored = AVSpeechUtteranceDefaultSpeechRate * rate
        val min = platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
        val max = platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
        return anchored.coerceIn(min, max)
    }
}

actual fun createTtsController(): TtsController = IosTtsController()
