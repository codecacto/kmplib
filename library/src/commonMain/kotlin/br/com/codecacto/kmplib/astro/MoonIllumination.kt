package br.com.codecacto.kmplib.astro

import kotlinx.datetime.Instant

/**
 * Fração iluminada do disco lunar pelo **capítulo 48 de Meeus** (com os argumentos fundamentais do
 * capítulo 47), na forma recomendada pelo próprio autor quando não se precisa da posição completa
 * da Lua: o ângulo de fase `i` a partir da elongação média corrigida, e então
 * `k = (1 + cos i) / 2`.
 *
 * Precisão da fração: melhor que ~0,0014 (0,14 ponto percentual) — muito além do que qualquer UI
 * exibe ("87% iluminada").
 *
 * ### Por que não `(1 − cos(2π · idade / sinódico)) / 2`
 * A aproximação de órbita circular usada por calendários simples supõe que a iluminação avança de
 * forma uniforme no tempo. Ela erra sistematicamente perto dos quartos (onde a derivada é máxima) e
 * herda todo o erro da própria "idade média" — chegando a alguns pontos percentuais. Como a conta
 * correta é uma soma de sete senos, não há motivo para o atalho.
 *
 * Interno: a superfície pública é [MoonCalculator]/[MoonPhaseInfo].
 */
internal object MoonIllumination {

    /**
     * Fração iluminada (0.0 = nova, 1.0 = cheia) no [instant] dado. Sempre em `0.0..1.0`.
     */
    fun fractionAt(instant: Instant): Double {
        val jd = AstroTime.julianDay(instant)
        // O algoritmo é definido na escala dinâmica; a diferença (≈70 s) é irrelevante para a
        // fração, mas converter é barato e mantém a coerência com o resto do módulo.
        val t = AstroTime.julianCenturies(jd + AstroTime.deltaTSeconds(AstroTime.approximateYearOf(jd)) / AstroTime.SECONDS_PER_DAY)
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // (47.2) Elongação média da Lua.
        val d = normalizeDegrees(
            297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0
        )
        // (47.3) Anomalia média do Sol.
        val m = normalizeDegrees(
            357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        )
        // (47.4) Anomalia média da Lua.
        val mp = normalizeDegrees(
            134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0
        )

        // (48.4) Ângulo de fase da Lua, em graus.
        val i = 180.0 - d -
            6.289 * sinDeg(mp) +
            2.100 * sinDeg(m) -
            1.274 * sinDeg(2.0 * d - mp) -
            0.658 * sinDeg(2.0 * d) -
            0.214 * sinDeg(2.0 * mp) -
            0.110 * sinDeg(d)

        val fraction = (1.0 + cosDeg(i)) / 2.0
        return fraction.coerceIn(0.0, 1.0)
    }
}
