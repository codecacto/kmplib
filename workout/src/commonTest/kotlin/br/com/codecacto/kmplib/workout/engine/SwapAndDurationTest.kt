package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.metrics.RunIssue
import br.com.codecacto.kmplib.workout.metrics.timeUnderTensionSeconds
import br.com.codecacto.kmplib.workout.metrics.validateRun
import br.com.codecacto.kmplib.workout.model.ExerciseSwap
import br.com.codecacto.kmplib.workout.model.SET_DURATION_RANGE
import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import br.com.codecacto.kmplib.workout.model.currentExerciseId
import br.com.codecacto.kmplib.workout.model.effectiveDurationSeconds
import br.com.codecacto.kmplib.workout.protocol.Command
import br.com.codecacto.kmplib.workout.protocol.CommandEvent
import br.com.codecacto.kmplib.workout.protocol.toWorkoutEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * 2.275.0 — duração informada da série por tempo e troca de exercício no cursor.
 *
 * | Caso | Esperado |
 * |---|---|
 * | `CompleteSet(durationSeconds=40)` em série `Timed` | `SetResult.durationSeconds = 40`; TUT usa 40, não a diferença de instantes |
 * | duração em série por repetições | não gravada (null) — o motor não produz sessão inválida |
 * | duração 0 ou 3601 | não gravada; o `validateRun` aponta `INVALID_DURATION` se vier assim de fora |
 * | `SwapExercise(item, B)` em `InSet` do item | cursor fica; relógio da série recomeça; séries seguintes com `exerciseRefId=B`, `swappedFromExerciseId=A` |
 * | troca encadeada A→B→C | série registra C trocado de A |
 * | troca de volta para A | série volta a ser do plano (os dois campos nulos) |
 * | troca para o mesmo exercício / item inexistente / item já concluído ou pulado / id em branco | sem efeito |
 * | `SwapExercise` no descanso | o descanso segue; o próximo passo já é do substituto |
 * | `Undo` logo depois da troca | desfaz a troca (o cursor fica) |
 * | `Undo` depois de uma série feita com o substituto | desfaz a série; a troca fica |
 * | `validateRun`: troca para o mesmo, original errado, troca sem o feito, feito diferente sem troca | `INVALID_SWAP` |
 * | `validateRun`: registro de troca de item fora do plano ou de A para A | `INVALID_SWAP_RECORD` |
 */
class SwapAndDurationTest {

    private val engine = GuidedWorkoutEngine()

    private fun started(plan: br.com.codecacto.kmplib.workout.model.WorkoutPlan) = engine.start(plan, T0, "r")

    // ── duração ──

    @Test
    fun `duracao informada vale na serie por tempo e tem prioridade no tempo sob tensao`() {
        val s = engine.reduce(started(timedPlan()), WorkoutEvent.CompleteSet(null, null, durationSeconds = 40), T0 + 70.seconds)
        val set = assertIs<WorkoutState.Resting>(s).run.sets.single()
        assertEquals(40, set.durationSeconds)
        assertEquals(40, set.effectiveDurationSeconds)
        assertEquals(40, s.run.timeUnderTensionSeconds())
        assertEquals(emptyList(), validateRun(timedPlan(), s.run))
    }

    @Test
    fun `sem duracao informada o tempo e a diferenca de instantes`() {
        val s = engine.reduce(started(timedPlan()), WorkoutEvent.CompleteSet(null, null), T0 + 70.seconds) as WorkoutState.Resting
        assertNull(s.run.sets.single().durationSeconds)
        assertEquals(70, s.run.timeUnderTensionSeconds())
    }

    @Test
    fun `duracao em serie por repeticoes ou fora da faixa nao e gravada`() {
        val reps = engine.reduce(started(simplePlan()), WorkoutEvent.CompleteSet(10, 20.0, durationSeconds = 30), T0 + 30.seconds)
        assertNull((reps as WorkoutState.Resting).run.sets.single().durationSeconds)
        for (bad in listOf(0, -5, 3601)) {
            val s = engine.reduce(started(timedPlan()), WorkoutEvent.CompleteSet(null, null, durationSeconds = bad), T0 + 50.seconds)
            assertNull((s as WorkoutState.Resting).run.sets.single().durationSeconds, "duração $bad")
        }
        assertEquals(1..3600, SET_DURATION_RANGE)
    }

