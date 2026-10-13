package br.com.codecacto.kmplib.workout.cardio

import br.com.codecacto.kmplib.workout.engine.GuidedWorkoutEngine
import br.com.codecacto.kmplib.workout.engine.T0
import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.engine.WorkoutState
import br.com.codecacto.kmplib.workout.engine.cardioIntervalPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** O `cardioResult` do envio da sessão (A15b), agregado das fases que o motor gravou. */
class CardioResultTest {

    private val engine = GuidedWorkoutEngine()
    private fun at(s: Int) = T0 + s.seconds

    /** W0 inteira (Tick), R0 pulada aos 10 s, W1 30 s com FC, R1 5 s com FC, W2 encerrada aos 10 s. */
    private fun mixedRun() = run {
        val plan = cardioIntervalPlan()
        var s: WorkoutState = engine.start(plan, T0, "r")
        s = engine.reduce(s, WorkoutEvent.Tick, at(41))
        s = engine.reduce(s, WorkoutEvent.SkipPhase, at(50))
        s = engine.reduce(s, WorkoutEvent.CompleteSet(null, null, heartRateAvg = 150, heartRateMax = 170), at(80))
        s = engine.reduce(s, WorkoutEvent.CompleteSet(null, null, heartRateAvg = 120, heartRateMax = 130), at(85))
        s = engine.reduce(s, WorkoutEvent.Finish, at(95))
        plan to (s as WorkoutState.Finished).run
    }

    @Test
    fun `agrega voltas, segundos por fase, puladas e FC`() {
        val (plan, run) = mixedRun()
        assertEquals(
            CardioResult(
                completedRounds = 1,
                workSecondsDone = 80,
                restSecondsDone = 15,
                skippedPhases = 1,
                avgHr = 135,
                maxHr = 170,
                timeInTargetZoneSeconds = 22,
            ),
            run.cardioResult(plan, "ex-run", timeInTargetZoneSeconds = 22),
        )
    }

    @Test
    fun `FC do sensor vence a calculada`() {
        val (plan, run) = mixedRun()
        val result = run.cardioResult(plan, "ex-run", heartRateAvg = 141, heartRateMax = 181)!!
        assertEquals(141, result.avgHr)
        assertEquals(181, result.maxHr)
    }

    @Test
    fun `item sem fase registrada devolve o agregado zerado, nao nulo`() {
        val plan = cardioIntervalPlan()
        val run = (engine.start(plan, T0, "r") as WorkoutState.InSet).run
        assertEquals(CardioResult(0, 0, 0, 0), run.cardioResult(plan, "ex-run"))
    }

    @Test
    fun `item de forca ou desconhecido nao tem resultado de cardio`() {
        val (plan, run) = mixedRun()
        assertNull(run.cardioResult(plan, "ex-1"))
        assertNull(run.cardioResult(plan, "fantasma"))
    }

    @Test
    fun `voltas completas contam cada volta uma vez, todas cheias`() {
        val plan = cardioIntervalPlan()
        val s = engine.reduce(engine.start(plan, T0, "r"), WorkoutEvent.Tick, at(500))
        assertEquals(
            CardioResult(3, 120, 60, 0),
            (s as WorkoutState.Resting).run.cardioResult(plan, "ex-run"),
        )
    }

    @Test
    fun `JSON tem os nomes do contrato e toString nao leva FC`() {
        val result = CardioResult(3, 120, 60, 1, avgHr = 151, maxHr = 177, timeInTargetZoneSeconds = 99)
        val keys = Json.parseToJsonElement(Json.encodeToString(result)).jsonObject.keys
        assertEquals(
            setOf("completedRounds", "workSecondsDone", "restSecondsDone", "skippedPhases", "avgHr", "maxHr", "timeInTargetZoneSeconds"),
            keys,
        )
        val text = result.toString()
        listOf("151", "177", "99").forEach { assertFalse(text.contains(it), "vazou $it em $text") }
        assertTrue(text.contains("hasHeartRate=true"))
        assertTrue(CardioResult(0, 0, 0, 0).toString().contains("hasHeartRate=false, hasTimeInZone=false"))
    }
}
