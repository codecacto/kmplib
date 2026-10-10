package br.com.codecacto.kmplib.workout.metrics

import br.com.codecacto.kmplib.workout.engine.ENGINE_TRANSITION_CASES
import br.com.codecacto.kmplib.workout.engine.GuidedWorkoutEngine
import br.com.codecacto.kmplib.workout.engine.T0
import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.engine.WorkoutState
import br.com.codecacto.kmplib.workout.engine.biSetPlan
import br.com.codecacto.kmplib.workout.engine.circuitPlan
import br.com.codecacto.kmplib.workout.engine.circuitShortLastPlan
import br.com.codecacto.kmplib.workout.engine.dropSetPlan
import br.com.codecacto.kmplib.workout.engine.dropSetWithoutStagesPlan
import br.com.codecacto.kmplib.workout.engine.instant
import br.com.codecacto.kmplib.workout.engine.simplePlan
import br.com.codecacto.kmplib.workout.engine.timedPlan
import br.com.codecacto.kmplib.workout.engine.toWorkoutEvent
import br.com.codecacto.kmplib.workout.engine.twoExercisePlan
import br.com.codecacto.kmplib.workout.model.ExerciseSwap
import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.SkippedExercise
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/**
 * Tabela sessão -> veredito do `validateRun`: a régua do backend (A5, `INVALID_SESSION`) em DADOS.
 * Exportada para `workout/fixtures/session-validation.json` (`TransitionFixtureExportTest`) e
 * publicada no artefato `kmplib-workout-fixtures`, de onde o teste do backend roda os MESMOS casos
 * com o JAR JVM — o que garante que o servidor não recuse a sessão que o celular produz.
 *
 * Duas famílias:
 * - **motor**: a sessão que o [GuidedWorkoutEngine] produz em cada caso da tabela evento -> estado e
 *   em cada plano percorrido até o fim. A expectativa (lista vazia) é o invariante "o motor nunca
 *   produz sessão inválida", escrito à mão;
 * - **à mão**: sessões montadas passo a passo, com o veredito exato (ordem incluída) escrito à mão.
 *
 * O formato de cada caso usa só tipos do artefato principal (`WorkoutPlan`, `WorkoutRun`,
 * `RunIssue`) — o backend não precisa de nada desta tabela além do JSON.
 */
const val SESSION_VALIDATION_TABLE_VERSION: Int = 1

@Serializable
data class SessionValidationTable(
    val version: Int,
    val protocolVersion: Int,
    val cases: List<SessionValidationCase>,
)

@Serializable
data class SessionValidationCase(
    val name: String,
    val plan: WorkoutPlan,
    val run: WorkoutRun,
    val expectedIssues: List<RunIssue>,
)

private val engine = GuidedWorkoutEngine()

/** Percorre o plano inteiro: conclui cada passo 5 s depois do anterior e pula todo descanso. */
fun runToEnd(plan: WorkoutPlan): WorkoutRun {
    var state: WorkoutState = engine.start(plan, T0, "run-${plan.id}")
    var now = T0
    while (state !is WorkoutState.Finished) {
        now += 5.seconds
        state = when (state) {
            is WorkoutState.Resting -> engine.reduce(state, WorkoutEvent.SkipRest, now)
            else -> engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), now)
        }
    }
    return state.run
}

private fun WorkoutState.runOrNull(): WorkoutRun? = when (this) {
    is WorkoutState.InSet -> run
    is WorkoutState.Resting -> run
    is WorkoutState.Paused -> run
    is WorkoutState.Finished -> run
    WorkoutState.Idle, is WorkoutState.Ready -> null
}

private fun engineCases(): List<SessionValidationCase> {
    val fromTable = ENGINE_TRANSITION_CASES.mapNotNull { case ->
        var state: WorkoutState = WorkoutState.Idle
        case.steps.forEach { step -> state = engine.reduce(state, step.event.toWorkoutEvent(case), step.instant()) }
        state.runOrNull()?.let { SessionValidationCase("motor (tabela): ${case.name}", case.plan, it, emptyList()) }
    }
    val complete = listOf(
        simplePlan(), twoExercisePlan(), biSetPlan(), circuitPlan(), circuitShortLastPlan(), dropSetPlan(),
        dropSetWithoutStagesPlan(), timedPlan(),
    ).map { plan -> SessionValidationCase("motor (completo): ${plan.id}", plan, runToEnd(plan), emptyList()) }
    return fromTable + complete
}

