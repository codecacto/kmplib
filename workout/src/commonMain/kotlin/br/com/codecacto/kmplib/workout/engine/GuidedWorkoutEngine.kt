package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.SkippedExercise
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Posição corrente dentro do plano: bloco, exercício dentro do bloco, série dentro do exercício e
 * estágio dentro da série (drop-set; 0 quando a série não tem estágios). Em `BI_SET`/`CIRCUIT` a volta
 * é o próprio [setIndex] — a volta *r* faz a série *r* de cada exercício.
 */
data class Cursor(
    val blockIndex: Int,
    val exerciseIndex: Int,
    val setIndex: Int,
    val stageIndex: Int = 0,
) {
    fun exercise(plan: WorkoutPlan): ExerciseStep = plan.blocks[blockIndex].exercises[exerciseIndex]

    fun block(plan: WorkoutPlan): Block = plan.blocks[blockIndex]

    /** A série inteira do cursor (com todos os estágios); o estágio resolvido está em `plan.stepAt`. */
    fun set(plan: WorkoutPlan): SetTarget = exercise(plan).sets[setIndex]
}

/**
 * Estado da máquina do treino guiado. É a MESMA máquina no celular, no relógio e no backend
 * (reaproveitada como caso de teste em outra linguagem, ex.: Monkey C) — por isso puramente de
 * dados, sem efeito colateral: quem avança o tempo é o `now: Instant` passado a `reduce`.
 */
sealed interface WorkoutState {
    data object Idle : WorkoutState

    /**
     * Plano carregado, treino ainda não começou. [localRunId] é o id que a sessão vai receber no
     * `Start` (o cliente gera o UUID — `session.id` do backend é do CLIENTE, para o envio ser
     * idempotente); `null` deriva um id determinístico de `plan.id` + instante do início.
     */
    data class Ready(val plan: WorkoutPlan, val localRunId: String? = null) : WorkoutState

    data class InSet(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val setStartedAt: Instant,
    ) : WorkoutState

    /** `restEndsAt` é absoluto (não "segundos restantes"): sobrevive a app em 2º plano e troca de
     * dispositivo, e fica testável sem mockar um timer de verdade. `cursor` já aponta o PRÓXIMO passo. */
    data class Resting(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val restEndsAt: Instant,
    ) : WorkoutState

    /**
     * Treino pausado. [pausedAt] é o instante da pausa: no `Resume`, o tempo parado é devolvido —
     * o descanso termina tanto depois quanto durou a pausa (`restEndsAt` empurrado) e a série por tempo
     * não conta o intervalo parado (`setStartedAt` empurrado). `null` (estado montado à mão, sem
     * instante) retoma sem ajuste.
     */
    data class Paused(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val previous: WorkoutState,
        val pausedAt: Instant? = null,
    ) : WorkoutState

    data class Finished(val plan: WorkoutPlan, val run: WorkoutRun) : WorkoutState
}

sealed interface WorkoutEvent {
    /** Carrega o plano: `Idle`/`Finished` -> `Ready`. Em qualquer outro estado não tem efeito (não se
     * troca de plano no meio de um treino — encerre com `Finish` antes). */
    data class Load(val plan: WorkoutPlan, val localRunId: String? = null) : WorkoutEvent

    /** `Ready` -> primeiro passo do plano (`InSet`), ou `Finished` se o plano não tem série. */
    data object Start : WorkoutEvent

    /** Conclui o passo corrente: a série — ou, no drop-set, o ESTÁGIO corrente dela. */
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
 * mais `Paused`. Anda SEMPRE pela [executionOrder] do plano (a regra de cada método está lá):
 *
 * | Método | Ordem | Descanso |
 * |---|---|---|
 * | `NORMAL` | exercício → série | `restSeconds` do exercício depois de cada série |
 * | `DROP_SET` | exercício → série → estágio | nenhum entre estágios; `restSeconds` depois do último estágio |
 * | `BI_SET`/`CIRCUIT` | volta → exercício (→ estágio) | nenhum dentro da volta; depois do último exercício da volta, o `restSeconds` DELE |
 *
 * O descanso da última série/volta de um bloco também separa esse bloco do seguinte; o último passo do
 * plano termina em `Finished`, sem descanso. Exercício pulado sai das voltas que faltam — se ele era o
 * último da volta, o descanso passa a vir depois do exercício que de fato fechou a volta.
 *
 * `reduce` nunca lê o relógio do sistema: tudo que depende de tempo recebe `now` de fora, para o
 * mesmo teste valer em JVM, Android, iOS e watchOS.
 */
class GuidedWorkoutEngine {

