package br.com.codecacto.kmplib.platform.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import kotlin.math.max

private const val TAG = "AudioCapture"

/**
 * Holder do [Context] da aplicação para a captura de áudio no Android. Inicializado por
 * `KmpLib.init(context)` — nenhum app precisa chamar isto à mão.
 */
object AudioCaptureHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Cria a captura de áudio do Android.
 *
 * Sem `KmpLib.init(context)` no `Application.onCreate()`, devolve uma captura inerte — em vez de
 * estourar no primeiro `start()`.
 */
actual fun createAudioCapture(config: AudioCaptureConfig): AudioCapture {
    val context = AudioCaptureHolder.getContext() ?: run {
        AppLogger.e(TAG, "KmpLib.init(context) não foi chamado — captura de áudio indisponível")
        return UnavailableAudioCapture(
            AudioCaptureError.Unknown("KmpLib.init(context) não foi chamado"),
        )
    }
    return AndroidAudioCapture(context, config)
}

/**
 * **Padrão-ouro do Android: `AudioRecord`, PCM 16-bit mono, cru.**
 *
 * - **Por que `AudioRecord` e não `MediaRecorder.getMaxAmplitude()`:** o `MediaRecorder` **grava um
 *   arquivo em disco** só para devolver um inteiro de amplitude de pico, com resolução grosseira,
 *   sem ponderação, sem RMS e **sem acesso nenhum ao buffer**. Um medidor de nível feito assim
 *   escreve áudio no armazenamento do usuário a cada leitura — o oposto do que este módulo
 *   promete —, e um afinador não teria como existir sobre ele.
 * - **Fonte de entrada:** `UNPROCESSED` quando o aparelho declara suporte
 *   (`AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`), senão `VOICE_RECOGNITION`. O
 *   motivo é medição, não qualidade: ganho automático (AGC), supressão de ruído e cancelamento de
 *   eco **destroem a relação amplitude → SPL** — o AGC "conserta" exatamente aquilo que estamos
 *   medindo, e o número na tela para de significar o volume do ambiente. `UNPROCESSED` é a única
 *   fonte que a plataforma garante sem processamento; entre as sempre disponíveis, a documentação
 *   do Android aponta `VOICE_RECOGNITION` como a que menos processa (`MIC` costuma vir com AGC/NS
 *   em fabricante). A fonte efetiva vai em [AudioCaptureState.Running.source] — e trocar de fonte
 *   **invalida a calibração** de [SplCalibration].
 * - **`AudioRecord.Builder`** (API 23+) em vez do construtor depreciado.
 * - **Thread dedicada** com `THREAD_PRIORITY_URGENT_AUDIO`: a leitura é bloqueante e um atraso
 *   dela vira *overrun* do buffer do driver (amostras perdidas, nível medido para menos).
 * - **Emissão sem back-pressure** (`DROP_OLDEST`): o laço de áudio **nunca** espera coletor. Uma
 *   tela lenta atrasaria a leitura do hardware e corromperia a medição — descartar a leitura mais
 *   velha é o comportamento certo para dado de tempo real.
 */
