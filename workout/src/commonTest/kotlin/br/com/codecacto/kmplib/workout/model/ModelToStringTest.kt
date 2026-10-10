package br.com.codecacto.kmplib.workout.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Higiene de log (2.272.0): o `toString` de [SetResult], [WorkoutRun] e [SkippedExercise] não imprime
 * dado de saúde — só ids, índices e contagens. `equals` continua estrutural.
 */
class ModelToStringTest {

    private val t0 = Instant.fromEpochSeconds(1_700_000_000)

    private fun result() = SetResult(
        blockId = "block-1",
        exerciseStepId = "ex-1",
        setIndex = 1,
        target = SetTarget.Reps(reps = 12, load = 87.5),
        repsDone = 11,
        loadDone = 92.5,
        startedAt = t0,
        completedAt = t0,
        heartRateAvg = 143,
        heartRateMax = 171,
        stageIndex = 2,
        roundIndex = null,
    )

    private fun run() = WorkoutRun(
        localId = "run-1",
        planId = "plan-1",
        startedAt = t0,
        finishedAt = t0,
        sets = listOf(result(), result().copy(setIndex = 2)),
        skippedExercises = listOf(SkippedExercise("ex-2", "dor no joelho esquerdo", t0)),
        effort = 9,
        comment = "senti tontura no fim",
    )

    /** Valores que não podem aparecer no texto (carga, reps, FC, esforço, comentário, motivo). */
    private val sensiveis = listOf("87.5", "92.5", "11", "12", "143", "171", "tontura", "joelho", "effort=9")

    @Test
    fun `SetResult imprime so ids e indices`() {
        val texto = result().toString()
        assertEquals(
            "SetResult(blockId=block-1, exerciseStepId=ex-1, setIndex=1, stageIndex=2, roundIndex=null, skipped=false, " +
                "hasDuration=false, exerciseRefId=null, swappedFromExerciseId=null)",
            texto,
        )
        sensiveis.forEach { assertFalse(texto.contains(it), "vazou '$it' em $texto") }
    }

    @Test
    fun `WorkoutRun imprime so ids e contagens, nem as series dentro`() {
        val texto = run().toString()
        assertEquals(
            "WorkoutRun(localId=run-1, planId=plan-1, finished=true, sets=2, skippedExercises=1, " +
                "swaps=0, hasEffort=true, hasComment=true)",
            texto,
        )
        sensiveis.forEach { assertFalse(texto.contains(it), "vazou '$it' em $texto") }
        assertFalse(texto.contains("SetResult"), "não descer às séries")
    }

    @Test
    fun `WorkoutRun vazio diz que nao ha esforco nem comentario`() {
        val texto = WorkoutRun(localId = "r", planId = "p", startedAt = t0).toString()
        assertTrue(texto.contains("finished=false"))
        assertTrue(texto.contains("hasEffort=false"))
        assertTrue(texto.contains("hasComment=false"))
    }

    @Test
    fun `SkippedExercise nao imprime o motivo`() {
        val texto = SkippedExercise("ex-2", "dor no joelho esquerdo", t0).toString()
        assertEquals("SkippedExercise(exerciseStepId=ex-2, hasReason=true)", texto)
    }

    @Test
    fun `equals continua estrutural`() {
        assertEquals(result(), result())
        assertEquals(run(), run())
        assertFalse(result() == result().copy(loadDone = 1.0), "carga diferente = resultado diferente")
    }
}
