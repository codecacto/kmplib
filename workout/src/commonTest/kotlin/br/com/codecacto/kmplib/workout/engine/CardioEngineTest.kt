package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.CardioBlock
import br.com.codecacto.kmplib.workout.model.CardioPhase
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.isCardio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Cardio estruturado no motor (L-WK2, 2.297.0): fases com instante final absoluto. */
class CardioEngineTest {

    private val engine = GuidedWorkoutEngine()

    private fun at(seconds: Int) = T0 + seconds.seconds

    private fun started(plan: WorkoutPlan = cardioIntervalPlan()): WorkoutState.InSet =
        engine.start(plan, T0, "run-c") as WorkoutState.InSet

    // ── Ordem de execução ──

    @Test
    fun `intervalado vira uma fase por passo, volta a volta, trabalho e descanso`() {
        val order = cardioIntervalPlan().executionOrder()
        assertEquals(7, order.size)
        val cardio = order.take(6)
        assertEquals(
            listOf(CardioPhase.WORK, CardioPhase.REST, CardioPhase.WORK, CardioPhase.REST, CardioPhase.WORK, CardioPhase.REST),
            cardio.map { it.cardioPhase },
        )
        assertEquals(listOf(0, 0, 1, 1, 2, 2), cardio.map { it.setIndex })
        assertEquals(listOf(0, 1, 0, 1, 0, 1), cardio.map { it.cursor.stageIndex })
        assertTrue(cardio.all { it.isCardio && it.setCount == 3 && it.stageCount == 2 && it.stageIndex == null })
        assertEquals(listOf(40, 20, 40, 20, 40, 20), cardio.map { it.phaseSeconds })
        assertEquals(SetTarget.Timed(40), cardio[0].target)
        assertEquals(listOf(false, true, false, true, false, true), cardio.map { it.isLastStage })
        val force = order.last()
        assertFalse(force.isCardio)
        assertNull(force.phaseSeconds)
        assertNull(force.cardio)
    }

    @Test
    fun `continuo e uma volta so de trabalho`() {
        val order = cardioContinuousPlan().executionOrder()
        assertEquals(1, order.size)
        assertEquals(CardioPhase.WORK, order[0].cardioPhase)
        assertEquals(300, order[0].phaseSeconds)
        assertTrue(order[0].isLastStage)
    }

    @Test
    fun `cardio sem voltas nao gera passo`() {
        val plan = WorkoutPlan(
            "p", "P",
            listOf(
                Block(
                    "b", WorkoutMethod.NORMAL,
                    listOf(ExerciseStep("x", "run", "Corrida", emptyList(), 0, cardio = CardioBlock.interval(30, 10, 0))),
                ),
            ),
        )
        assertTrue(plan.executionOrder().isEmpty())
        assertIs<WorkoutState.Finished>(engine.start(plan, T0, "r"))
    }

    @Test
    fun `no circuito o cardio roda inteiro na primeira volta e nao conta para as voltas`() {
        val plan = cardioCircuitPlan()
        assertEquals(2, plan.blocks[0].roundCount)
        val order = plan.executionOrder()
        assertEquals(
            listOf("c-a", "c-rope", "c-rope", "c-b", "c-a", "c-b"),
            order.map { it.exercise.id },
        )
        assertEquals(listOf(0, 0, 0, 0, 1, 1), order.map { it.roundIndex })
        assertEquals(listOf(0, 1), order.filter { it.isCardio }.map { it.setIndex })
        // roundExercises: no cardio, só ele; na volta de força, sem o cardio.
        assertEquals(listOf("c-rope"), plan.roundExercises(order[1].cursor).map { it.id })
        assertEquals(listOf("c-a", "c-b"), plan.roundExercises(order[0].cursor).map { it.id })
    }

    @Test
    fun `bloco por volta so de cardio tem uma volta`() {
        val plan = WorkoutPlan(
            "p", "P",
            listOf(
                Block(
                    "b", WorkoutMethod.CIRCUIT,
                    listOf(ExerciseStep("x", "run", "Corrida", emptyList(), 0, cardio = CardioBlock.continuous(60))),
                ),
            ),
        )
        assertEquals(1, plan.blocks[0].roundCount)
        assertEquals(1, plan.executionOrder().size)
        assertTrue(plan.blocks[0].exercises[0].isCardio)
    }

    @Test
    fun `cardio nao entra nas series planejadas`() {
        assertEquals(1, cardioIntervalPlan().plannedSetCount())
        assertEquals(4, cardioCircuitPlan().plannedSetCount())
    }

