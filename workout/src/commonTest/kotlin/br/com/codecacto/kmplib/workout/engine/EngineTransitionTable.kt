package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/**
 * Tabela evento -> estado esperado da máquina do treino guiado, em DADOS. É a especificação
 * executável da máquina: roda aqui em todo alvo (`EngineTransitionTableTest`) e é exportada para
 * `workout/fixtures/engine-transitions.json` (`TransitionFixtureExportTest`, jvm), de onde outra
 * implementação — o app Connect IQ em Monkey C (RF-REL-18) — roda os MESMOS casos.
 *
 * Cada caso parte de `Idle`; cada passo aplica um evento no instante `T0 + atSeconds` e confere o
 * estado projetado. As expectativas são escritas à mão (nunca geradas pelo motor), senão a tabela
 * só provaria que o motor concorda consigo mesmo.
 *
 * Formato (versão [TRANSITION_TABLE_VERSION]):
 * - `cursor` = `[bloco, exercício, série, estágio]` (em bi-set/circuito a série É a volta);
 * - tempos em segundos desde [TransitionTable.epochBaseSeconds];
 * - campo que não se aplica à fase vem `null` (ex.: `restEndsAtSeconds` fora de `RESTING`);
 * - `completeSet.durationSeconds` (2.275.0, opcional) = o cronômetro da série por tempo;
 * - `swapExercise` (2.275.0) = troca o exercício do item `stepId` por `toExerciseId`; `swaps` no estado
 *   esperado = as trocas ativas, `"<stepId>-><toExerciseId>"`, na ordem.
 */
const val TRANSITION_TABLE_VERSION: Int = 1

@Serializable
data class TransitionTable(
    val version: Int,
    val protocolVersion: Int,
    val epochBaseSeconds: Long,
    val cases: List<TransitionCase>,
)

@Serializable
data class TransitionCase(
    val name: String,
    val plan: WorkoutPlan,
    val localRunId: String = "run-1",
    val steps: List<TransitionStep>,
)

@Serializable
data class TransitionStep(val atSeconds: Long, val event: TableEvent, val expect: ExpectedState)

@Serializable
sealed interface TableEvent {
    /** Carrega o `plan` do caso, com o `localRunId` do caso. */
    @Serializable @SerialName("load")
    data object Load : TableEvent

    @Serializable @SerialName("start")
    data object Start : TableEvent

    @Serializable @SerialName("completeSet")
    data class CompleteSet(val reps: Int?, val load: Double?, val durationSeconds: Int? = null) : TableEvent

    @Serializable @SerialName("skipRest")
    data object SkipRest : TableEvent

    @Serializable @SerialName("addRestTime")
    data class AddRestTime(val seconds: Int) : TableEvent

    @Serializable @SerialName("skipExercise")
    data class SkipExercise(val reason: String? = null) : TableEvent

    @Serializable @SerialName("swapExercise")
    data class SwapExercise(val stepId: String, val toExerciseId: String, val reason: String? = null) : TableEvent

    @Serializable @SerialName("undo")
    data object Undo : TableEvent

    @Serializable @SerialName("pause")
    data object Pause : TableEvent

    @Serializable @SerialName("resume")
    data object Resume : TableEvent

    @Serializable @SerialName("finish")
    data object Finish : TableEvent
}

@Serializable
enum class TablePhase { IDLE, READY, IN_SET, RESTING, PAUSED, FINISHED }

@Serializable
data class ExpectedState(
    val phase: TablePhase,
    val cursor: List<Int>? = null,
    val setStartedAtSeconds: Long? = null,
    val restEndsAtSeconds: Long? = null,
    val finishedAtSeconds: Long? = null,
    val recordedSteps: Int? = null,
    val skippedExercises: List<String> = emptyList(),
    val swaps: List<String> = emptyList(),
)

val TABLE_T0: Instant = T0

fun TableEvent.toWorkoutEvent(case: TransitionCase): WorkoutEvent = when (this) {
    TableEvent.Load -> WorkoutEvent.Load(case.plan, case.localRunId)
    TableEvent.Start -> WorkoutEvent.Start
    is TableEvent.CompleteSet -> WorkoutEvent.CompleteSet(reps, load, durationSeconds = durationSeconds)
    is TableEvent.SwapExercise -> WorkoutEvent.SwapExercise(stepId, toExerciseId, reason)
    TableEvent.SkipRest -> WorkoutEvent.SkipRest
    is TableEvent.AddRestTime -> WorkoutEvent.AddRestTime(seconds)
    is TableEvent.SkipExercise -> WorkoutEvent.SkipExercise(reason)
    TableEvent.Undo -> WorkoutEvent.Undo
    TableEvent.Pause -> WorkoutEvent.Pause
    TableEvent.Resume -> WorkoutEvent.Resume
    TableEvent.Finish -> WorkoutEvent.Finish
}

