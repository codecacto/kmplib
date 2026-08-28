package br.com.codecacto.kmplib.astro

import kotlinx.datetime.Instant
import kotlin.math.floor

/**
 * Instantes das 4 fases principais da lua pelo **capítulo 49 de Jean Meeus, _Astronomical
 * Algorithms_ (2ª ed.)** — série de correções periódicas sobre a fase média, com os termos de
 * anomalia solar/lunar, argumento de latitude, nodo ascendente e as 14 correções planetárias.
 *
 * Precisão declarada por Meeus: erro máximo de **~17 s** e médio de **~4 s** frente às efemérides
 * modernas (ELP-2000/82) para o período 1980–2020, degradando suavemente fora dele. É o algoritmo
 * de referência para calendário lunar embarcado — o único caminho melhor seria integrar uma
 * efeméride completa (VSOP87/ELP ou JPL DE), o que é desproporcional para um app offline.
 *
 * ### Por que NÃO a "idade lunar por módulo do sinódico médio"
 * O atalho clássico (`(data − época_de_referência) mod 29.530588861`) trata o mês sinódico como
 * constante. Ele **não é**: varia entre ~29,27 e ~29,83 dias por causa da excentricidade das órbitas
 * lunar e terrestre. O erro instantâneo chega a **±0,6 dia** — mais de meio dia. Para exibir "hoje é
 * lua crescente" isso passa despercebido; para **ancorar um cronograma no instante da lua nova**
 * (protocolo de N dias contados a partir dela), meio dia de erro desloca o ciclo inteiro em um dia.
 *
 * Interno: a superfície pública é [MoonCalculator].
 */
internal object MeeusMoonPhases {

    /** Mês sinódico MÉDIO, em dias — usado só como passo de busca do `k`, nunca como verdade. */
    const val MEAN_SYNODIC_MONTH: Double = 29.530588861

    /** Lunações por ano juliano (Meeus 49.2): `k ≈ (ano − 2000) × 12.3685`. */
    private const val LUNATIONS_PER_YEAR: Double = 12.3685

    /**
     * Instante (UTC) da ocorrência de [phase] identificada pelo índice de lunação [lunation].
     *
     * `lunation = 0` corresponde à lua nova de 2000-01-06; valores negativos vão para o passado.
     */
    fun instantOf(phase: PrincipalMoonPhase, lunation: Int): Instant =
        AstroTime.instantOfEphemerisJulianDay(ephemerisJulianDay(phase, lunation))

    /**
     * Índice de lunação cuja ocorrência de [phase] é a **última que não passa** de [instant]
     * (aproximação; quem usa deve varrer lunações vizinhas conferindo o instante real, porque a
     * estimativa de `k` é média e pode errar por uma unidade perto do marco).
     */
    fun approximateLunation(phase: PrincipalMoonPhase, instant: Instant): Int {
        val year = AstroTime.approximateYearOf(AstroTime.julianDay(instant))
        val k = (year - 2000.0) * LUNATIONS_PER_YEAR - phase.cycleOffset
        return floor(k).toInt()
    }

