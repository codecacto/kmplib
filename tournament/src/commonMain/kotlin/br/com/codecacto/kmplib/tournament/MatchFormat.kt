package br.com.codecacto.kmplib.tournament

/**
 * Formato de partida de **esporte de raquete** (beach tennis, tênis, padel): quantos sets, de
 * quantos games, com ou sem tie-break, e se o set decisivo vira super tie-break.
 *
 * Serve a duas coisas, e é por isso que mora no motor e não na tela:
 * 1. **Validar o placar digitado** ([validate]) — `6-4` fecha um set de 6, `6-5` não; `7-6` exige o
 *    tie-break; `8-6` não existe num set de 6. Placar inválido aceito vira saldo de games errado, e
 *    saldo de games decide classificação.
 * 2. **Derivar o vencedor do placar** ([outcomeOf]) — em raquete não existe empate, e quem venceu sai
 *    do placar; o organizador não marca vencedor à parte (seria uma segunda fonte de verdade).
 *
 * Placar por pontos (futebol, pênalti) não passa por aqui: lá o `Score.Points` é o resultado.
 *
 * @property setsToWin sets para vencer a partida (1 = set único; 2 = melhor de 3).
 * @property gamesPerSet games que fecham o set (6; 8 no pro-set).
 * @property tieBreak `true` = tie-break em `gamesPerSet`×`gamesPerSet` (o set termina `7-6`/`9-8`);
 *   `false` = set de vantagem, decidido por 2 games de diferença sem teto.
 * @property tieBreakPoints pontos do tie-break (7), com 2 de diferença.
 * @property decidingSetMatchTieBreak o set decisivo (o 3º do melhor de 3) é um **super tie-break**,
 *   registrado com [SetScore.matchTieBreak].
 * @property matchTieBreakPoints pontos do super tie-break (10), com 2 de diferença.
 */