    @Test
    fun `Cursor set no cardio devolve a fase como Timed, sem estourar o indice`() {
        val plan = cardioIntervalPlan()
        assertEquals(SetTarget.Timed(40), Cursor(0, 0, 2, 0).set(plan))
        assertEquals(SetTarget.Timed(20), Cursor(0, 0, 2, 1).set(plan))
        assertEquals(plan.blocks[0].exercises[1].sets[0], Cursor(0, 1, 0, 0).set(plan))
    }

    // ── Relógio: Tick e instante final absoluto ──

    @Test
    fun `Tick antes do fim nao muda nada`() {
        val state = started()
        assertSame(state, engine.reduce(state, WorkoutEvent.Tick, at(39)))
    }

    @Test
    fun `Tick no fim fecha a fase no instante final e entra na proxima nele`() {
        val next = engine.reduce(started(), WorkoutEvent.Tick, at(43)) as WorkoutState.InSet
        assertEquals(Cursor(0, 0, 0, 1), next.cursor)
        assertEquals(at(40), next.setStartedAt)
        val work = next.run.sets.single()
        assertEquals(CardioPhase.WORK, work.cardioPhase)
        assertEquals(40, work.durationSeconds)
        assertEquals(T0, work.startedAt)
        assertEquals(at(40), work.completedAt)
        assertFalse(work.skipped)
        assertEquals(SetTarget.Timed(40), work.target)
        assertNull(work.repsDone)
    }

    @Test
    fun `um Tick so repoe as voltas que correram em segundo plano e para no descanso do item`() {
        val resting = engine.reduce(started(), WorkoutEvent.Tick, at(250)) as WorkoutState.Resting
        assertEquals(Cursor(0, 1, 0, 0), resting.cursor)
        // 3 × (40 + 20) = 180, mais os 60 s de descanso do item.
        assertEquals(at(240), resting.restEndsAt)
        assertEquals(6, resting.run.sets.size)
        assertEquals(listOf(40, 20, 40, 20, 40, 20), resting.run.sets.map { it.durationSeconds })
        assertEquals(listOf(0, 40, 60, 100, 120, 160).map { at(it) }, resting.run.sets.map { it.startedAt })
        // Tick no descanso entre itens não faz nada: o descanso termina pela tela.
        assertSame(resting, engine.reduce(resting, WorkoutEvent.Tick, at(500)))
    }

    @Test
    fun `continuo sem descanso depois termina o treino no instante final`() {
        val finished = engine.reduce(started(cardioContinuousPlan()), WorkoutEvent.Tick, at(999)) as WorkoutState.Finished
        assertEquals(at(300), finished.run.finishedAt)
        assertEquals(300, finished.run.sets.single().durationSeconds)
    }

    @Test
    fun `fases do mesmo item se emendam e o descanso de volta do bloco nao entra`() {
        assertEquals(0, engine.restAfter(cardioIntervalPlan(), Cursor(0, 0, 0, 0), started().run))
        assertEquals(0, engine.restAfter(cardioIntervalPlan(), Cursor(0, 0, 1, 1), started().run))
        assertEquals(60, engine.restAfter(cardioIntervalPlan(), Cursor(0, 0, 2, 1), started().run))
    }

    @Test
    fun `phaseEndsAt e timeRemaining`() {
        val inPhase = started()
        assertEquals(at(40), engine.phaseEndsAt(inPhase))
        assertEquals(30.seconds, engine.timeRemaining(inPhase, at(10)))
        assertEquals(Duration.ZERO, engine.timeRemaining(inPhase, at(90)))
        val force = engine.start(simplePlan(), T0, "r")
        assertNull(engine.phaseEndsAt(force))
        assertNull(engine.timeRemaining(force, T0))
        val resting = engine.reduce(force, WorkoutEvent.CompleteSet(10, 20.0), at(30)) as WorkoutState.Resting
        assertEquals(resting.restEndsAt, engine.phaseEndsAt(resting))
        assertNull(engine.phaseEndsAt(WorkoutState.Idle))
        assertNull(engine.timeRemaining(engine.reduce(force, WorkoutEvent.Pause, T0), T0))
    }

    // ── Pausa ──

    @Test
    fun `pausa congela o que falta e devolve o tempo parado ao fim da fase`() {
        val paused = engine.reduce(started(), WorkoutEvent.Pause, at(10))
        assertIs<WorkoutState.Paused>(paused)
        assertEquals(30.seconds, engine.timeRemaining(paused, at(500)))
        // Pausado, o relógio não anda.
        assertSame(paused, engine.reduce(paused, WorkoutEvent.Tick, at(500)))
        val resumed = engine.reduce(paused, WorkoutEvent.Resume, at(100)) as WorkoutState.InSet
        assertEquals(at(130), engine.phaseEndsAt(resumed))
        assertEquals(Cursor(0, 0, 0, 0), resumed.cursor)
    }

