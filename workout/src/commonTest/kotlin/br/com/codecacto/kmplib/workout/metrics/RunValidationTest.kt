package br.com.codecacto.kmplib.workout.metrics

import br.com.codecacto.kmplib.workout.engine.GuidedWorkoutEngine
import br.com.codecacto.kmplib.workout.engine.T0
import br.com.codecacto.kmplib.workout.engine.WorkoutEvent
import br.com.codecacto.kmplib.workout.engine.WorkoutState
import br.com.codecacto.kmplib.workout.engine.biSetPlan
import br.com.codecacto.kmplib.workout.engine.dropSetPlan
import br.com.codecacto.kmplib.workout.engine.simplePlan
import br.com.codecacto.kmplib.workout.model.SkippedExercise
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A régua do backend (A5, `INVALID_SESSION`): sessão produzida pelo motor sempre passa. */
class RunValidationTest {

    private val engine = GuidedWorkoutEngine()

    private fun runAll(plan: WorkoutPlan): WorkoutRun {
        var state: WorkoutState = engine.start(plan, T0, "r")
        var now = T0
        while (state !is WorkoutState.Finished) {
            now += 5.seconds
            state = when (state) {
                is WorkoutState.Resting -> engine.reduce(state, WorkoutEvent.SkipRest, now)
                else -> engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), now)
            }
        }
        return state.run
    }

    @Test
    fun `sessao completa do motor e valida em todos os metodos`() {
        for (plan in listOf(simplePlan(), biSetPlan(), dropSetPlan())) {
            assertTrue(validateRun(plan, runAll(plan)).isEmpty(), plan.id)
        }
    }

    @Test
    fun `sessao parcial e valida`() {
        val plan = biSetPlan()
        val run = runAll(plan)
        assertTrue(validateRun(plan, run.copy(sets = run.sets.take(3))).isEmpty())
    }

    @Test
    fun `plano diferente passo duplicado passo inexistente e duracao negativa`() {
        val plan = dropSetPlan()
        val run = runAll(plan)
        val first = run.sets[0]
        val bad = run.copy(
            planId = "outro",
            sets = run.sets + first + first.copy(stageIndex = 7) + first.copy(stageIndex = null) +
                first.copy(roundIndex = 0) + run.sets[1].copy(completedAt = T0 - 1.seconds, startedAt = T0),
            skippedExercises = listOf(SkippedExercise("fantasma", null, T0), SkippedExercise("ex-1", null, T0)),
        )

        val issues = validateRun(plan, bad)

        val n = run.sets.size
        assertEquals(RunIssue.PlanMismatch("plan-dropset", "outro"), issues[0])
        assertTrue(RunIssue.DuplicateStep(n) in issues)
        assertTrue(RunIssue.UnknownStep(n + 1) in issues, "estágio fora do intervalo")
        assertTrue(RunIssue.UnknownStep(n + 2) in issues, "série com estágios sem stageIndex")
        assertTrue(RunIssue.UnknownStep(n + 3) in issues, "volta em bloco que não é por volta")
        assertTrue(RunIssue.DuplicateStep(n + 4) in issues)
        assertTrue(RunIssue.NegativeDuration(n + 4) in issues)
        assertTrue(RunIssue.UnknownSkippedExercise("fantasma") in issues)
        assertEquals(1, issues.count { it is RunIssue.UnknownSkippedExercise })
    }

    @Test
    fun `bi-set com roundIndex errado e recusado`() {
        val plan = biSetPlan()
        val run = runAll(plan)
        val issues = validateRun(plan, run.copy(sets = listOf(run.sets[0].copy(roundIndex = 2))))
        assertIs<RunIssue.UnknownStep>(issues.single())
    }
}
