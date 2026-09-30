package br.com.codecacto.kmplib.media

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * O que cada plataforma entrega ao [AmbientSoundPlayer]: um player nativo em laço com ganho
 * ajustável. A máquina de estados e o fade são comuns ([DefaultAmbientSoundPlayer]) — o fade é uma
 * rampa de ganho em passos de [AmbientSoundDefaults.FADE_STEP_MILLIS], idêntica nas duas plataformas,
 * que pode ser **revertida no meio** (tocar durante um fade-out sobe a partir do ponto atual). O
 * `setVolume(_:fadeDuration:)` do `AVAudioPlayer` não expõe o ponto da rampa nem a cancela, e a Media3
 * não tem fade — por isso a rampa não é delegada.
 */
internal interface AmbientAudioEngine {
    /** Prepara o som; `null` = pronto. Troca o anterior. */
    suspend fun load(bytes: ByteArray): AmbientSoundError?

    fun start()
    fun pause()
    fun rewind()

    /** Ganho do player nativo, `0f..1f`. */
    fun setGain(gain: Float)

    fun release()

    /** O sistema pausou sozinho (fone desconectado, perda de foco, ligação). */
    var onSystemPause: (() -> Unit)?

    /** O sistema devolveu o áudio e indica que retomar é esperado (fim de interrupção no iOS). */
    var onSystemResumeAllowed: (() -> Unit)?
}

internal class DefaultAmbientSoundPlayer(
    private val engine: AmbientAudioEngine,
    dispatcher: CoroutineDispatcher,
) : AmbientSoundPlayer {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(AmbientSoundState())
    override val state: StateFlow<AmbientSoundState> = _state.asStateFlow()

    /** Ganho aplicado agora no player nativo (o ponto da rampa). */
    internal var gain: Float = 0f
        private set
    private var engineRunning = false
    private var pausedBySystem = false
    private var fadeJob: Job? = null
    private var released = false

    init {
        engine.onSystemPause = {
            if (!released && engineRunning) {
                fadeJob?.cancel()
                engineRunning = false
                when (_state.value.status) {
                    AmbientSoundStatus.PLAYING -> {
                        pausedBySystem = true
                        setStatus(AmbientSoundStatus.PAUSED)
                    }
                    // Um stop descendo: termina o que ele ia fazer.
                    AmbientSoundStatus.READY -> engine.rewind()
                    else -> Unit
                }
            }
        }
        engine.onSystemResumeAllowed = {
            if (!released && pausedBySystem && _state.value.status == AmbientSoundStatus.PAUSED) play()
        }
    }

    override suspend fun load(bytes: ByteArray): AmbientSoundOutcome {
        if (released) return AmbientSoundOutcome.Failure(AmbientSoundError.Released)
        if (bytes.isEmpty()) return AmbientSoundOutcome.Failure(AmbientSoundError.InvalidAudio)
        fadeJob?.cancel()
        if (engineRunning) engine.pause()
        engineRunning = false
        pausedBySystem = false
        setStatus(AmbientSoundStatus.LOADING)
        val error = try {
            engine.load(bytes)
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            AmbientSoundError.Unknown(e.message)
        }
        if (released) return AmbientSoundOutcome.Failure(AmbientSoundError.Released)
        return if (error == null) {
            setStatus(AmbientSoundStatus.READY)
            AmbientSoundOutcome.Success
        } else {
            setStatus(AmbientSoundStatus.EMPTY)
            AmbientSoundOutcome.Failure(error)
        }
    }

    override fun play(fadeInMillis: Long): AmbientSoundOutcome {
        if (released) return AmbientSoundOutcome.Failure(AmbientSoundError.Released)
        val status = _state.value.status
        if (status == AmbientSoundStatus.EMPTY || status == AmbientSoundStatus.LOADING) {
            return AmbientSoundOutcome.Failure(AmbientSoundError.NotLoaded)
        }
        pausedBySystem = false
        val target = _state.value.volume
        if (status == AmbientSoundStatus.PLAYING && fadeJob?.isActive != true && gain == target) {
            return AmbientSoundOutcome.Success
        }
        fadeJob?.cancel()
        if (!engineRunning) {
            applyGain(if (fadeInMillis > 0) 0f else target)
            engine.start()
            engineRunning = true
        }
        setStatus(AmbientSoundStatus.PLAYING)
        fadeJob = rampTo(target, fadeInMillis)
        return AmbientSoundOutcome.Success
    }

    override fun pause(fadeOutMillis: Long) = fadeOutAndThen(fadeOutMillis, rewind = false)

    override fun stop(fadeOutMillis: Long) = fadeOutAndThen(fadeOutMillis, rewind = true)

    private fun fadeOutAndThen(fadeOutMillis: Long, rewind: Boolean) {
        if (released) return
        val status = _state.value.status
        if (status != AmbientSoundStatus.PLAYING && status != AmbientSoundStatus.PAUSED) return
        pausedBySystem = false
        fadeJob?.cancel()
        if (!engineRunning) {
            // Já estava parado: só o rebobinar do stop tem efeito.
            if (rewind) {
                engine.rewind()
                setStatus(AmbientSoundStatus.READY)
            }
            return
        }
        setStatus(if (rewind) AmbientSoundStatus.READY else AmbientSoundStatus.PAUSED)
        fadeJob = rampTo(0f, fadeOutMillis) {
            engine.pause()
            engineRunning = false
            if (rewind) engine.rewind()
        }
    }

    override fun setVolume(volume: Float, fadeMillis: Long) {
        if (released) return
        val v = if (volume.isNaN()) 0f else volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = v) }
        if (_state.value.isPlaying && engineRunning) {
            fadeJob?.cancel()
            fadeJob = rampTo(v, fadeMillis)
        }
    }

    override fun release() {
        if (released) return
        released = true
        scope.cancel()
        engine.onSystemPause = null
        engine.onSystemResumeAllowed = null
        engine.release()
        engineRunning = false
        setStatus(AmbientSoundStatus.RELEASED)
    }

    private fun setStatus(status: AmbientSoundStatus) = _state.update { it.copy(status = status) }

    private fun applyGain(value: Float) {
        gain = value
        engine.setGain(value)
    }

    /** Rampa linear do ganho atual até [target] em [durationMillis]; [then] ao chegar. */
    private fun rampTo(target: Float, durationMillis: Long, then: () -> Unit = {}): Job = scope.launch {
        val from = gain
        val steps = ambientFadeSteps(durationMillis)
        for (i in 1..steps) {
            if (durationMillis > 0) delay(durationMillis / steps)
            applyGain(ambientFadeGainAt(from, target, i, steps))
        }
        then()
    }
}

/** Quantos passos a rampa dá em [durationMillis] (≥ 1; duração 0 = um passo imediato). */
internal fun ambientFadeSteps(durationMillis: Long): Int =
    if (durationMillis <= 0) 1 else (durationMillis / AmbientSoundDefaults.FADE_STEP_MILLIS).toInt().coerceAtLeast(1)

/** Ganho no passo [step] de [steps] de uma rampa linear de [from] a [to]. O último passo é exato. */
internal fun ambientFadeGainAt(from: Float, to: Float, step: Int, steps: Int): Float =
    if (step >= steps) to else from + (to - from) * step / steps
