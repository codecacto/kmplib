package br.com.codecacto.kmplib.workout.protocol

import br.com.codecacto.kmplib.workout.engine.Cursor
import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.engine.dropSetPlan
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtocolTest {

    @Test
    fun `plano com estagios atravessa a ponte sem perder o drop-set`() {
        val snapshot = PlanSnapshot(seq = 1, plan = dropSetPlan())
        val decoded = Json.decodeFromString<PlanSnapshot>(Json.encodeToString(snapshot))
        assertEquals(snapshot, decoded)
        assertEquals(WORKOUT_PROTOCOL_VERSION, decoded.v)
    }

    @Test
    fun `StateSnapshot leva o estagio e o cursor`() {
        val state = StateSnapshot(seq = 2, blockIndex = 0, exerciseIndex = 0, setIndex = 1, phase = StateSnapshot.Phase.IN_SET, stageIndex = 2)
        val decoded = Json.decodeFromString<StateSnapshot>(Json.encodeToString(state))
        assertEquals(Cursor(0, 0, 1, 2), decoded.cursor)
        assertEquals(2, WORKOUT_PROTOCOL_VERSION)
    }

    @Test
    fun `StateSnapshot da versao 1 sem estagio le como estagio 0`() {
        val v1 = """{"seq":3,"blockIndex":1,"exerciseIndex":0,"setIndex":0,"phase":"RESTING","v":1}"""
        assertEquals(Cursor(1, 0, 0, 0), Json.decodeFromString<StateSnapshot>(v1).cursor)
    }

    @Test
    fun `todo CommandEvent vira o WorkoutEvent correspondente`() {
        assertEquals(WorkoutEvent.CompleteSet(10, 20.0), CommandEvent.CompleteSet(10, 20.0).toWorkoutEvent())
        assertEquals(WorkoutEvent.SkipRest, CommandEvent.SkipRest.toWorkoutEvent())
        assertEquals(WorkoutEvent.AddRestTime(15), CommandEvent.AddRestTime(15).toWorkoutEvent())
        assertEquals(WorkoutEvent.SkipExercise("dor"), CommandEvent.SkipExercise("dor").toWorkoutEvent())
        assertEquals(WorkoutEvent.Undo, CommandEvent.Undo.toWorkoutEvent())
        assertEquals(WorkoutEvent.Pause, CommandEvent.Pause.toWorkoutEvent())
        assertEquals(WorkoutEvent.Resume, CommandEvent.Resume.toWorkoutEvent())
        assertEquals(WorkoutEvent.Finish, CommandEvent.Finish.toWorkoutEvent())
    }

    @Test
    fun `SequenceGuard descarta duplicada e atrasada`() {
        val guard = SequenceGuard()
        assertTrue(guard.accept(1))
        assertFalse(guard.accept(1))
        assertTrue(guard.accept(3))
        assertFalse(guard.accept(2))
    }
}
