package br.com.codecacto.kmplib.workout.cardio

import br.com.codecacto.kmplib.workout.model.CardioPhase
import br.com.codecacto.kmplib.workout.model.WorkoutPlan
import br.com.codecacto.kmplib.workout.model.WorkoutRun
import br.com.codecacto.kmplib.workout.model.effectiveDurationSeconds
import kotlinx.serialization.Serializable

/**
 * O que foi feito num bloco de cardio, AGREGADO (2.297.0) — o `cardioResult` do envio da sessão do App
 * do Personal, campo a campo. Nenhuma série temporal de FC: só médias, máximo e tempo na zona.
 *
 * @property completedRounds voltas cujo TRABALHO correu inteiro, sem ser pulado.
 * @property workSecondsDone soma do que correu nas fases de trabalho (parciais e puladas inclusive).
 * @property restSecondsDone soma do que correu nas fases de descanso.
 * @property skippedPhases fases encerradas por "Pular fase".
 * @property avgHr média das FCs médias registradas nas fases; `null` sem nenhuma.
 * @property maxHr maior FC máxima registrada nas fases; `null` sem nenhuma.
 * @property timeInTargetZoneSeconds tempo na zona-alvo (o app mede com [secondsInZone]); `null` = sem FC
 *   ou sem zona.
 */
@Serializable
data class CardioResult(
    val completedRounds: Int,
    val workSecondsDone: Int,
    val restSecondsDone: Int,
    val skippedPhases: Int,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val timeInTargetZoneSeconds: Int? = null,
) {
    /** Sem FC (dado de saúde): só as contagens e QUAIS leituras de FC vieram. */
    override fun toString(): String =
        "CardioResult(completedRounds=$completedRounds, workSecondsDone=$workSecondsDone, " +
            "restSecondsDone=$restSecondsDone, skippedPhases=$skippedPhases, " +
            "hasHeartRate=${avgHr != null || maxHr != null}, hasTimeInZone=${timeInTargetZoneSeconds != null})"
}

/**
 * Agrega as fases registradas do item de cardio [exerciseStepId] desta sessão. `null` quando o item não
 * existe no [plan] ou não é cardio. Item de cardio sem nenhuma fase registrada (pulado inteiro) devolve
 * o agregado zerado — o envio diz "não fez", em vez de omitir.
 *
 * FC: [avgHr]/[maxHr] saem das fases que gravaram FC (`CompleteSet` com FC). Quem acompanha a FC ao
 * vivo pelo sensor costuma ter um número melhor — passe-o por [heartRateAvg]/[heartRateMax], que
 * vencem o calculado. [timeInTargetZoneSeconds] vem do app ([secondsInZone]).
 */
fun WorkoutRun.cardioResult(
    plan: WorkoutPlan,
    exerciseStepId: String,
    timeInTargetZoneSeconds: Int? = null,
    heartRateAvg: Int? = null,
    heartRateMax: Int? = null,
): CardioResult? {
    val exercise = plan.blocks.asSequence().flatMap { it.exercises }.firstOrNull { it.id == exerciseStepId } ?: return null
    val cardio = exercise.cardio ?: return null
    val phases = sets.filter { it.exerciseStepId == exerciseStepId && it.cardioPhase != null }
    fun secondsOf(phase: CardioPhase): Int =
        phases.filter { it.cardioPhase == phase }.sumOf { it.effectiveDurationSeconds }.toInt()
    val completedRounds = phases
        .filter { it.cardioPhase == CardioPhase.WORK && !it.skipped && it.effectiveDurationSeconds >= cardio.workSeconds }
        .distinctBy { it.setIndex }
        .size
    val hrSamples = phases.mapNotNull { it.heartRateAvg }
    return CardioResult(
        completedRounds = completedRounds,
        workSecondsDone = secondsOf(CardioPhase.WORK),
        restSecondsDone = secondsOf(CardioPhase.REST),
        skippedPhases = phases.count { it.skipped },
        avgHr = heartRateAvg ?: if (hrSamples.isEmpty()) null else hrSamples.sum() / hrSamples.size,
        maxHr = heartRateMax ?: phases.mapNotNull { it.heartRateMax }.maxOrNull(),
        timeInTargetZoneSeconds = timeInTargetZoneSeconds,
    )
}
