package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.hasStages
import br.com.codecacto.kmplib.workout.model.stageCount
import br.com.codecacto.kmplib.workout.model.stageTarget

/**
 * Um passo da execução: o menor trecho que o aluno conclui com um toque — uma série, ou UM estágio
 * dela no drop-set. É o que a tela desenha e o que vira um `SetResult`.
 *
 * Os campos de contagem existem para a tela escrever "Volta 2 de 3", "Série 2 de 3 · Estágio 2" e
 * "Exercício 1 de 2" sem refazer a regra do método.
 *
 * @property roundIndex volta (0-based) em `BI_SET`/`CIRCUIT`, igual ao [setIndex]; `null` fora deles.
 * @property roundCount voltas do bloco em `BI_SET`/`CIRCUIT`; `null` fora deles.
 * @property stageIndex estágio (0-based) quando a série tem estágios; `null` quando não tem.
 * @property stageCount estágios da série (1 quando não tem estágios).
 * @property target meta DESTE passo, já resolvida para o estágio.
 */
data class ExecutionStep(
    val cursor: Cursor,
    val blockId: String,
    val method: WorkoutMethod,
    val exercise: ExerciseStep,
    val exerciseCount: Int,
    val setCount: Int,
    val roundIndex: Int?,
    val roundCount: Int?,
    val stageIndex: Int?,
    val stageCount: Int,
    val target: SetTarget,
) {
    val setIndex: Int get() = cursor.setIndex

    /** `true` no último (ou único) estágio da série: só ele pode abrir o descanso. */
    val isLastStage: Boolean get() = cursor.stageIndex == stageCount - 1
}

/**
 * A ordem COMPLETA de execução do plano, sem nenhum pulo — a regra de [WorkoutMethod] escrita uma vez:
 * - `NORMAL`/`DROP_SET`: exercício a exercício, série a série, estágio a estágio;
 * - `BI_SET`/`CIRCUIT`: volta a volta; dentro da volta, exercício a exercício (o que não tem a série
 *   daquela volta fica de fora dela), estágio a estágio.
 *
 * Bloco sem exercício e exercício sem série não geram passo. É a MESMA lista no celular, no relógio e
 * no backend (validação de sessão): o motor nunca anda fora dela.
 */
fun WorkoutPlan.executionOrder(): List<ExecutionStep> = buildList {
    blocks.forEachIndexed { blockIndex, block ->
        fun addSet(exerciseIndex: Int, setIndex: Int, roundIndex: Int?, roundCount: Int?) {
            val exercise = block.exercises[exerciseIndex]
            val set = exercise.sets[setIndex]
            repeat(set.stageCount) { stage ->
                add(
                    ExecutionStep(
                        cursor = Cursor(blockIndex, exerciseIndex, setIndex, stage),
                        blockId = block.id,
                        method = block.method,
                        exercise = exercise,
                        exerciseCount = block.exercises.size,
                        setCount = exercise.sets.size,
                        roundIndex = roundIndex,
                        roundCount = roundCount,
                        stageIndex = if (set.hasStages) stage else null,
                        stageCount = set.stageCount,
                        target = set.stageTarget(stage),
                    ),
                )
            }
        }

        if (block.isRoundBased) {
            val rounds = block.roundCount
            for (round in 0 until rounds) {
                block.exercises.forEachIndexed { exerciseIndex, exercise ->
                    if (round < exercise.sets.size) addSet(exerciseIndex, round, round, rounds)
                }
            }
        } else {
            block.exercises.forEachIndexed { exerciseIndex, exercise ->
                exercise.sets.indices.forEach { setIndex -> addSet(exerciseIndex, setIndex, null, null) }
            }
        }
    }
}

/** O passo do [cursor], ou `null` se o cursor não existe neste plano. */
fun WorkoutPlan.stepAt(cursor: Cursor): ExecutionStep? = executionOrder().firstOrNull { it.cursor == cursor }

/**
 * Os exercícios que fazem parte da volta do [cursor], na ordem — a lista do circuito ("Volta 2 de 3",
 * uma linha por exercício). Fora de `BI_SET`/`CIRCUIT`, devolve só o exercício do cursor. Considera o
 * plano, não os pulos: quem pulou um exercício o vê marcado como pulado pelo `WorkoutRun`.
 */
fun WorkoutPlan.roundExercises(cursor: Cursor): List<ExerciseStep> {
    val block = blocks.getOrNull(cursor.blockIndex) ?: return emptyList()
    if (!block.isRoundBased) return listOfNotNull(block.exercises.getOrNull(cursor.exerciseIndex))
    return block.exercises.filter { cursor.setIndex < it.sets.size }
}

/** Séries planejadas no plano inteiro (estágio NÃO conta como série: drop-set de 3 estágios é 1 série). */
fun WorkoutPlan.plannedSetCount(): Int = blocks.sumOf { block -> block.exercises.sumOf { it.sets.size } }
