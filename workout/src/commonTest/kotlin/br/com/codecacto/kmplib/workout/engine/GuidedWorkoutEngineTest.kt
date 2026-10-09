package br.com.codecacto.kmplib.workout.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tabela evento -> estado esperado: a MESMA máquina roda em Android/iOS/watchOS/backend, então
 * esta suíte é também o caso de teste de referência para a reescrita em Monkey C (Garmin, Onda 3). */
class GuidedWorkoutEngineTest {

    private val engine = GuidedWorkoutEngine()

    @Test
    fun `start entra na primeira serie em InSet`() {
        val plan = simplePlan()
        val state = engine.start(plan, T0, "run-1")

        assertIs<WorkoutState.InSet>(state)
        assertEquals(Cursor(0, 0, 0), state.cursor)
        assertEquals("run-1", state.run.localId)
        assertEquals(T0, state.setStartedAt)
    }

    @Test
    fun `completar serie com descanso pendente entra em Resting com restEndsAt absoluto`() {
        val plan = simplePlan()
        val inSet = engine.start(plan, T0, "run-1")

        val resting = engine.reduce(inSet, WorkoutEvent.CompleteSet(reps = 10, load = 20.0), T0)

        assertIs<WorkoutState.Resting>(resting)
        assertEquals(Cursor(0, 0, 1), resting.cursor)
        assertEquals(T0.plus(kotlin.time.Duration.parse("30s")), resting.restEndsAt)
        assertEquals(1, resting.run.sets.size)
        assertEquals(10, resting.run.sets[0].repsDone)
    }

    @Test
    fun `pular descanso avanca direto para a proxima serie`() {
        val plan = simplePlan()
        val inSet = engine.start(plan, T0, "run-1")
        val resting = engine.reduce(inSet, WorkoutEvent.CompleteSet(10, 20.0), T0)

        val nextSet = engine.reduce(resting, WorkoutEvent.SkipRest, T0.plus(kotlin.time.Duration.parse("5s")))

        assertIs<WorkoutState.InSet>(nextSet)
        assertEquals(Cursor(0, 0, 1), nextSet.cursor)
    }

    @Test
    fun `concluir a ultima serie do plano termina em Finished`() {
        val plan = simplePlan()
        var state: WorkoutState = engine.start(plan, T0, "run-1")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0) // -> Resting
        state = engine.reduce(state, WorkoutEvent.SkipRest, T0) // -> InSet (serie 2)
        state = engine.reduce(state, WorkoutEvent.CompleteSet(9, 20.0), T0) // -> Finished