data class MatchFormat(
    val setsToWin: Int,
    val gamesPerSet: Int,
    val tieBreak: Boolean = true,
    val tieBreakPoints: Int = 7,
    val decidingSetMatchTieBreak: Boolean = false,
    val matchTieBreakPoints: Int = 10,
) {
    init {
        require(setsToWin >= 1) { "setsToWin precisa ser ≥ 1 (recebeu $setsToWin)" }
        require(gamesPerSet >= 1) { "gamesPerSet precisa ser ≥ 1 (recebeu $gamesPerSet)" }
        require(tieBreakPoints >= 1 && matchTieBreakPoints >= 1) { "tie-break precisa de ao menos 1 ponto" }
        require(!decidingSetMatchTieBreak || setsToWin >= 2) {
            "super tie-break no set decisivo só existe com mais de um set"
        }
    }

    /** Número máximo de sets jogados (`2 × setsToWin − 1`). */
    val maxSets: Int get() = 2 * setsToWin - 1

    /**
     * Valida o placar e, se válido, diz quem venceu.
     *
     * Erros saem como [ScoreError] (código, sem texto — a frase é da UI) e com o índice do set
     * (0-based) quando o defeito é de um set.
     */
    fun validate(score: Score): ScoreValidation {
        val sets = (score as? Score.Sets)?.sets ?: return ScoreValidation.Invalid(ScoreError.NOT_A_SET_SCORE)
        if (sets.isEmpty()) return ScoreValidation.Invalid(ScoreError.NO_SETS)

        var wonA = 0
        var wonB = 0
        sets.forEachIndexed { index, set ->
            if (wonA == setsToWin || wonB == setsToWin) {
                return ScoreValidation.Invalid(ScoreError.SET_AFTER_MATCH_DECIDED, index)
            }
            val deciding = decidingSetMatchTieBreak && wonA == setsToWin - 1 && wonB == setsToWin - 1
            val error = if (deciding) checkMatchTieBreak(set) else checkSet(set)
            if (error != null) return ScoreValidation.Invalid(error, index)
            if (set.winner == Side.A) wonA++ else wonB++
        }
        return when {
            wonA == setsToWin -> ScoreValidation.Valid(Side.A)
            wonB == setsToWin -> ScoreValidation.Valid(Side.B)
            else -> ScoreValidation.Invalid(ScoreError.MATCH_NOT_FINISHED)
        }
    }

    /** `true` se o placar fecha uma partida válida neste formato. */
    fun isValid(score: Score): Boolean = validate(score) is ScoreValidation.Valid

    /** Vencedor derivado do placar, ou `null` se o placar não é válido. */
    fun winnerOf(score: Score): Side? = (validate(score) as? ScoreValidation.Valid)?.winner

    /**
     * O desfecho que o placar produz: [MatchOutcome.Decided] com o vencedor, ou
     * [MatchOutcome.InProgress] enquanto o placar não fecha uma partida válida.
     */
    fun outcomeOf(score: Score): MatchOutcome =
        winnerOf(score)?.let { MatchOutcome.Decided(it) } ?: MatchOutcome.InProgress

    /**
     * O placar **convencionado** do W.O.: o vencedor leva `setsToWin` sets de `gamesPerSet`×0
     * (beach tennis de set único: `6-0`; melhor de 3: `6-0 6-0`; pro-set: `8-0`).
     */
    fun walkoverScore(winner: Side): Score.Sets {
        val forA = Score.Sets(List(setsToWin) { SetScore(gamesPerSet, 0) })
        return if (winner == Side.A) forA else forA.mirrored()
    }

    /** Um [MatchResult] de W.O. pronto: desfecho [MatchOutcome.Walkover] + [walkoverScore]. */
    fun walkover(sideAId: Long, sideBId: Long, winner: Side): MatchResult =
        MatchResult(sideAId, sideBId, MatchOutcome.Walkover(winner), walkoverScore(winner))

    private fun checkSet(set: SetScore): ScoreError? {
        if (set.a < 0 || set.b < 0) return ScoreError.NEGATIVE_VALUE
        val high = maxOf(set.a, set.b)
        val low = minOf(set.a, set.b)
        val g = gamesPerSet

        if (!tieBreak) {
            if (set.tieBreak != null) return ScoreError.UNEXPECTED_TIE_BREAK
            if (high < g || high - low < 2) return ScoreError.SET_NOT_FINISHED
            if (high > g && high - low != 2) return ScoreError.IMPOSSIBLE_SET_SCORE
            return null
        }

        return when {
            high < g -> ScoreError.SET_NOT_FINISHED
            high == g && low <= g - 2 -> if (set.tieBreak != null) ScoreError.UNEXPECTED_TIE_BREAK else null
            // 6-5 (falta um game) e 6-6 (falta o tie-break): o set não acabou.
            high == g -> ScoreError.SET_NOT_FINISHED
            high == g + 1 && low == g - 1 -> if (set.tieBreak != null) ScoreError.UNEXPECTED_TIE_BREAK else null
            high == g + 1 && low == g -> {
                val tb = set.tieBreak ?: return ScoreError.TIE_BREAK_REQUIRED
                if (!isValidTieBreak(tb, tieBreakPoints) || tb.winner != set.winner) {
                    ScoreError.INVALID_TIE_BREAK
                } else {
                    null
                }
            }
            else -> ScoreError.IMPOSSIBLE_SET_SCORE
        }
    }

    private fun checkMatchTieBreak(set: SetScore): ScoreError? {
        if (set.a < 0 || set.b < 0) return ScoreError.NEGATIVE_VALUE
        val tb = set.tieBreak ?: return ScoreError.INVALID_MATCH_TIE_BREAK
        if (tb.a < 0 || tb.b < 0) return ScoreError.NEGATIVE_VALUE
        val gamesOk = (set.a == 1 && set.b == 0) || (set.a == 0 && set.b == 1)
        if (!gamesOk || !isValidTieBreak(tb, matchTieBreakPoints) || tb.winner != set.winner) {
            return ScoreError.INVALID_MATCH_TIE_BREAK
        }
        return null
    }

    private fun isValidTieBreak(tb: TieBreak, target: Int): Boolean {
        if (tb.a < 0 || tb.b < 0) return false
        val high = maxOf(tb.a, tb.b)
        val low = minOf(tb.a, tb.b)
        // Chega a `target` com 2 de vantagem; passou do alvo, só termina com exatamente 2.
        return high >= target && high - low >= 2 && (high == target || high - low == 2)
    }

    companion object {
        /** **Padrão do beach tennis**: 1 set de 6 games, tie-break de 7 no 6×6. */
        val ONE_SET_OF_SIX = MatchFormat(setsToWin = 1, gamesPerSet = 6)

        /** Pro-set de 8 games, tie-break de 7 no 8×8 (termina `9-8`). */
        val PRO_SET_OF_EIGHT = MatchFormat(setsToWin = 1, gamesPerSet = 8)

        /** Melhor de 3 sets de 6 (tie-break no 6×6), com super tie-break de 10 no lugar do 3º set. */
        val BEST_OF_THREE_MATCH_TIE_BREAK = MatchFormat(
            setsToWin = 2,
            gamesPerSet = 6,
            decidingSetMatchTieBreak = true,
        )
    }
}

/** Resultado de [MatchFormat.validate]. */
sealed interface ScoreValidation {
    data class Valid(val winner: Side) : ScoreValidation

    /** @property setIndex índice (0-based) do set com defeito, ou `null` quando o defeito é da partida. */
    data class Invalid(val error: ScoreError, val setIndex: Int? = null) : ScoreValidation
}

/** Por que o placar não fecha. Código para a UI traduzir — nenhum texto sai da lib. */
enum class ScoreError {
    /** Placar por pontos entregue a um formato de raquete. */
    NOT_A_SET_SCORE,

    /** Nenhum set registrado. */
    NO_SETS,

    /** Games ou pontos negativos. */
    NEGATIVE_VALUE,

    /** O set ainda não acabou (`5-3`, `6-5`, `6-6`). */
    SET_NOT_FINISHED,

    /** Placar que não existe no formato (`8-6` ou `7-4` num set de 6). */
    IMPOSSIBLE_SET_SCORE,

    /** `7-6` sem o tie-break. */
    TIE_BREAK_REQUIRED,

    /** Tie-break num set que não foi a ele (`6-4 (7-5)`), ou num formato sem tie-break. */
    UNEXPECTED_TIE_BREAK,

    /** Tie-break impossível (`7-6`, `8-5`) ou vencido por quem perdeu o set. */
    INVALID_TIE_BREAK,

    /** O set decisivo deveria ser um super tie-break válido (`1-0` com `10-8`, `12-10`…). */
    INVALID_MATCH_TIE_BREAK,

    /** Set registrado depois de a partida já estar decidida (`6-4 6-3 6-2` em melhor de 3). */
    SET_AFTER_MATCH_DECIDED,

    /** Ninguém chegou aos sets necessários (`6-4 3-6` em melhor de 3). */
    MATCH_NOT_FINISHED,
}
