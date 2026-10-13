package br.com.codecacto.kmplib.workout.energy

import kotlin.math.floor

/** De onde veio a estimativa — a UI sempre mostra a fonte ao lado do valor ("pelo seu relógio",
 * "pela sua frequência cardíaca", "estimada pela duração e intensidade"). */
enum class EnergySource { DEVICE, HEART_RATE, MET }

/**
 * `low`/`high` só vêm preenchidos quando a fonte é `MET` (faixa); `DEVICE` e `HEART_RATE` são um
 * valor só. Nunca é gravada como medição no HealthKit/Health Connect (ver `kmplib-health`).
 *
 * [kcal], [low] e [high] já vêm ARREDONDADOS PARA EXIBIR pela régua de [CalorieEstimator.roundKcal]
 * (dezena a partir de 10 kcal, unidade abaixo disso, nunca 0 para gasto positivo — 2.299.0). [exactKcal]
 * (2.299.0) é o valor da conta, sem arredondar: é o que se SOMA (semana, mês) e o que se manda ao
 * backend — somar valores já arredondados acumula o erro do arredondamento a cada treino.
 */
data class Estimate(
    val kcal: Int,
    val low: Int?,
    val high: Int?,
    val source: EnergySource,
    val exactKcal: Double = kcal.toDouble(),
)

enum class WorkoutIntensity { LIGHT, MODERATE, VIGOROUS }

enum class WorkoutType { STRENGTH, CIRCUIT, HIIT }

/**
 * Subconjunto do *2024 Adult Compendium of Physical Activities* (CC BY-NC-ND — citamos a fonte,
 * não republicamos o documento): só os ~9 valores numéricos que o produto usa.
 */
object MetTable {
    fun metFor(type: WorkoutType, intensity: WorkoutIntensity): Double = when (type) {
        WorkoutType.STRENGTH -> when (intensity) {
            WorkoutIntensity.LIGHT -> 3.5 // 02054 — musculação, vários exercícios, 8-15 reps
            WorkoutIntensity.MODERATE -> 5.0 // 02052 — agachamento/levantamento terra, lento ou explosivo
            WorkoutIntensity.VIGOROUS -> 6.0 // 02050 — musculação/powerlifting/fisiculturismo vigoroso
        }

        WorkoutType.CIRCUIT -> when (intensity) {
            WorkoutIntensity.LIGHT -> 5.0 // 02035 — circuito moderado
            WorkoutIntensity.MODERATE -> 6.25 // ponto médio moderado/vigoroso (sem valor próprio no Compendium)
            WorkoutIntensity.VIGOROUS -> 7.5 // 02040 — circuito vigoroso com kettlebell
        }

        WorkoutType.HIIT -> when (intensity) {
            WorkoutIntensity.LIGHT -> 7.0
            WorkoutIntensity.MODERATE -> 9.0
            WorkoutIntensity.VIGOROUS -> 11.0
        }
    }

    /** Esforço de 1 a 10 informado pelo aluno ao fim do treino (RF-FIM-03) -> intensidade do MET. */
    fun intensityFromEffort(effort: Int): WorkoutIntensity = when {
        effort <= 4 -> WorkoutIntensity.LIGHT
        effort <= 7 -> WorkoutIntensity.MODERATE
        else -> WorkoutIntensity.VIGOROUS
    }
}

/**
 * Estimativa de gasto calórico em musculação — nenhuma fonte é precisa (erro de 30 a 50% ou mais
 * em qualquer uma, Fuller 2020/Düking 2020/Polar Verity Sense em força); o relógio é a MELHOR
 * fonte disponível, não uma fonte precisa. Por isso a UI sempre mostra "≈ X kcal (estimativa)" com
 * a origem — nunca um número seco.
 */
object CalorieEstimator {

    /** Fonte 1: valor que já veio do relógio ou do repositório de saúde (HealthKit/Health Connect),
     * lido por INTERVALO do treino — nunca somado a uma segunda contagem. */
    fun fromDevice(kcal: Double): Estimate = estimateOf(kcal, withRange = false, EnergySource.DEVICE)