    @Test
    fun `validateRun aponta duracao fora da faixa ou em serie que nao e por tempo`() {
        val plan = timedPlan()
        fun set(ex: String, index: Int, seconds: Int?) = SetResult(
            "block-1", ex, index, plan.blocks[0].exercises.first { it.id == ex }.sets[index], null, null,
            startedAt = T0, completedAt = T0 + 60.seconds, durationSeconds = seconds,
        )
        val run = WorkoutRun(
            "r", plan.id, T0,
            sets = listOf(set("ex-t", 0, 1), set("ex-t", 1, 3600), set("ex-r", 0, 30)),
        )
        assertEquals(listOf<RunIssue>(RunIssue.InvalidDuration(2)), validateRun(plan, run))
        val outOfRange = run.copy(sets = listOf(set("ex-t", 0, 0), set("ex-t", 1, 3601)))
        assertEquals(listOf<RunIssue>(RunIssue.InvalidDuration(0), RunIssue.InvalidDuration(1)), validateRun(plan, outOfRange))
    }

    @Test
    fun `instante invertido continua NEGATIVE_DURATION mesmo com duracao informada`() {
        val plan = timedPlan()
        val set = SetResult(
            "block-1", "ex-t", 0, plan.blocks[0].exercises[0].sets[0], null, null,
            startedAt = T0 + 10.seconds, completedAt = T0, durationSeconds = 30,
        )
        assertEquals(listOf<RunIssue>(RunIssue.NegativeDuration(0)), validateRun(plan, WorkoutRun("r", plan.id, T0, sets = listOf(set))))
        assertEquals(30, set.effectiveDurationSeconds)
        assertEquals(0, set.copy(durationSeconds = null).effectiveDurationSeconds, "nunca negativa")
    }

    @Test
    fun `duracao atravessa a ponte do relogio`() {
        val cmd = Command(seq = 1, id = "c", event = CommandEvent.CompleteSet(null, null, durationSeconds = 42))
        val back = Json.decodeFromString(Command.serializer(), Json.encodeToString(Command.serializer(), cmd))
        assertEquals(cmd, back)
        assertEquals(WorkoutEvent.CompleteSet(null, null, durationSeconds = 42), back.event.toWorkoutEvent())
        val v2 = """{"seq":1,"id":"c","event":{"type":"br.com.codecacto.kmplib.workout.protocol.CommandEvent.CompleteSet","reps":8,"load":null},"v":2}"""
        assertNull((Json.decodeFromString(Command.serializer(), v2).event as CommandEvent.CompleteSet).durationSeconds)
    }

    // ── troca ──

    @Test
    fun `troca no InSet do item mantem o cursor - recomeca a serie e registra o substituto`() {
        val s0 = started(simplePlan())
        val s1 = engine.reduce(s0, WorkoutEvent.SwapExercise("ex-1", "leg-press", "aparelho ocupado"), T0 + 5.seconds)
        val inSet = assertIs<WorkoutState.InSet>(s1)
        assertEquals(Cursor(0, 0, 0), inSet.cursor)
        assertEquals(T0 + 5.seconds, inSet.setStartedAt)
        assertEquals(listOf(ExerciseSwap("ex-1", "squat", "leg-press", "aparelho ocupado", T0 + 5.seconds, 0)), inSet.run.swaps)
        assertEquals("leg-press", inSet.run.currentExerciseId(simplePlan().blocks[0].exercises[0]))

        val s2 = engine.reduce(s1, WorkoutEvent.CompleteSet(10, 50.0), T0 + 35.seconds) as WorkoutState.Resting
        val set = s2.run.sets.single()
        assertEquals("ex-1", set.exerciseStepId)
        assertEquals("leg-press", set.exerciseRefId)
        assertEquals("squat", set.swappedFromExerciseId)
        assertEquals(T0 + 65.seconds, s2.restEndsAt, "o descanso é o do item")
        assertEquals(simplePlan().blocks[0].exercises[0].sets[0], set.target, "a prescrição é a do item")
        assertEquals(emptyList(), validateRun(simplePlan(), s2.run))
    }

