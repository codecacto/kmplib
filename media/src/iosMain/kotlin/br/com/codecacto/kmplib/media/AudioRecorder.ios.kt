@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package br.com.codecacto.kmplib.media

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.AUDIO_CAPTURE_TEMP_DIRECTORY
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.createPermissionManager
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioRecorderDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.posix.memcpy

actual fun createAudioRecorder(config: AudioRecorderConfig): AudioRecorder = IosAudioRecorder(config)

internal actual fun readLocalAudioFile(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val n = data.length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

internal actual fun deleteLocalAudioFile(path: String): Boolean {
    val fm = NSFileManager.defaultManager
    return !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, null)
}

private fun agoraMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

/**
 * [AudioRecorder] sobre o `AVAudioRecorder` (AAC em MPEG-4, `recordForDuration` = teto pela
 * plataforma, `meteringEnabled` para a onda), com a sessão `playAndRecord` (alto-falante por padrão,
 * fone Bluetooth aceito) ativada só enquanto grava e devolvida com `notifyOthersOnDeactivation`.
 */
internal class IosAudioRecorder(private val config: AudioRecorderConfig) : AudioRecorder {

    private val _state = MutableStateFlow<AudioRecorderState>(AudioRecorderState.Idle)
    override val state: StateFlow<AudioRecorderState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: AVAudioRecorder? = null
    private var path: String? = null
    private var startedAt = 0L
    private var stoppedAt = 0L
    private var reachedLimit = false
    private var meter: Job? = null
    private var interrupcao: NSObjectProtocol? = null
    private val levels = ArrayList<Float>()

    private val delegate = object : NSObject(), AVAudioRecorderDelegateProtocol {
        override fun audioRecorderDidFinishRecording(recorder: AVAudioRecorder, successfully: Boolean) {
            // Chega no teto (recordForDuration) e no stop(); só o teto marca o limite.
            if (stoppedAt == 0L && this@IosAudioRecorder.recorder === recorder) {
                reachedLimit = successfully
                stoppedAt = agoraMillis()
                meter?.cancel()
                _state.value = AudioRecorderState.LimitReached(stoppedAt - startedAt, levels.toList())
            }
        }

        override fun audioRecorderEncodeErrorDidOccur(recorder: AVAudioRecorder, error: NSError?) {
            AppLogger.w(TAG, "erro de codificação do gravador")
            cancelarInterno(AudioRecorderError.WRITE_FAILED)
        }
    }

    override suspend fun start(): Boolean {
        if (recorder != null) return true
        if (createPermissionManager().currentStatus(AppPermission.MICROPHONE) != PermissionStatus.GRANTED) {
            return falhar(AudioRecorderError.PERMISSION_DENIED)
        }
        val sessao = AVAudioSession.sharedInstance()
        val configurou = sessao.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth,
            error = null,
        )
        if (!configurou || !sessao.setActive(true, withOptions = 0u, error = null)) {
            return falhar(AudioRecorderError.MICROPHONE_BUSY)
        }
        val dir = NSTemporaryDirectory().trimEnd('/') + "/" + AUDIO_CAPTURE_TEMP_DIRECTORY
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val destino = "$dir/voz-${NSUUID().UUIDString}.m4a"
        val ajustes: Map<Any?, Any?> = mapOf(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber(double = config.sampleRateHz.toDouble()),
            AVNumberOfChannelsKey to NSNumber(int = config.channels),
            AVEncoderBitRateKey to NSNumber(int = config.bitRate),
            AVEncoderAudioQualityKey to NSNumber(long = AVAudioQualityHigh),
        )
        val rec = AVAudioRecorder(uRL = NSURL.fileURLWithPath(destino), settings = ajustes, error = null)
        rec.delegate = delegate
        rec.meteringEnabled = true
        if (!rec.prepareToRecord() || !rec.recordForDuration(config.maxDurationMillis / 1000.0)) {
            deleteLocalAudioFile(destino)
            desativarSessao()
            return falhar(AudioRecorderError.START_FAILED)
        }
        recorder = rec
        path = destino
        startedAt = agoraMillis()
        stoppedAt = 0L
        reachedLimit = false
        levels.clear()
        ouvirInterrupcao()
        _state.value = AudioRecorderState.Recording(0L, 0f, emptyList())
        meter = scope.launch {
            while (isActive && recorder === rec) {
                rec.updateMeters()
                val nivel = audioLevelFromDecibels(rec.averagePowerForChannel(0u))
                levels += nivel
                _state.value = AudioRecorderState.Recording(agoraMillis() - startedAt, nivel, levels.toList())
                delay(config.levelIntervalMillis)
            }
        }
        return true
    }

    private fun ouvirInterrupcao() {
        interrupcao = NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            // Ligação/Siri: o sistema já parou o gravador; o trecho gravado é descartado.
            if (recorder != null && stoppedAt == 0L) cancelarInterno(AudioRecorderError.INTERRUPTED)
        }
    }

    private fun pararDeOuvir() {
        interrupcao?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        interrupcao = null
    }

    override suspend fun stop(): AudioRecordingResult {
        val rec = recorder ?: return AudioRecordingResult.NotRecording
        val arquivo = path
        meter?.cancel()
        val fim = if (stoppedAt > 0L) stoppedAt else agoraMillis()
        stoppedAt = fim
        rec.stop()
        recorder = null
        path = null
        pararDeOuvir()
        desativarSessao()
        _state.value = AudioRecorderState.Idle
        if (arquivo == null) return AudioRecordingResult.Failed(AudioRecorderError.WRITE_FAILED)
        val duracao = (fim - startedAt).coerceAtLeast(0L)
        if (duracao < config.minDurationMillis) {
            deleteLocalAudioFile(arquivo)
            return AudioRecordingResult.TooShort
        }
        val tamanho = (NSFileManager.defaultManager.attributesOfItemAtPath(arquivo, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        if (tamanho <= 0L) {
            deleteLocalAudioFile(arquivo)
            return AudioRecordingResult.Failed(AudioRecorderError.WRITE_FAILED)
        }
        return AudioRecordingResult.Recorded(
            RecordedAudio(arquivo, duracao.coerceAtMost(config.maxDurationMillis), tamanho, levels.toList(), reachedLimit),
        )
    }

    override fun cancel() {
        cancelarInterno(null)
    }

    private fun cancelarInterno(erro: AudioRecorderError?) {
        meter?.cancel()
        recorder?.let { rec ->
            stoppedAt = agoraMillis()
            rec.stop()
            rec.deleteRecording()
        }
        recorder = null
        path?.let { deleteLocalAudioFile(it) }
        path = null
        pararDeOuvir()
        desativarSessao()
        _state.value = erro?.let { AudioRecorderState.Failed(it) } ?: AudioRecorderState.Idle
    }

    private fun desativarSessao() {
        AVAudioSession.sharedInstance().setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    override fun release() {
        cancel()
    }

    private fun falhar(erro: AudioRecorderError): Boolean {
        _state.value = AudioRecorderState.Failed(erro)
        return false
    }

    private companion object {
        const val TAG = "AudioRecorder"
    }
}
