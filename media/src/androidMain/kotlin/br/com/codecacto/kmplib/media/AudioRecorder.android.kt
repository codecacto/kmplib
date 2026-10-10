package br.com.codecacto.kmplib.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.AUDIO_CAPTURE_TEMP_DIRECTORY
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
import java.io.File

actual fun createAudioRecorder(config: AudioRecorderConfig): AudioRecorder = AndroidAudioRecorder(config)

internal actual fun readLocalAudioFile(path: String): ByteArray? = runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()

internal actual fun deleteLocalAudioFile(path: String): Boolean = runCatching { File(path).let { !it.exists() || it.delete() } }.getOrDefault(false)

/**
 * [AudioRecorder] sobre o `MediaRecorder` — o gravador para arquivo do Android, com AAC em contêiner
 * MPEG-4 e teto de duração aplicado pela plataforma (`setMaxDuration` + `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED`).
 */
internal class AndroidAudioRecorder(private val config: AudioRecorderConfig) : AudioRecorder {

    private val _state = MutableStateFlow<AudioRecorderState>(AudioRecorderState.Idle)
    override val state: StateFlow<AudioRecorderState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var stoppedAt = 0L
    private var reachedLimit = false
    private var meter: Job? = null
    private val levels = ArrayList<Float>()

    override suspend fun start(): Boolean {
        if (recorder != null) return true
        val context = SoundEffectPlayerHolder.getContext() ?: return falhar(AudioRecorderError.START_FAILED, "initKmpLibMedia não foi chamado")
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return falhar(AudioRecorderError.PERMISSION_DENIED, null)
        }
        val destino = runCatching {
            val dir = File(context.cacheDir, AUDIO_CAPTURE_TEMP_DIRECTORY).apply { mkdirs() }
            File.createTempFile("voz-", ".m4a", dir)
        }.getOrNull() ?: return falhar(AudioRecorderError.WRITE_FAILED, "sem espaço para o arquivo")

        val mr = novoRecorder(context)
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(config.bitRate)
            mr.setAudioSamplingRate(config.sampleRateHz)
            mr.setAudioChannels(config.channels)
            mr.setMaxDuration(config.maxDurationMillis.toInt())
            mr.setOutputFile(destino.absolutePath)
            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    // A plataforma já parou de gravar; o arquivo fica válido até o stop()/release().
                    reachedLimit = true
                    stoppedAt = SystemClock.elapsedRealtime()
                    meter?.cancel()
                    _state.value = AudioRecorderState.LimitReached(stoppedAt - startedAt, levels.toList())
                }
            }
            mr.setOnErrorListener { _, what, _ ->
                AppLogger.w(TAG, "MediaRecorder erro $what")
                cancelarInterno(AudioRecorderError.INTERRUPTED)
            }
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            AppLogger.w(TAG, "não começou a gravar: ${e::class.simpleName}")
            runCatching { mr.release() }
            destino.delete()
            // start() lança IllegalStateException quando o microfone está com outro app.
            return falhar(if (e is RuntimeException) AudioRecorderError.MICROPHONE_BUSY else AudioRecorderError.START_FAILED, null)
        }
        recorder = mr
        file = destino
        startedAt = SystemClock.elapsedRealtime()
        stoppedAt = 0L
        reachedLimit = false
        levels.clear()
        _state.value = AudioRecorderState.Recording(0L, 0f, emptyList())
        meter = scope.launch {
            while (isActive && recorder === mr) {
                val nivel = audioLevelFromAmplitude(runCatching { mr.maxAmplitude }.getOrDefault(0))
                levels += nivel
                _state.value = AudioRecorderState.Recording(SystemClock.elapsedRealtime() - startedAt, nivel, levels.toList())
                delay(config.levelIntervalMillis)
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun novoRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    override suspend fun stop(): AudioRecordingResult {
        val mr = recorder ?: return AudioRecordingResult.NotRecording
        val arquivo = file
        meter?.cancel()
        val fim = if (stoppedAt > 0L) stoppedAt else SystemClock.elapsedRealtime()
        val duracao = (fim - startedAt).coerceAtLeast(0L)
        val parou = runCatching { mr.stop() }.isSuccess
        runCatching { mr.release() }
        recorder = null
        file = null
        _state.value = AudioRecorderState.Idle
        if (arquivo == null) return AudioRecordingResult.Failed(AudioRecorderError.WRITE_FAILED)
        if (duracao < config.minDurationMillis) {
            arquivo.delete()
            return AudioRecordingResult.TooShort
        }
        // stop() lança quando nada foi gravado (sem dado) — exceto após o teto, em que já parou.
        if (!parou && !reachedLimit || arquivo.length() <= 0L) {
            arquivo.delete()
            return AudioRecordingResult.Failed(AudioRecorderError.WRITE_FAILED)
        }
        return AudioRecordingResult.Recorded(
            RecordedAudio(arquivo.absolutePath, duracao.coerceAtMost(config.maxDurationMillis), arquivo.length(), levels.toList(), reachedLimit),
        )
    }

    override fun cancel() {
        cancelarInterno(null)
    }

    private fun cancelarInterno(erro: AudioRecorderError?) {
        meter?.cancel()
        recorder?.let { mr ->
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        recorder = null
        file?.delete()
        file = null
        _state.value = erro?.let { AudioRecorderState.Failed(it) } ?: AudioRecorderState.Idle
    }

    override fun release() {
        cancel()
    }

    private fun falhar(erro: AudioRecorderError, log: String?): Boolean {
        log?.let { AppLogger.w(TAG, it) }
        _state.value = AudioRecorderState.Failed(erro)
        return false
    }

    private companion object {
        const val TAG = "AudioRecorder"
    }
}
