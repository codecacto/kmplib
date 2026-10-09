package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.metrics.completedSetCount
import br.com.codecacto.kmplib.workout.metrics.volumeKg
import br.com.codecacto.kmplib.workout.model.Block
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.stageTarget
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tabela de execução por método (L-WK1, kmplib 2.266.0). Cada linha da tabela é um passo: o cursor em
 * que se está, e o que concluir esse passo produz — `null` = entra direto no próximo, número = descanso
 * em segundos, `FIM` = termina. Esta tabela é o caso de referência da regra para qualquer reescrita
 * (Monkey C no Garmin, backend).
 */
class MethodExecutionTest {

    private val engine = GuidedWorkoutEngine()

    private sealed interface After {
        data object Direct : After
        data class Rest(val seconds: Int) : After
        data object End : After
    }

    private fun direct() = After.Direct
    private fun rest(seconds: Int) = After.Rest(seconds)
    private val FIM = After.End

    /**
     * Percorre o plano inteiro concluindo cada passo e pulando todo descanso, conferindo linha a linha.
     * Devolve o estado final para conferências extras.
     */
    private fun walk(plan: WorkoutPlan, vararg rows: Pair<Cursor, After>): WorkoutState.Finished {
        var now: Instant = T0
        var state: WorkoutState = engine.start(plan, now, "run")
        for ((i, row) in rows.withIndex()) {
            val (cursor, after) = row
            val inSet = assertIs<WorkoutState.InSet>(state, "linha $i: esperava InSet")
            assertEquals(cursor, inSet.cursor, "linha $i: cursor")
            val expectedRest = if (after is After.Rest) after.seconds else 0
            assertEquals(expectedRest, engine.restAfter(plan, cursor, inSet.run), "linha $i: restAfter antes de concluir")
            now += 10.seconds
            val next = engine.reduce(inSet, WorkoutEvent.CompleteSet(reps = 10, load = 20.0), now)
            state = when (after) {
                After.Direct -> assertIs<WorkoutState.InSet>(next, "linha $i: sem descanso")
                is After.Rest -> {
                    val resting = assertIs<WorkoutState.Resting>(next, "linha $i: descanso")
                    assertEquals(now + after.seconds.seconds, resting.restEndsAt, "linha $i: restEndsAt absoluto")
                    engine.reduce(resting, WorkoutEvent.SkipRest, now)
                }
                After.End -> assertIs<WorkoutState.Finished>(next, "linha $i: fim")
            }
        }
        return assertIs(state)
    }

