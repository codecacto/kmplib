package br.com.codecacto.kmplib.workout.engine

import br.com.codecacto.kmplib.workout.model.CardioBlock
import br.com.codecacto.kmplib.workout.model.CardioPhase
import br.com.codecacto.kmplib.workout.model.ExerciseStep
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutMethod
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.hasStages
import br.com.codecacto.kmplib.workout.model.phasesPerRound
import br.com.codecacto.kmplib.workout.model.secondsOf
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
 * @property target meta DESTE passo, já resolvida para o estágio. Numa fase de cardio, `SetTarget.Timed`
 *   com os segundos planejados da fase.
 * @property cardio o bloco de cardio do item (2.297.0); `null` em série de força.
 * @property cardioPhase a fase deste passo no cardio (`WORK`/`REST`); `null` em série de força. Num
 *   passo de cardio, [setIndex] é a VOLTA do cardio ("Volta 3 de 8" = `setIndex + 1` de [setCount]) e
 *   `cursor.stageIndex` é a posição da fase na volta (0 = trabalho, 1 = descanso).
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
    val cardio: CardioBlock? = null,
    val cardioPhase: CardioPhase? = null,
) {
    val setIndex: Int get() = cursor.setIndex

    /** `true` no último (ou único) estágio da série: só ele pode abrir o descanso. */
    val isLastStage: Boolean get() = cursor.stageIndex == stageCount - 1

    /** `true` quando o passo é uma fase de cardio — a tela abre o modo de cardio (fase, anel, zona). */
    val isCardio: Boolean get() = cardioPhase != null

    /**
     * Segundos planejados desta fase de cardio (o anel conta deles até zero); `null` fora do cardio.
     * O instante final é `setStartedAt + phaseSeconds` (ver `GuidedWorkoutEngine.phaseEndsAt`).
     */
    val phaseSeconds: Int? get() = if (cardio != null && cardioPhase != null) cardio.secondsOf(cardioPhase) else null
}

/**
 * A ordem COMPLETA de execução do plano, sem nenhum pulo — a regra de [WorkoutMethod] escrita uma vez:
 * - `NORMAL`/`DROP_SET`: exercício a exercício, série a série, estágio a estágio;
 * - `BI_SET`/`CIRCUIT`: volta a volta; dentro da volta, exercício a exercício (o que não tem a série
 *   daquela volta fica de fora dela), estágio a estágio.
 *
 * Bloco sem exercício e exercício sem série não geram passo. É a MESMA lista no celular, no relógio e
 * no backend (validação de sessão): o motor nunca anda fora dela.
 *
 * Item de cardio (`ExerciseStep.cardio`, 2.297.0) vira uma fase por passo: volta a volta, TRABALHO e
 * (se houver) DESCANSO — `setIndex` = volta do cardio, `cursor.stageIndex` = fase na volta. Ele roda
 * inteiro no lugar dele; num bloco `BI_SET`/`CIRCUIT`, inteiro na primeira volta do bloco.
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

        fun addCardio(exerciseIndex: Int, cardio: CardioBlock, roundIndex: Int?, roundCount: Int?) {
            val exercise = block.exercises[exerciseIndex]
            val phases = cardio.phasesPerRound()
            for (cardioRound in 0 until cardio.rounds.coerceAtLeast(0)) {
                phases.forEachIndexed { phaseIndex, phase ->
                    add(
                        ExecutionStep(
                            cursor = Cursor(blockIndex, exerciseIndex, cardioRound, phaseIndex),
                            blockId = block.id,
                            method = block.method,
                            exercise = exercise,
                            exerciseCount = block.exercises.size,
                            setCount = cardio.rounds,
                            roundIndex = roundIndex,
                            roundCount = roundCount,
                            stageIndex = null,
                            stageCount = phases.size,
                            target = SetTarget.Timed(cardio.secondsOf(phase)),
                            cardio = cardio,
                            cardioPhase = phase,
                        ),
                    )
                }
            }
        }

        if (block.isRoundBased) {
            val rounds = block.roundCount
            for (round in 0 until rounds) {
                block.exercises.forEachIndexed { exerciseIndex, exercise ->
                    val cardio = exercise.cardio
                    when {
                        cardio != null -> if (round == 0) addCardio(exerciseIndex, cardio, round, rounds)
                        round < exercise.sets.size -> addSet(exerciseIndex, round, round, rounds)
                    }
                }
            }
        } else {
            block.exercises.forEachIndexed { exerciseIndex, exercise ->
                val cardio = exercise.cardio
                if (cardio != null) {
                    addCardio(exerciseIndex, cardio, null, null)
                } else {
                    exercise.sets.indices.forEach { setIndex -> addSet(exerciseIndex, setIndex, null, null) }
                }
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
    val current = block.exercises.getOrNull(cursor.exerciseIndex)
    if (!block.isRoundBased || current?.cardio != null) return listOfNotNull(current)
    return block.exercises.filter { it.cardio == null && cursor.setIndex < it.sets.size }
}

/**
 * Séries planejadas no plano inteiro (estágio NÃO conta como série: drop-set de 3 estágios é 1 série).
 * Item de cardio não tem série e não entra (cardio não é série efetiva).
 */
fun WorkoutPlan.plannedSetCount(): Int =
    blocks.sumOf { block -> block.exercises.filter { it.cardio == null }.sumOf { it.sets.size } }