    /** Entra no primeiro passo do plano; plano sem nenhuma série termina na hora (`Finished`). */
    fun start(plan: WorkoutPlan, now: Instant, localRunId: String): WorkoutState {
        val run = WorkoutRun(localId = localRunId, planId = plan.id, startedAt = now)
        val first = plan.executionOrder().firstOrNull()
            ?: return WorkoutState.Finished(plan, run.copy(finishedAt = now))
        return enterSet(plan, first.cursor, run, now)
    }

    fun reduce(state: WorkoutState, event: WorkoutEvent, now: Instant): WorkoutState = when (event) {
        is WorkoutEvent.Load -> load(state, event)
        WorkoutEvent.Start -> if (state is WorkoutState.Ready) {
            start(state.plan, now, state.localRunId ?: defaultRunId(state.plan, now))
        } else {
            state
        }
        WorkoutEvent.Pause -> pause(state, now)
        WorkoutEvent.Resume -> if (state is WorkoutState.Paused) resume(state, now) else state
        WorkoutEvent.Finish -> finish(state, now)
        else -> reduceActive(state, event, now)
    }

    private fun load(state: WorkoutState, event: WorkoutEvent.Load): WorkoutState = when (state) {
        WorkoutState.Idle, is WorkoutState.Ready, is WorkoutState.Finished -> WorkoutState.Ready(event.plan, event.localRunId)
        else -> state
    }

    /** Id determinístico quando o chamador não informou um — mesmo plano e mesmo instante dão o
     * mesmo id em qualquer plataforma (a máquina não sorteia nada). */
    private fun defaultRunId(plan: WorkoutPlan, now: Instant): String = "${plan.id}@${now.toEpochMilliseconds()}"

    /**
     * O passo que vem depois do [cursor], respeitando os exercícios já pulados em [run] — `null` quando
     * o [cursor] é o último. Serve à tela ("Próximo: Tríceps corda").
     */
    fun upcoming(plan: WorkoutPlan, cursor: Cursor, run: WorkoutRun): ExecutionStep? =
        nextStep(plan.executionOrder(), cursor, skippedIds(run))

    /**
     * Segundos de descanso que concluir o passo do [cursor] vai abrir — 0 quando o próximo passo entra
     * direto (estágio que não é o último, exercício que não fecha a volta, fim do plano). É o que decide
     * o rótulo do botão ("Concluir estágio" × "Concluir série") sem a tela conhecer a regra do método.
     */
    fun restAfter(plan: WorkoutPlan, cursor: Cursor, run: WorkoutRun): Int {
        val order = plan.executionOrder()
        val current = order.firstOrNull { it.cursor == cursor } ?: return 0
        return restBetween(current, nextStep(order, cursor, skippedIds(run)))
    }

    private fun pause(state: WorkoutState, now: Instant): WorkoutState = when (state) {
        is WorkoutState.InSet -> WorkoutState.Paused(state.plan, state.cursor, state.run, state, now)
        is WorkoutState.Resting -> WorkoutState.Paused(state.plan, state.cursor, state.run, state, now)
        else -> state
    }

    /** Devolve o tempo parado: relógio que voltou para trás (pausa "no futuro") não encurta nada. */
    private fun resume(state: WorkoutState.Paused, now: Instant): WorkoutState {
        val pausedAt = state.pausedAt ?: return state.previous
        val elapsed = (now - pausedAt).coerceAtLeast(kotlin.time.Duration.ZERO)
        return when (val previous = state.previous) {
            is WorkoutState.Resting -> previous.copy(restEndsAt = previous.restEndsAt + elapsed)
            is WorkoutState.InSet -> previous.copy(setStartedAt = previous.setStartedAt + elapsed)
            else -> previous
        }
    }

    private fun finish(state: WorkoutState, now: Instant): WorkoutState = when (state) {
        is WorkoutState.InSet -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        is WorkoutState.Resting -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        is WorkoutState.Paused -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        else -> state
    }

    private fun reduceActive(state: WorkoutState, event: WorkoutEvent, now: Instant): WorkoutState = when (state) {
        is WorkoutState.InSet -> when (event) {
            is WorkoutEvent.CompleteSet -> completeStep(state, event, now)
            is WorkoutEvent.SkipExercise -> skipExercise(state.plan, state.cursor, state.run, event, now)
            WorkoutEvent.Undo -> undo(state, state.plan, state.run, now)
            else -> state
        }

        is WorkoutState.Resting -> when (event) {
            WorkoutEvent.SkipRest -> enterSet(state.plan, state.cursor, state.run, now)
            is WorkoutEvent.AddRestTime -> state.copy(restEndsAt = state.restEndsAt + event.seconds.seconds)
            is WorkoutEvent.SkipExercise -> skipExercise(state.plan, state.cursor, state.run, event, now)
            WorkoutEvent.Undo -> undo(state, state.plan, state.run, now)
            else -> state
        }

        else -> state
    }