    // ── BI_SET ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bi-set anda por volta e descansa o restSeconds do ultimo exercicio da volta`() {
        val finished = walk(
            biSetPlan(),
            Cursor(0, 0, 0) to direct(),
            Cursor(0, 1, 0) to rest(90),
            Cursor(0, 0, 1) to direct(),
            Cursor(0, 1, 1) to rest(90),
            Cursor(0, 0, 2) to direct(),
            Cursor(0, 1, 2) to FIM,
        )
        assertEquals(listOf(0, 0, 1, 1, 2, 2), finished.run.sets.map { it.roundIndex })
        assertEquals(listOf("ex-1", "ex-2", "ex-1", "ex-2", "ex-1", "ex-2"), finished.run.sets.map { it.exerciseStepId })
        assertTrue(finished.run.sets.all { it.stageIndex == null })
    }

    @Test
    fun `bi-set expoe Volta N de M e Exercicio N de M pelo passo`() {
        val plan = biSetPlan()
        val step = assertIs<ExecutionStep>(plan.stepAt(Cursor(0, 1, 1)))
        assertEquals(1, step.roundIndex)
        assertEquals(3, step.roundCount)
        assertEquals(2, step.exerciseCount)
        assertEquals("Tríceps corda", step.exercise.name)
        assertNull(step.stageIndex)
        assertEquals(1, step.stageCount)
        assertEquals(3, plan.blocks[0].roundCount)
    }

    // ── CIRCUIT ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `circuito pula na volta o exercicio sem aquela serie e o descanso separa o bloco seguinte`() {
        walk(
            circuitPlan(),
            Cursor(0, 0, 0) to direct(),
            Cursor(0, 1, 0) to direct(),
            Cursor(0, 2, 0) to rest(60),
            Cursor(0, 0, 1) to direct(),
            Cursor(0, 1, 1) to direct(),
            Cursor(0, 2, 1) to rest(60),
            Cursor(0, 0, 2) to direct(), // B só tem 2 séries: fica fora da volta 3
            Cursor(0, 2, 2) to rest(60), // fim do bloco: descansa antes do próximo
            Cursor(1, 0, 0) to FIM,
        )
    }

    @Test
    fun `circuito cujo ultimo exercicio acabou antes fecha a volta no exercicio que sobrou`() {
        walk(
            circuitShortLastPlan(),
            Cursor(0, 0, 0) to direct(),
            Cursor(0, 1, 0) to rest(45),
            Cursor(0, 0, 1) to direct(),
            Cursor(0, 1, 1) to rest(45),
            Cursor(0, 0, 2) to rest(30), // volta 3 só com o A: o descanso é o dele
            Cursor(1, 0, 0) to FIM,
        )
    }

    @Test
    fun `roundExercises lista so quem participa da volta`() {
        val plan = circuitPlan()
        assertEquals(listOf("c-a", "c-b", "c-c"), plan.roundExercises(Cursor(0, 1, 0)).map { it.id })
        assertEquals(listOf("c-a", "c-c"), plan.roundExercises(Cursor(0, 0, 2)).map { it.id })
        assertEquals(listOf("n-1"), plan.roundExercises(Cursor(1, 0, 0)).map { it.id })
        assertTrue(plan.roundExercises(Cursor(9, 0, 0)).isEmpty())
    }

    @Test
    fun `serie por tempo no circuito registra a meta Timed`() {
        val plan = circuitPlan()
        val state = engine.reduce(engine.start(plan, T0, "r"), WorkoutEvent.CompleteSet(null, null), T0)
        assertIs<WorkoutState.InSet>(state)
        assertEquals(SetTarget.Timed(30), state.run.sets.single().target)
    }

    // ── DROP_SET ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `drop-set faz os estagios sem descanso e descansa so depois do ultimo`() {
        val finished = walk(
            dropSetPlan(),
            Cursor(0, 0, 0, 0) to direct(),
            Cursor(0, 0, 0, 1) to direct(),
            Cursor(0, 0, 0, 2) to rest(90),
            Cursor(0, 0, 1, 0) to direct(),
            Cursor(0, 0, 1, 1) to direct(),
            Cursor(0, 0, 1, 2) to rest(90), // fim do bloco
            Cursor(1, 0, 0) to FIM,
        )
        val dropResults = finished.run.sets.take(6)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), dropResults.map { it.stageIndex })
        assertEquals(listOf(0, 0, 0, 1, 1, 1), dropResults.map { it.setIndex })
        assertEquals(
            listOf(40.0, 32.0, 25.0),
            dropResults.take(3).map { (it.target as SetTarget.Reps).load },
            "o target de cada resultado é o do ESTÁGIO",
        )
        assertTrue(dropResults.all { it.roundIndex == null })
        assertEquals(3, finished.run.completedSetCount(), "drop-set conta a série uma vez: 2 + 1")
    }

    @Test
    fun `drop-set sem estagios descansa depois de cada serie`() {
        walk(
            dropSetWithoutStagesPlan(),
            Cursor(0, 0, 0) to rest(90),
            Cursor(0, 0, 1) to FIM,
        )
    }

    @Test
    fun `passo do drop-set expoe Serie N de M e Estagio N de M`() {
        val step = assertIs<ExecutionStep>(dropSetPlan().stepAt(Cursor(0, 0, 1, 1)))
        assertEquals(1, step.setIndex)
        assertEquals(2, step.setCount)
        assertEquals(1, step.stageIndex)
        assertEquals(3, step.stageCount)
        assertEquals(SetTarget.Reps(10, 32.0), step.target)
        assertNull(step.roundIndex)
        assertNull(step.roundCount)
    }

    @Test
    fun `volume do drop-set soma cada estagio com a carga registrada`() {
        val plan = dropSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(12, 40.0), T0)
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 32.0), T0)
        state = engine.reduce(state, WorkoutEvent.CompleteSet(8, 25.0), T0)
        assertIs<WorkoutState.Resting>(state)
        assertEquals(12 * 40.0 + 10 * 32.0 + 8 * 25.0, state.run.volumeKg())
    }

    @Test
    fun `undo no meio do drop-set volta ao estagio anterior`() {
        val plan = dropSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(12, 40.0), T0)
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 32.0), T0)
        assertEquals(Cursor(0, 0, 0, 2), assertIs<WorkoutState.InSet>(state).cursor)

        state = engine.reduce(state, WorkoutEvent.Undo, T0)

        assertEquals(Cursor(0, 0, 0, 1), assertIs<WorkoutState.InSet>(state).cursor)
        assertEquals(listOf(0), state.run.sets.map { it.stageIndex })
    }

    @Test
    fun `undo durante o descanso do drop-set volta ao ultimo estagio`() {
        val plan = dropSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")
        repeat(3) { state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0) }
        assertIs<WorkoutState.Resting>(state)

        state = engine.reduce(state, WorkoutEvent.Undo, T0)

        assertEquals(Cursor(0, 0, 0, 2), assertIs<WorkoutState.InSet>(state).cursor)
    }

    @Test
    fun `pausar no meio do estagio e retomar devolve o mesmo passo`() {
        val plan = dropSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(12, 40.0), T0)
        val paused = engine.reduce(state, WorkoutEvent.Pause, T0)
        assertEquals(Cursor(0, 0, 0, 1), assertIs<WorkoutState.Paused>(paused).cursor)
        assertEquals(state, engine.reduce(paused, WorkoutEvent.Resume, T0))
    }

    // ── Pular exercício em bloco por volta ───────────────────────────────────────────────────

    @Test
    fun `pular no bi-set tira o exercicio das voltas que faltam`() {
        val plan = biSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")

        state = engine.reduce(state, WorkoutEvent.SkipExercise("aparelho ocupado"), T0)
        assertEquals(Cursor(0, 1, 0), assertIs<WorkoutState.InSet>(state).cursor, "pulo vai direto, sem descanso")

        state = engine.reduce(state, WorkoutEvent.CompleteSet(12, 15.0), T0)
        assertIs<WorkoutState.Resting>(state)
        assertEquals(Cursor(0, 1, 1), state.cursor, "volta 2 já sem a rosca")
    }

    @Test
    fun `pular o ultimo da volta faz o descanso vir depois de quem fechou a volta`() {
        val plan = biSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0) // A volta 1 -> B volta 1
        state = engine.reduce(state, WorkoutEvent.SkipExercise(), T0) // pula B -> A volta 2, sem descanso
        assertEquals(Cursor(0, 0, 1), assertIs<WorkoutState.InSet>(state).cursor)
        assertEquals(60, engine.restAfter(plan, state.cursor, state.run), "A fecha a volta sozinho agora")

        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0)

        assertIs<WorkoutState.Resting>(state)
        assertEquals(T0 + 60.seconds, state.restEndsAt)
        assertEquals(Cursor(0, 0, 2), state.cursor)
    }

    @Test
    fun `pular durante o descanso pula o exercicio que vinha`() {
        val plan = biSetPlan()
        var state: WorkoutState = engine.start(plan, T0, "r")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0)
        state = engine.reduce(state, WorkoutEvent.CompleteSet(12, 15.0), T0)
        assertEquals(Cursor(0, 0, 1), assertIs<WorkoutState.Resting>(state).cursor)

        state = engine.reduce(state, WorkoutEvent.SkipExercise(), T0)

        assertEquals(Cursor(0, 1, 1), assertIs<WorkoutState.InSet>(state).cursor)
        assertEquals("ex-1", state.run.skippedExercises.single().exerciseStepId)
    }

    @Test
    fun `undo de volta a um exercicio pulado o devolve ao treino`() {
        val plan = WorkoutPlan(
            "p", "P",
            listOf(
                Block(
                    "b", WorkoutMethod.NORMAL,
                    listOf(
                        ExerciseStep("a", "a", "A", List(3) { repsSet() }, 30),
                        ExerciseStep("b", "b", "B", List(1) { repsSet() }, 30),
                    ),
                ),
            ),
        )
        var state: WorkoutState = engine.start(plan, T0, "r")
        state = engine.reduce(state, WorkoutEvent.CompleteSet(10, 20.0), T0) // descanso, próximo A s2
        state = engine.reduce(state, WorkoutEvent.SkipExercise(), T0) // pula o resto do A -> B
        assertEquals(Cursor(0, 1, 0), assertIs<WorkoutState.InSet>(state).cursor)

        state = engine.reduce(state, WorkoutEvent.Undo, T0)

        assertEquals(Cursor(0, 0, 0), assertIs<WorkoutState.InSet>(state).cursor)
        assertTrue(state.run.skippedExercises.isEmpty())
        assertEquals(Cursor(0, 0, 1), engine.upcoming(plan, state.cursor, state.run)?.cursor)
    }

    // ── Ordem, bordas e utilitários ──────────────────────────────────────────────────────────

    @Test
    fun `ordem de execucao do plano inteiro e as contagens planejadas`() {
        assertEquals(6, biSetPlan().executionOrder().size)
        assertEquals(8 + 1, circuitPlan().executionOrder().size)
        assertEquals(6 + 1, dropSetPlan().executionOrder().size)
        assertEquals(2 + 1, dropSetPlan().plannedSetCount(), "estágio não conta como série")
        assertEquals(8 + 1, circuitPlan().plannedSetCount())
    }

    @Test
    fun `plano sem nenhuma serie termina na largada e bloco vazio nao gera passo`() {
        val empty = WorkoutPlan("p", "P", listOf(Block("b", WorkoutMethod.CIRCUIT, emptyList())))
        val state = engine.start(empty, T0, "r")
        assertIs<WorkoutState.Finished>(state)
        assertEquals(T0, state.run.finishedAt)
        assertEquals(0, empty.blocks[0].roundCount)

        val withEmptyFirst = WorkoutPlan("p", "P", listOf(Block("vazio", WorkoutMethod.NORMAL, emptyList())) + simplePlan().blocks)
        assertEquals(Cursor(1, 0, 0), assertIs<WorkoutState.InSet>(engine.start(withEmptyFirst, T0, "r")).cursor)
    }

    @Test
    fun `upcoming e restAfter no fim do plano e em cursor que nao existe`() {
        val plan = simplePlan()
        val run = assertIs<WorkoutState.InSet>(engine.start(plan, T0, "r")).run
        assertNull(engine.upcoming(plan, Cursor(0, 0, 1), run))
        assertEquals(0, engine.restAfter(plan, Cursor(0, 0, 1), run), "último passo não descansa")
        assertNull(engine.upcoming(plan, Cursor(5, 0, 0), run))
        assertEquals(0, engine.restAfter(plan, Cursor(5, 0, 0), run))
        assertNull(plan.stepAt(Cursor(0, 0, 0, 1)), "série sem estágio só tem o estágio 0")
    }

    @Test
    fun `metodo NORMAL nao tem volta e roundCount e zero`() {
        val block = simplePlan().blocks[0]
        assertEquals(false, block.isRoundBased)
        assertEquals(0, block.roundCount)
        assertNull(simplePlan().stepAt(Cursor(0, 0, 0))?.roundIndex)
    }

    @Test
    fun `cursor resolve a serie inteira com os estagios`() {
        val plan = dropSetPlan()
        assertEquals(SetTarget.Reps.dropSet(DROP_STAGES), Cursor(0, 0, 1, 2).set(plan))
    }

    @Test
    fun `dropSet sem estagio e estagio inexistente sao recusados`() {
        assertFailsWith<IllegalArgumentException> { SetTarget.Reps.dropSet(emptyList()) }
        assertFailsWith<IllegalArgumentException> { SetTarget.Timed(30).stageTarget(1) }
        assertEquals(SetTarget.Timed(30), SetTarget.Timed(30).stageTarget(0))
    }
}
