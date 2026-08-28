package br.com.codecacto.kmplib.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSystemSpeechRecognizer
import androidx.core.content.ContextCompat
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Holder do [Context] da aplicação para o [SpeechRecognizer] no Android.
 * Inicializado por `KmpLib.init(context)` (ou diretamente `SpeechRecognizerHolder.init`).
 */
object SpeechRecognizerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Implementação Android do [SpeechRecognizer] via `android.speech.SpeechRecognizer` +
 * `RecognitionListener` (API oficial on-device, padrão-ouro).
 *
 * O `SpeechRecognizer` do Android **exige** ser criado e operado na **main thread**; por isso o app
 * deve chamar `startListening`/`stop`/`cancel` a partir da UI (o `VoiceCaptureButton`/
 * `DictationOverlay` já operam na composição). Best-effort: nenhuma operação lança.
 */
class AndroidSpeechRecognizer : SpeechRecognizer {

    private companion object {
        const val TAG = "SpeechRecognizer"
    }

    private val _state = MutableStateFlow(SpeechRecognitionState.Idle)
    override val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastError = MutableStateFlow<SpeechRecognitionError?>(null)
    override val lastError: StateFlow<SpeechRecognitionError?> = _lastError.asStateFlow()

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: Flow<SpeechEvent> = _events.asSharedFlow()

    private var recognizer: AndroidSystemSpeechRecognizer? = null
    private var released = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = SpeechRecognitionState.Listening
            _events.tryEmit(SpeechEvent.ReadyForSpeech)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = SpeechRecognitionState.Processing
            _events.tryEmit(SpeechEvent.EndOfSpeech)
        }

        override fun onError(error: Int) {
            val mapped = mapAndroidError(error)
            _lastError.value = mapped
            _state.value = SpeechRecognitionState.Error
            _events.tryEmit(SpeechEvent.Failed(mapped))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(AndroidSystemSpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) {
                _lastError.value = SpeechRecognitionError.NoMatch
                _state.value = SpeechRecognitionState.Error
                _events.tryEmit(SpeechEvent.Failed(SpeechRecognitionError.NoMatch))
            } else {
                _events.tryEmit(SpeechEvent.Result(text))
            }
            _partialText.value = ""
            if (_state.value != SpeechRecognitionState.Error) {
                _state.value = SpeechRecognitionState.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(AndroidSystemSpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                _partialText.value = text
                _events.tryEmit(SpeechEvent.Partial(text))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override suspend fun isRecognitionAvailable(config: SpeechRecognitionConfig): Boolean {
        val context = SpeechRecognizerHolder.getContext() ?: return false
        return try {
            AndroidSystemSpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao checar disponibilidade: ${e.message}")
            false
        }
    }

    override fun hasMicrophonePermission(): Boolean {
        val context = SpeechRecognizerHolder.getContext() ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun startListening(config: SpeechRecognitionConfig) {
        if (released) return
        val context = SpeechRecognizerHolder.getContext()
        if (context == null) {
            AppLogger.w(TAG, "Context ausente — chame KmpLib.init(context).")
            fail(SpeechRecognitionError.Unavailable)
            return
        }
        if (!hasMicrophonePermission()) {
            fail(SpeechRecognitionError.PermissionDenied)
            return
        }
        if (!AndroidSystemSpeechRecognizer.isRecognitionAvailable(context)) {
            fail(SpeechRecognitionError.Unavailable)
            return
        }
        try {
            // Recria o engine a cada sessão (mais robusto que reusar após erro).
            recognizer?.destroy()
            val engine = AndroidSystemSpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
            recognizer = engine
            _lastError.value = null
            _partialText.value = ""
            engine.startListening(buildIntent(context, config))
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao iniciar escuta: ${e.message}")
            fail(SpeechRecognitionError.Unknown)
        }
    }

    override fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao parar escuta: ${e.message}")
        }
    }

    override fun cancel() {
        try {
            recognizer?.cancel()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao cancelar: ${e.message}")
        }
        _partialText.value = ""
        if (_state.value != SpeechRecognitionState.Error) {
            _state.value = SpeechRecognitionState.Idle
        }
    }

    override fun release() {
        released = true
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao liberar: ${e.message}")
        }
        recognizer = null
        _partialText.value = ""
        _state.value = SpeechRecognitionState.Idle
    }

    private fun fail(error: SpeechRecognitionError) {
        _lastError.value = error
        _state.value = SpeechRecognitionState.Error
        _events.tryEmit(SpeechEvent.Failed(error))
    }

    private fun buildIntent(context: Context, config: SpeechRecognitionConfig): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Preferir on-device (curral sem sinal). EXTRA_PREFER_OFFLINE = API 23+.
            if (config.preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    private fun mapAndroidError(code: Int): SpeechRecognitionError = when (code) {
        AndroidSystemSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            SpeechRecognitionError.PermissionDenied
        AndroidSystemSpeechRecognizer.ERROR_NO_MATCH ->
            SpeechRecognitionError.NoMatch
        AndroidSystemSpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            SpeechRecognitionError.Timeout
        AndroidSystemSpeechRecognizer.ERROR_NETWORK,
        AndroidSystemSpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            SpeechRecognitionError.Network
        AndroidSystemSpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            SpeechRecognitionError.Busy
        // ERROR_LANGUAGE_NOT_SUPPORTED (12) / ERROR_LANGUAGE_UNAVAILABLE (13) — API 31+.
        12, 13 -> SpeechRecognitionError.Unavailable
        else -> SpeechRecognitionError.Unknown
    }
}

actual fun createSpeechRecognizer(): SpeechRecognizer = AndroidSpeechRecognizer()
