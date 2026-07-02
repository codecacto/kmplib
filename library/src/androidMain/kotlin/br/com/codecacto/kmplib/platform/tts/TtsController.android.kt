package br.com.codecacto.kmplib.platform.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Holder do [Context] da aplicação para o [TtsController] no Android.
 * Inicializado por `KmpLib.init(context)` (ou diretamente):
 *
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         TtsControllerHolder.init(this) // já chamado por KmpLib.init(this)
 *     }
 * }
 * ```
 */
object TtsControllerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Implementação Android do [TtsController] usando `android.speech.tts.TextToSpeech`.
 *
 * A inicialização do motor é assíncrona (`OnInitListener`); [speak]/[isLanguageAvailable] aguardam
 * o motor ficar pronto. O [state] é atualizado por um [UtteranceProgressListener]. Best-effort:
 * nenhuma operação lança — falhas viram log + [TtsState.Error]/[TtsVoiceAvailability.NotSupported].
 */
class AndroidTtsController : TtsController {

    private companion object {
        const val TAG = "TtsController"
        const val UTTERANCE_ID = "kmplib-tts"
    }

    private val _state = MutableStateFlow(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentRate: Float = 1f
    private var released = false

    private val ready = CompletableDeferred<Boolean>()

    private val tts: TextToSpeech? = run {
        val context = TtsControllerHolder.getContext()
        if (context == null) {
            AppLogger.w(TAG, "Context ausente — chame KmpLib.init(context). TTS desativado.")
            ready.complete(false)
            null
        } else {
            try {
                TextToSpeech(context) { status ->
                    ready.complete(status == TextToSpeech.SUCCESS)
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Falha ao criar TextToSpeech: ${e.message}")
                ready.complete(false)
                null
            }
        }
    }

    init {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = TtsState.Speaking
            }

            override fun onDone(utteranceId: String?) {
                if (_state.value != TtsState.Error) _state.value = TtsState.Idle
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _state.value = TtsState.Error
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _state.value = TtsState.Error
            }
        })
    }

    private suspend fun engine(): TextToSpeech? {
        if (released) return null
        val ok = try {
            ready.await()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao aguardar init do TTS: ${e.message}")
            false
        }
        return if (ok) tts else null
    }

    override suspend fun speak(text: String, langTag: String, rate: Float) {
        val engine = engine() ?: return
        val effectiveRate = resolveTtsSpeakRate(rate, currentRate)
        currentRate = effectiveRate
        try {
            val locale = localeOf(langTag)
            val availability = ttsAvailabilityFromAndroidCode(engine.isLanguageAvailable(locale))
            if (availability == TtsVoiceAvailability.NotSupported ||
                availability == TtsVoiceAvailability.MissingData
            ) {
                // Voz indisponível NUNCA bloqueia: apenas não fala.
                if (_state.value == TtsState.Speaking) _state.value = TtsState.Idle
                return
            }
            engine.language = locale
            engine.setSpeechRate(effectiveRate)
            // Roteia a fala pelo stream de MÍDIA (mais alto/previsível) em vez do stream padrão.
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
                // Volume máximo relativo ao stream (app de acessibilidade — fala precisa ser audível).
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao falar: ${e.message}")
            _state.value = TtsState.Error
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao parar TTS: ${e.message}")
        }
        if (_state.value != TtsState.Error) _state.value = TtsState.Idle
    }

    override suspend fun isLanguageAvailable(langTag: String): TtsVoiceAvailability {
        val engine = engine() ?: return TtsVoiceAvailability.NotSupported
        return try {
            ttsAvailabilityFromAndroidCode(engine.isLanguageAvailable(localeOf(langTag)))
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao checar idioma: ${e.message}")
            TtsVoiceAvailability.NotSupported
        }
    }

    override fun setRate(rate: Float) {
        currentRate = clampTtsRate(rate)
        try {
            tts?.setSpeechRate(currentRate)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao ajustar velocidade: ${e.message}")
        }
    }

    override fun release() {
        released = true
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao liberar TTS: ${e.message}")
        }
        _state.value = TtsState.Idle
    }

    private fun localeOf(langTag: String): Locale =
        Locale.forLanguageTag(normalizeTtsLangTag(langTag))
}

actual fun createTtsController(): TtsController = AndroidTtsController()
