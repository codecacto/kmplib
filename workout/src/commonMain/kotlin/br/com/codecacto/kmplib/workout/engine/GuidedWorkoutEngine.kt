package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.ExerciseSwap
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.SkippedExercise
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import br.com.codecacto.kmplib.workout.model.SET_DURATION_RANGE
import br.com.codecacto.kmplib.workout.model.currentExerciseId
import br.com.codecacto.kmplib.workout.model.phasesPerRound
import br.com.codecacto.kmplib.workout.model.secondsOf
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Posição corrente dentro do plano: bloco, exercício dentro do bloco, série dentro do exercício e
 * estágio dentro da série (drop-set; 0 quando a série não tem estágios). Em `BI_SET`/`CIRCUIT` a volta
 * é o próprio [setIndex] — a volta *r* faz a série *r* de cada exercício.
 *
 * Num item de cardio (2.297.0), [setIndex] é a VOLTA do cardio e [stageIndex] a fase dentro da volta
 * (0 = trabalho, 1 = descanso) — a fase de fato está em `plan.stepAt(cursor).cardioPhase`.
 */
data class Cursor(
    val blockIndex: Int,
    val exerciseIndex: Int,
    val setIndex: Int,
    val stageIndex: Int = 0,
) {
    fun exercise(plan: WorkoutPlan): ExerciseStep = plan.blocks[blockIndex].exercises[exerciseIndex]

    fun block(plan: WorkoutPlan): Block = plan.blocks[blockIndex]

    /**
     * A série inteira do cursor (com todos os estágios); o estágio resolvido está em `plan.stepAt`. Num
     * item de cardio (2.297.0), a fase do cursor como `SetTarget.Timed` com os segundos planejados — o
     * item de cardio não tem série, e ler `sets[setIndex]` estouraria o índice.
     */
    fun set(plan: WorkoutPlan): SetTarget {
        val exercise = exercise(plan)
        val cardio = exercise.cardio ?: return exercise.sets[setIndex]
        return SetTarget.Timed(cardio.secondsOf(cardio.phasesPerRound()[stageIndex]))
    }
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
     * dispositivo, e fica testável sem mockar um timer de verdade. `cursor` já aponta o PRÓXIMO passo.
     * [kind] (2.299.0) diz QUE descanso é este — a tela escreve "Descanso", "Transição — vá para a
     * próxima estação" ou "Fim da volta 2 de 3" sem refazer a regra do método (ver [RestKind]). */
    data class Resting(
        val plan: WorkoutPlan,
        val cursor: Cursor,
        val run: WorkoutRun,
        val restEndsAt: Instant,
        val kind: RestKind = RestKind.SET,
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

/**
 * Que descanso é o `WorkoutState.Resting` corrente (2.299.0):
 * - [SET]: depois de uma série (ou do último estágio do drop-set, ou do fim de um item de cardio) —
 *   o `restSeconds` do exercício. Também é o descanso que separa um bloco `NORMAL`/`DROP_SET` do seguinte.
 * - [TRANSITION]: no CIRCUITO, entre um exercício e o seguinte dentro da MESMA volta — o
 *   `Block.transitionRestSeconds` (trocar de estação). Nunca acontece no conjugado (`BI_SET`).
 * - [ROUND]: no conjugado/circuito, depois do exercício que FECHA a volta — o `restSeconds` dele. Também
 *   é o descanso que separa um bloco por volta do seguinte.
 */
@Serializable
enum class RestKind { SET, TRANSITION, ROUND }

/** Um descanso que o motor vai abrir: [seconds] (> 0) e o [kind] dele (ver [RestKind]). */
data class ScheduledRest(val seconds: Int, val kind: RestKind)

sealed interface WorkoutEvent {
    /** Carrega o plano: `Idle`/`Finished` -> `Ready`. Em qualquer outro estado não tem efeito (não se
     * troca de plano no meio de um treino — encerre com `Finish` antes). */
    data class Load(val plan: WorkoutPlan, val localRunId: String? = null) : WorkoutEvent

    /** `Ready` -> primeiro passo do plano (`InSet`), ou `Finished` se o plano não tem série. */
    data object Start : WorkoutEvent

    /**
     * Conclui o passo corrente: a série — ou, no drop-set, o ESTÁGIO corrente dela.
     *
     * [durationSeconds] (2.275.0) = a duração que o cronômetro da série POR TEMPO marcou; vai para
     * `SetResult.durationSeconds` e vale mais que a diferença de instantes. Só é gravada quando o passo
     * é `SetTarget.Timed` e o valor está em `SET_DURATION_RANGE` (1..3600) — fora disso a série é
     * registrada SEM ela (cai na diferença de instantes), para o motor nunca produzir sessão que o
     * `validateRun` recuse.
     */
    data class CompleteSet(
        val reps: Int?,
        val load: Double?,
        val heartRateAvg: Int? = null,
        val heartRateMax: Int? = null,
        val durationSeconds: Int? = null,
    ) : WorkoutEvent {
        /**
         * Só QUAIS campos vieram, nunca o valor: carga, repetições, duração e FC são dado de saúde do
         * aluno, e o `toString` gerado ia parar em log/GlitchTip quando alguém logava o evento
         * (2.273.0). `equals`/`hashCode` não mudam.
         */
        override fun toString(): String =
            "CompleteSet(hasReps=${reps != null}, hasLoad=${load != null}, " +
                "hasHeartRate=${heartRateAvg != null || heartRateMax != null}, " +
                "hasDuration=${durationSeconds != null})"
    }

    data object SkipRest : WorkoutEvent

    /**
     * "O relógio andou" (2.297.0): fecha toda fase de cardio cujo instante final absoluto
     * (`setStartedAt + segundos da fase`) já passou, cada uma NO SEU instante final — não no `now`. É o
     * que faz o cardio sobreviver a app em 2º plano, processo morto e tela apagada: ao voltar, um único
     * `Tick` repõe as voltas que correram enquanto ninguém olhava, com os horários certos, e para na
     * fase corrente (ou no descanso entre itens, ou no próximo exercício, ou em `Finished`).
     *
     * Fora de uma fase de cardio não tem efeito — o descanso entre séries de força continua terminando
     * por `SkipRest` (decisão da tela), como sempre. Pausado, o relógio não anda. Todo outro evento num
     * passo de cardio aplica o mesmo acerto ANTES de agir (ex.: `Pause` atrasado pausa a fase em que o
     * aluno de fato estava). Mande um `Tick` a cada segundo com a tela de cardio aberta e um ao voltar
     * ao primeiro plano.
     */
    data object Tick : WorkoutEvent

    /**
     * "Pular fase" do cardio (2.297.0): encerra a fase corrente agora, registrada como pulada
     * (`SetResult.skipped = true`, `durationSeconds` = o que já correu), e entra na próxima. Fora de uma
     * fase de cardio não tem efeito (no descanso entre séries use `SkipRest`).
     */
    data object SkipPhase : WorkoutEvent

    data class AddRestTime(val seconds: Int) : WorkoutEvent

    data class SkipExercise(val reason: String? = null) : WorkoutEvent {
        /** Sem o [reason] (texto livre do aluno — "dor no joelho" é dado de saúde), 2.273.0. */
        override fun toString(): String = "SkipExercise(hasReason=${reason != null})"
    }

    /**
     * TROCA o exercício do item [stepId] (o `ExerciseStep.id`) por [toExerciseId] (o `exerciseRefId` do
     * substituto, do catálogo do app) a partir do cursor atual (2.275.0): as séries que FALTAM daquele
     * item passam a ser registradas com `SetResult.exerciseRefId = toExerciseId` e
     * `swappedFromExerciseId` = o exercício original do plano. Prescrição (séries, metas, estágios) e
     * descanso continuam os do item; o que já foi feito não muda.
     *
     * Vale em `InSet` e `Resting`. Sem efeito quando: o item não existe, já não tem passo pela frente
     * (concluído ou pulado), [toExerciseId] está em branco, ou é o exercício que o item JÁ usa (trocar
     * para o mesmo não é troca). Em `InSet` do próprio item, o relógio da série recomeça (é outro
     * exercício que começa agora). `Undo` logo depois desfaz a troca.
     */
    data class SwapExercise(
        val stepId: String,
        val toExerciseId: String,
        val reason: String? = null,
    ) : WorkoutEvent {
        /** Sem o [reason] (texto livre do aluno — dado de saúde); os ids são do plano/catálogo. */
        override fun toString(): String =
            "SwapExercise(stepId=$stepId, toExerciseId=$toExerciseId, hasReason=${reason != null})"
    }

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
 * | `BI_SET` (conjugado) | volta → exercício (→ estágio) | nenhum dentro da volta ("segue para A2"); depois do último exercício da volta, o `restSeconds` DELE (`RestKind.ROUND`) |
 * | `CIRCUIT` | volta → exercício (→ estágio) | entre exercícios da volta, o `Block.transitionRestSeconds` (`RestKind.TRANSITION`; 0 = segue direto); depois do último da volta, o `restSeconds` DELE (`RestKind.ROUND`) |
 *
 * Qual descanso está correndo sai em `WorkoutState.Resting.kind`; o que concluir o passo atual vai abrir,
 * em [nextRest] (2.299.0).
 *
 * O descanso da última série/volta de um bloco também separa esse bloco do seguinte; o último passo do
 * plano termina em `Finished`, sem descanso. Exercício pulado sai das voltas que faltam — se ele era o
 * último da volta, o descanso passa a vir depois do exercício que de fato fechou a volta.
 *
 * `reduce` nunca lê o relógio do sistema: tudo que depende de tempo recebe `now` de fora, para o
 * mesmo teste valer em JVM, Android, iOS e watchOS.
 *
 * ## Cardio (2.297.0)
 * Item com `ExerciseStep.cardio` é conduzido por FASES (`ExecutionStep.cardioPhase`), cada uma um
 * `InSet` com instante final absoluto ([phaseEndsAt]):
 *
 * | Evento numa fase de cardio | Efeito |
 * |---|---|
 * | `Tick` com `now` ≥ fim | fecha a fase no instante FINAL (`durationSeconds` = planejado) e entra na próxima nesse instante; repete enquanto houver fase vencida |
 * | `CompleteSet` | fecha a fase agora (`durationSeconds` = o que correu); carga/repetições ignoradas, FC gravada |
 * | `SkipPhase` | fecha a fase agora como PULADA |
 * | `Pause`/`Resume` | o relógio da fase para e é devolvido (o fim anda junto) |
 * | `Finish` | grava a fase corrente PARCIAL (se correu ≥ 1 s) — "salvar o que já fez" |
 * | `SkipExercise` | pula o resto do bloco de cardio |
 * | `Undo` | desfaz a última fase registrada e a recomeça agora |
 *
 * Entre as fases do MESMO item não há descanso de série (o descanso do intervalado É uma fase). Depois
 * da última fase vale o `restSeconds` do item, como depois de uma série de força.
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
        WorkoutEvent.Pause -> pause(catchUp(state, now), now)
        WorkoutEvent.Resume -> if (state is WorkoutState.Paused) resume(state, now) else state
        WorkoutEvent.Finish -> finish(catchUp(state, now), now)
        else -> reduceActive(catchUp(state, now), event, now)
    }

    /**
     * Instante em que a fase corrente termina sozinha (2.297.0): numa fase de cardio, `setStartedAt` +
     * segundos da fase; no descanso, `restEndsAt`. `null` em série de força, pausado, fora do treino. É
     * o que o anel desenha — calculado de um instante absoluto, nunca de um contador.
     */
    fun phaseEndsAt(state: WorkoutState): Instant? = when (state) {
        is WorkoutState.InSet -> state.plan.stepAt(state.cursor)?.phaseSeconds?.let { state.setStartedAt + it.seconds }
        is WorkoutState.Resting -> state.restEndsAt
        else -> null
    }

    /**
     * Quanto falta para a fase corrente terminar (2.297.0), nunca negativo. Pausado, o valor fica
     * CONGELADO no instante da pausa (o anel parado mostra o que faltava). `null` quando não há fim
     * ([phaseEndsAt] nulo).
     */
    fun timeRemaining(state: WorkoutState, now: Instant): Duration? {
        val (end, reference) = when (state) {
            is WorkoutState.Paused -> (phaseEndsAt(state.previous) ?: return null) to (state.pausedAt ?: now)
            else -> (phaseEndsAt(state) ?: return null) to now
        }
        return (end - reference).coerceAtLeast(Duration.ZERO)
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
    fun restAfter(plan: WorkoutPlan, cursor: Cursor, run: WorkoutRun): Int = nextRest(plan, cursor, run)?.seconds ?: 0

    /**
     * O descanso que concluir o passo do [cursor] vai abrir, com o TIPO (2.299.0) — `null` quando o
     * próximo passo entra direto. É o que a tela usa para o rótulo de antes do toque ("Concluir — depois,
     * 15 s de transição") e para "Próximo: …" no conjugado ("sem descanso — segue para A2").
     */
    fun nextRest(plan: WorkoutPlan, cursor: Cursor, run: WorkoutRun): ScheduledRest? {
        val order = plan.executionOrder()
        val current = order.firstOrNull { it.cursor == cursor } ?: return null
        return restBetween(plan, current, nextStep(order, cursor, skippedIds(run)))
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
        is WorkoutState.InSet -> WorkoutState.Finished(state.plan, withPartialPhase(state, now).copy(finishedAt = now))
        is WorkoutState.Resting -> WorkoutState.Finished(state.plan, state.run.copy(finishedAt = now))
        is WorkoutState.Paused -> {
            val previous = state.previous as? WorkoutState.InSet
            val run = if (previous != null) withPartialPhase(previous, state.pausedAt ?: now) else state.run
            WorkoutState.Finished(state.plan, run.copy(finishedAt = now))
        }
        else -> state
    }

    /** A sessão com a fase de cardio corrente registrada até [at], se ela correu ≥ 1 s (encerrar no meio). */
    private fun withPartialPhase(state: WorkoutState.InSet, at: Instant): WorkoutRun {
        val step = state.plan.stepAt(state.cursor)?.takeIf { it.isCardio } ?: return state.run
        if (elapsedSeconds(state, step, at) < 1) return state.run
        return state.run.copy(sets = state.run.sets + phaseResult(state, step, at, skipped = false, null, null))
    }

    /**
     * Fecha, cada uma no seu instante final, as fases de cardio já vencidas em [now] (regra no KDoc de
     * [WorkoutEvent.Tick]). Fora de uma fase de cardio devolve o próprio estado.
     */
    private fun catchUp(state: WorkoutState, now: Instant): WorkoutState {
        var current = state
        while (current is WorkoutState.InSet) {
            val step = current.plan.stepAt(current.cursor)?.takeIf { it.isCardio } ?: break
            val end = current.setStartedAt + (step.phaseSeconds ?: break).seconds
            if (now < end) break
            current = closePhase(current, step, end, skipped = false, heartRateAvg = null, heartRateMax = null)
        }
        return current
    }

    private fun reduceActive(state: WorkoutState, event: WorkoutEvent, now: Instant): WorkoutState = when (state) {
        is WorkoutState.InSet -> when (event) {
            is WorkoutEvent.CompleteSet -> completeStep(state, event, now)
            WorkoutEvent.SkipPhase -> skipPhase(state, now)
            is WorkoutEvent.SkipExercise -> skipExercise(state.plan, state.cursor, state.run, event, now)
            is WorkoutEvent.SwapExercise -> swapExercise(state, state.plan, state.cursor, state.run, event, now)
            WorkoutEvent.Undo -> undo(state, state.plan, state.run, now)
            else -> state
        }

        is WorkoutState.Resting -> when (event) {
            WorkoutEvent.SkipRest -> enterSet(state.plan, state.cursor, state.run, now)
            is WorkoutEvent.AddRestTime -> state.copy(restEndsAt = state.restEndsAt + event.seconds.seconds)
            is WorkoutEvent.SkipExercise -> skipExercise(state.plan, state.cursor, state.run, event, now)
            is WorkoutEvent.SwapExercise -> swapExercise(state, state.plan, state.cursor, state.run, event, now)
            WorkoutEvent.Undo -> undo(state, state.plan, state.run, now)
            else -> state
        }

        else -> state
    }

    private fun completeStep(state: WorkoutState.InSet, event: WorkoutEvent.CompleteSet, now: Instant): WorkoutState {
        val plan = state.plan
        val order = plan.executionOrder()
        val current = order.firstOrNull { it.cursor == state.cursor } ?: return state
        if (current.isCardio) {
            return closePhase(state, current, now, skipped = false, event.heartRateAvg, event.heartRateMax)
        }
        val performed = state.run.currentExerciseId(current.exercise)
        val swapped = performed != current.exercise.exerciseRefId
        val duration = event.durationSeconds?.takeIf { current.target is SetTarget.Timed && it in SET_DURATION_RANGE }
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
            durationSeconds = duration,
            exerciseRefId = if (swapped) performed else null,
            swappedFromExerciseId = if (swapped) current.exercise.exerciseRefId else null,
        )
        return advance(plan, order, current, state.run.copy(sets = state.run.sets + result), now)
    }

    /**
     * Depois de registrar o passo [current] em [nextRun], no instante [at]: o próximo passo (com ou sem
     * descanso antes) ou `Finished`.
     */
    private fun advance(
        plan: WorkoutPlan,
        order: List<ExecutionStep>,
        current: ExecutionStep,
        nextRun: WorkoutRun,
        at: Instant,
    ): WorkoutState {
        val next = nextStep(order, current.cursor, skippedIds(nextRun))
            ?: return WorkoutState.Finished(plan, nextRun.copy(finishedAt = at))
        val rest = restBetween(plan, current, next)
            ?: return enterSet(plan, next.cursor, nextRun, at)
        return WorkoutState.Resting(plan, next.cursor, nextRun, at + rest.seconds.seconds, rest.kind)
    }

    /** "Pular fase": só numa fase de cardio (regra no KDoc de [WorkoutEvent.SkipPhase]). */
    private fun skipPhase(state: WorkoutState.InSet, now: Instant): WorkoutState {
        val step = state.plan.stepAt(state.cursor)?.takeIf { it.isCardio } ?: return state
        return closePhase(state, step, now, skipped = true, heartRateAvg = null, heartRateMax = null)
    }

    /** Registra a fase de cardio [step] terminada em [at] e anda para o que vem depois, nesse instante. */
    private fun closePhase(
        state: WorkoutState.InSet,
        step: ExecutionStep,
        at: Instant,
        skipped: Boolean,
        heartRateAvg: Int?,
        heartRateMax: Int?,
    ): WorkoutState {
        val result = phaseResult(state, step, at, skipped, heartRateAvg, heartRateMax)
        val end = if (at < state.setStartedAt) state.setStartedAt else at
        return advance(state.plan, state.plan.executionOrder(), step, state.run.copy(sets = state.run.sets + result), end)
    }

    private fun phaseResult(
        state: WorkoutState.InSet,
        step: ExecutionStep,
        at: Instant,
        skipped: Boolean,
        heartRateAvg: Int?,
        heartRateMax: Int?,
    ): SetResult {
        val performed = state.run.currentExerciseId(step.exercise)
        val swapped = performed != step.exercise.exerciseRefId
        // Relógio que voltou para trás não produz fim antes do início (seria NEGATIVE_DURATION).
        val end = if (at < state.setStartedAt) state.setStartedAt else at
        return SetResult(
            blockId = step.blockId,
            exerciseStepId = step.exercise.id,
            setIndex = step.setIndex,
            target = step.target,
            repsDone = null,
            loadDone = null,
            skipped = skipped,
            startedAt = state.setStartedAt,
            completedAt = end,
            heartRateAvg = heartRateAvg,
            heartRateMax = heartRateMax,
            stageIndex = null,
            roundIndex = step.roundIndex,
            durationSeconds = elapsedSeconds(state, step, at),
            exerciseRefId = if (swapped) performed else null,
            swappedFromExerciseId = if (swapped) step.exercise.exerciseRefId else null,
            cardioPhase = step.cardioPhase,
        )
    }

    /** Segundos inteiros que a fase correu até [at], entre 0 e o planejado. */
    private fun elapsedSeconds(state: WorkoutState.InSet, step: ExecutionStep, at: Instant): Int =
        (at - state.setStartedAt).inWholeSeconds.coerceIn(0L, (step.phaseSeconds ?: 0).toLong()).toInt()

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
     * Troca o exercício de um item a partir do cursor (regra no KDoc de [WorkoutEvent.SwapExercise]).
     * O cursor não anda: em `InSet` o relógio da série recomeça em [now]; em `Resting` o descanso segue.
     */
    private fun swapExercise(
        state: WorkoutState,
        plan: WorkoutPlan,
        cursor: Cursor,
        run: WorkoutRun,
        event: WorkoutEvent.SwapExercise,
        now: Instant,
    ): WorkoutState {
        if (event.toExerciseId.isBlank()) return state
        val order = plan.executionOrder()
        val from = order.indexOfFirst { it.cursor == cursor }
        if (from < 0) return state
        val skipped = skippedIds(run)
        val ahead = order.subList(from, order.size)
            .firstOrNull { it.exercise.id == event.stepId && it.exercise.id !in skipped } ?: return state
        val current = run.currentExerciseId(ahead.exercise)
        if (current == event.toExerciseId) return state
        val swap = ExerciseSwap(
            exerciseStepId = event.stepId,
            fromExerciseId = current,
            toExerciseId = event.toExerciseId,
            reason = event.reason,
            at = now,
            setsRecordedBefore = run.sets.size,
        )
        val nextRun = run.copy(swaps = run.swaps + swap)
        return when (state) {
            is WorkoutState.InSet ->
                if (order[from].exercise.id == event.stepId) enterSet(plan, cursor, nextRun, now) else state.copy(run = nextRun)
            is WorkoutState.Resting -> state.copy(run = nextRun)
            else -> state
        }
    }

    /**
     * Desfaz a última AÇÃO registrada, em ordem inversa: uma troca de exercício feita depois do último
     * passo concluído sai primeiro (o cursor fica onde está); senão, desfaz o último passo concluído
     * (série ou estágio) e volta o cursor para ele, sem perder o que já foi registrado antes — a sessão
     * nunca duplica nem perde séries por causa de um "voltar". Voltar a um exercício pulado o devolve ao
     * treino (o pulo dele sai do registro). Sem nada registrado ainda, `Undo` não tem efeito.
     */
    private fun undo(state: WorkoutState, plan: WorkoutPlan, run: WorkoutRun, now: Instant): WorkoutState {
        val lastSwap = run.swaps.lastOrNull()
        if (lastSwap != null && lastSwap.setsRecordedBefore >= run.sets.size) {
            val nextRun = run.copy(swaps = run.swaps.dropLast(1))
            return when (state) {
                is WorkoutState.InSet -> enterSet(plan, state.cursor, nextRun, now)
                is WorkoutState.Resting -> state.copy(run = nextRun)
                else -> state
            }
        }
        val lastSet = run.sets.lastOrNull() ?: return state
        val blockIndex = plan.blocks.indexOfFirst { it.id == lastSet.blockId }
        val exerciseIndex = plan.blocks.getOrNull(blockIndex)?.exercises
            ?.indexOfFirst { it.id == lastSet.exerciseStepId } ?: -1
        val cardio = plan.blocks.getOrNull(blockIndex)?.exercises?.getOrNull(exerciseIndex)?.cardio
        val stage = when (val phase = lastSet.cardioPhase) {
            null -> lastSet.stageIndex ?: 0
            else -> cardio?.phasesPerRound()?.indexOf(phase) ?: -1
        }
        val restored = Cursor(blockIndex, exerciseIndex, lastSet.setIndex, stage)
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
     * Descanso entre o passo concluído e o próximo (tabela no KDoc da classe), ou `null` quando não há.
     * Estágio que não é o último nunca descansa; em bi-set/circuito, dentro da volta só o circuito
     * descansa (a transição), e o descanso de volta é de quem FECHA a volta — o próximo passo é de outra
     * volta ou de outro bloco.
     */
    private fun restBetween(plan: WorkoutPlan, current: ExecutionStep, next: ExecutionStep?): ScheduledRest? {
        if (next == null || !current.isLastStage) return null
        // Fases do mesmo bloco de cardio se emendam: o descanso do intervalado É uma fase.
        val sameCardioItem = current.isCardio &&
            next.cursor.blockIndex == current.cursor.blockIndex &&
            next.cursor.exerciseIndex == current.cursor.exerciseIndex
        if (sameCardioItem) return null
        val sameRound = current.roundIndex != null &&
            next.cursor.blockIndex == current.cursor.blockIndex &&
            next.roundIndex == current.roundIndex
        val (seconds, kind) = when {
            sameRound -> plan.blocks[current.cursor.blockIndex].effectiveTransitionRestSeconds to RestKind.TRANSITION
            current.roundIndex != null -> current.exercise.restSeconds to RestKind.ROUND
            else -> current.exercise.restSeconds to RestKind.SET
        }
        return if (seconds > 0) ScheduledRest(seconds, kind) else null
    }
}
