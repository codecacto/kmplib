package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.SkippedExercise
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/** Posição corrente dentro do plano: bloco, exercício dentro do bloco, série dentro do exercício. */
data class Cursor(val blockIndex: Int, val exerciseIndex: Int, val setIndex: Int) {
    fun exercise(plan: WorkoutPlan) = plan.blocks[blockIndex].exercises[exerciseIndex]

    fun block(plan: WorkoutPlan): Block = plan.blocks[blockIndex]
}

/**
 * Estado da máquina do treino guiado. É a MESMA máquina no celular, no relógio e no backend
 * (reaproveitada como caso de teste em outra linguagem, ex.: Monkey C) — por isso puramente de
 * dados, sem efeito colateral: quem avança o tempo é o `now: Instant` passado a `reduce`.
 */
sealed interface WorkoutState {
    data object Idle : WorkoutState

    data class Ready(val plan: WorkoutPlan) : WorkoutState

    data class InSet(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val setStartedAt: Instant,
    ) : WorkoutState

    /** `restEndsAt` é absoluto (não "segundos restantes"): sobrevive a app em 2º plano e troca de
     * dispositivo, e fica testável sem mockar um timer de verdade. */
    data class Resting(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val restEndsAt: Instant,
    ) : WorkoutState

    data class Paused(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val previous: WorkoutState,
    ) : WorkoutState

    data class Finished(val plan: WorkoutPlan, val run: WorkoutRun) : WorkoutState
}

sealed interface WorkoutEvent {
    data object Start : WorkoutEvent

    data class CompleteSet(
        val reps: Int?,
        val load: Double?,
        val heartRateAvg: Int? = null,
        val heartRateMax: Int? = null,
    ) : WorkoutEvent

    data object SkipRest : WorkoutEvent

    data class AddRestTime(val seconds: Int) : WorkoutEvent

    data class SkipExercise(val reason: String? = null) : WorkoutEvent

    data object Undo : WorkoutEvent

    data object Pause : WorkoutEvent

    data object Resume : WorkoutEvent

    data object Finish : WorkoutEvent
}

/**
 * Máquina de estados pura do treino guiado: `Idle -> Ready -> InSet -> Resting -> ... -> Finished`,
 * mais `Paused`. `BI_SET`/`CIRCUIT` não têm o descanso do bloco entre os exercícios do próprio bloco
 * (só depois do último); `DROP_SET` não tem descanso entre as séries do mesmo exercício.
 *
 * `reduce` nunca lê o relógio do sistema: tudo que depende de tempo recebe `now` de fora, para o
 * mesmo teste valer em JVM, Android, iOS e watchOS.
 */
class GuidedWorkoutEngine {

    fun start(plan: WorkoutPlan, now: Instant, localRunId: String): WorkoutState {
        val run = WorkoutRun(localId = localRunId, planId = plan.id, startedAt = now)
        return enterSet(plan, Cursor(0, 0, 0), run, now)
    }

    fun reduce(state: WorkoutState, event: WorkoutEvent, now: Instant): WorkoutState = when (event) {
        WorkoutEvent.Pause -> pause(state)
        WorkoutEvent.Resume -> if (state is WorkoutState.Paused) state.previous else state
        WorkoutEvent.Finish -> finish(state, now)
        else -> reduceActive(state, event, now)
    }

    private fun pause(state: WorkoutState): WorkoutState = when (state) {
        is WorkoutState.InSet -> WorkoutState.Paused(state.plan, state.cursor, state.run, state)
        is WorkoutState.Resting -> WorkoutState.Paused(state.plan, state.cursor, state.run, state)
        else -> state
    }

    private fun finish(state: WorkoutState, now: Instant): WorkoutState = when (state) {
        is WorkoutState.InSet -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        is WorkoutState.Resting -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        is WorkoutState.Paused -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        else -> state
    }

    private fun reduceActive(state: WorkoutState, event: WorkoutEvent, now: Instant): WorkoutState = when (state) {
        is WorkoutState.InSet -> when (event) {
            is WorkoutEvent.CompleteSet -> completeSet(state.plan, state.cursor, state.run, state.setStartedAt, event, now)
            is WorkoutEvent.SkipExercise -> skipExercise(state.plan, state.cursor, state.run, event, now)
            WorkoutEvent.Undo -> undo(state.plan, state.run, now)
            else -> state
        }

        is WorkoutState.Resting -> when (event) {
            WorkoutEvent.SkipRest -> enterSet(state.plan, state.cursor, state.run, now)
            is WorkoutEvent.AddRestTime -> state.copy(restEndsAt = state.restEndsAt + event.seconds.seconds)
            is WorkoutEvent.SkipExercise -> skipExercise(state.plan, state.cursor, state.run, event, now)
            WorkoutEvent.Undo -> undo(state.plan, state.run, now)
            else -> state
        }

        else -> state
    }

