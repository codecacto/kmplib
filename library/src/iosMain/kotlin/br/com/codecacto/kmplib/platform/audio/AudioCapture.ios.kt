@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.platform.audio

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.get
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermission
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionType
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionRecordPermission
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.setActive
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObjectProtocol

private const val TAG = "AudioCapture"

/** Cria a captura de áudio do iOS. */
actual fun createAudioCapture(config: AudioCaptureConfig): AudioCapture = IosAudioCapture(config)

/**
 * **Padrão-ouro do iOS: `AVAudioEngine` com tap no nó de entrada + `AVAudioSession` em categoria
 * `record` e modo `measurement`.**
 *
 * - **`AVAudioSessionModeMeasurement` é o ponto central.** É o modo que a Apple documenta como o
 *   que **minimiza o processamento do sistema sobre a entrada** — o par do `UNPROCESSED` do
 *   Android. Sem ele, o iOS aplica ganho e filtragem próprios, e a amplitude que chega ao nosso
 *   cálculo deixa de significar o volume do ambiente: o sistema "conserta" justamente aquilo que
 *   estamos medindo, e o número da tela vira ficção.
 * - **Por que `AVAudioEngine` e não `AVAudioRecorder`:** o `AVAudioRecorder` **grava um arquivo**
 *   e só expõe `averagePower`/`peakPower` — nada de buffer, nenhuma ponderação possível, e escrita
 *   em disco que este módulo promete não fazer. O tap do engine entrega o PCM.
 *
 * ⚠️ **O tap OBRIGATORIAMENTE usa o formato nativo do `inputNode`.** Instalar tap com um formato
 * diferente do hardware **derruba o app em runtime** com
 * `required condition is false: format.sampleRate == hwFormat.sampleRate` — não é erro de
 * compilação, não aparece em teste de unidade, e só acontece no aparelho. Por isso
 * [AudioCaptureConfig.preferredSampleRate] é **ignorado no iOS**: quem manda é
 * `inputNode.outputFormatForBus(0)`, e a taxa efetiva vai em [AudioCaptureState.Running.sampleRate]
 * e em cada [AudioLevel]. As amostras `Float32` do buffer são convertidas no cálculo, nunca na
 * instalação do tap.
 *
 * **Interrupção e mudança de rota são tratadas aqui**, e não pelo app: ele não tem como. Ligação
 * recebida ou Siri levam o estado para [AudioCaptureState.Interrupted] e a captura **volta sozinha**
 * quando o sistema sinaliza `shouldResume`; sem isso, o app volta da ligação com a tela viva e o
 * número **congelado**. Fone plugado/retirado (`RouteChange`) e
 * `AVAudioEngineConfigurationChange` reinstalam o tap — a taxa pode ter mudado junto, e com ela os
 * coeficientes da curva A, que são recalculados.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** os alvos Apple não compilam no servidor Linux
 * (`HostManager.hostIsMac`). Este arquivo entra como código pronto e revisado, **não** como
 * verificado — a compilação e o teste em dispositivo saem no Mac.
 */
internal class IosAudioCapture(config: AudioCaptureConfig) : AudioCapture {

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

    private var weighting: AudioWeighting = config.weighting
    private var timeWeighting: AudioTimeWeighting = config.timeWeighting
    private var emitIntervalMillis: Long = config.emitIntervalMillis

    private val engine = AVAudioEngine()
    private var analyzer: AudioLevelAnalyzer? = null
    private var lastEmitAtMillis: Long = 0L
    private var tapInstalled: Boolean = false
    private var observers: List<NSObjectProtocol> = emptyList()
    private var released: Boolean = false
    private var running: Boolean = false

    /** Verdadeiro quando a interrupção do sistema pausou uma captura que o app queria rodando. */
    private var interrupted: Boolean = false

    override val isAvailable: Boolean
        get() = !released && hasRecordPermission() && AVAudioSession.sharedInstance().inputAvailable

    override fun start(): Boolean {
        if (released) {
            AppLogger.w(TAG, "start() após release() — crie uma nova captura")
            return false
        }
        if (running) return true

        _state.value = AudioCaptureState.Starting

        if (!hasRecordPermission()) {
            // Sem a permissão o tap entrega silêncio digital: o app mostraria -120 dB para sempre.
            AppLogger.w(TAG, "Permissão de microfone ausente — o app precisa pedir antes")
            _state.value = AudioCaptureState.Failed(AudioCaptureError.PermissionDenied)
            return false
        }

        if (!configureSession()) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return false
        }

        val sampleRate = installTapAndStart()
        if (sampleRate == null) {
            teardownAudio()
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return false
        }

