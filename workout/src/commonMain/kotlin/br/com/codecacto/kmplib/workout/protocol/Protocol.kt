package br.com.codecacto.kmplib.workout.protocol

import br.com.codecacto.kmplib.workout.engine.Cursor
import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import kotlinx.serialization.Serializable

/**
 * Versão do conteúdo das mensagens. **2** (kmplib 2.266.0): `StateSnapshot.stageIndex` (drop-set) e
 * `SetTarget.Reps.stages` dentro do `PlanSnapshot`. Quem recebe `v` maior do que conhece deve pedir um
 * `PlanSnapshot` novo em vez de adivinhar o formato.
 */
const val WORKOUT_PROTOCOL_VERSION: Int = 2

/**
 * Mensagens celular<->relógio, versionadas (`v`) e idempotentes por `seq` crescente (resistem a
 * mensagem duplicada ou fora de ordem — o transporte real é nativo: WatchConnectivity/espelhamento
 * no watchOS, Data Layer no Wear OS; aqui só o CONTEÚDO). Regra: **o dono do estado é quem iniciou a
 * sessão**; o outro lado manda `Command` e recebe `StateSnapshot`/`Ack`.
 */
sealed interface WorkoutMessage {
    val v: Int
    val seq: Long
}

@Serializable
data class PlanSnapshot(
    override val seq: Long,
    val plan: WorkoutPlan,
    override val v: Int = WORKOUT_PROTOCOL_VERSION,
) : WorkoutMessage

/** Serialização textual compacta do estado, para não exigir que o outro lado conheça as classes
 * seladas de `GuidedWorkoutEngine` — a máquina de estados fica só de um lado da ponte. Os índices são
 * os do `Cursor` (em bi-set/circuito a volta é o `setIndex`; no drop-set o estágio é o `stageIndex`). */
@Serializable
data class StateSnapshot(
    override val seq: Long,
    val blockIndex: Int,
    val exerciseIndex: Int,
    val setIndex: Int,
    val phase: Phase,
    val restEndsAtEpochMillis: Long? = null,
    override val v: Int = WORKOUT_PROTOCOL_VERSION,
    val stageIndex: Int = 0,
) : WorkoutMessage {
    /** O cursor que este instantâneo descreve. */
    val cursor: Cursor get() = Cursor(blockIndex, exerciseIndex, setIndex, stageIndex)

    @Serializable
    enum class Phase { IN_SET, RESTING, PAUSED, FINISHED }
}

@Serializable
data class Command(
    override val seq: Long,
    val id: String,
    val event: CommandEvent,
    override val v: Int = WORKOUT_PROTOCOL_VERSION,
) : WorkoutMessage

/** Subconjunto serializável de `WorkoutEvent` — só os eventos que fazem sentido vir do OUTRO lado
 * (nem todo `WorkoutEvent` cruza a ponte: `Start` é sempre local a quem tem o plano). */
@Serializable
sealed interface CommandEvent {
    @Serializable
    data class CompleteSet(val reps: Int?, val load: Double?) : CommandEvent {
        /** Sem carga nem repetições (dado de saúde), 2.273.0. Serialização não muda. */
        override fun toString(): String = "CompleteSet(hasReps=${reps != null}, hasLoad=${load != null})"
    }

    @Serializable
    data object SkipRest : CommandEvent

    @Serializable
    data class AddRestTime(val seconds: Int) : CommandEvent

    @Serializable
    data class SkipExercise(val reason: String? = null) : CommandEvent {
        /** Sem o [reason] (texto livre do aluno), 2.273.0. */
        override fun toString(): String = "SkipExercise(hasReason=${reason != null})"
    }

    @Serializable
    data object Undo : CommandEvent

    @Serializable
    data object Pause : CommandEvent

    @Serializable
    data object Resume : CommandEvent

    @Serializable
    data object Finish : CommandEvent
}

fun CommandEvent.toWorkoutEvent(): WorkoutEvent = when (this) {
    is CommandEvent.CompleteSet -> WorkoutEvent.CompleteSet(reps, load)
    CommandEvent.SkipRest -> WorkoutEvent.SkipRest
    is CommandEvent.AddRestTime -> WorkoutEvent.AddRestTime(seconds)
    is CommandEvent.SkipExercise -> WorkoutEvent.SkipExercise(reason)
    CommandEvent.Undo -> WorkoutEvent.Undo
    CommandEvent.Pause -> WorkoutEvent.Pause
    CommandEvent.Resume -> WorkoutEvent.Resume
    CommandEvent.Finish -> WorkoutEvent.Finish
}

@Serializable
data class Ack(override val seq: Long, val commandId: String, override val v: Int = WORKOUT_PROTOCOL_VERSION) : WorkoutMessage

@Serializable
data class Metrics(
    override val seq: Long,
    val heartRate: Int? = null,
    val kcal: Double? = null,
    val epochMillis: Long,
    override val v: Int = WORKOUT_PROTOCOL_VERSION,
) : WorkoutMessage {
    /**
     * Sem FC, kcal nem o instante (dado de saúde + rastro da rotina), 2.273.0 — só `seq`, versão e
     * QUAIS leituras vieram. Serialização, `equals` e `hashCode` não mudam.
     */
    override fun toString(): String =
        "Metrics(seq=$seq, v=$v, hasHeartRate=${heartRate != null}, hasKcal=${kcal != null})"
}

/** Deduplica e ordena mensagens pelo `seq`, descartando duplicata e mensagem já vista fora de
 * ordem — o canal nativo (WatchConnectivity/Data Layer) não garante nem uma coisa nem outra. */
class SequenceGuard {
    private var lastSeq: Long = -1

    /** `true` quando a mensagem é nova e deve ser aplicada; `false` quando é duplicada/atrasada. */
    fun accept(seq: Long): Boolean {
        if (seq <= lastSeq) return false
        lastSeq = seq
        return true
    }
}