    private fun completeStep(state: WorkoutState.InSet, event: WorkoutEvent.CompleteSet, now: Instant): WorkoutState {
        val plan = state.plan
        val order = plan.executionOrder()
        val current = order.firstOrNull { it.cursor == state.cursor } ?: return state
        val result = SetResult(
            blockId = current.blockId,
            exerciseStepId = current.exercise.id,
            setIndex = current.setIndex,
            target = current.target,
            repsDone = event.reps,
            loadDone = event.load,
            startedAt = state.setStartedAt,
            completedAt = now,
            heartRateAvg = event.heartRateAvg,
            heartRateMax = event.heartRateMax,
            stageIndex = current.stageIndex,
            roundIndex = current.roundIndex,
        )
        val nextRun = state.run.copy(sets = state.run.sets + result)
        val next = nextStep(order, current.cursor, skippedIds(nextRun))
            ?: return WorkoutState.Finished(plan, nextRun.copy(finishedAt = now))

        val restSeconds = restBetween(current, next)
        return if (restSeconds > 0) {
            WorkoutState.Resting(plan, next.cursor, nextRun, now + restSeconds.seconds)
        } else {
            enterSet(plan, next.cursor, nextRun, now)
        }
    }

    /**
     * Pula o exercício do [cursor] no RESTO do treino (em bi-set/circuito, nas voltas que faltam) e vai
     * direto ao próximo passo, sem descanso: quem pulou não fez esforço a recuperar.
     */
    private fun skipExercise(
        plan: WorkoutPlan,
        cursor: Cursor,
        run: WorkoutRun,
        event: WorkoutEvent.SkipExercise,
        now: Instant,
    ): WorkoutState {
        val exercise = plan.stepAt(cursor)?.exercise ?: return enterSet(plan, cursor, run, now)
        val nextRun = run.copy(
            skippedExercises = run.skippedExercises + SkippedExercise(exercise.id, event.reason, now),
        )
        val next = nextStep(plan.executionOrder(), cursor, skippedIds(nextRun))
            ?: return WorkoutState.Finished(plan, nextRun.copy(finishedAt = now))
        return enterSet(plan, next.cursor, nextRun, now)
    }

    /**
     * Desfaz o último passo concluído (série ou estágio) e volta o cursor para ele, sem perder o que já
     * foi registrado antes — a sessão nunca duplica nem perde séries por causa de um "voltar". Voltar a um
     * exercício pulado o devolve ao treino (o pulo dele sai do registro). Sem passo registrado ainda,
     * `Undo` não tem efeito.
     */
    private fun undo(state: WorkoutState, plan: WorkoutPlan, run: WorkoutRun, now: Instant): WorkoutState {
        val lastSet = run.sets.lastOrNull() ?: return state
        val blockIndex = plan.blocks.indexOfFirst { it.id == lastSet.blockId }
        val exerciseIndex = plan.blocks.getOrNull(blockIndex)?.exercises
            ?.indexOfFirst { it.id == lastSet.exerciseStepId } ?: -1
        val restored = Cursor(blockIndex, exerciseIndex, lastSet.setIndex, lastSet.stageIndex ?: 0)
        if (plan.stepAt(restored) == null) return state
        val nextRun = run.copy(
            sets = run.sets.dropLast(1),
            skippedExercises = run.skippedExercises.filterNot { it.exerciseStepId == lastSet.exerciseStepId },
        )
        return enterSet(plan, restored, nextRun, now)
    }

    private fun enterSet(plan: WorkoutPlan, cursor: Cursor, run: WorkoutRun, now: Instant): WorkoutState.InSet =
        WorkoutState.InSet(plan, cursor, run, now)

    private fun skippedIds(run: WorkoutRun): Set<String> = run.skippedExercises.mapTo(HashSet()) { it.exerciseStepId }

    /** Primeiro passo DEPOIS de [from] na ordem do plano cujo exercício não foi pulado. */
    private fun nextStep(order: List<ExecutionStep>, from: Cursor, skipped: Set<String>): ExecutionStep? {
        val index = order.indexOfFirst { it.cursor == from }
        if (index < 0) return null
        for (i in index + 1 until order.size) {
            if (order[i].exercise.id !in skipped) return order[i]
        }
        return null
    }

    /**
     * Descanso entre o passo concluído e o próximo (tabela no KDoc da classe). Estágio que não é o
     * último nunca descansa; em bi-set/circuito, só descansa quem FECHA a volta — o próximo passo é de
     * outra volta ou de outro bloco.
     */
    private fun restBetween(current: ExecutionStep, next: ExecutionStep?): Int {
        if (next == null || !current.isLastStage) return 0
        val sameRound = current.roundIndex != null &&
            next.cursor.blockIndex == current.cursor.blockIndex &&
            next.roundIndex == current.roundIndex
        return if (sameRound) 0 else current.exercise.restSeconds
    }
}
