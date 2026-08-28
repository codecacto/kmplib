package br.com.codecacto.kmplib.astro

import kotlinx.datetime.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Conversões de tempo usadas pelo cálculo lunar: **Dia Juliano** (JD), **escala dinâmica** (TT/TD) e
 * a diferença ΔT entre elas.
 *
 * Por que isto existe: os algoritmos de Meeus produzem **JDE** (Julian *Ephemeris* Day, na escala
 * dinâmica TT), enquanto todo relógio de aparelho vive em UTC. Ignorar a conversão embute um erro
 * fixo de ~70 s hoje (e de minutos em datas do século passado ou do próximo). Não muda o dia civil
 * quase nunca — mas "quase nunca" é exatamente o tipo de premissa que quebra um cronograma ancorado
 * num instante, e o custo de fazer certo é um polinômio.
 *
 * Interno de propósito: a API pública fala em [kotlinx.datetime.Instant].
 */
internal object AstroTime {

    /** JD do epoch Unix (1970-01-01T00:00:00Z). */
    const val UNIX_EPOCH_JD: Double = 2440587.5

    /** JD de J2000.0 (2000-01-01T12:00:00 TT). */
    const val J2000_JD: Double = 2451545.0

    const val MILLIS_PER_DAY: Double = 86_400_000.0
    const val SECONDS_PER_DAY: Double = 86_400.0

    /** Dia Juliano (escala UTC) de um instante. */
    fun julianDay(instant: Instant): Double =
        UNIX_EPOCH_JD + instant.toEpochMilliseconds() / MILLIS_PER_DAY

    /** Instante correspondente a um Dia Juliano na escala UTC. */
    fun instantOfJulianDay(julianDay: Double): Instant =
        Instant.fromEpochMilliseconds(((julianDay - UNIX_EPOCH_JD) * MILLIS_PER_DAY).roundToLong())

    /**
     * Converte um **JDE** (escala dinâmica TT, saída dos algoritmos de Meeus) no instante UTC
     * correspondente: `UTC = TT − ΔT`.
     *
     * ΔT é avaliado no ano do próprio JDE (a dependência de ΔT com o ano é suave o bastante para
     * que uma iteração baste).
     */
    fun instantOfEphemerisJulianDay(jde: Double): Instant {
        val year = approximateYearOf(jde)
        val deltaTDays = deltaTSeconds(year) / SECONDS_PER_DAY
        return instantOfJulianDay(jde - deltaTDays)
    }

    /** Séculos julianos desde J2000.0 para um JD/JDE. */
    fun julianCenturies(julianDay: Double): Double = (julianDay - J2000_JD) / 36525.0

    /** Ano gregoriano aproximado (com fração) de um Dia Juliano — precisão de dias, suficiente p/ ΔT. */
    fun approximateYearOf(julianDay: Double): Double = 2000.0 + (julianDay - J2000_JD) / 365.25

    /**
     * ΔT = TT − UT, em segundos, pelos polinômios de **Espenak & Meeus** (os mesmos publicados pela
     * NASA no *Five Millennium Canon of Solar Eclipses*), cobrindo **1600–2150** — a era das
     * observações telescópicas, que é onde o algoritmo de fases faz sentido — e caindo na parábola
     * secular `−20 + 32u²` fora desse intervalo.
     *
     * Precisão suficiente com folga: um erro de alguns segundos em ΔT é ordens de grandeza menor que
     * a incerteza do próprio algoritmo de fases (≈ ±alguns segundos a ±1 minuto).
     *
     * Antes de 1600 a própria ΔT histórica é incerta em dezenas de segundos (é reconstruída de
     * registros de eclipses antigos), então a descontinuidade da parábola naquela borda não é ruído
     * que se possa "consertar" — é a incerteza real do dado.
     */
    fun deltaTSeconds(year: Double): Double = when {
        year < 1600.0 || year > 2150.0 -> {
            val u = (year - 1820.0) / 100.0
            -20.0 + 32.0 * u * u
        }

        year < 1700.0 -> {
            val t = year - 1600.0
            120.0 - 0.9808 * t - 0.01532 * t.pow(2) + t.pow(3) / 7129.0
        }

        year < 1800.0 -> {
            val t = year - 1700.0
            8.83 + 0.1603 * t - 0.0059285 * t.pow(2) + 0.00013336 * t.pow(3) - t.pow(4) / 1174000.0
        }

        year < 1860.0 -> {
            val t = year - 1800.0
            13.72 - 0.332447 * t + 0.0068612 * t.pow(2) + 0.0041116 * t.pow(3) -
                0.00037436 * t.pow(4) + 0.0000121272 * t.pow(5) -
                0.0000001699 * t.pow(6) + 0.000000000875 * t.pow(7)
        }

        year < 1900.0 -> {
            val t = year - 1860.0
            7.62 + 0.5737 * t - 0.251754 * t.pow(2) + 0.01680668 * t.pow(3) -
                0.0004473624 * t.pow(4) + t.pow(5) / 233174.0
        }

        year < 1920.0 -> {
            val t = year - 1900.0
            -2.79 + 1.494119 * t - 0.0598939 * t.pow(2) + 0.0061966 * t.pow(3) - 0.000197 * t.pow(4)
        }

        year < 1941.0 -> {
            val t = year - 1920.0
            21.20 + 0.84493 * t - 0.076100 * t.pow(2) + 0.0020936 * t.pow(3)
        }

        year < 1961.0 -> {
            val t = year - 1950.0
            29.07 + 0.407 * t - t.pow(2) / 233.0 + t.pow(3) / 2547.0
        }

        year < 1986.0 -> {
            val t = year - 1975.0
            45.45 + 1.067 * t - t.pow(2) / 260.0 - t.pow(3) / 718.0
        }

        year < 2005.0 -> {
            val t = year - 2000.0
            63.86 + 0.3345 * t - 0.060374 * t.pow(2) + 0.0017275 * t.pow(3) +
                0.000651814 * t.pow(4) + 0.00002373599 * t.pow(5)
        }

        year < 2050.0 -> {
            val t = year - 2000.0
            62.92 + 0.32217 * t + 0.005589 * t.pow(2)
        }

        else -> {
            val u = (year - 1820.0) / 100.0
            -20.0 + 32.0 * u * u - 0.5628 * (2150.0 - year)
        }
    }

    private fun Double.pow(n: Int): Double {
        var result = 1.0
        repeat(n) { result *= this }
        return result
    }
}

/** Graus → radianos. */
internal const val DEG_TO_RAD: Double = PI / 180.0

/** Seno de um ângulo em GRAUS (os polinômios de Meeus vêm todos em graus). */
internal fun sinDeg(degrees: Double): Double = sin(degrees * DEG_TO_RAD)

/** Cosseno de um ângulo em GRAUS. */
internal fun cosDeg(degrees: Double): Double = cos(degrees * DEG_TO_RAD)

/** Normaliza um ângulo para [0, 360). */
internal fun normalizeDegrees(degrees: Double): Double {
    val wrapped = degrees % 360.0
    return if (wrapped < 0.0) wrapped + 360.0 else wrapped
}
