package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import kotlinx.datetime.Instant

val T0: Instant = Instant.fromEpochSeconds(1_700_000_000)

fun repsSet(reps: Int = 10, load: Double? = 20.0): SetTarget.Reps = SetTarget.Reps(reps, load)

/** Plano simples: 1 bloco NORMAL, 1 exercício, 2 séries, 30s de descanso. */
fun simplePlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-1",
    name = "Treino A",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.NORMAL,
            exercises = listOf(
                ExerciseStep(
                    id = "ex-1",
                    exerciseRefId = "squat",
                    name = "Agachamento",
                    sets = listOf(repsSet(), repsSet()),
                    restSeconds = 30,
                ),
            ),
        ),
    ),
)

/** Plano com 2 exercícios no mesmo bloco NORMAL (2º exercício avalia a navegação entre exercícios). */
fun twoExercisePlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-2",
    name = "Treino B",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.NORMAL,
            exercises = listOf(
                ExerciseStep("ex-1", "squat", "Agachamento", listOf(repsSet()), restSeconds = 30),
                ExerciseStep("ex-2", "lunge", "Avanço", listOf(repsSet()), restSeconds = 45),
            ),
        ),
    ),
)

/** Bi-set: 2 exercícios no mesmo bloco, sem descanso entre eles; descanso só ao fechar o bloco. */
fun biSetPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-biset",
    name = "Treino Bi-set",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.BI_SET,
            exercises = listOf(
                ExerciseStep("ex-1", "curl", "Rosca direta", listOf(repsSet()), restSeconds = 60),
                ExerciseStep("ex-2", "triceps", "Tríceps corda", listOf(repsSet()), restSeconds = 60),
            ),
        ),
    ),
)

/** Drop-set: 1 exercício, 3 séries, sem descanso entre elas. */
fun dropSetPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-dropset",
    name = "Treino Drop-set",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.DROP_SET,
            exercises = listOf(
                ExerciseStep("ex-1", "leg-press", "Leg press", listOf(repsSet(), repsSet(), repsSet()), restSeconds = 90),
            ),
        ),
    ),
)