private fun result(
    plan: WorkoutPlan,
    blockId: String,
    exerciseId: String,
    setIndex: Int,
    stageIndex: Int? = null,
    roundIndex: Int? = null,
    startS: Long = 0,
    endS: Long = 30,
    durationSeconds: Int? = null,
    exerciseRefId: String? = null,
    swappedFrom: String? = null,
): SetResult {
    val target = plan.blocks.flatMap { it.exercises }.firstOrNull { it.id == exerciseId }
        ?.sets?.getOrNull(setIndex) ?: simplePlan().blocks[0].exercises[0].sets[0]
    return SetResult(
        blockId = blockId,
        exerciseStepId = exerciseId,
        setIndex = setIndex,
        target = target,
        repsDone = 10,
        loadDone = 20.0,
        startedAt = T0 + startS.seconds,
        completedAt = T0 + endS.seconds,
        stageIndex = stageIndex,
        roundIndex = roundIndex,
        durationSeconds = durationSeconds,
        exerciseRefId = exerciseRefId,
        swappedFromExerciseId = swappedFrom,
    )
}

private fun session(
    plan: WorkoutPlan,
    vararg sets: SetResult,
    planId: String = plan.id,
    skipped: List<String> = emptyList(),
    swaps: List<ExerciseSwap> = emptyList(),
) =
    WorkoutRun(
        localId = "run-hand",
        planId = planId,
        startedAt = T0,
        finishedAt = T0 + 600.seconds,
        sets = sets.toList(),
        skippedExercises = skipped.map { SkippedExercise(it, null, T0) },
        swaps = swaps,
    )