    private fun completeSet(
        plan: WorkoutPlan,
        cursor: Cursor,
        run: WorkoutRun,
        setStartedAt: Instant,
        event: WorkoutEvent.CompleteSet,
        now: Instant,
    ): WorkoutState {
        val exercise = cursor.exercise(plan)
        val target = exercise.sets[cursor.setIndex]
        val result = SetResult(
            blockId = cursor.block(plan).id,
            exerciseStepId = exercise.id,
            setIndex = cursor.setIndex,
            target = target,
            repsDone = event.reps,
            loadDone = event.load,
            startedAt = setStartedAt,
            completedAt = now,
            heartRateAvg = event.heartRateAvg,
            heartRateMax = event.heartRateMax,
        )
        val nextRun = run.copy(sets = run.sets + result)
        val next = nextCursor(plan, cursor)
            ?: return WorkoutState.Finished(plan, nextRun.copy(finishedAt = now))

        val restSeconds = restBefore(plan, cursor, next)
        return if (restSeconds > 0) {
            WorkoutState.Resting(plan, next, nextRun, now + restSeconds.seconds)
        } else {
            enterSet(plan, next, nextRun, now)
        }
    }

    private fun skipExercise(
        plan: WorkoutPlan,
        cursor: Cursor,
        run: WorkoutRun,
        event: WorkoutEvent.SkipExercise,
        now: Instant,
    ): WorkoutState {
        val exercise = cursor.exercise(plan)
        val nextRun = run.copy(
            skippedExercises = run.skippedExercises + SkippedExercise(exercise.id, event.reason, now),
        )
        val next = firstSetOfNextExercise(plan, cursor)
            ?: return WorkoutState.Finished(plan, nextRun.copy(finishedAt = now))
        return enterSet(plan, next, nextRun, now)
    }

    /** Desfaz a última série concluída e volta o cursor para ela, sem perder o que já foi registrado
     * antes — a sessão nunca duplica nem perde séries por causa de um "voltar". Sem série registrada
     * ainda, `Undo` não tem efeito (nada a desfazer). */
    private fun undo(plan: WorkoutPlan, run: WorkoutRun, now: Instant): WorkoutState {
        val lastSet = run.sets.lastOrNull()
            ?: return enterSet(plan, Cursor(0, 0, 0), run, now)
        val block = plan.blocks.first { it.id == lastSet.blockId }
        val restoredCursor = Cursor(
            blockIndex = plan.blocks.indexOf(block),
            exerciseIndex = block.exercises.indexOfFirst { it.id == lastSet.exerciseStepId },
            setIndex = lastSet.setIndex,
        )
        return enterSet(plan, restoredCursor, run.copy(sets = run.sets.dropLast(1)), now)
    }

    private fun enterSet(plan: WorkoutPlan, cursor: Cursor, run: WorkoutRun, now: Instant): WorkoutState.InSet =
        WorkoutState.InSet(plan, cursor, run, now)

    /** Próxima série dentro do mesmo exercício, senão o primeiro exercício do próximo bloco; `null`
     * quando acabou o plano. */
    private fun nextCursor(plan: WorkoutPlan, cursor: Cursor): Cursor? {
        val exercise = cursor.exercise(plan)
        if (cursor.setIndex + 1 < exercise.sets.size) {
            return cursor.copy(setIndex = cursor.setIndex + 1)
        }
        return firstSetOfNextExercise(plan, cursor)
    }

    private fun firstSetOfNextExercise(plan: WorkoutPlan, cursor: Cursor): Cursor? {
        val block = cursor.block(plan)
        if (cursor.exerciseIndex + 1 < block.exercises.size) {
            return Cursor(cursor.blockIndex, cursor.exerciseIndex + 1, 0)
        }
        if (cursor.blockIndex + 1 < plan.blocks.size) {
            return Cursor(cursor.blockIndex + 1, 0, 0)
        }
        return null
    }

    /**
     * Descanso ANTES de entrar na próxima série/exercício, pela regra do método do bloco de ORIGEM
     * (de onde se está saindo): `BI_SET`/`CIRCUIT` não descansam entre os exercícios do próprio
     * bloco (só ao concluir o último); `DROP_SET` não descansa entre séries do mesmo exercício.
     */
    private fun restBefore(plan: WorkoutPlan, from: Cursor, to: Cursor): Int {
        val fromBlock = from.block(plan)
        val stayedInBlock = to.blockIndex == from.blockIndex
        val stayedInExercise = stayedInBlock && to.exerciseIndex == from.exerciseIndex
        return when (fromBlock.method) {
            WorkoutMethod.DROP_SET -> if (stayedInExercise) 0 else from.exercise(plan).restSeconds
            WorkoutMethod.BI_SET, WorkoutMethod.CIRCUIT -> if (stayedInBlock) 0 else from.exercise(plan).restSeconds
            WorkoutMethod.NORMAL -> from.exercise(plan).restSeconds
        }
    }
}
