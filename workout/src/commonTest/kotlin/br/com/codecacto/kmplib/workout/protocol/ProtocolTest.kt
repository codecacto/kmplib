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
        assertEquals(3, WORKOUT_PROTOCOL_VERSION)
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

    @Test
    fun `Command - Ack e Metrics atravessam a ponte e levam a versao`() {
        val command = Command(seq = 4, id = "cmd-1", event = CommandEvent.CompleteSet(8, 57.5))
        assertEquals(command, Json.decodeFromString<Command>(Json.encodeToString(command)))
        assertEquals(WORKOUT_PROTOCOL_VERSION, command.v)

        val skip = Command(seq = 5, id = "cmd-2", event = CommandEvent.SkipExercise("dor"))
        assertEquals(skip, Json.decodeFromString<Command>(Json.encodeToString(skip)))

        val ack = Ack(seq = 6, commandId = "cmd-1")
        assertEquals(ack, Json.decodeFromString<Ack>(Json.encodeToString(ack)))

        val metrics = Metrics(seq = 7, heartRate = 132, kcal = 41.5, epochMillis = 1_700_000_000_000)
        val decoded = Json.decodeFromString<Metrics>(Json.encodeToString(metrics))
        assertEquals(metrics, decoded)
        assertEquals(WORKOUT_PROTOCOL_VERSION, decoded.v)
    }

    @Test
    fun `Metrics sem amostra nao inventa zero`() {
        val decoded = Json.decodeFromString<Metrics>("""{"seq":8,"epochMillis":1}""")
        assertEquals(null, decoded.heartRate)
        assertEquals(null, decoded.kcal)
    }
}
