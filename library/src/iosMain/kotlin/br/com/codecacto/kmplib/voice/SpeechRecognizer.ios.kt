@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.voice

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionResult
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.NSObject

/**
 * Implementação iOS do [SpeechRecognizer] via framework **Speech** (`SFSpeechRecognizer` +
 * `SFSpeechAudioBufferRecognitionRequest`) alimentado pelo microfone com `AVAudioEngine`
 * (API oficial da Apple, padrão-ouro on-device).
 *
 * **Passos de integração no app iOS** (Info.plist — sem eles o iOS derruba o app ao pedir):
 * - `NSMicrophoneUsageDescription`
 * - `NSSpeechRecognitionUsageDescription`
 *
 * On-device: quando `SFSpeechRecognizer.supportsOnDeviceRecognition` for `true` para o idioma e
 * [SpeechRecognitionConfig.preferOffline] for `true`, marca `requiresOnDeviceRecognition = true`
 * (curral sem sinal). Best-effort: nada lança; falhas viram [SpeechEvent.Failed].
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito por construção para compilar/rodar em iOS; o
 * ambiente atual é Linux (targets Apple SKIPPED) — validação em macOS/CI.
 */
class IosSpeechRecognizer : SpeechRecognizer {

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

    private val audioEngine = AVAudioEngine()
    private var recognizer: SFSpeechRecognizer? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var released = false

    override suspend fun isRecognitionAvailable(config: SpeechRecognitionConfig): Boolean {
        return try {
            val locale = NSLocale(localeIdentifier = config.languageTag)
            val r = SFSpeechRecognizer(locale = locale)
            r?.available == true
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao checar disponibilidade: ${e.message}")
            false
        }
    }

    override fun hasMicrophonePermission(): Boolean {
        // No iOS a permissão de reconhecimento de fala é distinta da de microfone; ambas são
        // pedidas pelo fluxo de UI. Aqui reportamos o status de autorização do Speech.
        return SFSpeechRecognizer.authorizationStatus() ==
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
    }

    override fun startListening(config: SpeechRecognitionConfig) {
        if (released) return
        val status = SFSpeechRecognizer.authorizationStatus()
        if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
            // Solicita autorização e (best-effort) tenta de novo quando concedida.
            SFSpeechRecognizer.requestAuthorization { newStatus ->
                if (newStatus == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    beginSession(config)
                } else {
                    fail(SpeechRecognitionError.PermissionDenied)
                }
            }
            return
        }
        beginSession(config)
    }

    private fun beginSession(config: SpeechRecognitionConfig) {
        try {
            cancelInternal()
            val locale = NSLocale(localeIdentifier = config.languageTag)
            val engine = SFSpeechRecognizer(locale = locale)
            if (engine == null || !engine.available) {
                fail(SpeechRecognitionError.Unavailable)
                return
            }
            recognizer = engine

            val session = AVAudioSession.sharedInstance()
            // Categoria de gravação (mesmo padrão do TtsController.ios, que usa o overload
            // setCategory(category, error) — member direto, sem import).
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            session.setActive(true, error = null)

            val req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = config.partialResults
            if (config.preferOffline && engine.supportsOnDeviceRecognition) {
                req.requiresOnDeviceRecognition = true
            }
            request = req

            val inputNode = audioEngine.inputNode
            val recordingFormat = inputNode.outputFormatForBus(0uL)
            inputNode.installTapOnBus(
                bus = 0uL,
                bufferSize = 1024u,
                format = recordingFormat,
            ) { buffer, _ ->
                buffer?.let { req.appendAudioPCMBuffer(it) }
            }

            audioEngine.prepare()
            audioEngine.startAndReturnError(null)

            _lastError.value = null
            _partialText.value = ""
            _state.value = SpeechRecognitionState.Listening
            _events.tryEmit(SpeechEvent.ReadyForSpeech)

            task = engine.recognitionTaskWithRequest(req) { result, error ->
                handleTaskCallback(result, error != null)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao iniciar sessão: ${e.message}")
            fail(SpeechRecognitionError.Unknown)
        }
    }

    private fun handleTaskCallback(result: SFSpeechRecognitionResult?, hasError: Boolean) {
        if (result != null) {
            val text = result.bestTranscription.formattedString
            // Propriedade ObjC `@property(getter=isFinal) BOOL final` → nome Kotlin `final`
            // (keyword → backticks). Reconciliar em host macOS se o nome gerado diferir.
            if (result.`final`) {
                if (text.isBlank()) {
                    _lastError.value = SpeechRecognitionError.NoMatch
                    _events.tryEmit(SpeechEvent.Failed(SpeechRecognitionError.NoMatch))
                    _state.value = SpeechRecognitionState.Error
                } else {
                    _events.tryEmit(SpeechEvent.Result(text))
                    _state.value = SpeechRecognitionState.Idle
                }
                _partialText.value = ""
                stopAudio()
            } else if (text.isNotBlank()) {
                _partialText.value = text
                _events.tryEmit(SpeechEvent.Partial(text))
            }
        }
        if (hasError) {
            // O framework Speech não distingue causa finamente aqui; reporta como Unknown/NoMatch.
            if (_state.value != SpeechRecognitionState.Idle) {
                _lastError.value = SpeechRecognitionError.Unknown
                _events.tryEmit(SpeechEvent.Failed(SpeechRecognitionError.Unknown))
                _state.value = SpeechRecognitionState.Error
            }
            _partialText.value = ""
            stopAudio()
        }
    }

    override fun stopListening() {
        // Finaliza o áudio; o task entrega o resultado final via callback.
        try {
            _state.value = SpeechRecognitionState.Processing
            _events.tryEmit(SpeechEvent.EndOfSpeech)
            request?.endAudio()
            stopAudio()
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao parar escuta: ${e.message}")
        }
    }

    override fun cancel() {
        cancelInternal()
        _partialText.value = ""
        if (_state.value != SpeechRecognitionState.Error) {
            _state.value = SpeechRecognitionState.Idle
        }
    }

    private fun cancelInternal() {
        try {
            task?.cancel()
        } catch (_: Exception) {
        }
        task = null
        try {
            request?.endAudio()
        } catch (_: Exception) {
        }
        request = null
        stopAudio()
    }

    private fun stopAudio() {
        try {
            if (audioEngine.running) {
                audioEngine.stop()
                audioEngine.inputNode.removeTapOnBus(0uL)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao parar áudio: ${e.message}")
        }
    }

    override fun release() {
        released = true
        cancelInternal()
        recognizer = null
        _partialText.value = ""
        _state.value = SpeechRecognitionState.Idle
    }

    private fun fail(error: SpeechRecognitionError) {
        _lastError.value = error
        _state.value = SpeechRecognitionState.Error
        _events.tryEmit(SpeechEvent.Failed(error))
    }
}

actual fun createSpeechRecognizer(): SpeechRecognizer = IosSpeechRecognizer()