    @Test
    fun `troca encadeada registra o ultimo trocado do original e voltar ao original limpa`() {
        var s: WorkoutState = started(simplePlan())
        s = engine.reduce(s, WorkoutEvent.SwapExercise("ex-1", "leg-press"), T0)
        s = engine.reduce(s, WorkoutEvent.SwapExercise("ex-1", "hack"), T0)
        s = engine.reduce(s, WorkoutEvent.CompleteSet(10, 20.0), T0 + 30.seconds)
        val first = (s as WorkoutState.Resting).run.sets.single()
        assertEquals("hack" to "squat", first.exerciseRefId to first.swappedFromExerciseId)
        assertEquals("leg-press", s.run.swaps[1].fromExerciseId)

        s = engine.reduce(s, WorkoutEvent.SwapExercise("ex-1", "squat"), T0 + 35.seconds)
        s = engine.reduce(s, WorkoutEvent.SkipRest, T0 + 40.seconds)
        s = engine.reduce(s, WorkoutEvent.CompleteSet(10, 20.0), T0 + 70.seconds)
        val second = (s as WorkoutState.Finished).run.sets[1]
        assertNull(second.exerciseRefId)
        assertNull(second.swappedFromExerciseId)
        assertEquals(emptyList(), validateRun(simplePlan(), s.run))
    }

    @Test
    fun `troca sem efeito - mesmo exercicio - item inexistente - id em branco - item concluido ou pulado`() {
        val s0 = started(simplePlan())
        assertSame(s0, engine.reduce(s0, WorkoutEvent.SwapExercise("ex-1", "squat"), T0))
        assertSame(s0, engine.reduce(s0, WorkoutEvent.SwapExercise("ex-9", "leg-press"), T0))
        assertSame(s0, engine.reduce(s0, WorkoutEvent.SwapExercise("ex-1", " "), T0))

        val two = started(twoExercisePlan())
        val done = engine.reduce(two, WorkoutEvent.CompleteSet(10, 20.0), T0 + 30.seconds) // ex-1 concluído
        assertSame(done, engine.reduce(done, WorkoutEvent.SwapExercise("ex-1", "leg-press"), T0 + 31.seconds))

        val circuit = engine.reduce(started(circuitPlan()), WorkoutEvent.CompleteSet(null, null), T0 + 30.seconds)
        val skipped = engine.reduce(circuit, WorkoutEvent.SkipExercise(), T0 + 31.seconds) // pula c-b
        assertSame(skipped, engine.reduce(skipped, WorkoutEvent.SwapExercise("c-b", "dips"), T0 + 32.seconds))
        assertSame(WorkoutState.Idle, engine.reduce(WorkoutState.Idle, WorkoutEvent.SwapExercise("ex-1", "x"), T0))
    }

    @Test
    fun `troca no descanso mantem o descanso e vale para o proximo passo`() {
        val resting = engine.reduce(started(simplePlan()), WorkoutEvent.CompleteSet(10, 20.0), T0 + 30.seconds) as WorkoutState.Resting
        val swapped = engine.reduce(resting, WorkoutEvent.SwapExercise("ex-1", "leg-press"), T0 + 40.seconds)
        assertEquals(resting.copy(run = (swapped as WorkoutState.Resting).run), swapped)
        val s = engine.reduce(engine.reduce(swapped, WorkoutEvent.SkipRest, T0 + 60.seconds), WorkoutEvent.CompleteSet(10, 20.0), T0 + 90.seconds)
        val sets = (s as WorkoutState.Finished).run.sets
        assertNull(sets[0].exerciseRefId, "o que já foi feito não muda")
        assertEquals("leg-press", sets[1].exerciseRefId)
    }