        registerObservers()
        running = true
        interrupted = false
        // O modo `measurement` é o que faz a entrada chegar sem processamento do sistema; quando
        // ele é aceito, a fonte é equivalente ao UNPROCESSED do Android.
        _state.value = AudioCaptureState.Running(AudioInputSource.UNPROCESSED, sampleRate)
        return true
    }

    override fun stop() {
        if (!running && !tapInstalled) {
            if (!released) _state.value = AudioCaptureState.Idle
            return
        }
        running = false
        interrupted = false
        unregisterObservers()
        teardownAudio()
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
        weighting?.let {
            this.weighting = it
            analyzer?.weighting = it
        }
        timeWeighting?.let {
            this.timeWeighting = it
            analyzer?.timeWeighting = it
        }
        emitIntervalMillis?.let {
            this.emitIntervalMillis = it.coerceIn(
                AudioCaptureConfig.MIN_EMIT_INTERVAL_MILLIS,
                AudioCaptureConfig.MAX_EMIT_INTERVAL_MILLIS,
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Sessão, tap e laço de amostras
    // -----------------------------------------------------------------------------------------

    private fun configureSession(): Boolean {
        val session = AVAudioSession.sharedInstance()
        return runCatching {
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            // `measurement`: mínimo de processamento do sistema sobre a entrada (ver KDoc).
            session.setMode(AVAudioSessionModeMeasurement, error = null)
            session.setActive(true, null)
            true
        }.getOrElse { error ->
            AppLogger.e(TAG, "Falha ao configurar a AVAudioSession: ${error.message}")
            false
        }
    }

    /** Instala o tap **no formato do hardware** e sobe o engine. Devolve a taxa efetiva. */
    private fun installTapAndStart(): Int? = runCatching {
        val inputNode = engine.inputNode
        val hardwareFormat = inputNode.outputFormatForBus(0uL)
        val sampleRate = hardwareFormat.sampleRate.toInt()
        if (sampleRate < AudioCaptureConfig.MIN_SAMPLE_RATE) {
            AppLogger.e(TAG, "Taxa do hardware inesperada: $sampleRate Hz")
            return@runCatching null
        }

        val levelAnalyzer = AudioLevelAnalyzer(sampleRate, weighting, timeWeighting)
        analyzer = levelAnalyzer
        lastEmitAtMillis = nowMillis()

        inputNode.installTapOnBus(
            bus = 0uL,
            bufferSize = TAP_BUFFER_FRAMES,
            format = hardwareFormat,
        ) { buffer, _ ->
            if (buffer != null) onBuffer(buffer, levelAnalyzer, sampleRate)
        }
        tapInstalled = true

        engine.prepare()
        engine.startAndReturnError(null)
        sampleRate
    }.getOrElse { error ->
        AppLogger.e(TAG, "Falha ao iniciar o AVAudioEngine: ${error.message}")
        null
    }

    private fun onBuffer(
        buffer: AVAudioPCMBuffer,
        levelAnalyzer: AudioLevelAnalyzer,
        sampleRate: Int,
    ) {
        val frameCount = buffer.frameLength.toInt()
        if (frameCount <= 0) return
        // Float32 no formato nativo do hardware; canal 0 basta — nível sonoro não tem estéreo.
        val channelData = buffer.floatChannelData ?: return
        val channel = channelData[0] ?: return

        val samples = FloatArray(frameCount) { index -> channel[index] }
        levelAnalyzer.accumulate(samples, frameCount)

        val now = nowMillis()

        if (emitFrames) {
            _frames.tryEmit(
                AudioFrame(
                    samples = ShortArray(frameCount) { index ->
                        (samples[index].toDouble() * AudioLevelAnalyzer.FULL_SCALE)
                            .coerceIn(SHORT_MIN, SHORT_MAX)
                            .toInt()
                            .toShort()
                    },
                    sampleRate = sampleRate,
                    channelCount = 1,
                    timestampMillis = now,
                ),
            )
        }

        if (now - lastEmitAtMillis >= emitIntervalMillis && levelAnalyzer.hasPendingSamples) {
            _levels.tryEmit(levelAnalyzer.buildLevel(now))
            lastEmitAtMillis = now
        }
    }

    private fun teardownAudio() {
        runCatching {
            if (engine.running) engine.stop()
            if (tapInstalled) engine.inputNode.removeTapOnBus(0uL)
        }.onFailure { AppLogger.w(TAG, "Falha ao parar o engine: ${it.message}") }
        tapInstalled = false
        analyzer = null
        // Sessão `record` esquecida ativa derruba o áudio de outros apps e mantém aceso o
        // indicador de microfone do sistema — o usuário vê o app "ouvindo" depois de sair da tela.
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
            .onFailure { AppLogger.w(TAG, "Falha ao desativar a sessão: ${it.message}") }
    }

    // -----------------------------------------------------------------------------------------
    // Interrupção, rota e reconfiguração
    // -----------------------------------------------------------------------------------------

    private fun registerObservers() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue

        val interruption = center.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = queue,
        ) { notification ->
            val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)
                ?.unsignedLongValue
            when (type) {
                AVAudioSessionInterruptionType.AVAudioSessionInterruptionTypeBegan.value ->
                    onInterruptionBegan()

                AVAudioSessionInterruptionType.AVAudioSessionInterruptionTypeEnded.value -> {
                    val options =
                        (notification?.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)
                            ?.unsignedLongValue ?: 0uL
                    val shouldResume =
                        (options and AVAudioSessionInterruptionOptionShouldResume) != 0uL
                    onInterruptionEnded(shouldResume)
                }

                else -> Unit
            }
        }

        val routeChange = center.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = queue,
        ) { _ -> restartAudio("mudança de rota de áudio") }

        val configChange = center.addObserverForName(
            name = AVAudioEngineConfigurationChangeNotification,
            `object` = null,
            queue = queue,
        ) { _ -> restartAudio("reconfiguração do AVAudioEngine") }

        observers = listOf(interruption, routeChange, configChange)
    }

    private fun unregisterObservers() {
        if (observers.isEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers = emptyList()
    }

    private fun onInterruptionBegan() {
        if (!running) return
        // O sistema JÁ parou o engine; aqui só soltamos o tap e registramos o estado.
        interrupted = true
        teardownAudio()
        _state.value = AudioCaptureState.Interrupted("Áudio interrompido pelo sistema")
    }

    private fun onInterruptionEnded(shouldResume: Boolean) {
        if (!running || !interrupted) return
        if (!shouldResume) {
            AppLogger.i(TAG, "Interrupção terminou sem shouldResume — captura segue pausada")
            return
        }
        interrupted = false
        if (!configureSession()) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return
        }
        val sampleRate = installTapAndStart()
        if (sampleRate == null) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return
        }
        _state.value = AudioCaptureState.Running(AudioInputSource.UNPROCESSED, sampleRate)
    }

    /**
     * Fone plugado/retirado ou engine reconfigurado: a taxa do hardware pode ter mudado, e com ela
     * os coeficientes da curva A. Reinstalar é mais barato (e mais correto) que continuar medindo
     * com um filtro calibrado para outra taxa.
     */
    private fun restartAudio(reason: String) {
        if (!running || interrupted || released) return
        AppLogger.i(TAG, "Reiniciando a captura: $reason")
        teardownAudio()
        if (!configureSession()) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return
        }
        val sampleRate = installTapAndStart()
        if (sampleRate == null) {
            _state.value = AudioCaptureState.Failed(AudioCaptureError.DeviceUnavailable)
            return
        }
        _state.value = AudioCaptureState.Running(AudioInputSource.UNPROCESSED, sampleRate)
    }

    // -----------------------------------------------------------------------------------------
    // Permissão
    // -----------------------------------------------------------------------------------------

    /**
     * Consulta (nunca pede) a permissão de microfone. `AVAudioApplication` é a API do iOS 17+;
     * antes disso a mesma informação vive em `AVAudioSession.recordPermission`.
     *
     * O app precisa declarar `NSMicrophoneUsageDescription` no `Info.plist` — **o texto é de cada
     * app**, não da lib. Sem a chave, o iOS derruba o processo no instante em que a permissão é
     * pedida.
     */
    private fun hasRecordPermission(): Boolean = if (isIos17OrLater) {
        AVAudioApplication.sharedInstance().recordPermission ==
            AVAudioApplicationRecordPermission.AVAudioApplicationRecordPermissionGranted
    } else {
        AVAudioSession.sharedInstance().recordPermission ==
            AVAudioSessionRecordPermission.AVAudioSessionRecordPermissionGranted
    }

    private val isIos17OrLater: Boolean by lazy {
        NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 17 }
    }

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

    private companion object {
        /** ~46 ms em 44,1 kHz — mesma granularidade do laço do Android. */
        const val TAP_BUFFER_FRAMES: UInt = 2_048u

        const val LEVEL_BUFFER = 8
        const val FRAME_BUFFER = 4

        const val SHORT_MIN = -32_768.0
        const val SHORT_MAX = 32_767.0
    }
}
