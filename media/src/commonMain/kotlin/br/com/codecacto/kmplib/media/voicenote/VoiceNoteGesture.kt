package br.com.codecacto.kmplib.media.voicenote

/**
 * A regra do botão de microfone — **segurar** e **tocar-tocar** no mesmo botão, como nos
 * mensageiros: pura, sem Compose, testada.
 *
 * - **Segurar:** encostou, começa a gravar; soltou, envia. Arrastar para o lado (início da linha)
 *   além de [VoiceNoteGestureConfig.cancelDistancePx] cancela; arrastar para cima além de
 *   [VoiceNoteGestureConfig.lockDistancePx] **trava** (solta o dedo e segue gravando).
 * - **Tocar-tocar:** um toque curto (soltou antes de [VoiceNoteGestureConfig.tapMaxMillis] sem
 *   arrastar) **trava** a gravação em vez de enviar um áudio de meio segundo; depois, "Enviar" ou
 *   "Cancelar" na barra de gravação.
 *
 * O gravador começa no **toque** (não depois do tempo de toque longo): esperar o long-press corta a
 * primeira sílaba. Se virar tocar-tocar, a gravação já em curso continua.
 */
data class VoiceNoteGestureConfig(
    val tapMaxMillis: Long = 300L,
    val cancelDistancePx: Float = 160f,
    val lockDistancePx: Float = 120f,
)

/** Em que pé está o gesto. */
sealed interface VoiceNoteGestureState {
    data object Idle : VoiceNoteGestureState

    /**
     * Gravando com o dedo no botão. [cancelProgress] e [lockProgress] (0..1) são para a dica visual
     * ("deslize para cancelar" esmaecendo, cadeado subindo).
     */
    data class Holding(
        val startedAtMillis: Long,
        val cancelProgress: Float = 0f,
        val lockProgress: Float = 0f,
    ) : VoiceNoteGestureState

    /** Gravando sem o dedo (travado por arrasto ou por toque curto). Sai por Enviar/Cancelar. */
    data class Locked(val startedAtMillis: Long) : VoiceNoteGestureState
}

/** Eventos de entrada. [dx] negativo = rumo ao início da linha (em RTL, o chamador já inverte). */
sealed interface VoiceNoteGestureEvent {
    data class Press(val atMillis: Long) : VoiceNoteGestureEvent
    data class Drag(val dx: Float, val dy: Float) : VoiceNoteGestureEvent
    data class Release(val atMillis: Long) : VoiceNoteGestureEvent
    data object SendTapped : VoiceNoteGestureEvent
    data object CancelTapped : VoiceNoteGestureEvent

    /** O sistema tirou o gesto (outra janela, rolagem pegou o toque): trata como cancelar. */
    data object GestureLost : VoiceNoteGestureEvent
}

/** O que o gravador deve fazer em resposta. */
enum class VoiceNoteGestureEffect { NONE, START, SEND, CANCEL }

/** Resultado de [reduceVoiceNoteGesture]. */
data class VoiceNoteGestureStep(val state: VoiceNoteGestureState, val effect: VoiceNoteGestureEffect = VoiceNoteGestureEffect.NONE)

/** A máquina de estados do botão de microfone. */
fun reduceVoiceNoteGesture(
    state: VoiceNoteGestureState,
    event: VoiceNoteGestureEvent,
    config: VoiceNoteGestureConfig = VoiceNoteGestureConfig(),
): VoiceNoteGestureStep = when (state) {
    VoiceNoteGestureState.Idle -> when (event) {
        is VoiceNoteGestureEvent.Press -> VoiceNoteGestureStep(VoiceNoteGestureState.Holding(event.atMillis), VoiceNoteGestureEffect.START)
        else -> VoiceNoteGestureStep(state)
    }

    is VoiceNoteGestureState.Holding -> when (event) {
        is VoiceNoteGestureEvent.Drag -> {
            val cancelar = progresso(-event.dx, config.cancelDistancePx)
            val travar = progresso(-event.dy, config.lockDistancePx)
            when {
                cancelar >= 1f -> VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.CANCEL)
                travar >= 1f -> VoiceNoteGestureStep(VoiceNoteGestureState.Locked(state.startedAtMillis))
                else -> VoiceNoteGestureStep(state.copy(cancelProgress = cancelar, lockProgress = travar))
            }
        }
        is VoiceNoteGestureEvent.Release -> {
            val curto = event.atMillis - state.startedAtMillis < config.tapMaxMillis &&
                state.cancelProgress == 0f && state.lockProgress == 0f
            if (curto) {
                VoiceNoteGestureStep(VoiceNoteGestureState.Locked(state.startedAtMillis))
            } else {
                VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.SEND)
            }
        }
        VoiceNoteGestureEvent.CancelTapped, VoiceNoteGestureEvent.GestureLost ->
            VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.CANCEL)
        VoiceNoteGestureEvent.SendTapped -> VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.SEND)
        is VoiceNoteGestureEvent.Press -> VoiceNoteGestureStep(state)
    }

    is VoiceNoteGestureState.Locked -> when (event) {
        VoiceNoteGestureEvent.SendTapped -> VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.SEND)
        // Tocar de novo no microfone = o segundo toque do tocar-tocar: envia.
        is VoiceNoteGestureEvent.Press -> VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.SEND)
        VoiceNoteGestureEvent.CancelTapped -> VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.CANCEL)
        // Travado, o dedo já saiu: arrasto/soltura/perda de gesto não mudam nada.
        else -> VoiceNoteGestureStep(state)
    }
}

/** O gravador parou sozinho (teto): o gesto volta ao repouso e a nota é enviada. */
fun voiceNoteGestureOnLimitReached(state: VoiceNoteGestureState): VoiceNoteGestureStep =
    if (state == VoiceNoteGestureState.Idle) VoiceNoteGestureStep(state) else VoiceNoteGestureStep(VoiceNoteGestureState.Idle, VoiceNoteGestureEffect.SEND)

private fun progresso(distancia: Float, limite: Float): Float =
    if (limite <= 0f || distancia <= 0f) 0f else (distancia / limite).coerceIn(0f, 1f)
