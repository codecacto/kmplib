package br.com.codecacto.kmplib.workout.metrics

import br.com.codecacto.kmplib.workout.engine.T0
import br.com.codecacto.kmplib.workout.engine.simplePlan
import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun setResult(
    exerciseStepId: String = "ex-1",
    setIndex: Int = 0,
    reps: Int? = 10,
    load: Double? = 20.0,
    skipped: Boolean = false,
    hrAvg: Int? = null,
    hrMax: Int? = null,
    start: kotlinx.datetime.Instant = T0,
    end: kotlinx.datetime.Instant = T0.plus(kotlin.time.Duration.parse("40s")),
) = SetResult(
    blockId = "block-1",
    exerciseStepId = exerciseStepId,
    setIndex = setIndex,
    target = SetTarget.Reps(reps ?: 0, load),
    repsDone = reps,
    loadDone = load,
    skipped = skipped,
    startedAt = start,
    completedAt = end,
    heartRateAvg = hrAvg,
    heartRateMax = hrMax,
)

class MetricsTest {

    @Test
    fun `volume soma reps vezes carga de toda serie com carga conhecida`() {
        val run = WorkoutRun("r1", "plan-1", T0, sets = listOf(setResult(reps = 10, load = 20.0), setResult(setIndex = 1, reps = 8, load = 25.0)))
        assertEquals(10 * 20.0 + 8 * 25.0, run.volumeKg())
    }

    @Test
    fun `serie sem carga nao entra no volume`() {
        val run = WorkoutRun("r1", "plan-1", T0, sets = listOf(setResult(reps = 10, load = null)))
        assertEquals(0.0, run.volumeKg())
    }

    @Test
    fun `fc media e maxima ignoram series sem amostra`() {
        val run = WorkoutRun(
            "r1", "plan-1", T0,
            sets = listOf(
                setResult(setIndex = 0, hrAvg = 120, hrMax = 140),
                setResult(setIndex = 1, hrAvg = null, hrMax = null),
                setResult(setIndex = 2, hrAvg = 130, hrMax = 150),
            ),
        )
        assertEquals(125, run.heartRateAvgOverall())
        assertEquals(150, run.heartRateMaxOverall())
    }

    @Test
    fun `sem nenhuma amostra de fc devolve null`() {
        val run = WorkoutRun("r1", "plan-1", T0, sets = listOf(setResult(hrAvg = null, hrMax = null)))
        assertNull(run.heartRateAvgOverall())
        assertNull(run.heartRateMaxOverall())
    }

    @Test
    fun `tempo sob tensao soma a duracao das series nao puladas`() {
        val run = WorkoutRun(
            "r1", "plan-1", T0,
            sets = listOf(
                setResult(setIndex = 0, start = T0, end = T0.plus(kotlin.time.Duration.parse("30s"))),
                setResult(setIndex = 1, skipped = true, start = T0, end = T0.plus(kotlin.time.Duration.parse("100s"))),
            ),
        )
        assertEquals(30L, run.timeUnderTensionSeconds())
    }

    @Test
    fun `reconciliacao conta planejado e feito por exercicio, sem contar serie pulada`() {
        val plan = simplePlan() // 1 exercicio, 2 series planejadas
        val run = WorkoutRun(
            "r1", plan.id, T0,
            sets = listOf(
                setResult(exerciseStepId = "ex-1", setIndex = 0),
                setResult(exerciseStepId = "ex-1", setIndex = 1, skipped = true),
            ),
        )

        val result = reconcile(plan, run)

        assertEquals(1, result.size)
        assertEquals(2, result[0].plannedSets)
        assertEquals(1, result[0].completedSets)
    }

    @Test
    fun `describe de meta por reps e por tempo`() {
        assertEquals("10 x 20.0 kg", SetTarget.Reps(10, 20.0).describe())
        assertEquals("10 reps", SetTarget.Reps(10, null).describe())
        assertEquals("30 s", SetTarget.Timed(30).describe())
    }

    @Test
    fun `describeDone cobre pulada, com carga, so reps e so a meta`() {
        assertEquals("pulada", setResult(skipped = true).describeDone())
        assertEquals("10 x 20.0 kg", setResult(reps = 10, load = 20.0).describeDone())
        assertEquals("10 reps", setResult(reps = 10, load = null).describeDone())
    }
}