private fun Instant.sinceT0(): Long = (this - TABLE_T0).inWholeSeconds

private fun Cursor.asList(): List<Int> = listOf(blockIndex, exerciseIndex, setIndex, stageIndex)

private fun br.com.codecacto.kmplib.workout.model.WorkoutRun.swapList(): List<String> =
    swaps.map { "${it.exerciseStepId}->${it.toExerciseId}" }

/** Projeção do estado real no formato da tabela — o que o caso confere. */
fun WorkoutState.project(): ExpectedState = when (this) {
    WorkoutState.Idle -> ExpectedState(TablePhase.IDLE)
    is WorkoutState.Ready -> ExpectedState(TablePhase.READY)
    is WorkoutState.InSet -> ExpectedState(
        phase = TablePhase.IN_SET,
        cursor = cursor.asList(),
        setStartedAtSeconds = setStartedAt.sinceT0(),
        recordedSteps = run.sets.size,
        skippedExercises = run.skippedExercises.map { it.exerciseStepId },
        swaps = run.swapList(),
    )
    is WorkoutState.Resting -> ExpectedState(
        phase = TablePhase.RESTING,
        cursor = cursor.asList(),
        restEndsAtSeconds = restEndsAt.sinceT0(),
        recordedSteps = run.sets.size,
        skippedExercises = run.skippedExercises.map { it.exerciseStepId },
        swaps = run.swapList(),
    )
    is WorkoutState.Paused -> ExpectedState(
        phase = TablePhase.PAUSED,
        cursor = cursor.asList(),
        recordedSteps = run.sets.size,
        skippedExercises = run.skippedExercises.map { it.exerciseStepId },
        swaps = run.swapList(),
    )
    is WorkoutState.Finished -> ExpectedState(
        phase = TablePhase.FINISHED,
        finishedAtSeconds = run.finishedAt?.sinceT0(),
        recordedSteps = run.sets.size,
        skippedExercises = run.skippedExercises.map { it.exerciseStepId },
        swaps = run.swapList(),
    )
}

private fun inSet(cursor: List<Int>, startedAt: Long, recorded: Int, skipped: List<String> = emptyList(), swaps: List<String> = emptyList()) =
    ExpectedState(TablePhase.IN_SET, cursor, setStartedAtSeconds = startedAt, recordedSteps = recorded, skippedExercises = skipped, swaps = swaps)

private fun resting(cursor: List<Int>, endsAt: Long, recorded: Int, skipped: List<String> = emptyList(), swaps: List<String> = emptyList()) =
    ExpectedState(TablePhase.RESTING, cursor, restEndsAtSeconds = endsAt, recordedSteps = recorded, skippedExercises = skipped, swaps = swaps)

private fun paused(cursor: List<Int>, recorded: Int) =
    ExpectedState(TablePhase.PAUSED, cursor, recordedSteps = recorded)

private fun finished(at: Long, recorded: Int, skipped: List<String> = emptyList(), swaps: List<String> = emptyList()) =
    ExpectedState(TablePhase.FINISHED, finishedAtSeconds = at, recordedSteps = recorded, skippedExercises = skipped, swaps = swaps)

private val IDLE = ExpectedState(TablePhase.IDLE)
private val READY = ExpectedState(TablePhase.READY)
private val DONE = TableEvent.CompleteSet(10, 20.0)

private fun step(at: Long, event: TableEvent, expect: ExpectedState) = TransitionStep(at, event, expect)

