package br.com.codecacto.kmplib.workout.metrics

import br.com.codecacto.kmplib.workout.engine.executionOrder
import br.com.codecacto.kmplib.workout.model.SET_DURATION_RANGE
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Inconsistência entre uma sessão gravada e o plano que ela diz ter executado. É a régua do backend
 * para recusar a sessão (`INVALID_SESSION`) com a MESMA regra do motor do aparelho — cada `SetResult`
 * tem de ser um passo de `WorkoutPlan.executionOrder()`.
 *
 * Os índices ([setResultIndex]) apontam a posição em `WorkoutRun.sets`, para o log dizer QUAL linha.
 *
 * Serializável (discriminador `type` com os nomes em MAIÚSCULAS abaixo): é o formato dos
 * `expectedIssues` da fixture `session-validation.json` e serve de `details` do `INVALID_SESSION`.
 * Nenhum campo carrega dado do aluno — só ids do plano e posições.
 */
@Serializable
sealed interface RunIssue {
    /** `WorkoutRun.planId` não é o `WorkoutPlan.id` recebido. */
    @Serializable
    @SerialName("PLAN_MISMATCH")
    data class PlanMismatch(val expectedPlanId: String, val runPlanId: String) : RunIssue

    /**
     * O passo não existe no plano: bloco/exercício desconhecido, série ou estágio fora do intervalo, ou
     * volta/estágio declarados que não batem com o método (ex.: `roundIndex` num bloco `NORMAL`,
     * `stageIndex` nulo numa série com estágios).
     */
    @Serializable
    @SerialName("UNKNOWN_STEP")
    data class UnknownStep(val setResultIndex: Int) : RunIssue

    /** O mesmo passo (bloco, exercício, série, estágio) aparece mais de uma vez. */
    @Serializable
    @SerialName("DUPLICATE_STEP")
    data class DuplicateStep(val setResultIndex: Int) : RunIssue

    /** `completedAt` antes de `startedAt`. */
    @Serializable
    @SerialName("NEGATIVE_DURATION")
    data class NegativeDuration(val setResultIndex: Int) : RunIssue

    /** Pulo de exercício que não está no plano. */
    @Serializable
    @SerialName("UNKNOWN_SKIPPED_EXERCISE")
    data class UnknownSkippedExercise(val exerciseStepId: String) : RunIssue

    /**
     * `SetResult.durationSeconds` informado fora de `SET_DURATION_RANGE` (1..3600) ou numa série que não
     * é por tempo (`SetTarget.Timed` no plano). 2.275.0.
     */
    @Serializable
    @SerialName("INVALID_DURATION")
    data class InvalidDuration(val setResultIndex: Int) : RunIssue

    /**
     * Troca inconsistente numa série (2.275.0): `swappedFromExerciseId` que não é o exercício do item
     * no plano, troca sem o exercício feito (`exerciseRefId` nulo), troca para o MESMO exercício, ou
     * `exerciseRefId` diferente do plano sem `swappedFromExerciseId` (troca não declarada).
     */
    @Serializable
    @SerialName("INVALID_SWAP")
    data class InvalidSwap(val setResultIndex: Int) : RunIssue

    /**
     * Registro de troca (`WorkoutRun.swaps`) de um item que não está no plano, ou para o mesmo
     * exercício de que saiu. [swapIndex] = posição em `WorkoutRun.swaps`. 2.275.0.
     */
    @Serializable
    @SerialName("INVALID_SWAP_RECORD")
    data class InvalidSwapRecord(val swapIndex: Int) : RunIssue
}

/**
 * Confere a sessão contra o plano; lista vazia = consistente. NÃO exige que a sessão esteja completa
 * nem na ordem do motor (treino encerrado antes, `Undo`, pulo e treino retroativo são legítimos) — só
 * que todo passo registrado exista no plano, uma vez.
 *
 * Ordem dos apontamentos: plano; depois, linha a linha de `sets`, `UNKNOWN_STEP`/`DUPLICATE_STEP`,
 * `NEGATIVE_DURATION`, `INVALID_DURATION`, `INVALID_SWAP`; depois os pulos; por fim as trocas.
 *
 * Duração (2.275.0): quando `durationSeconds` vem, É a duração da série — tem de estar em 1..3600 e a
 * série tem de ser por tempo. Os instantes continuam conferidos (`NEGATIVE_DURATION`): fim antes do
 * início é registro corrompido, com ou sem duração informada.
 */
fun validateRun(plan: WorkoutPlan, run: WorkoutRun): List<RunIssue> {
    val issues = mutableListOf<RunIssue>()
    if (run.planId != plan.id) issues += RunIssue.PlanMismatch(plan.id, run.planId)

    data class Key(val blockId: String, val exerciseId: String, val setIndex: Int, val stageIndex: Int?, val roundIndex: Int?)

    val steps = plan.executionOrder()
        .mapTo(HashSet()) { Key(it.blockId, it.exercise.id, it.setIndex, it.stageIndex, it.roundIndex) }
    val stepById = plan.blocks.flatMap { block -> block.exercises.map { block to it } }
        .flatMap { (block, exercise) -> exercise.sets.indices.map { Triple(block.id, exercise.id, it) to exercise } }
        .toMap()
    val seen = HashSet<Key>()
    run.sets.forEachIndexed { index, set ->
        val key = Key(set.blockId, set.exerciseStepId, set.setIndex, set.stageIndex, set.roundIndex)
        when {
            key !in steps -> issues += RunIssue.UnknownStep(index)
            !seen.add(key) -> issues += RunIssue.DuplicateStep(index)
        }
        if (set.completedAt < set.startedAt) issues += RunIssue.NegativeDuration(index)
        // Passo desconhecido já foi apontado (UNKNOWN_STEP): duração e troca só se conferem em passo do plano.
        val step = if (key in steps) stepById[Triple(set.blockId, set.exerciseStepId, set.setIndex)] else null
        set.durationSeconds?.let { seconds ->
            if (step != null && (seconds !in SET_DURATION_RANGE || step.sets[set.setIndex] !is SetTarget.Timed)) {
                issues += RunIssue.InvalidDuration(index)
            }
        }
        if (step != null && !swapConsistent(step.exerciseRefId, set.exerciseRefId, set.swappedFromExerciseId)) {
            issues += RunIssue.InvalidSwap(index)
        }
    }

    val exerciseIds = plan.blocks.flatMapTo(HashSet()) { block -> block.exercises.map { it.id } }
    run.skippedExercises
        .filter { it.exerciseStepId !in exerciseIds }
        .forEach { issues += RunIssue.UnknownSkippedExercise(it.exerciseStepId) }
    run.swaps.forEachIndexed { index, swap ->
        if (swap.exerciseStepId !in exerciseIds || swap.fromExerciseId == swap.toExerciseId || swap.toExerciseId.isBlank()) {
            issues += RunIssue.InvalidSwapRecord(index)
        }
    }
    return issues
}

/**
 * A série cita o exercício certo: sem troca, `exerciseRefId` é nulo ou o do plano; com troca
 * (`swappedFromExerciseId`), o original é o do plano e o feito existe e é OUTRO.
 */
private fun swapConsistent(planned: String, performed: String?, swappedFrom: String?): Boolean = when (swappedFrom) {
    null -> performed == null || performed == planned
    else -> swappedFrom == planned && !performed.isNullOrBlank() && performed != planned
}