private fun handCases(): List<SessionValidationCase> {
    val simple = simplePlan()
    val biSet = biSetPlan()
    val drop = dropSetPlan()
    val timed = timedPlan()
    fun case(name: String, plan: WorkoutPlan, run: WorkoutRun, vararg issues: RunIssue) =
        SessionValidationCase("à mão: $name", plan, run, issues.toList())

    return listOf(
        case("sessão vazia é válida", simple, session(simple)),
        case(
            "parcial e fora de ordem é válida (Undo, treino retroativo)",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 1), result(simple, "block-1", "ex-1", 0)),
        ),
        case(
            "bi-set por volta, com roundIndex = setIndex",
            biSet,
            session(
                biSet,
                result(biSet, "block-1", "ex-1", 0, roundIndex = 0),
                result(biSet, "block-1", "ex-2", 0, roundIndex = 0),
                result(biSet, "block-1", "ex-1", 1, roundIndex = 1),
            ),
        ),
        case(
            "drop-set com os três estágios da série",
            drop,
            session(
                drop,
                result(drop, "block-1", "ex-1", 0, stageIndex = 0),
                result(drop, "block-1", "ex-1", 0, stageIndex = 1),
                result(drop, "block-1", "ex-1", 0, stageIndex = 2),
                result(drop, "block-2", "ex-2", 0),
            ),
        ),
        case(
            "plano trocado",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0), planId = "plan-outro"),
            RunIssue.PlanMismatch("plan-1", "plan-outro"),
        ),
        case(
            "a mesma série duas vezes",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0), result(simple, "block-1", "ex-1", 1), result(simple, "block-1", "ex-1", 0)),
            RunIssue.DuplicateStep(2),
        ),
        case("série além do plano", simple, session(simple, result(simple, "block-1", "ex-1", 2)), RunIssue.UnknownStep(0)),
        case("exercício que não está no plano", simple, session(simple, result(simple, "block-1", "ex-9", 0)), RunIssue.UnknownStep(0)),
        case("bloco que não está no plano", simple, session(simple, result(simple, "block-9", "ex-1", 0)), RunIssue.UnknownStep(0)),
        case(
            "drop-set sem informar o estágio",
            drop,
            session(drop, result(drop, "block-1", "ex-1", 0)),
            RunIssue.UnknownStep(0),
        ),
        case(
            "drop-set com estágio além dos três",
            drop,
            session(drop, result(drop, "block-1", "ex-1", 0, stageIndex = 3)),
            RunIssue.UnknownStep(0),
        ),
        case(
            "estágio em bloco normal",
            drop,
            session(drop, result(drop, "block-2", "ex-2", 0, stageIndex = 0)),
            RunIssue.UnknownStep(0),
        ),
        case("volta em bloco normal", simple, session(simple, result(simple, "block-1", "ex-1", 0, roundIndex = 0)), RunIssue.UnknownStep(0)),
        case("bi-set sem informar a volta", biSet, session(biSet, result(biSet, "block-1", "ex-1", 0)), RunIssue.UnknownStep(0)),
        case(
            "bi-set com a volta diferente da série",
            biSet,
            session(biSet, result(biSet, "block-1", "ex-1", 0, roundIndex = 2)),
            RunIssue.UnknownStep(0),
        ),
        case(
            "série que termina antes de começar",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0, startS = 30, endS = 29)),
            RunIssue.NegativeDuration(0),
        ),
        case(
            "repetida E com duração negativa: os dois apontam a mesma linha",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0), result(simple, "block-1", "ex-1", 0, startS = 30, endS = 10)),
            RunIssue.DuplicateStep(1),
            RunIssue.NegativeDuration(1),
        ),
        case(
            "pulo de exercício que não está no plano (o que está, passa)",
            simple,
            session(simple, skipped = listOf("ex-1", "fantasma")),
            RunIssue.UnknownSkippedExercise("fantasma"),
        ),
        case(
            "tudo junto, na ordem do validateRun: plano, linha a linha, pulos",
            simple,
            session(
                simple,
                result(simple, "block-1", "ex-1", 5),
                result(simple, "block-1", "ex-1", 0, startS = 9, endS = 8),
                planId = "x",
                skipped = listOf("y"),
            ),
            RunIssue.PlanMismatch("plan-1", "x"),
            RunIssue.UnknownStep(0),
            RunIssue.NegativeDuration(1),
            RunIssue.UnknownSkippedExercise("y"),
        ),
        // ── 2.275.0: duração informada (série por tempo) ──
        case(
            "série por tempo com a duração informada (1 s e 3600 s, os limites)",
            timed,
            session(
                timed,
                result(timed, "block-1", "ex-t", 0, endS = 70, durationSeconds = 1),
                result(timed, "block-1", "ex-t", 1, startS = 80, endS = 130, durationSeconds = 3600),
            ),
        ),
        case(
            "duração informada em série por repetições",
            timed,
            session(timed, result(timed, "block-1", "ex-r", 0, durationSeconds = 30)),
            RunIssue.InvalidDuration(0),
        ),
        case("duração informada zero", timed, session(timed, result(timed, "block-1", "ex-t", 0, durationSeconds = 0)), RunIssue.InvalidDuration(0)),
        case(
            "duração informada acima de 1 hora",
            timed,
            session(timed, result(timed, "block-1", "ex-t", 0, durationSeconds = 3601)),
            RunIssue.InvalidDuration(0),
        ),
        // ── 2.275.0: troca de exercício ──
        case(
            "troca declarada: o item feito com outro exercício, a partir da 2ª série",
            simple,
            session(
                simple,
                result(simple, "block-1", "ex-1", 0),
                result(simple, "block-1", "ex-1", 1, exerciseRefId = "leg-press", swappedFrom = "squat"),
                swaps = listOf(ExerciseSwap("ex-1", "squat", "leg-press", null, T0, 1)),
            ),
        ),
        case(
            "troca para o mesmo exercício",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0, exerciseRefId = "squat", swappedFrom = "squat")),
            RunIssue.InvalidSwap(0),
        ),
        case(
            "troca cujo original não é o exercício do item",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0, exerciseRefId = "leg-press", swappedFrom = "hack")),
            RunIssue.InvalidSwap(0),
        ),
        case(
            "troca sem dizer qual exercício foi feito",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0, swappedFrom = "squat")),
            RunIssue.InvalidSwap(0),
        ),
        case(
            "exercício diferente do plano sem declarar a troca",
            simple,
            session(simple, result(simple, "block-1", "ex-1", 0, exerciseRefId = "leg-press")),
            RunIssue.InvalidSwap(0),
        ),
        case(
            "registro de troca de item fora do plano e de troca para o mesmo",
            simple,
            session(
                simple,
                swaps = listOf(
                    ExerciseSwap("ex-9", "x", "y", null, T0, 0),
                    ExerciseSwap("ex-1", "squat", "squat", null, T0, 0),
                ),
            ),
            RunIssue.InvalidSwapRecord(0),
            RunIssue.InvalidSwapRecord(1),
        ),
    )
}

/** Os casos. Ao acrescentar um, rode `TransitionFixtureExportTest` com a atualização ligada. */
val SESSION_VALIDATION_CASES: List<SessionValidationCase> = engineCases() + handCases()
