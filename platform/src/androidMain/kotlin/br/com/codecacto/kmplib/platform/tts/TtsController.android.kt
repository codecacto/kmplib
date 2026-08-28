package br.com.codecacto.kmplib.platform.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
 * **Volume:** por padrão a fala **respeita o volume do aparelho** — é roteada pelo stream de MÍDIA
 * (o param de volume do enunciado é relativo a ele), sem forçar nada. A amplificação é **opt-in**
 * via [setVolumeBoost]: só então o controller sobe o volume de mídia ao máximo e sintetiza para um
 * arquivo ([TextToSpeech.synthesizeToFile]) para tocar com um [LoudnessEnhancer] (ganho real de
 * áudio, além do teto do volume). Se qualquer etapa da amplificação falhar, cai no `speak` direto
 * (fallback) — nunca fica mudo.
 *
 * Best-effort: nenhuma operação lança; voz ausente apenas não fala.
 */
class AndroidTtsController : TtsController {

    private companion object {
        const val TAG = "TtsController"
        const val UTTERANCE_ID = "kmplib-tts"
        const val SYNTH_PREFIX = "kmplib-tts-synth-"
        /** Ganho de amplificação em milibéis (2500 mB = +25 dB). O device faz clamp no suportado. */
        const val LOUDNESS_GAIN_MB = 2500
        const val SYNTH_TIMEOUT_MS = 8_000L
    }

