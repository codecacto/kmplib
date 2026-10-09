package br.com.codecacto.kmplib.workout.metrics

import br.com.codecacto.kmplib.workout.model.SetResult
import br.com.codecacto.kmplib.workout.model.SetTarget
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun

/** Volume total da sessão: Σ (reps x carga) de toda série concluída com carga conhecida. Série de
 * tempo (isometria) e série sem carga não entram na soma, mas contam em `setsCompleted`. */
fun WorkoutRun.volumeKg(): Double = sets.sumOf { set ->
    val reps = set.repsDone ?: return@sumOf 0.0
    val load = set.loadDone ?: return@sumOf 0.0
    reps * load
}

/** FC média da sessão entre as séries que registraram FC — `null` sem nenhuma amostra. */
fun WorkoutRun.heartRateAvgOverall(): Int? {
    val samples = sets.mapNotNull { it.heartRateAvg }
    return if (samples.isEmpty()) null else samples.sum() / samples.size
}

fun WorkoutRun.heartRateMaxOverall(): Int? = sets.mapNotNull { it.heartRateMax }.maxOrNull()

/** Tempo sob tensão: soma da duração de cada série concluída (fim - início), em segundos. Série
 * pulada não entra. */
fun WorkoutRun.timeUnderTensionSeconds(): Long =
    sets.filterNot { it.skipped }.sumOf { (it.completedAt - it.startedAt).inWholeSeconds }

/** Conciliação planejado x feito: quantas séries o plano previa contra quantas de fato foram
 * concluídas (sem contar as puladas), por exercício. Alimenta o relatório do aluno e do personal. */
data class ExerciseReconciliation(val exerciseStepId: String, val plannedSets: Int, val completedSets: Int)

fun reconcile(plan: WorkoutPlan, run: WorkoutRun): List<ExerciseReconciliation> {
    val completedByExercise: Map<String, Int> = run.sets
        .filterNot { it.skipped }
        .groupingBy { it.exerciseStepId }
        .eachCount()

    return plan.blocks.flatMap { it.exercises }.map { exercise ->
        ExerciseReconciliation(
            exerciseStepId = exercise.id,
            plannedSets = exercise.sets.size,
            completedSets = completedByExercise[exercise.id] ?: 0,
        )
    }
}

/** Representação textual de uma meta de série, para telas de histórico ("última vez: 8 x 57,5 kg"
 * ou "30 s"), sem casar com um formato de UI específico. */
fun SetTarget.describe(): String = when (this) {
    is SetTarget.Reps -> if (load != null) "$reps x $load kg" else "$reps reps"
    is SetTarget.Timed -> "$seconds s"
}

fun SetResult.describeDone(): String = when {
    skipped -> "pulada"
    repsDone != null && loadDone != null -> "$repsDone x $loadDone kg"
    repsDone != null -> "$repsDone reps"
    else -> target.describe()
}
