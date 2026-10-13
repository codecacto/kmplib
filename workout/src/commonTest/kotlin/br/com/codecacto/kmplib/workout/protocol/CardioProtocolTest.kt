package br.com.codecacto.kmplib.workout.protocol

import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.engine.cardioIntervalPlan
import br.com.codecacto.kmplib.workout.model.CardioPhase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Cardio na ponte celular↔relógio (protocolo 4, 2.297.0). */
class CardioProtocolTest {

    @Test
    fun `plano com cardio atravessa a ponte sem perder o bloco`() {
        val snapshot = PlanSnapshot(seq = 1, plan = cardioIntervalPlan())
        assertEquals(snapshot, Json.decodeFromString<PlanSnapshot>(Json.encodeToString(snapshot)))
    }

    @Test
    fun `StateSnapshot leva a fase e o instante final absoluto`() {
        val state = StateSnapshot(
            seq = 5, blockIndex = 0, exerciseIndex = 0, setIndex = 2, phase = StateSnapshot.Phase.IN_SET,
            stageIndex = 1, cardioPhase = CardioPhase.REST, phaseEndsAtEpochMillis = 1_700_000_180_000,
        )
        val json = Json.encodeToString(state)
        val decoded = Json.decodeFromString<StateSnapshot>(json)
        assertEquals(state, decoded)
        assertEquals(CardioPhase.REST, decoded.cardioPhase)
    }

    @Test
    fun `StateSnapshot da versao 3 sem cardio le como forca`() {
        val v3 = """{"seq":3,"blockIndex":0,"exerciseIndex":0,"setIndex":0,"phase":"IN_SET","v":3,"stageIndex":0}"""
        val decoded = Json.decodeFromString<StateSnapshot>(v3)
        assertNull(decoded.cardioPhase)
        assertNull(decoded.phaseEndsAtEpochMillis)
    }

    @Test
    fun `SkipPhase vindo do outro lado vira o evento do motor`() {
        assertEquals(WorkoutEvent.SkipPhase, CommandEvent.SkipPhase.toWorkoutEvent())
        val command = Command(seq = 9, id = "c-9", event = CommandEvent.SkipPhase)
        assertEquals(command, Json.decodeFromString<Command>(Json.encodeToString(command)))
    }
}