    /** JDE (escala dinâmica TT) da ocorrência de [phase] na lunação [lunation]. */
    fun ephemerisJulianDay(phase: PrincipalMoonPhase, lunation: Int): Double {
        val k = lunation + phase.cycleOffset
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // (49.1) — instante da fase MÉDIA.
        var jde = 2451550.09766 +
            MEAN_SYNODIC_MONTH * k +
            0.00015437 * t2 -
            0.000000150 * t3 +
            0.00000000073 * t4

        // Excentricidade da órbita terrestre (49.6) — escala os termos que dependem do Sol.
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2
        val e2 = e * e

        // Anomalia média do Sol.
        val m = normalizeDegrees(
            2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3
        )
        // Anomalia média da Lua.
        val mp = normalizeDegrees(
            201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4
        )
        // Argumento de latitude da Lua.
        val f = normalizeDegrees(
            160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4
        )
        // Longitude do nodo ascendente da órbita lunar.
        val omega = normalizeDegrees(
            124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3
        )

        jde += when (phase) {
            PrincipalMoonPhase.NEW -> newMoonCorrection(e, e2, m, mp, f, omega)
            PrincipalMoonPhase.FULL -> fullMoonCorrection(e, e2, m, mp, f, omega)
            PrincipalMoonPhase.FIRST_QUARTER, PrincipalMoonPhase.LAST_QUARTER -> {
                val base = quarterCorrection(e, e2, m, mp, f, omega)
                val w = 0.00306 -
                    0.00038 * e * cosDeg(m) +
                    0.00026 * cosDeg(mp) -
                    0.00002 * cosDeg(mp - m) +
                    0.00002 * cosDeg(mp + m) +
                    0.00002 * cosDeg(2.0 * f)
                if (phase == PrincipalMoonPhase.FIRST_QUARTER) base + w else base - w
            }
        }

        jde += planetaryCorrection(k, t2)
        return jde
    }

    /** Correções periódicas da LUA NOVA (tabela de Meeus 49). */
    private fun newMoonCorrection(
        e: Double,
        e2: Double,
        m: Double,
        mp: Double,
        f: Double,
        omega: Double,
    ): Double =
        -0.40720 * sinDeg(mp) +
            0.17241 * e * sinDeg(m) +
            0.01608 * sinDeg(2.0 * mp) +
            0.01039 * sinDeg(2.0 * f) +
            0.00739 * e * sinDeg(mp - m) -
            0.00514 * e * sinDeg(mp + m) +
            0.00208 * e2 * sinDeg(2.0 * m) -
            0.00111 * sinDeg(mp - 2.0 * f) -
            0.00057 * sinDeg(mp + 2.0 * f) +
            0.00056 * e * sinDeg(2.0 * mp + m) -
            0.00042 * sinDeg(3.0 * mp) +
            0.00042 * e * sinDeg(m + 2.0 * f) +
            0.00038 * e * sinDeg(m - 2.0 * f) -
            0.00024 * e * sinDeg(2.0 * mp - m) -
            0.00017 * sinDeg(omega) -
            0.00007 * sinDeg(mp + 2.0 * m) +
            0.00004 * sinDeg(2.0 * mp - 2.0 * f) +
            0.00004 * sinDeg(3.0 * m) +
            0.00003 * sinDeg(mp + m - 2.0 * f) +
            0.00003 * sinDeg(2.0 * mp + 2.0 * f) -
            0.00003 * sinDeg(mp + m + 2.0 * f) +
            0.00003 * sinDeg(mp - m + 2.0 * f) -
            0.00002 * sinDeg(mp - m - 2.0 * f) -
            0.00002 * sinDeg(3.0 * mp + m) +
            0.00002 * sinDeg(4.0 * mp)

    /** Correções periódicas da LUA CHEIA (tabela de Meeus 49). */
    private fun fullMoonCorrection(
        e: Double,
        e2: Double,
        m: Double,
        mp: Double,
        f: Double,
        omega: Double,
    ): Double =
        -0.40614 * sinDeg(mp) +
            0.17302 * e * sinDeg(m) +
            0.01614 * sinDeg(2.0 * mp) +
            0.01043 * sinDeg(2.0 * f) +
            0.00734 * e * sinDeg(mp - m) -
            0.00515 * e * sinDeg(mp + m) +
            0.00209 * e2 * sinDeg(2.0 * m) -
            0.00111 * sinDeg(mp - 2.0 * f) -
            0.00057 * sinDeg(mp + 2.0 * f) +
            0.00056 * e * sinDeg(2.0 * mp + m) -
            0.00042 * sinDeg(3.0 * mp) +
            0.00042 * e * sinDeg(m + 2.0 * f) +
            0.00038 * e * sinDeg(m - 2.0 * f) -
            0.00024 * e * sinDeg(2.0 * mp - m) -
            0.00017 * sinDeg(omega) -
            0.00007 * sinDeg(mp + 2.0 * m) +
            0.00004 * sinDeg(2.0 * mp - 2.0 * f) +
            0.00004 * sinDeg(3.0 * m) +
            0.00003 * sinDeg(mp + m - 2.0 * f) +
            0.00003 * sinDeg(2.0 * mp + 2.0 * f) -
            0.00003 * sinDeg(mp + m + 2.0 * f) +
            0.00003 * sinDeg(mp - m + 2.0 * f) -
            0.00002 * sinDeg(mp - m - 2.0 * f) -
            0.00002 * sinDeg(3.0 * mp + m) +
            0.00002 * sinDeg(4.0 * mp)