    @Test
    fun `troca de outro exercicio da volta no InSet nao recomeca a serie corrente`() {
        val s0 = started(biSetPlan()) as WorkoutState.InSet
        val s1 = engine.reduce(s0, WorkoutEvent.SwapExercise("ex-2", "pushdown"), T0 + 5.seconds) as WorkoutState.InSet
        assertEquals(s0.setStartedAt, s1.setStartedAt)
        assertEquals(s0.cursor, s1.cursor)
        var s: WorkoutState = engine.reduce(s1, WorkoutEvent.CompleteSet(10, 20.0), T0 + 30.seconds)
        s = engine.reduce(s, WorkoutEvent.CompleteSet(12, 15.0), T0 + 60.seconds)
        val sets = (s as WorkoutState.Resting).run.sets
        assertEquals(listOf(null, "pushdown"), sets.map { it.exerciseRefId })
        assertEquals(listOf(null, "triceps"), sets.map { it.swappedFromExerciseId })
        assertEquals(emptyList(), validateRun(biSetPlan(), s.run))
    }

    @Test
    fun `undo desfaz a troca primeiro e depois a serie - em ordem inversa`() {
        val s0 = started(simplePlan())
        val swapped = engine.reduce(s0, WorkoutEvent.SwapExercise("ex-1", "leg-press"), T0 + 5.seconds)
        val undone = engine.reduce(swapped, WorkoutEvent.Undo, T0 + 6.seconds) as WorkoutState.InSet
        assertTrue(undone.run.swaps.isEmpty())
        assertEquals(Cursor(0, 0, 0), undone.cursor)

        var s: WorkoutState = engine.reduce(swapped, WorkoutEvent.CompleteSet(10, 20.0), T0 + 30.seconds)
        s = engine.reduce(s, WorkoutEvent.Undo, T0 + 31.seconds) // desfaz a série; a troca fica
        assertIs<WorkoutState.InSet>(s)
        assertTrue(s.run.sets.isEmpty())
        assertEquals(1, s.run.swaps.size)
        s = engine.reduce(s, WorkoutEvent.Undo, T0 + 32.seconds) // agora a troca
        assertTrue((s as WorkoutState.InSet).run.swaps.isEmpty())

        val resting = engine.reduce(started(simplePlan()), WorkoutEvent.CompleteSet(10, 20.0), T0 + 30.seconds) as WorkoutState.Resting
        val restingSwapped = engine.reduce(resting, WorkoutEvent.SwapExercise("ex-1", "leg-press"), T0 + 35.seconds)
        assertEquals(resting, engine.reduce(restingSwapped, WorkoutEvent.Undo, T0 + 36.seconds), "no descanso, desfaz só a troca")
    }

    @Test
    fun `validateRun aponta troca inconsistente na serie`() {
        val plan = simplePlan()
        fun set(index: Int, performed: String?, from: String?) = SetResult(
            "block-1", "ex-1", index, plan.blocks[0].exercises[0].sets[index], 10, 20.0,
            startedAt = T0, completedAt = T0 + 30.seconds, exerciseRefId = performed, swappedFromExerciseId = from,
        )
        val ok = WorkoutRun("r", plan.id, T0, sets = listOf(set(0, "squat", null), set(1, "leg-press", "squat")))
        assertEquals(emptyList(), validateRun(plan, ok), "o feito igual ao plano sem troca é redundante, mas válido")
        val cases = listOf(
            set(0, "squat", "squat"), // troca para o mesmo
            set(0, "leg-press", "hack"), // original que não é o do item
            set(0, null, "squat"), // troca sem o feito
            set(0, "leg-press", null), // troca não declarada
        )
        cases.forEachIndexed { i, s ->
            assertEquals(listOf<RunIssue>(RunIssue.InvalidSwap(0)), validateRun(plan, WorkoutRun("r", plan.id, T0, sets = listOf(s))), "caso $i")
        }
    }

