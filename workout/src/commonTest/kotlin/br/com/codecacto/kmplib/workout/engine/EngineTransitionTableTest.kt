package br.com.codecacto.kmplib.workout.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Roda a [ENGINE_TRANSITION_CASES] contra o motor, em todo alvo (JVM, Android, iOS, watchOS). */
class EngineTransitionTableTest {

    private val engine = GuidedWorkoutEngine()

    @Test
    fun `cada caso da tabela evento - estado bate com o motor`() {
        assertTrue(ENGINE_TRANSITION_CASES.isNotEmpty())
        for (case in ENGINE_TRANSITION_CASES) {
            var state: WorkoutState = WorkoutState.Idle
            case.steps.forEachIndexed { index, step ->
                state = engine.reduce(state, step.event.toWorkoutEvent(case), step.instant())
                assertEquals(step.expect, state.project(), "caso \"${case.name}\", passo ${index + 1} (${step.event})")
            }
        }
    }

    @Test
    fun `nomes dos casos sao unicos`() {
        val names = ENGINE_TRANSITION_CASES.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `Start usa o localRunId do Load`() {
        val ready = engine.reduce(WorkoutState.Idle, WorkoutEvent.Load(simplePlan(), "uuid-9"), T0)
        val started = engine.reduce(ready, WorkoutEvent.Start, T0)
        assertEquals("uuid-9", (started as WorkoutState.InSet).run.localId)
    }

    @Test
    fun `Start sem localRunId deriva um id deterministico`() {
        val ready = engine.reduce(WorkoutState.Idle, WorkoutEvent.Load(simplePlan()), T0)
        val a = engine.reduce(ready, WorkoutEvent.Start, T0) as WorkoutState.InSet
        val b = engine.reduce(ready, WorkoutEvent.Start, T0) as WorkoutState.InSet
        assertEquals("plan-1@${T0.toEpochMilliseconds()}", a.run.localId)
        assertEquals(a.run.localId, b.run.localId)
    }

    @Test
    fun `Resume sem instante de pausa volta ao estado anterior sem ajuste`() {
        val inSet = engine.start(simplePlan(), T0, "r")
        val paused = WorkoutState.Paused(inSet.plan(), (inSet as WorkoutState.InSet).cursor, inSet.run, inSet)
        assertEquals(inSet, engine.reduce(paused, WorkoutEvent.Resume, T0 + kotlin.time.Duration.parse("1m")))
    }

    @Test
    fun `Resume com relogio anterior a pausa nao encurta o descanso`() {
        val resting = engine.reduce(engine.start(simplePlan(), T0, "r"), WorkoutEvent.CompleteSet(10, 20.0), T0) as WorkoutState.Resting
        val paused = engine.reduce(resting, WorkoutEvent.Pause, T0 + kotlin.time.Duration.parse("10s"))
        val resumed = engine.reduce(paused, WorkoutEvent.Resume, T0) as WorkoutState.Resting
        assertEquals(resting.restEndsAt, resumed.restEndsAt)
    }

    private fun WorkoutState.plan() = (this as WorkoutState.InSet).plan
}
