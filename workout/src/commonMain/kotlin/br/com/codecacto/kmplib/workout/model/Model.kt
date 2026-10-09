package br.com.codecacto.kmplib.workout.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Como as séries de um bloco se sucedem (regra completa no KDoc de `GuidedWorkoutEngine`):
 * - `NORMAL` e `DROP_SET`: exercício a exercício, série a série, descansando o `restSeconds` do
 *   exercício depois de cada série. No `DROP_SET` cada série tem ESTÁGIOS (`SetTarget.Reps.stages`),
 *   feitos sem descanso entre si; o descanso vem só depois do último estágio.
 * - `BI_SET` e `CIRCUIT`: por VOLTA. A volta *r* faz a série *r* de cada exercício do bloco, na ordem e
 *   sem descanso; depois do último exercício da volta, descansa o `restSeconds` dele.
 */
@Serializable
enum class WorkoutMethod { NORMAL, BI_SET, DROP_SET, CIRCUIT }

/** Um estágio de uma série de drop-set: as repetições e a carga daquele trecho, sem descanso antes. */
@Serializable
data class DropStage(val reps: Int, val load: Double?)

/** Tipo de meta de uma série: repetições ou tempo (isometria/prancha). */
@Serializable
sealed interface SetTarget {

    /**
     * Série por repetições. Em drop-set, [stages] é a lista COMPLETA de estágios da série (o primeiro
     * incluído), e [reps]/[load] repetem o primeiro estágio — use [dropSet] para não ter de escrever
     * isso à mão. Sem estágios (lista vazia), a série é um estágio só: [reps] x [load].
     */
    @Serializable
    data class Reps(
        val reps: Int,
        val load: Double?,
        val stages: List<DropStage> = emptyList(),
    ) : SetTarget {
        companion object {
            /** Série de drop-set a partir dos estágios (o primeiro vira [reps]/[load]). */
            fun dropSet(stages: List<DropStage>): Reps {
                require(stages.isNotEmpty()) { "drop-set precisa de pelo menos um estágio" }
                return Reps(stages.first().reps, stages.first().load, stages)
            }
        }
    }

    @Serializable
    data class Timed(val seconds: Int) : SetTarget
}

/** `true` quando a série é dividida em estágios de drop-set (cada um vira um passo da execução). */
val SetTarget.hasStages: Boolean get() = this is SetTarget.Reps && stages.isNotEmpty()

/** Quantos passos de execução a série tem: o número de estágios, ou 1 se não tiver estágios. */
val SetTarget.stageCount: Int get() = if (this is SetTarget.Reps && stages.isNotEmpty()) stages.size else 1

/**
 * A meta de UM estágio, já resolvida (sem estágios dentro): é o que vai no `SetResult.target`, então
 * volume e "última vez" contam cada estágio com a carga dele. Série sem estágios devolve ela mesma.
 */
fun SetTarget.stageTarget(stageIndex: Int): SetTarget = when {
    this is SetTarget.Reps && stages.isNotEmpty() -> stages[stageIndex].let { SetTarget.Reps(it.reps, it.load) }
    else -> {
        require(stageIndex == 0) { "série sem estágios só tem o estágio 0 (pedido: $stageIndex)" }
        this
    }
}

/** Um exercício dentro de um bloco, com a meta de cada série e o descanso depois dela. */
@Serializable
data class ExerciseStep(
    val id: String,
    val exerciseRefId: String,
    val name: String,
    val sets: List<SetTarget>,
    val restSeconds: Int,
    val videoRequested: Boolean = false,
)

/**
 * Um bloco de exercícios executado pela regra do seu [method] (ver [WorkoutMethod]). Em
 * `BI_SET`/`CIRCUIT` o número de voltas é [roundCount] — derivado das séries, nunca declarado à parte,
 * para não existir plano em que as duas contas discordem.
 */
@Serializable
data class Block(
    val id: String,
    val method: WorkoutMethod,
    val exercises: List<ExerciseStep>,
) {
    /** `true` em `BI_SET`/`CIRCUIT`: o bloco é percorrido por volta, não por exercício. */
    val isRoundBased: Boolean get() = method == WorkoutMethod.BI_SET || method == WorkoutMethod.CIRCUIT

    /**
     * Voltas do bloco = o MAIOR número de séries entre os exercícios (o exercício com menos séries é
     * pulado nas voltas que faltam). Fora de `BI_SET`/`CIRCUIT` não há volta: 0.
     */
    val roundCount: Int get() = if (isRoundBased) exercises.maxOfOrNull { it.sets.size } ?: 0 else 0
}

/** Instantâneo imutável do treino prescrito — o que o aluno baixa e executa, mesmo offline. */
@Serializable
data class WorkoutPlan(
    val id: String,
    val name: String,
    val blocks: List<Block>,
)

/**
 * O que de fato aconteceu num passo da execução: uma série, ou UM estágio dela no drop-set (aí há um
 * `SetResult` por estágio, todos com o mesmo [setIndex]). [target] é a meta daquele estágio, já
 * resolvida. [stageIndex] é `null` quando a série não tem estágios; [roundIndex] (0-based) só existe em
 * `BI_SET`/`CIRCUIT` e é igual ao [setIndex] — fica explícito para o backend não ter de deduzir.
 */
@Serializable
data class SetResult(
    val blockId: String,
    val exerciseStepId: String,
    val setIndex: Int,
    val target: SetTarget,
    val repsDone: Int?,
    val loadDone: Double?,
    val skipped: Boolean = false,
    val startedAt: Instant,
    val completedAt: Instant,
    val heartRateAvg: Int? = null,
    val heartRateMax: Int? = null,
    val stageIndex: Int? = null,
    val roundIndex: Int? = null,
)

/** Ocorrência de "pular exercício", com o motivo informado pelo aluno (visto pelo personal). */
@Serializable
data class SkippedExercise(
    val exerciseStepId: String,
    val reason: String?,
    val at: Instant,
)

/** O que de fato aconteceu na sessão — a gravação completa, para sincronizar com o backend. */
@Serializable
data class WorkoutRun(
    val localId: String,
    val planId: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val sets: List<SetResult> = emptyList(),
    val skippedExercises: List<SkippedExercise> = emptyList(),
    val effort: Int? = null,
    val comment: String? = null,
)
