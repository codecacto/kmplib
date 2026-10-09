package br.com.codecacto.kmplib.workout.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Como as séries de um bloco se sucedem. */
@Serializable
enum class WorkoutMethod { NORMAL, BI_SET, DROP_SET, CIRCUIT }

/** Tipo de meta de uma série: repetições ou tempo (isometria/prancha). */
@Serializable
sealed interface SetTarget {
    @Serializable
    data class Reps(val reps: Int, val load: Double?) : SetTarget

    @Serializable
    data class Timed(val seconds: Int) : SetTarget
}

/** Um exercício dentro de um bloco, com a meta de cada série e o descanso depois dela. */
@Serializable
data class ExerciseStep(
    val id: String,
    val exerciseRefId: String,
    val name: String,
    val sets: List<SetTarget>,
    val restSeconds: Int,
    val videoRequested: Boolean = false,
)

/**
 * Um bloco de exercícios. Em `NORMAL` tem um único exercício; em `BI_SET`/`DROP_SET`/`CIRCUIT`,
 * os exercícios do bloco se sucedem sem o descanso entre eles ser o normal (ver `GuidedWorkoutEngine`).
 */
@Serializable
data class Block(
    val id: String,
    val method: WorkoutMethod,
    val exercises: List<ExerciseStep>,
)

/** Instantâneo imutável do treino prescrito — o que o aluno baixa e executa, mesmo offline. */
@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String,
    val blocks: List<Block>,
)

/** O que de fato aconteceu numa série. */
@Serializable
data class SetResult(
    val blockId: String,
    val exerciseStepId: String,
    val setIndex: Int,
    val target: SetTarget,
    val repsDone: Int?,
    val loadDone: Double?,
    val skipped: Boolean = false,
    val startedAt: Instant,
    val completedAt: Instant,
    val heartRateAvg: Int? = null,
    val heartRateMax: Int? = null,
)

/** Ocorrência de "pular exercício", com o motivo informado pelo aluno (visto pelo personal). */
@Serializable
data class SkippedExercise(
    val exerciseStepId: String,
    val reason: String?,
    val at: Instant,
)

/** O que de fato aconteceu na sessão — a gravação completa, para sincronizar com o backend. */
@Serializable
data class WorkoutRun(
    val localId: String,
    val planId: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val sets: List<SetResult> = emptyList(),
    val skippedExercises: List<SkippedExercise> = emptyList(),
    val effort: Int? = null,
    val comment: String? = null,
)