internal class AndroidAudioCapture(
    context: Context,
    config: AudioCaptureConfig,
) : AudioCapture {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<AudioCaptureState>(AudioCaptureState.Idle)
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()

    private val _levels = MutableSharedFlow<AudioLevel>(
        extraBufferCapacity = LEVEL_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val levels: Flow<AudioLevel> = _levels.asSharedFlow()

    private val _frames = MutableSharedFlow<AudioFrame>(
        extraBufferCapacity = FRAME_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val frames: Flow<AudioFrame> = _frames.asSharedFlow()

    private val emitFrames: Boolean = config.emitFrames
    private val preferredSampleRate: Int = config.preferredSampleRate

    @Volatile
    private var weighting: AudioWeighting = config.weighting

    @Volatile
    private var timeWeighting: AudioTimeWeighting = config.timeWeighting

    @Volatile
    private var emitIntervalMillis: Long = config.emitIntervalMillis

    @Volatile
    private var loopActive: Boolean = false

    private var recorder: AudioRecord? = null
    private var readerThread: Thread? = null
    private var released: Boolean = false

    override val isAvailable: Boolean
        get() = !released && hasMicrophone() && hasPermission()

    @Synchronized
    override fun start(): Boolean {
        if (released) {
            AppLogger.w(TAG, "start() após release() — crie uma nova captura")
            return false
        }
        if (loopActive) return true

        _state.value = AudioCaptureState.Starting

        if (!hasPermission()) {
            // Sem esta guarda o AudioRecord ABRE normalmente e entrega SILÊNCIO DIGITAL: o app
            // mostraria -120 dB para sempre, sem erro no log e sem pista nenhuma da causa.
            AppLogger.w(TAG, "RECORD_AUDIO não concedida — o app precisa pedir antes de capturar")
            _state.value = AudioCaptureState.Failed(AudioCaptureError.PermissionDenied)
            return false
        }
        if (!hasMicrophone()) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return false
        }

        val opened = openRecorder()
        if (opened == null) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.UnsupportedConfiguration)
            return false
        }

        val started = runCatching {
            opened.record.startRecording()
            opened.record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        }.getOrElse { error ->
            AppLogger.e(TAG, "startRecording falhou: ${error.message}")
            false
        }

        if (!started) {
            runCatching { opened.record.release() }
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return false
        }

        recorder = opened.record
        loopActive = true
        _state.value = AudioCaptureState.Running(opened.source, opened.sampleRate)

        readerThread = Thread({ readLoop(opened) }, "kmplib-audio-capture").apply {
            isDaemon = true
            start()
        }
        return true
    }

    @Synchronized
    override fun stop() {
        if (!loopActive && recorder == null) {
            if (!released && _state.value !is AudioCaptureState.Failed) {
                _state.value = AudioCaptureState.Idle
            }
            return
        }
        loopActive = false
        val record = recorder
        recorder = null
        runCatching { if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
        // join curto: o read() bloqueado retorna assim que o stop() acima chega ao driver.
        runCatching { readerThread?.join(THREAD_JOIN_TIMEOUT_MILLIS) }
        readerThread = null
        runCatching { record?.release() }
        if (!released) _state.value = AudioCaptureState.Idle
    }

    override fun release() {
        if (released) return
        stop()
        released = true
        _state.value = AudioCaptureState.Released
    }

    override fun updateProcessing(
        weighting: AudioWeighting?,
        timeWeighting: AudioTimeWeighting?,
        emitIntervalMillis: Long?,
    ) {
        weighting?.let { this.weighting = it }
        timeWeighting?.let { this.timeWeighting = it }
        emitIntervalMillis?.let {
            this.emitIntervalMillis = it.coerceIn(
                AudioCaptureConfig.MIN_EMIT_INTERVAL_MILLIS,
                AudioCaptureConfig.MAX_EMIT_INTERVAL_MILLIS,
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Laço de leitura
    // -----------------------------------------------------------------------------------------

    private fun readLoop(opened: OpenRecorder) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val analyzer = AudioLevelAnalyzer(opened.sampleRate, weighting, timeWeighting)
        val buffer = ShortArray(opened.readFrames)
        var lastEmitAt = SystemClock.elapsedRealtime()

        while (loopActive) {
            val record = recorder ?: break
            val read = runCatching { record.read(buffer, 0, buffer.size) }.getOrElse { error ->
                AppLogger.e(TAG, "Falha ao ler do microfone: ${error.message}")
                AudioRecord.ERROR
            }

            if (read < 0) {
                if (loopActive) reportReadError(read)
                return
            }
            if (read == 0) continue

            // Ajustes trocados a quente pela tela de Configurações (setters são no-op se iguais).
            analyzer.weighting = weighting
            analyzer.timeWeighting = timeWeighting

            analyzer.accumulate(buffer, read)

            if (emitFrames) {
                _frames.tryEmit(
                    AudioFrame(
                        samples = buffer.copyOf(read),
                        sampleRate = opened.sampleRate,
                        channelCount = 1,
                        timestampMillis = System.currentTimeMillis(),
                    ),
                )
            }

            val now = SystemClock.elapsedRealtime()
            if (now - lastEmitAt >= emitIntervalMillis && analyzer.hasPendingSamples) {
                _levels.tryEmit(analyzer.buildLevel(System.currentTimeMillis()))
                lastEmitAt = now
            }
        }
    }

    private fun reportReadError(code: Int) {
        val error = when (code) {
            AudioRecord.ERROR_BAD_VALUE -> AudioCaptureError.UnsupportedConfiguration
            AudioRecord.ERROR_INVALID_OPERATION, AudioRecord.ERROR_DEAD_OBJECT ->
                AudioCaptureError.DeviceUnavailable
            else -> AudioCaptureError.Unknown("AudioRecord.read devolveu $code")
        }
        AppLogger.e(TAG, "Leitura do microfone interrompida (código $code)")
        loopActive = false
        _state.value = AudioCaptureState.Failed(error)
    }

    // -----------------------------------------------------------------------------------------
    // Abertura do recurso nativo
    // -----------------------------------------------------------------------------------------

    private class OpenRecorder(
        val record: AudioRecord,
        val sampleRate: Int,
        val source: AudioInputSource,
        val readFrames: Int,
    )

    /**
     * Tenta abrir o microfone na melhor combinação disponível: primeiro a fonte que menos processa,
     * depois a taxa pedida e os fallbacks. Devolve `null` só quando **nenhuma** combinação abriu.
     */
    private fun openRecorder(): OpenRecorder? {
        val sources = resolveSources()
        val rates = listOf(
            preferredSampleRate,
            AudioCaptureConfig.FALLBACK_SAMPLE_RATE,
            AudioCaptureConfig.LAST_RESORT_SAMPLE_RATE,
        ).distinct()

        for ((androidSource, source) in sources) {
            for (rate in rates) {
                val minBufferBytes = runCatching {
                    AudioRecord.getMinBufferSize(rate, CHANNEL_CONFIG, AUDIO_ENCODING)
                }.getOrDefault(AudioRecord.ERROR)

                if (minBufferBytes <= 0) {
                    // ERROR_BAD_VALUE: esta taxa não existe neste aparelho. Segue para a próxima.
                    continue
                }

                val bufferBytes = max(minBufferBytes, bytesForMillis(rate, BUFFER_MILLIS))
                val record = runCatching { buildRecord(androidSource, rate, bufferBytes) }
                    .getOrElse { error ->
                        AppLogger.w(TAG, "AudioRecord recusou $rate Hz: ${error.message}")
                        null
                    }

                if (record == null) continue
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    runCatching { record.release() }
                    continue
                }

                val readFrames = minOf(READ_CHUNK_FRAMES, max(MIN_READ_FRAMES, bufferBytes / 2))
                AppLogger.i(TAG, "Microfone aberto: ${rate}Hz, fonte $source")
                return OpenRecorder(record, rate, source, readFrames)
            }
        }
        AppLogger.e(TAG, "Nenhuma configuração de captura foi aceita pelo aparelho")
        return null
    }

    private fun buildRecord(androidSource: Int, sampleRate: Int, bufferBytes: Int): AudioRecord =
        AudioRecord.Builder()
            .setAudioSource(androidSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()

    /** Ordem de preferência das fontes de entrada, da que menos processa para a garantida. */
    private fun resolveSources(): List<Pair<Int, AudioInputSource>> {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessedSupported = runCatching {
            audioManager?.getProperty(
                AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED,
            ) == "true"
        }.getOrDefault(false)

        val sources = mutableListOf<Pair<Int, AudioInputSource>>()
        if (unprocessedSupported) {
            sources += MediaRecorder.AudioSource.UNPROCESSED to AudioInputSource.UNPROCESSED
        }
        sources += MediaRecorder.AudioSource.VOICE_RECOGNITION to AudioInputSource.VOICE_RECOGNITION
        return sources
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasMicrophone(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    private companion object {
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** Folga mínima do buffer do driver, em ms, acima do mínimo que o aparelho exige. */
        const val BUFFER_MILLIS = 50

        /** ~46 ms em 44,1 kHz: latência baixa e janela redonda para quem for analisar o sinal. */
        const val READ_CHUNK_FRAMES = 2_048

        const val MIN_READ_FRAMES = 256
        const val LEVEL_BUFFER = 8
        const val FRAME_BUFFER = 4
        const val THREAD_JOIN_TIMEOUT_MILLIS = 500L

        fun bytesForMillis(sampleRate: Int, millis: Int): Int =
            sampleRate * millis / 1_000 * 2 // 2 bytes por amostra (PCM 16-bit mono)
    }
}