    /**
     * Fonte 2: equação de Keytel et al. (2005), validada em exercício aeróbico contínuo — NÃO em
     * musculação (a FC sobe por pressão/isometria e desacopla do O2 consumido, tendendo a
     * SUPERESTIMAR). O teto de bom senso fica a cargo do chamador (ex.: `estimate()` abaixo).
     *
     * Devolve `null` também quando a equação dá gasto ≤ 0 (FC baixa demais para ela — acontece na fórmula
     * feminina perto de 60 bpm): "0 kcal pela sua frequência cardíaca" num treino que durou é número
     * falso, e o `estimate()` cai para o MET (2.299.0; até a 2.298.0 vinha `Estimate(0)`).
     */
    fun fromHeartRate(avgHeartRate: Int, weightKg: Double, ageYears: Int, isMale: Boolean, minutes: Double): Estimate? {
        if (weightKg <= 0 || minutes <= 0 || avgHeartRate <= 0) return null
        val hr = avgHeartRate.toDouble()
        val kJPerMin = if (isMale) {
            -55.0969 + 0.6309 * hr + 0.1988 * weightKg + 0.2017 * ageYears
        } else {
            -20.4022 + 0.4472 * hr - 0.1263 * weightKg + 0.074 * ageYears
        }
        val kcalPerMin = kJPerMin / 4.184
        if (kcalPerMin <= 0.0) return null
        return estimateOf(kcalPerMin * minutes, withRange = false, EnergySource.HEART_RATE)
    }

    /** Fonte 3, o piso para todo aluno: `kcal = MET x peso(kg) x horas`. Sem peso, não há
     * estimativa (regra do produto: "sem peso, não mostra"). Devolve faixa (±30%), porque o MET
     * médio ignora o descanso entre séries e a pessoa. */
    fun fromMet(weightKg: Double?, minutes: Double, type: WorkoutType, effort: Int): Estimate? {
        if (weightKg == null || weightKg <= 0 || minutes <= 0) return null
        val met = MetTable.metFor(type, MetTable.intensityFromEffort(effort))
        val kcal = met * weightKg * (minutes / 60.0)
        return estimateOf(kcal, withRange = true, EnergySource.MET)
    }

    /**
     * Prioridade da fonte (RF-CAL-01/03): (1) relógio/repositório de saúde no intervalo do treino,
     * (2) FC ao vivo com teto de 2x a estimativa por MET quando ela existir (senão sem teto),
     * (3) MET x peso x tempo. Sem nenhuma fonte disponível, devolve `null` (a UI não mostra nada).
     *
     * `deviceKcal` ≤ 0 conta como AUSENTE (2.299.0): o repositório de saúde devolve 0 quando não há
     * registro no intervalo, e isso não é medição — cai para a FC/MET em vez de mostrar "0 kcal".
     */
    fun estimate(
        deviceKcal: Double?,
        heartRate: HeartRateInput?,
        met: MetInput,
    ): Estimate? {
        if (deviceKcal != null && deviceKcal > 0.0) return fromDevice(deviceKcal)

        val metEstimate = fromMet(met.weightKg, met.minutes, met.type, met.effort)

        if (heartRate != null) {
            val hrEstimate = fromHeartRate(
                heartRate.avgHeartRate,
                heartRate.weightKg,
                heartRate.ageYears,
                heartRate.isMale,
                heartRate.minutes,
            )
            if (hrEstimate != null) {
                val cap = metEstimate?.let { it.exactKcal * 2 }
                return if (cap != null && hrEstimate.exactKcal > cap) {
                    estimateOf(cap, withRange = false, EnergySource.HEART_RATE)
                } else {
                    hrEstimate
                }
            }
        }

        return metEstimate
    }

    data class HeartRateInput(val avgHeartRate: Int, val weightKg: Double, val ageYears: Int, val isMale: Boolean, val minutes: Double)

    data class MetInput(val weightKg: Double?, val minutes: Double, val type: WorkoutType, val effort: Int)

    /**
     * A régua de EXIBIÇÃO do gasto (2.299.0) — use-a para qualquer kcal que vá para a tela, inclusive
     * somas (semana, mês) feitas sobre [Estimate.exactKcal]:
     * - **≥ 10 kcal: dezena** ("≈ 230 kcal") — a estimativa erra 30 a 50%, e o dígito da unidade seria
     *   precisão que ela não tem;
     * - **> 0 e < 10 kcal: unidade, no mínimo 1** ("≈ 4 kcal") — até a 2.298.0 tudo ia para a dezena, e
     *   treino curto (< ~1 min, ou só aquecimento) virava **0 kcal**: dado zerado na tela e no histórico;
     * - **≤ 0 (ou não finito): 0** — não há gasto a mostrar.
     *
     * Arredondamento comercial (meio para cima): 4,5 → 5; 15 → 20.
     */
    fun roundKcal(value: Double): Int = when {
        !value.isFinite() || value <= 0.0 -> 0
        value < 10.0 -> floor(value + 0.5).toInt().coerceIn(1, 10)
        else -> (floor(value / 10.0 + 0.5) * 10).toInt()
    }

    private fun estimateOf(exact: Double, withRange: Boolean, source: EnergySource): Estimate {
        val value = if (exact.isFinite()) exact.coerceAtLeast(0.0) else 0.0
        return Estimate(
            kcal = roundKcal(value),
            low = if (withRange) roundKcal(value * 0.7) else null,
            high = if (withRange) roundKcal(value * 1.3) else null,
            source = source,
            exactKcal = value,
        )
    }
}