    private val _state = MutableStateFlow(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentRate: Float = 1f
    private var voiceGender: TtsVoiceGender = TtsVoiceGender.Female
    /** Amplificação opt-in ([setVolumeBoost]). Desligada = respeita o volume do aparelho. */
    private var volumeBoost = false
    private var released = false

    private val ready = CompletableDeferred<Boolean>()

    // Playback amplificado
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    // Completa quando a síntese-para-arquivo termina (por utteranceId).
    private val synthDone = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

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
                // Só a fala DIRETA marca Speaking aqui; a síntese-para-arquivo é silenciosa
                // (o playback amplificado é quem marca Speaking).
                if (utteranceId == UTTERANCE_ID) _state.value = TtsState.Speaking
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith(SYNTH_PREFIX)) {
                    synthDone[utteranceId]?.complete(true)
                    return
                }
                if (_state.value != TtsState.Error) _state.value = TtsState.Idle
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith(SYNTH_PREFIX)) {
                    synthDone[utteranceId]?.complete(false)
                    return
                }
                _state.value = TtsState.Error
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null && utteranceId.startsWith(SYNTH_PREFIX)) {
                    synthDone[utteranceId]?.complete(false)
                    return
                }
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
        // Só com a amplificação ligada explicitamente ([setVolumeBoost]) o app mexe no volume do
        // aparelho. Desligada (padrão), o volume é o que o usuário definiu no device.
        if (volumeBoost) maximizeMediaVolume()
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
            // Seleção de voz por gênero (best-effort): antes de sintetizar, tenta trocar a Voice
            // para uma do gênero pedido no locale atual. Vale para os dois caminhos abaixo
            // (amplificado via synthesizeToFile e direto via speak).
            applyVoiceGender(engine, langTag)
            engine.setSpeechRate(effectiveRate)
            // Roteia a fala pelo stream de MÍDIA (mais alto/previsível) em vez do stream padrão.
            engine.setAudioAttributes(mediaAudioAttributes())

            // Caminho padrão: fala direta pelo motor, no volume do aparelho. Só com amplificação
            // ligada ([setVolumeBoost]) usa a síntese + playback com LoudnessEnhancer (que também
            // cai na fala direta se qualquer etapa falhar).
            val amplified = volumeBoost && speakAmplified(engine, text)
            if (!amplified) {
                speakDirect(engine, text)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao falar: ${e.message}")
            _state.value = TtsState.Error
        }
    }

    /**
     * Sintetiza [text] para um arquivo e toca com [LoudnessEnhancer] (ganho real). Retorna `true`
     * se conseguiu iniciar o playback amplificado; `false` para acionar o fallback direto.
     * Não inicia áudio em nenhum caminho de falha (evita fala dupla).
     */
    private suspend fun speakAmplified(engine: TextToSpeech, text: String): Boolean {
        val context = TtsControllerHolder.getContext() ?: return false
        return try {
            releasePlayer()
            val file = File(context.cacheDir, "kmplib_tts_out.wav")
            val utteranceId = SYNTH_PREFIX + System.nanoTime()
            val deferred = CompletableDeferred<Boolean>()
            synthDone[utteranceId] = deferred

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            @Suppress("DEPRECATION")
            val requested = engine.synthesizeToFile(text, params, file, utteranceId)
            if (requested != TextToSpeech.SUCCESS) {
                synthDone.remove(utteranceId)
                return false
            }
            val ok = withTimeoutOrNull(SYNTH_TIMEOUT_MS) { deferred.await() } ?: false
            synthDone.remove(utteranceId)
            if (!ok || !file.exists() || file.length() == 0L) return false

            val mp = MediaPlayer().apply {
                setAudioAttributes(mediaAudioAttributes())
                setDataSource(file.absolutePath)
                prepare()
            }
            // Amplificação real além do teto de volume, na sessão de áudio do próprio player.
            val enh = try {
                LoudnessEnhancer(mp.audioSessionId).apply {
                    setTargetGain(LOUDNESS_GAIN_MB)
                    enabled = true
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "LoudnessEnhancer indisponível: ${e.message}")
                null
            }
            mp.setOnCompletionListener {
                if (_state.value != TtsState.Error) _state.value = TtsState.Idle
                releasePlayer()
            }
            mp.setOnErrorListener { _, _, _ ->
                _state.value = TtsState.Error
                releasePlayer()
                true
            }
            player = mp
            enhancer = enh
            _state.value = TtsState.Speaking
            mp.start()
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha no playback amplificado (usando fallback): ${e.message}")
            releasePlayer()
            false
        }
    }

    /**
     * Fala direta pelo motor. `KEY_PARAM_VOLUME = 1.0` é **relativo** ao stream de mídia: mantém a
     * fala no topo do que o usuário permitiu, sem alterar o volume do aparelho.
     */
    private fun speakDirect(engine: TextToSpeech, text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao parar TTS: ${e.message}")
        }
        releasePlayer()
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

    override fun setVoiceGender(gender: TtsVoiceGender) {
        voiceGender = gender
    }

    override fun setVolumeBoost(enabled: Boolean) {
        volumeBoost = enabled
    }

    /**
     * Best-effort: seleciona uma [android.speech.tts.Voice] de `engine.voices` para [langTag] cujo
     * nome sugira [voiceGender] (via [pickVoiceName]/[ttsVoiceGenderHint]) e faz `engine.voice = ...`.
     * Se não houver voz do gênero pedido, **mantém a voz padrão** (não altera). Nunca lança.
     */
    private fun applyVoiceGender(engine: TextToSpeech, langTag: String) {
        try {
            val voices = engine.voices ?: return
            val chosenName = pickVoiceName(voices.map { it.name }, voiceGender, langTag) ?: return
            voices.firstOrNull { it.name == chosenName }?.let { engine.voice = it }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao selecionar voz por gênero: ${e.message}")
        }
    }

    override fun release() {
        released = true
        releasePlayer()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao liberar TTS: ${e.message}")
        }
        _state.value = TtsState.Idle
    }

    private fun releasePlayer() {
        try {
            enhancer?.release()
        } catch (_: Exception) {
        }
        enhancer = null
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
    }

    private fun mediaAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun localeOf(langTag: String): Locale =
        Locale.forLanguageTag(normalizeTtsLangTag(langTag))

    /**
     * Sobe o volume do stream de MÍDIA para o máximo (best-effort, sem UI). Chamado **apenas** com
     * a amplificação ligada ([setVolumeBoost]) — sobrescreve o volume escolhido pelo usuário, então
     * nunca roda no caminho padrão. Não faz nada se já estiver no máximo.
     */
    private fun maximizeMediaVolume() {
        try {
            val context = TtsControllerHolder.getContext() ?: return
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < max) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao maximizar volume de mídia: ${e.message}")
        }
    }
}

actual fun createTtsController(): TtsController = AndroidTtsController()
