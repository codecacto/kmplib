package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.DropStage
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

/** Bi-set de 3 voltas: rosca (rest 60) + tríceps (rest 90). Descanso só depois do tríceps, de 90 s. */
fun biSetPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-biset",
    name = "Treino Bi-set",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.BI_SET,
            exercises = listOf(
                ExerciseStep("ex-1", "curl", "Rosca direta", List(3) { repsSet(10, 20.0) }, restSeconds = 60),
                ExerciseStep("ex-2", "triceps", "Tríceps corda", List(3) { repsSet(12, 15.0) }, restSeconds = 90),
            ),
        ),
    ),
)

/**
 * Circuito com exercício de MENOS séries (o 2º, só 2) e um bloco NORMAL depois, para provar o descanso
 * entre blocos: voltas 0 e 1 = A, B, C (descansa 60, o do C); volta 2 = A, C.
 */
fun circuitPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-circuit",
    name = "Treino Circuito",
    blocks = listOf(
        Block(
            id = "block-c",
            method = WorkoutMethod.CIRCUIT,
            exercises = listOf(
                ExerciseStep("c-a", "jump", "Polichinelo", List(3) { SetTarget.Timed(30) }, restSeconds = 30),
                ExerciseStep("c-b", "pushup", "Flexão", List(2) { repsSet(10, null) }, restSeconds = 45),
                ExerciseStep("c-c", "plank", "Prancha", List(3) { SetTarget.Timed(40) }, restSeconds = 60),
            ),
        ),
        Block(
            id = "block-n",
            method = WorkoutMethod.NORMAL,
            exercises = listOf(ExerciseStep("n-1", "squat", "Agachamento", listOf(repsSet()), restSeconds = 30)),
        ),
    ),
)

/** Circuito em que o ÚLTIMO exercício tem menos séries: a volta 2 termina no A, que descansa o dele. */
fun circuitShortLastPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-circuit-short",
    name = "Circuito curto",
    blocks = listOf(
        Block(
            id = "block-c",
            method = WorkoutMethod.CIRCUIT,
            exercises = listOf(
                ExerciseStep("c-a", "row", "Remada", List(3) { repsSet() }, restSeconds = 30),
                ExerciseStep("c-b", "pullover", "Pullover", List(2) { repsSet() }, restSeconds = 45),
            ),
        ),
        Block(
            id = "block-n",
            method = WorkoutMethod.NORMAL,
            exercises = listOf(ExerciseStep("n-1", "squat", "Agachamento", listOf(repsSet()), restSeconds = 30)),
        ),
    ),
)

val DROP_STAGES: List<DropStage> = listOf(DropStage(12, 40.0), DropStage(10, 32.0), DropStage(8, 25.0))

/** Drop-set: 1 exercício, 2 séries de 3 estágios cada, 90 s de descanso depois do último estágio. */
fun dropSetPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-dropset",
    name = "Treino Drop-set",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.DROP_SET,
            exercises = listOf(
                ExerciseStep(
                    "ex-1", "leg-curl", "Mesa flexora",
                    List(2) { SetTarget.Reps.dropSet(DROP_STAGES) },
                    restSeconds = 90,
                ),
            ),
        ),
        Block(
            id = "block-2",
            method = WorkoutMethod.NORMAL,
            exercises = listOf(ExerciseStep("ex-2", "calf", "Panturrilha", listOf(repsSet()), restSeconds = 30)),
        ),
    ),
)

/** Drop-set cujas séries vieram SEM estágios: cada série é um estágio só e descansa depois dela. */
fun dropSetWithoutStagesPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-dropset-flat",
    name = "Drop-set sem estágios",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.DROP_SET,
            exercises = listOf(ExerciseStep("ex-1", "leg-press", "Leg press", List(2) { repsSet() }, restSeconds = 90)),
        ),
    ),
)

/**
 * Série por tempo (2.275.0): prancha 2 x 45 s (rest 30) e depois agachamento 1 x reps (rest 30) — para
 * a duração informada (`CompleteSet.durationSeconds`) valer só onde a série é `Timed`.
 */
fun timedPlan(): WorkoutPlan = WorkoutPlan(
    id = "plan-timed",
    name = "Treino por tempo",
    blocks = listOf(
        Block(
            id = "block-1",
            method = WorkoutMethod.NORMAL,
            exercises = listOf(
                ExerciseStep("ex-t", "plank", "Prancha", List(2) { SetTarget.Timed(45) }, restSeconds = 30),
                ExerciseStep("ex-r", "squat", "Agachamento", listOf(repsSet()), restSeconds = 30),
            ),
        ),
    ),
)