    @Test
    fun `pausa atrasada pausa a fase em que o aluno de fato estava`() {
        val paused = engine.reduce(started(), WorkoutEvent.Pause, at(45)) as WorkoutState.Paused
        assertEquals(Cursor(0, 0, 0, 1), paused.cursor)
        assertEquals(1, paused.run.sets.size)
        assertEquals(15.seconds, engine.timeRemaining(paused, at(45)))
    }

    @Test
    fun `pausa sem instante devolve o tempo restante pelo now`() {
        val inPhase = started()
        val paused = WorkoutState.Paused(inPhase.plan, inPhase.cursor, inPhase.run, inPhase)
        assertEquals(35.seconds, engine.timeRemaining(paused, at(5)))
    }

    // ── Ações da tela ──

    @Test
    fun `CompleteSet fecha a fase agora com o que correu e grava a FC`() {
        val next = engine.reduce(started(), WorkoutEvent.CompleteSet(12, 50.0, heartRateAvg = 150, heartRateMax = 171), at(25))
            as WorkoutState.InSet
        val work = next.run.sets.single()
        assertEquals(25, work.durationSeconds)
        assertEquals(150, work.heartRateAvg)
        assertEquals(171, work.heartRateMax)
        assertNull(work.repsDone)
        assertNull(work.loadDone)
        assertEquals(at(25), next.setStartedAt)
    }

    @Test
    fun `SkipPhase fecha a fase como pulada - fora do cardio nao faz nada`() {
        val next = engine.reduce(started(), WorkoutEvent.SkipPhase, at(12)) as WorkoutState.InSet
        val work = next.run.sets.single()
        assertTrue(work.skipped)
        assertEquals(12, work.durationSeconds)
        assertEquals(Cursor(0, 0, 0, 1), next.cursor)

        val force = engine.start(simplePlan(), T0, "r")
        assertSame(force, engine.reduce(force, WorkoutEvent.SkipPhase, at(5)))
        val resting = engine.reduce(force, WorkoutEvent.CompleteSet(10, 20.0), at(30))
        assertSame(resting, engine.reduce(resting, WorkoutEvent.SkipPhase, at(31)))
    }

    @Test
    fun `relogio que voltou para tras nao gera fase com duracao negativa`() {
        val state = engine.reduce(started(), WorkoutEvent.Tick, at(41)) as WorkoutState.InSet
        val next = engine.reduce(state, WorkoutEvent.SkipPhase, at(30)) as WorkoutState.InSet
        val rest = next.run.sets.last()
        assertEquals(0, rest.durationSeconds)
        assertEquals(rest.startedAt, rest.completedAt)
        assertEquals(at(40), next.setStartedAt)
    }

    @Test
    fun `Finish no meio grava a fase parcial - com menos de 1 s nao grava`() {
        val finished = engine.reduce(started(), WorkoutEvent.Finish, at(25)) as WorkoutState.Finished
        assertEquals(25, finished.run.sets.single().durationSeconds)
        assertEquals(at(25), finished.run.finishedAt)

        val instant = engine.reduce(started(), WorkoutEvent.Finish, T0) as WorkoutState.Finished
        assertTrue(instant.run.sets.isEmpty())
    }

    @Test
    fun `Finish atrasado grava as fases vencidas inteiras e a corrente parcial`() {
        val finished = engine.reduce(started(), WorkoutEvent.Finish, at(70)) as WorkoutState.Finished
        assertEquals(listOf(40, 20, 10), finished.run.sets.map { it.durationSeconds })
    }

    @Test
    fun `Finish pausado grava a fase ate o instante da pausa`() {
        val paused = engine.reduce(started(), WorkoutEvent.Pause, at(15))
        val finished = engine.reduce(paused, WorkoutEvent.Finish, at(400)) as WorkoutState.Finished
        assertEquals(15, finished.run.sets.single().durationSeconds)
        assertEquals(at(400), finished.run.finishedAt)

        val pausedNoInstant = (started()).let { WorkoutState.Paused(it.plan, it.cursor, it.run, it) }
        val f2 = engine.reduce(pausedNoInstant, WorkoutEvent.Finish, at(7)) as WorkoutState.Finished
        assertEquals(7, f2.run.sets.single().durationSeconds)
    }

    @Test
    fun `Finish pausado no descanso nao grava fase`() {
        val resting = engine.reduce(started(), WorkoutEvent.Tick, at(200))
        val paused = engine.reduce(resting, WorkoutEvent.Pause, at(201))
        val finished = engine.reduce(paused, WorkoutEvent.Finish, at(202)) as WorkoutState.Finished
        assertEquals(6, finished.run.sets.size)
    }

