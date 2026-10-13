package br.com.codecacto.kmplib.workout.cardio

import br.com.codecacto.kmplib.workout.model.HeartRateZone
import kotlinx.datetime.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Onde uma FC está em relação à zona-alvo — o que a tela escreve em TEXTO ("abaixo da zona", "dentro
 * da zona", "acima da zona"), além da cor (2.297.0).
 */
enum class ZonePosition { BELOW, INSIDE, ABOVE }

/**
 * FC máxima estimada pela idade, pela fórmula de **Tanaka, Monahan & Seals (2001)**: `208 − 0,7 × idade`,
 * arredondada. É a recomendada no lugar da antiga `220 − idade`, que subestima a FC máxima de quem
 * tem mais de 40 anos. Use a FC máxima MEDIDA (teste de esforço) quando o perfil tiver uma.
 *
 * @throws IllegalArgumentException se [ageYears] estiver fora de 1..120.
 */
fun estimatedMaxHeartRate(ageYears: Int): Int {
    require(ageYears in 1..120) { "idade fora de 1..120: $ageYears" }
    return (208 - 0.7 * ageYears).roundToInt()
}

/**
 * Faixa de bpm da zona para uma FC máxima: do primeiro bpm em que a zona começa até o último antes da
 * próxima (a Z5 vai até a própria FC máxima). As zonas vizinhas não se sobrepõem nem deixam buraco —
 * é a mesma régua de [heartRateZoneOf].
 *
 * @throws IllegalArgumentException se [maxHeartRate] não for positivo.
 */
fun HeartRateZone.bpmRange(maxHeartRate: Int): IntRange {
    require(maxHeartRate > 0) { "FC máxima precisa ser positiva: $maxHeartRate" }
    val first = ceilDiv(maxHeartRate * lowerPercent, 100)
    val last = if (this == HeartRateZone.Z5) maxHeartRate else ceilDiv(maxHeartRate * upperPercent, 100) - 1
    return first..last
}

/**
 * A zona em que [bpm] cai para a FC máxima [maxHeartRate]; `null` abaixo de 50 % (aquecimento/repouso,
 * fora das 5 zonas). Acima da FC máxima continua Z5.
 *
 * @throws IllegalArgumentException se [maxHeartRate] não for positivo.
 */
fun heartRateZoneOf(bpm: Int, maxHeartRate: Int): HeartRateZone? {
    require(maxHeartRate > 0) { "FC máxima precisa ser positiva: $maxHeartRate" }
    val scaled = bpm * 100
    return HeartRateZone.entries.lastOrNull { scaled >= maxHeartRate * it.lowerPercent }
}

/**
 * Posição de [bpm] em relação a esta zona-alvo. Na Z5 não existe "acima" (tudo além de 90 % é Z5).
 *
 * @throws IllegalArgumentException se [maxHeartRate] não for positivo.
 */
fun HeartRateZone.positionOf(bpm: Int, maxHeartRate: Int): ZonePosition {
    val range = bpmRange(maxHeartRate)
    return when {
        bpm < range.first -> ZonePosition.BELOW
        this != HeartRateZone.Z5 && bpm > range.last -> ZonePosition.ABOVE
        else -> ZonePosition.INSIDE
    }
}

/**
 * Uma leitura de FC num instante — a amostra do sensor (BLE, relógio) que a tela acumula durante o
 * cardio para medir o tempo na zona. Fica em memória: a regra é não guardar série temporal de FC,
 * só os agregados (`CardioResult`).
 */
data class HeartRateSample(val at: Instant, val bpm: Int) {
    /** Sem bpm nem instante (dado de saúde + rastro da rotina). */
    override fun toString(): String = "HeartRateSample(***)"
}

/**
 * Segundos (inteiros, arredondados para baixo) em que a FC esteve DENTRO de [zone] na janela
 * [from]..[to] — o `timeInTargetZoneSeconds` do resultado do cardio.
 *
 * Cada amostra vale do seu instante até a amostra seguinte, mas no máximo [maxGap] (5 s por padrão):
 * sensor que perdeu contato por dois minutos não conta dois minutos "na zona" com a última leitura.
 * Amostras fora de ordem são ordenadas; amostra antes de [from] ainda vale para o começo da janela
 * (dentro do [maxGap]). Janela vazia ou invertida = 0.
 *
 * @throws IllegalArgumentException se [maxHeartRate] não for positivo ou [maxGap] for negativo.
 */
fun secondsInZone(
    samples: List<HeartRateSample>,
    zone: HeartRateZone,
    maxHeartRate: Int,
    from: Instant,
    to: Instant,
    maxGap: Duration = DEFAULT_SAMPLE_MAX_GAP,
): Int {
    require(maxGap >= Duration.ZERO) { "maxGap negativo: $maxGap" }
    val range = zone.bpmRange(maxHeartRate)
    if (to <= from) return 0
    val sorted = samples.sortedBy { it.at }
    var total = Duration.ZERO
    sorted.forEachIndexed { index, sample ->
        val limit = sample.at + maxGap
        val next = sorted.getOrNull(index + 1)?.at
        val segmentEnd = if (next != null && next < limit) next else limit
        val start = if (sample.at > from) sample.at else from
        val end = if (segmentEnd < to) segmentEnd else to
        if (end > start && sample.bpm in range) total += end - start
    }
    return total.inWholeSeconds.toInt()
}

/** Quanto uma amostra de FC vale, no máximo, até a próxima: 5 s. */
val DEFAULT_SAMPLE_MAX_GAP: Duration = 5.seconds

/** Divisão arredondada para cima, para operandos não negativos. */
private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
