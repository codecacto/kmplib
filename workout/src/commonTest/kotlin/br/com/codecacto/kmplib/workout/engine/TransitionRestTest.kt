package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.TRANSITION_REST_RANGE
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.protocol.StateSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** Descanso de transição do circuito e o tipo do descanso (2.299.0). */
class TransitionRestTest {

    private val engine = GuidedWorkoutEngine()

    private fun startedRun(plan: WorkoutPlan) =
        (engine.start(plan, T0, "run-1") as WorkoutState.InSet).run

    @Test
    fun `transicao so vale no circuito e fica na faixa`() {
        val ex = listOf(ExerciseStep("a", "a", "A", listOf(repsSet()), 30))
        assertEquals(15, Block("b", WorkoutMethod.CIRCUIT, ex, transitionRestSeconds = 15).effectiveTransitionRestSeconds)
        assertEquals(0, Block("b", WorkoutMethod.CIRCUIT, ex).effectiveTransitionRestSeconds)
        assertEquals(0, Block("b", WorkoutMethod.BI_SET, ex, transitionRestSeconds = 15).effectiveTransitionRestSeconds)
        assertEquals(0, Block("b", WorkoutMethod.NORMAL, ex, transitionRestSeconds = 15).effectiveTransitionRestSeconds)
        assertEquals(0, Block("b", WorkoutMethod.CIRCUIT, ex, transitionRestSeconds = -5).effectiveTransitionRestSeconds)
        assertEquals(
            TRANSITION_REST_RANGE.last,
            Block("b", WorkoutMethod.CIRCUIT, ex, transitionRestSeconds = 9_999).effectiveTransitionRestSeconds,
        )
    }

    @Test
    fun `plano sem o campo no JSON le transicao zero`() {
        val json = """{"id":"p","name":"P","blocks":[{"id":"b","method":"CIRCUIT","exercises":[]}]}"""
        val plan = Json.decodeFromString(WorkoutPlan.serializer(), json)
        assertEquals(0, plan.blocks.single().transitionRestSeconds)
        val back = Json.decodeFromString(WorkoutPlan.serializer(), Json.encodeToString(WorkoutPlan.serializer(), circuitTransitionPlan()))
        assertEquals(15, back.blocks.first().transitionRestSeconds)
    }

    @Test
    fun `nextRest diz o tipo do descanso que o passo vai abrir`() {
        val plan = circuitTransitionPlan()
        val run = startedRun(plan)
        assertEquals(ScheduledRest(15, RestKind.TRANSITION), engine.nextRest(plan, Cursor(0, 0, 0), run))
        assertEquals(ScheduledRest(15, RestKind.TRANSITION), engine.nextRest(plan, Cursor(0, 1, 0), run))
        assertEquals(ScheduledRest(90, RestKind.ROUND), engine.nextRest(plan, Cursor(0, 2, 0), run))
        assertEquals(ScheduledRest(90, RestKind.ROUND), engine.nextRest(plan, Cursor(0, 2, 1), run))
        assertNull(engine.nextRest(plan, Cursor(1, 0, 0), run), "último passo do plano termina sem descanso")
        assertEquals(15, engine.restAfter(plan, Cursor(0, 0, 0), run))
        assertEquals(0, engine.restAfter(plan, Cursor(1, 0, 0), run))
    }

    @Test
    fun `conjugado segue direto dentro da volta e o tipo do fim da volta e ROUND`() {
        val plan = triSetPlan()
        val run = startedRun(plan)
        assertNull(engine.nextRest(plan, Cursor(0, 0, 0), run))
        assertNull(engine.nextRest(plan, Cursor(0, 1, 0), run))
        assertEquals(ScheduledRest(90, RestKind.ROUND), engine.nextRest(plan, Cursor(0, 2, 0), run))
    }

    @Test
    fun `serie normal descansa com tipo SET e circuito sem transicao segue direto`() {
        val normal = simplePlan()
        assertEquals(RestKind.SET, engine.nextRest(normal, Cursor(0, 0, 0), startedRun(normal))?.kind)
        val circuit = circuitPlan()
        assertNull(engine.nextRest(circuit, Cursor(0, 0, 0), startedRun(circuit)))
    }

    @Test
    fun `transicao com o proximo exercicio pulado vai para quem sobrou na volta`() {
        val plan = circuitTransitionPlan()
        var state: WorkoutState = engine.start(plan, T0, "run-1")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(null, null), T0 + 30.seconds)
        assertIs<WorkoutState.Resting>(state)
        state = engine.reduce(state, WorkoutEvent.SkipRest, T0 + 45.seconds)
        // Pula a B: vai direto para a C, sem descanso (quem pulou não fez esforço a recuperar).
        state = engine.reduce(state, WorkoutEvent.SkipExercise("sem banco"), T0 + 50.seconds)
        assertIs<WorkoutState.InSet>(state)
        assertEquals(Cursor(0, 2, 0), state.cursor)
        // Volta 1: A conclui e a transição leva à C, pois a B saiu das voltas que faltam.
        state = engine.reduce(state, WorkoutEvent.CompleteSet(15, null), T0 + 80.seconds)
        state = engine.reduce(state, WorkoutEvent.SkipRest, T0 + 170.seconds)
        state = engine.reduce(state, WorkoutEvent.CompleteSet(null, null), T0 + 200.seconds)
        assertIs<WorkoutState.Resting>(state)
        assertEquals(Cursor(0, 2, 1), state.cursor)
        assertEquals(RestKind.TRANSITION, state.kind)
        assertEquals(T0 + 215.seconds, state.restEndsAt)
    }

    @Test
    fun `pausa e mais tempo preservam o tipo do descanso`() {
        val plan = circuitTransitionPlan()
        var state: WorkoutState = engine.start(plan, T0, "run-1")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(null, null), T0 + 30.seconds)
        state = engine.reduce(state, WorkoutEvent.AddRestTime(10), T0 + 31.seconds)
        state = engine.reduce(state, WorkoutEvent.Pause, T0 + 32.seconds)
        state = engine.reduce(state, WorkoutEvent.Resume, T0 + 42.seconds)
        assertIs<WorkoutState.Resting>(state)
        assertEquals(RestKind.TRANSITION, state.kind)
        assertEquals(T0 + 65.seconds, state.restEndsAt)
    }

    @Test
    fun `instantaneo leva o tipo do descanso e le nulo quando ausente`() {
        val snap = StateSnapshot(seq = 3, blockIndex = 0, exerciseIndex = 1, setIndex = 0, phase = StateSnapshot.Phase.RESTING, restKind = RestKind.TRANSITION)
        val decoded = Json.decodeFromString(StateSnapshot.serializer(), Json.encodeToString(StateSnapshot.serializer(), snap))
        assertEquals(RestKind.TRANSITION, decoded.restKind)
        val old = Json.decodeFromString(
            StateSnapshot.serializer(),
            """{"seq":1,"blockIndex":0,"exerciseIndex":0,"setIndex":0,"phase":"RESTING","v":4}""",
        )
        assertNull(old.restKind)
    }
}
