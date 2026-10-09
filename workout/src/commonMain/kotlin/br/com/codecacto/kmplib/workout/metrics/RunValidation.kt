package br.com.codecacto.kmplib.workout.metrics

import br.com.codecacto.kmplib.workout.engine.executionOrder
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun

/**
 * Inconsistência entre uma sessão gravada e o plano que ela diz ter executado. É a régua do backend
 * para recusar a sessão (`INVALID_SESSION`) com a MESMA regra do motor do aparelho — cada `SetResult`
 * tem de ser um passo de `WorkoutPlan.executionOrder()`.
 *
 * Os índices ([setResultIndex]) apontam a posição em `WorkoutRun.sets`, para o log dizer QUAL linha.
 */
sealed interface RunIssue {
    /** `WorkoutRun.planId` não é o `WorkoutPlan.id` recebido. */
    data class PlanMismatch(val expectedPlanId: String, val runPlanId: String) : RunIssue

    /**
     * O passo não existe no plano: bloco/exercício desconhecido, série ou estágio fora do intervalo, ou
     * volta/estágio declarados que não batem com o método (ex.: `roundIndex` num bloco `NORMAL`,
     * `stageIndex` nulo numa série com estágios).
     */
    data class UnknownStep(val setResultIndex: Int) : RunIssue

    /** O mesmo passo (bloco, exercício, série, estágio) aparece mais de uma vez. */
    data class DuplicateStep(val setResultIndex: Int) : RunIssue

    /** `completedAt` antes de `startedAt`. */
    data class NegativeDuration(val setResultIndex: Int) : RunIssue

    /** Pulo de exercício que não está no plano. */
    data class UnknownSkippedExercise(val exerciseStepId: String) : RunIssue
}

/**
 * Confere a sessão contra o plano; lista vazia = consistente. NÃO exige que a sessão esteja completa
 * nem na ordem do motor (treino encerrado antes, `Undo`, pulo e treino retroativo são legítimos) — só
 * que todo passo registrado exista no plano, uma vez.
 */
fun validateRun(plan: WorkoutPlan, run: WorkoutRun): List<RunIssue> {
    val issues = mutableListOf<RunIssue>()
    if (run.planId != plan.id) issues += RunIssue.PlanMismatch(plan.id, run.planId)

    data class Key(val blockId: String, val exerciseId: String, val setIndex: Int, val stageIndex: Int?, val roundIndex: Int?)

    val steps = plan.executionOrder()
        .mapTo(HashSet()) { Key(it.blockId, it.exercise.id, it.setIndex, it.stageIndex, it.roundIndex) }
    val seen = HashSet<Key>()
    run.sets.forEachIndexed { index, set ->
        val key = Key(set.blockId, set.exerciseStepId, set.setIndex, set.stageIndex, set.roundIndex)
        when {
            key !in steps -> issues += RunIssue.UnknownStep(index)
            !seen.add(key) -> issues += RunIssue.DuplicateStep(index)
        }
        if (set.completedAt < set.startedAt) issues += RunIssue.NegativeDuration(index)
    }

    val exerciseIds = plan.blocks.flatMapTo(HashSet()) { block -> block.exercises.map { it.id } }
    run.skippedExercises
        .filter { it.exerciseStepId !in exerciseIds }
        .forEach { issues += RunIssue.UnknownSkippedExercise(it.exerciseStepId) }
    return issues
}