/** Os casos. Ao acrescentar um, rode `TransitionFixtureExportTest` com a atualização ligada. */
val ENGINE_TRANSITION_CASES: List<TransitionCase> = listOf(
    TransitionCase(
        name = "normal: carregar, iniciar, série, descanso, pular descanso, última série termina",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(40, DONE, resting(listOf(0, 0, 1, 0), 70, 1)),
            step(50, TableEvent.SkipRest, inSet(listOf(0, 0, 1, 0), 50, 1)),
            step(90, DONE, finished(90, 2)),
        ),
    ),
    TransitionCase(
        name = "descanso: +tempo soma ao instante absoluto; evento fora de hora não muda nada",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(0, TableEvent.SkipRest, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(30, DONE, resting(listOf(0, 0, 1, 0), 60, 1)),
            step(35, DONE, resting(listOf(0, 0, 1, 0), 60, 1)),
            step(40, TableEvent.AddRestTime(15), resting(listOf(0, 0, 1, 0), 75, 1)),
            step(80, TableEvent.SkipRest, inSet(listOf(0, 0, 1, 0), 80, 1)),
        ),
    ),
    TransitionCase(
        name = "pausa no descanso: o tempo parado é devolvido ao descanso",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(30, DONE, resting(listOf(0, 0, 1, 0), 60, 1)),
            step(40, TableEvent.Pause, paused(listOf(0, 0, 1, 0), 1)),
            step(100, TableEvent.Resume, resting(listOf(0, 0, 1, 0), 120, 1)),
            step(100, TableEvent.Finish, finished(100, 1)),
        ),
    ),
    TransitionCase(
        name = "pausa na série: o início é empurrado; pausar de novo e retomar fora da pausa não fazem nada",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(5, TableEvent.Resume, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(10, TableEvent.Pause, paused(listOf(0, 0, 0, 0), 0)),
            step(12, TableEvent.Pause, paused(listOf(0, 0, 0, 0), 0)),
            step(12, DONE, paused(listOf(0, 0, 0, 0), 0)),
            step(25, TableEvent.Resume, inSet(listOf(0, 0, 0, 0), 15, 0)),
            step(30, TableEvent.Pause, paused(listOf(0, 0, 0, 0), 0)),
            step(31, TableEvent.Finish, finished(31, 0)),
        ),
    ),
    TransitionCase(
        name = "undo: sem passo registrado não faz nada; no descanso volta à série desfeita",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(5, TableEvent.Undo, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(30, DONE, resting(listOf(0, 0, 1, 0), 60, 1)),
            step(35, TableEvent.Undo, inSet(listOf(0, 0, 0, 0), 35, 0)),
            step(60, DONE, resting(listOf(0, 0, 1, 0), 90, 1)),
        ),
    ),
    TransitionCase(
        name = "dois exercícios: o descanso do primeiro separa do segundo; o último termina sem descanso",
        plan = twoExercisePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(30, DONE, resting(listOf(0, 1, 0, 0), 60, 1)),
            step(40, TableEvent.SkipRest, inSet(listOf(0, 1, 0, 0), 40, 1)),
            step(70, DONE, finished(70, 2)),
        ),
    ),
    TransitionCase(
        name = "bi-set: sem descanso dentro da volta; ao fechar a volta, o descanso do último exercício",
        plan = biSetPlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(30, DONE, inSet(listOf(0, 1, 0, 0), 30, 1)),
            step(60, DONE, resting(listOf(0, 0, 1, 0), 150, 2)),
            step(150, TableEvent.SkipRest, inSet(listOf(0, 0, 1, 0), 150, 2)),
        ),
    ),
    TransitionCase(
        name = "drop-set: estágios sem descanso; descanso depois do último estágio",
        plan = dropSetPlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(20, TableEvent.CompleteSet(12, 40.0), inSet(listOf(0, 0, 0, 1), 20, 1)),
            step(40, TableEvent.CompleteSet(10, 32.0), inSet(listOf(0, 0, 0, 2), 40, 2)),
            step(60, TableEvent.CompleteSet(8, 25.0), resting(listOf(0, 0, 1, 0), 150, 3)),
            step(70, TableEvent.Undo, inSet(listOf(0, 0, 0, 2), 70, 2)),
        ),
    ),
    TransitionCase(
        name = "circuito com série por tempo: exercício pulado sai das voltas seguintes, sem descanso",
        plan = circuitPlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(30, TableEvent.CompleteSet(null, null), inSet(listOf(0, 1, 0, 0), 30, 1)),
            step(35, TableEvent.SkipExercise("dor no punho"), inSet(listOf(0, 2, 0, 0), 35, 1, listOf("c-b"))),
            step(75, TableEvent.CompleteSet(null, null), resting(listOf(0, 0, 1, 0), 135, 2, listOf("c-b"))),
            step(135, TableEvent.SkipRest, inSet(listOf(0, 0, 1, 0), 135, 2, listOf("c-b"))),
            step(165, TableEvent.CompleteSet(null, null), inSet(listOf(0, 2, 1, 0), 165, 3, listOf("c-b"))),
        ),
    ),
    TransitionCase(
        name = "plano sem série: o Start termina na hora",
        plan = WorkoutPlan(id = "plan-empty", name = "Vazio", blocks = emptyList()),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, finished(0, 0)),
        ),
    ),
    TransitionCase(
        name = "encerrar antes do fim; depois do fim só o Load volta a valer",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(10, TableEvent.Finish, finished(10, 0)),
            step(20, DONE, finished(10, 0)),
            step(20, TableEvent.Undo, finished(10, 0)),
            step(30, TableEvent.Load, READY),
        ),
    ),
    TransitionCase(
        name = "antes do Load nenhum evento tem efeito; Load no meio do treino é ignorado",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Start, IDLE),
            step(0, DONE, IDLE),
            step(0, TableEvent.Pause, IDLE),
            step(0, TableEvent.Resume, IDLE),
            step(0, TableEvent.Finish, IDLE),
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Finish, READY),
            step(1, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 1, 0)),
            step(2, TableEvent.Load, inSet(listOf(0, 0, 0, 0), 1, 0)),
        ),
    ),
    TransitionCase(
        name = "série por tempo: a duração informada vai para a série; fora de 1..3600 ou em série de repetições é ignorada",
        plan = timedPlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(70, TableEvent.CompleteSet(null, null, durationSeconds = 40), resting(listOf(0, 0, 1, 0), 100, 1)),
            step(80, TableEvent.SkipRest, inSet(listOf(0, 0, 1, 0), 80, 1)),
            step(130, TableEvent.CompleteSet(null, null, durationSeconds = 5000), resting(listOf(0, 1, 0, 0), 160, 2)),
            step(140, TableEvent.SkipRest, inSet(listOf(0, 1, 0, 0), 140, 2)),
            step(170, TableEvent.CompleteSet(10, 20.0, durationSeconds = 30), finished(170, 3)),
        ),
    ),
    TransitionCase(
        name = "troca: o item passa ao substituto a partir do cursor; trocar para o mesmo não faz nada; undo desfaz a troca",
        plan = simplePlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(5, TableEvent.SwapExercise("ex-1", "leg-press", "aparelho ocupado"), inSet(listOf(0, 0, 0, 0), 5, 0, swaps = listOf("ex-1->leg-press"))),
            step(6, TableEvent.Undo, inSet(listOf(0, 0, 0, 0), 6, 0)),
            step(7, TableEvent.SwapExercise("ex-1", "leg-press"), inSet(listOf(0, 0, 0, 0), 7, 0, swaps = listOf("ex-1->leg-press"))),
            step(8, TableEvent.SwapExercise("ex-1", "leg-press"), inSet(listOf(0, 0, 0, 0), 7, 0, swaps = listOf("ex-1->leg-press"))),
            step(8, TableEvent.SwapExercise("ex-9", "hack"), inSet(listOf(0, 0, 0, 0), 7, 0, swaps = listOf("ex-1->leg-press"))),
            step(37, DONE, resting(listOf(0, 0, 1, 0), 67, 1, swaps = listOf("ex-1->leg-press"))),
            step(40, TableEvent.SwapExercise("ex-1", "hack"), resting(listOf(0, 0, 1, 0), 67, 1, swaps = listOf("ex-1->leg-press", "ex-1->hack"))),
            step(50, TableEvent.SkipRest, inSet(listOf(0, 0, 1, 0), 50, 1, swaps = listOf("ex-1->leg-press", "ex-1->hack"))),
            step(80, DONE, finished(80, 2, swaps = listOf("ex-1->leg-press", "ex-1->hack"))),
        ),
    ),
    TransitionCase(
        name = "troca no bi-set: o outro exercício da volta troca sem recomeçar a série corrente; undo em ordem inversa",
        plan = biSetPlan(),
        steps = listOf(
            step(0, TableEvent.Load, READY),
            step(0, TableEvent.Start, inSet(listOf(0, 0, 0, 0), 0, 0)),
            step(5, TableEvent.SwapExercise("ex-2", "pushdown"), inSet(listOf(0, 0, 0, 0), 0, 0, swaps = listOf("ex-2->pushdown"))),
            step(30, DONE, inSet(listOf(0, 1, 0, 0), 30, 1, swaps = listOf("ex-2->pushdown"))),
            step(60, DONE, resting(listOf(0, 0, 1, 0), 150, 2, swaps = listOf("ex-2->pushdown"))),
            step(70, TableEvent.Undo, inSet(listOf(0, 1, 0, 0), 70, 1, swaps = listOf("ex-2->pushdown"))),
            step(71, TableEvent.Undo, inSet(listOf(0, 0, 0, 0), 71, 0, swaps = listOf("ex-2->pushdown"))),
            step(72, TableEvent.Undo, inSet(listOf(0, 0, 0, 0), 72, 0)),
        ),
    ),
)

/** Instante absoluto de um passo da tabela. */
fun TransitionStep.instant(): Instant = TABLE_T0 + atSeconds.seconds
