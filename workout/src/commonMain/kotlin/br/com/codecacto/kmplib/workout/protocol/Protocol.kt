package br.com.codecacto.kmplib.workout.protocol

import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import kotlinx.serialization.Serializable

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
    override val v: Int = 1,
) : WorkoutMessage

/** Serialização textual compacta do estado, para não exigir que o outro lado conheça as classes
 * seladas de `GuidedWorkoutEngine` — a máquina de estados fica só de um lado da ponte. */
@Serializable
data class StateSnapshot(
    override val seq: Long,
    val blockIndex: Int,
    val exerciseIndex: Int,
    val setIndex: Int,
    val phase: Phase,
    val restEndsAtEpochMillis: Long? = null,
    override val v: Int = 1,
) : WorkoutMessage {
    @Serializable
    enum class Phase { IN_SET, RESTING, PAUSED, FINISHED }
}

@Serializable
data class Command(
    override val seq: Long,
    val id: String,
    val event: CommandEvent,
    override val v: Int = 1,
) : WorkoutMessage

/** Subconjunto serializável de `WorkoutEvent` — só os eventos que fazem sentido vir do OUTRO lado
 * (nem todo `WorkoutEvent` cruza a ponte: `Start` é sempre local a quem tem o plano). */
@Serializable
sealed interface CommandEvent {
    @Serializable
    data class CompleteSet(val reps: Int?, val load: Double?) : CommandEvent

    @Serializable
    data object SkipRest : CommandEvent

    @Serializable
    data class AddRestTime(val seconds: Int) : CommandEvent

    @Serializable
    data class SkipExercise(val reason: String? = null) : CommandEvent

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
data class Ack(override val seq: Long, val commandId: String, override val v: Int = 1) : WorkoutMessage

@Serializable
data class Metrics(
    override val seq: Long,
    val heartRate: Int? = null,
    val kcal: Double? = null,
    val epochMillis: Long,
    override val v: Int = 1,
) : WorkoutMessage

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