    @Test
    fun `validateRun aponta registro de troca invalido`() {
        val plan = simplePlan()
        val run = WorkoutRun(
            "r", plan.id, T0,
            swaps = listOf(
                ExerciseSwap("ex-1", "squat", "leg-press", null, T0, 0),
                ExerciseSwap("ex-9", "x", "y", null, T0, 0),
                ExerciseSwap("ex-1", "leg-press", "leg-press", null, T0, 0),
            ),
        )
        assertEquals(listOf<RunIssue>(RunIssue.InvalidSwapRecord(1), RunIssue.InvalidSwapRecord(2)), validateRun(plan, run))
    }

    @Test
    fun `troca atravessa a ponte e o JSON do RunIssue usa os nomes novos`() {
        val cmd = Command(seq = 1, id = "c", event = CommandEvent.SwapExercise("ex-1", "leg-press", "ocupado"))
        val back = Json.decodeFromString(Command.serializer(), Json.encodeToString(Command.serializer(), cmd))
        assertEquals(cmd, back)
        assertEquals(WorkoutEvent.SwapExercise("ex-1", "leg-press", "ocupado"), back.event.toWorkoutEvent())
        val text = Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RunIssue.serializer()),
            listOf(RunIssue.InvalidDuration(0), RunIssue.InvalidSwap(1), RunIssue.InvalidSwapRecord(2)),
        )
        assertTrue("\"INVALID_DURATION\"" in text && "\"INVALID_SWAP\"" in text && "\"INVALID_SWAP_RECORD\"" in text, text)
    }

    @Test
    fun `run antigo sem os campos novos ainda le`() {
        val old = """{"localId":"r","planId":"p","startedAt":"2023-11-14T22:13:20Z","sets":[{"blockId":"b","exerciseStepId":"e","setIndex":0,""" +
            """"target":{"type":"br.com.codecacto.kmplib.workout.model.SetTarget.Timed","seconds":30},"repsDone":null,"loadDone":null,""" +
            """"startedAt":"2023-11-14T22:13:20Z","completedAt":"2023-11-14T22:13:50Z"}]}"""
        val run = Json.decodeFromString(WorkoutRun.serializer(), old)
        assertTrue(run.swaps.isEmpty())
        assertNull(run.sets.single().durationSeconds)
        assertEquals(30, run.sets.single().effectiveDurationSeconds)
    }

    // ── toString sem dado de saúde ──

    @Test
    fun `toString dos campos novos so com flags e ids`() {
        assertEquals(
            "CompleteSet(hasReps=false, hasLoad=false, hasHeartRate=false, hasDuration=true)",
            WorkoutEvent.CompleteSet(null, null, durationSeconds = 37).toString(),
        )
        assertEquals("CompleteSet(hasReps=false, hasLoad=false, hasDuration=true)", CommandEvent.CompleteSet(null, null, 37).toString())
        val swap = WorkoutEvent.SwapExercise("ex-1", "leg-press", "dor no ombro")
        assertEquals("SwapExercise(stepId=ex-1, toExerciseId=leg-press, hasReason=true)", swap.toString())
        assertFalse("ombro" in CommandEvent.SwapExercise("ex-1", "leg-press", "dor no ombro").toString())
        val record = ExerciseSwap("ex-1", "squat", "leg-press", "dor no ombro", T0, 0)
        assertEquals("ExerciseSwap(exerciseStepId=ex-1, fromExerciseId=squat, toExerciseId=leg-press, hasReason=true)", record.toString())
        val set = SetResult(
            "b", "ex-1", 0, br.com.codecacto.kmplib.workout.model.SetTarget.Timed(45), null, null,
            startedAt = T0, completedAt = T0, durationSeconds = 37, exerciseRefId = "leg-press", swappedFromExerciseId = "squat",
        )
        assertFalse("37" in set.toString(), set.toString())
        assertTrue("hasDuration=true" in set.toString() && "exerciseRefId=leg-press" in set.toString())
    }
}