    @Test
    fun `Undo desfaz a ultima fase e a recomeca agora`() {
        val inRest = engine.reduce(started(), WorkoutEvent.Tick, at(41)) as WorkoutState.InSet
        val afterRest = engine.reduce(inRest, WorkoutEvent.SkipPhase, at(45)) as WorkoutState.InSet
        assertEquals(Cursor(0, 0, 1, 0), afterRest.cursor)
        val undone = engine.reduce(afterRest, WorkoutEvent.Undo, at(46)) as WorkoutState.InSet
        assertEquals(Cursor(0, 0, 0, 1), undone.cursor)
        assertEquals(at(46), undone.setStartedAt)
        assertEquals(1, undone.run.sets.size)
        val again = engine.reduce(undone, WorkoutEvent.Undo, at(47)) as WorkoutState.InSet
        assertEquals(Cursor(0, 0, 0, 0), again.cursor)
        assertTrue(again.run.sets.isEmpty())
    }

    @Test
    fun `Undo de fase de um item que mudou de forma no plano nao faz nada`() {
        val inRest = engine.reduce(started(), WorkoutEvent.Tick, at(41)) as WorkoutState.InSet
        // A fase gravada é REST, mas o plano agora não tem descanso no cardio: sem passo para voltar.
        val noRest = cardioIntervalPlan().let { plan ->
            plan.copy(
                blocks = listOf(
                    plan.blocks[0].copy(
                        exercises = listOf(
                            plan.blocks[0].exercises[0].copy(cardio = CardioBlock.interval(40, 0, 3)),
                            plan.blocks[0].exercises[1],
                        ),
                    ),
                ),
            )
        }
        val run = inRest.run.copy(sets = inRest.run.sets + inRest.run.sets[0].copy(cardioPhase = CardioPhase.REST))
        val state = WorkoutState.InSet(noRest, Cursor(0, 0, 1, 0), run, at(60))
        assertSame(state, engine.reduce(state, WorkoutEvent.Undo, at(61)))
    }

    @Test
    fun `SkipExercise pula o resto do bloco de cardio`() {
        val inRest = engine.reduce(started(), WorkoutEvent.Tick, at(41))
        val next = engine.reduce(inRest, WorkoutEvent.SkipExercise("cansado"), at(50)) as WorkoutState.InSet
        assertEquals(Cursor(0, 1, 0, 0), next.cursor)
        assertEquals(listOf("ex-run"), next.run.skippedExercises.map { it.exerciseStepId })
        assertEquals(1, next.run.sets.size)
    }

    @Test
    fun `troca do item de cardio registra o exercicio feito nas fases`() {
        val swapped = engine.reduce(started(), WorkoutEvent.SwapExercise("ex-run", "bike"), at(5)) as WorkoutState.InSet
        assertEquals(at(5), swapped.setStartedAt)
        val next = engine.reduce(swapped, WorkoutEvent.Tick, at(45)) as WorkoutState.InSet
        val work = next.run.sets.single()
        assertEquals("bike", work.exerciseRefId)
        assertEquals("treadmill", work.swappedFromExerciseId)
    }

    @Test
    fun `cardio no circuito anda volta a volta e segue para a forca sem descanso`() {
        var state: WorkoutState = engine.reduce(engine.start(cardioCircuitPlan(), T0, "r"), WorkoutEvent.CompleteSet(null, null), at(30))
        state as WorkoutState.InSet
        assertEquals(Cursor(0, 1, 0, 0), state.cursor)
        state = engine.reduce(state, WorkoutEvent.Tick, at(90))
        state as WorkoutState.InSet
        assertEquals(Cursor(0, 2, 0, 0), state.cursor)
        assertEquals(at(90), state.setStartedAt)
        assertEquals(listOf(0, 0), state.run.sets.filter { it.cardioPhase != null }.map { it.roundIndex })
        // O burpee fecha a volta 0: descansa os 45 s dele.
        val resting = engine.reduce(state, WorkoutEvent.CompleteSet(10, null), at(120)) as WorkoutState.Resting
        assertEquals(Cursor(0, 0, 1, 0), resting.cursor)
        assertEquals(at(165), resting.restEndsAt)
    }

    @Test
    fun `fora de uma fase de cardio o Tick nao tem efeito`() {
        val force = engine.start(simplePlan(), T0, "r")
        assertSame(force, engine.reduce(force, WorkoutEvent.Tick, at(9999)))
        assertSame(WorkoutState.Idle, engine.reduce(WorkoutState.Idle, WorkoutEvent.Tick, T0))
    }
}