        assertIs<WorkoutState.Finished>(state)
        assertEquals(2, state.run.sets.size)
        assertEquals(T0, state.run.finishedAt)
    }

    @Test
    fun `avancar entre exercicios normais passa pelo descanso do exercicio de origem`() {
        val plan = twoExercisePlan()
        var state: WorkoutState = engine.start(plan, T0, "run-2")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0)

        assertIs<WorkoutState.Resting>(state)
        assertEquals(Cursor(0, 1, 0), state.cursor)
        assertEquals(T0.plus(kotlin.time.Duration.parse("30s")), state.restEndsAt)
    }

    // Bi-set, circuito e drop-set: tabela própria em MethodExecutionTest (L-WK1, 2.266.0).

    @Test
    fun `pular exercicio registra o motivo e nao conta como serie concluida`() {
        val plan = twoExercisePlan()
        val inSet = engine.start(plan, T0, "run-3")

        val state = engine.reduce(inSet, WorkoutEvent.SkipExercise("dor no joelho"), T0)

        assertIs<WorkoutState.InSet>(state)
        assertEquals(Cursor(0, 1, 0), state.cursor)
        assertEquals(1, state.run.skippedExercises.size)
        assertEquals("ex-1", state.run.skippedExercises[0].exerciseStepId)
        assertEquals("dor no joelho", state.run.skippedExercises[0].reason)
        assertTrue(state.run.sets.isEmpty())
    }

    @Test
    fun `pular o ultimo exercicio termina o treino`() {
        val plan = simplePlan()
        val inSet = engine.start(plan, T0, "run-4")

        val state = engine.reduce(inSet, WorkoutEvent.SkipExercise(), T0)

        assertIs<WorkoutState.Finished>(state)
    }

    @Test
    fun `adicionar tempo de descanso soma ao restEndsAt absoluto`() {
        val plan = simplePlan()
        var state: WorkoutState = engine.start(plan, T0, "run-5")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0)
        assertIs<WorkoutState.Resting>(state)
        val before = state.restEndsAt

        state = engine.reduce(state, WorkoutEvent.AddRestTime(15), T0.plus(kotlin.time.Duration.parse("5s")))

        assertIs<WorkoutState.Resting>(state)
        assertEquals(before.plus(kotlin.time.Duration.parse("15s")), state.restEndsAt)
    }

    @Test
    fun `undo depois de concluir a 1a serie volta para ela sem perder o plano`() {
        val plan = simplePlan()
        var state: WorkoutState = engine.start(plan, T0, "run-6")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0) // Resting, cursor na serie 2

        state = engine.reduce(state, WorkoutEvent.Undo, T0)

        assertIs<WorkoutState.InSet>(state)
        assertEquals(Cursor(0, 0, 0), state.cursor, "Undo restaura o cursor da serie desfeita")
        assertTrue(state.run.sets.isEmpty(), "a serie desfeita sai do registro")
    }

    @Test
    fun `undo sem nenhuma serie concluida nao tem efeito`() {
        val plan = simplePlan()
        val inSet = engine.start(plan, T0, "run-7")

        val state = engine.reduce(inSet, WorkoutEvent.Undo, T0)

        assertEquals(inSet, state, "nada a desfazer: o estado fica igual (inclusive o inicio da serie)")
    }

    @Test
    fun `undo sem serie concluida depois de pular nao volta ao inicio do plano`() {
        val plan = twoExercisePlan()
        val skipped = engine.reduce(engine.start(plan, T0, "run-7b"), WorkoutEvent.SkipExercise(), T0)

        val state = engine.reduce(skipped, WorkoutEvent.Undo, T0)

        assertEquals(skipped, state)
    }

    @Test
    fun `pausar e retomar volta exatamente ao estado anterior`() {
        val plan = simplePlan()
        val inSet = engine.start(plan, T0, "run-8")

        val paused = engine.reduce(inSet, WorkoutEvent.Pause, T0)
        assertIs<WorkoutState.Paused>(paused)

        val resumed = engine.reduce(paused, WorkoutEvent.Resume, T0)
        assertEquals(inSet, resumed)
    }

    @Test
    fun `pausar durante o descanso tambem funciona e retoma no mesmo Resting`() {
        val plan = simplePlan()
        var state: WorkoutState = engine.start(plan, T0, "run-9")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0)
        assertIs<WorkoutState.Resting>(state)
        val resting = state

        val paused = engine.reduce(resting, WorkoutEvent.Pause, T0)
        assertIs<WorkoutState.Paused>(paused)
        val resumed = engine.reduce(paused, WorkoutEvent.Resume, T0)
        assertEquals(resting, resumed)
    }

    @Test
    fun `pausar em Idle ou Ready nao tem efeito - nao ha o que pausar`() {
        assertEquals(WorkoutState.Idle, engine.reduce(WorkoutState.Idle, WorkoutEvent.Pause, T0))
    }

    @Test
    fun `finish de dentro do treino marca finishedAt e preserva as series ja feitas`() {
        val plan = simplePlan()
        var state: WorkoutState = engine.start(plan, T0, "run-10")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0)

        val finishedAt = T0.plus(kotlin.time.Duration.parse("40s"))
        val finished = engine.reduce(state, WorkoutEvent.Finish, finishedAt)

        assertIs<WorkoutState.Finished>(finished)
        assertEquals(finishedAt, finished.run.finishedAt)
        assertEquals(1, finished.run.sets.size)
    }

    @Test
    fun `finish em Idle nao tem efeito`() {
        val result = engine.reduce(WorkoutState.Idle, WorkoutEvent.Finish, T0)
        assertEquals(WorkoutState.Idle, result)
    }

    @Test
    fun `eventos que nao se aplicam ao estado atual sao ignorados sem crash`() {
        val plan = simplePlan()
        val inSet = engine.start(plan, T0, "run-11")

        // SkipRest só se aplica a Resting; em InSet não deve ter efeito.
        val same = engine.reduce(inSet, WorkoutEvent.SkipRest, T0)
        assertEquals(inSet, same)

        // AddRestTime só se aplica a Resting.
        val same2 = engine.reduce(inSet, WorkoutEvent.AddRestTime(10), T0)
        assertEquals(inSet, same2)
    }
}