    /** Correções periódicas dos QUARTOS (crescente e minguante — tabela de Meeus 49). */
    private fun quarterCorrection(
        e: Double,
        e2: Double,
        m: Double,
        mp: Double,
        f: Double,
        omega: Double,
    ): Double =
        -0.62801 * sinDeg(mp) +
            0.17172 * e * sinDeg(m) -
            0.01183 * e * sinDeg(mp + m) +
            0.00862 * sinDeg(2.0 * mp) +
            0.00804 * sinDeg(2.0 * f) +
            0.00454 * e * sinDeg(mp - m) +
            0.00204 * e2 * sinDeg(2.0 * m) -
            0.00180 * sinDeg(mp - 2.0 * f) -
            0.00070 * sinDeg(mp + 2.0 * f) -
            0.00040 * sinDeg(3.0 * mp) -
            0.00034 * e * sinDeg(2.0 * mp - m) +
            0.00032 * e * sinDeg(m + 2.0 * f) +
            0.00032 * e * sinDeg(m - 2.0 * f) -
            0.00028 * e2 * sinDeg(mp + 2.0 * m) +
            0.00027 * e * sinDeg(2.0 * mp + m) -
            0.00017 * sinDeg(omega) -
            0.00005 * sinDeg(mp - m - 2.0 * f) +
            0.00004 * sinDeg(2.0 * mp + 2.0 * f) -
            0.00004 * sinDeg(mp + m + 2.0 * f) +
            0.00004 * sinDeg(mp - 2.0 * m) +
            0.00003 * sinDeg(mp + m - 2.0 * f) +
            0.00003 * sinDeg(3.0 * m) +
            0.00002 * sinDeg(2.0 * mp - 2.0 * f) +
            0.00002 * sinDeg(mp - m + 2.0 * f) -
            0.00002 * sinDeg(3.0 * mp + m)

    /** As 14 correções planetárias adicionais (A1..A14), iguais para todas as fases. */
    private fun planetaryCorrection(k: Double, t2: Double): Double {
        val a1 = 299.77 + 0.107408 * k - 0.009173 * t2
        val a2 = 251.88 + 0.016321 * k
        val a3 = 251.83 + 26.651886 * k
        val a4 = 349.42 + 36.412478 * k
        val a5 = 84.66 + 18.206239 * k
        val a6 = 141.74 + 53.303771 * k
        val a7 = 207.14 + 2.453732 * k
        val a8 = 154.84 + 7.306860 * k
        val a9 = 34.52 + 27.261239 * k
        val a10 = 207.19 + 0.121824 * k
        val a11 = 291.34 + 1.844379 * k
        val a12 = 161.72 + 24.198154 * k
        val a13 = 239.56 + 25.513099 * k
        val a14 = 331.55 + 3.592518 * k

        return 0.000325 * sinDeg(a1) +
            0.000165 * sinDeg(a2) +
            0.000164 * sinDeg(a3) +
            0.000126 * sinDeg(a4) +
            0.000110 * sinDeg(a5) +
            0.000062 * sinDeg(a6) +
            0.000060 * sinDeg(a7) +
            0.000056 * sinDeg(a8) +
            0.000047 * sinDeg(a9) +
            0.000042 * sinDeg(a10) +
            0.000040 * sinDeg(a11) +
            0.000037 * sinDeg(a12) +
            0.000035 * sinDeg(a13) +
            0.000023 * sinDeg(a14)
    }
}
