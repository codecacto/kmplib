package br.com.codecacto.kmplib.tournament

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchFormatTest {

    private fun sets(vararg s: SetScore) = Score.Sets(*s)
    private fun set(a: Int, b: Int, tb: Pair<Int, Int>? = null) = SetScore(a, b, tb?.let { TieBreak(it.first, it.second) })

    private fun MatchFormat.errorOf(score: Score): ScoreError? = (validate(score) as? ScoreValidation.Invalid)?.error

    // ── Placar ───────────────────────────────────────────────────────────────

    @Test
    fun pointsScore() {
        val score = Score.Points(3, 1)
        assertEquals(3, score.games(Side.A))
        assertEquals(1, score.games(Side.B))
        assertEquals(0, score.sets(Side.A), "placar por pontos não tem sets")
        assertEquals(0, score.setDifference(Side.A))
        assertEquals(2, score.gameDifference(Side.A))
        assertEquals(-2, score.gameDifference(Side.B))
        assertEquals(Side.A, score.leader)
        assertEquals(Side.B, Score.Points(0, 2).leader)
        assertNull(Score.Points.ZERO.leader)
        assertTrue(Score.Points(2, 2).isLevel)
        assertEquals(Score.Points(1, 3), score.mirrored())
        assertEquals(3, score.of(Side.A))
    }

    @Test
    fun setsScoreDerivesSetsAndGames() {
        // 6-4 3-6 [10-7]
        val score = sets(set(6, 4), set(3, 6), SetScore.matchTieBreak(10, 7))
        assertEquals(2, score.sets(Side.A))
        assertEquals(1, score.sets(Side.B))
        assertEquals(1, score.setDifference(Side.A))
        // O super tie-break conta UM game para quem venceu (ITF).
        assertEquals(10, score.games(Side.A))
        assertEquals(10, score.games(Side.B))
        assertEquals(0, score.gameDifference(Side.A))
        assertEquals(sets(set(4, 6), set(6, 3), SetScore.matchTieBreak(7, 10)), score.mirrored())

        val tieBreakSet = set(7, 6, 7 to 5)
        assertEquals(Side.A, tieBreakSet.winner)
        assertEquals(Side.B, set(6, 7, 3 to 7).winner)
        assertEquals(Side.B, set(6, 6, 4 to 7).winner, "games iguais: decide o tie-break")
        assertNull(set(6, 6).winner)
        assertEquals(TieBreak(5, 7), tieBreakSet.tieBreak!!.mirrored())
        assertEquals(7, tieBreakSet.tieBreak.points(Side.A))
        assertNull(TieBreak(3, 3).winner)
        assertEquals(SetScore(0, 1, TieBreak(8, 10)), SetScore.matchTieBreak(8, 10))
        assertEquals(Score.Sets(listOf(set(6, 1))), sets(set(6, 1)))
    }

    // ── 1 set de 6 (beach tennis) ────────────────────────────────────────────

    @Test
    fun oneSetOfSix() {
        val f = MatchFormat.ONE_SET_OF_SIX
        for (loser in 0..4) assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(6, loser))), "6-$loser")
        assertEquals(ScoreValidation.Valid(Side.B), f.validate(sets(set(4, 6))))
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(7, 5))))
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(7, 6, 7 to 5))))
        assertEquals(ScoreValidation.Valid(Side.B), f.validate(sets(set(6, 7, 8 to 10))))

        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(6, 5))), "6-5 é inválido")
        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(6, 6))))
        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(5, 3))))
        assertEquals(ScoreError.TIE_BREAK_REQUIRED, f.errorOf(sets(set(7, 6))), "7-6 exige o tie-break")
        assertEquals(ScoreError.INVALID_TIE_BREAK, f.errorOf(sets(set(7, 6, 7 to 6))), "tie-break sem 2 de vantagem")
        assertEquals(ScoreError.INVALID_TIE_BREAK, f.errorOf(sets(set(7, 6, 5 to 7))), "tie-break do perdedor do set")
        assertEquals(ScoreError.INVALID_TIE_BREAK, f.errorOf(sets(set(7, 6, 9 to 5))), "passou de 7 com mais de 2")
        assertEquals(ScoreError.INVALID_TIE_BREAK, f.errorOf(sets(set(7, 6, 6 to 4))), "não chegou a 7")
        assertEquals(ScoreError.INVALID_TIE_BREAK, f.errorOf(sets(set(7, 6, 7 to -1))))
        assertEquals(ScoreError.UNEXPECTED_TIE_BREAK, f.errorOf(sets(set(6, 4, 7 to 5))))
        assertEquals(ScoreError.UNEXPECTED_TIE_BREAK, f.errorOf(sets(set(7, 5, 7 to 5))))
        assertEquals(ScoreError.IMPOSSIBLE_SET_SCORE, f.errorOf(sets(set(8, 6))))
        assertEquals(ScoreError.IMPOSSIBLE_SET_SCORE, f.errorOf(sets(set(7, 4))))
        assertEquals(ScoreError.NEGATIVE_VALUE, f.errorOf(sets(set(6, -1))))
        assertEquals(ScoreError.NO_SETS, f.errorOf(Score.Sets(emptyList())))
        assertEquals(ScoreError.NOT_A_SET_SCORE, f.errorOf(Score.Points(6, 4)))
        assertEquals(
            ScoreValidation.Invalid(ScoreError.SET_AFTER_MATCH_DECIDED, setIndex = 1),
            f.validate(sets(set(6, 4), set(6, 2))),
        )
        assertEquals(ScoreValidation.Invalid(ScoreError.SET_NOT_FINISHED, setIndex = 0), f.validate(sets(set(6, 5))))

        // Vencedor derivado do placar; raquete não empata.
        assertEquals(Side.A, f.winnerOf(sets(set(6, 3))))
        assertNull(f.winnerOf(sets(set(6, 5))))
        assertEquals(MatchOutcome.Decided(Side.B), f.outcomeOf(sets(set(2, 6))))
        assertEquals(MatchOutcome.InProgress, f.outcomeOf(sets(set(5, 5))))
        assertTrue(f.isValid(sets(set(6, 0))))
        assertFalse(f.isValid(sets(set(6, 5))))
        assertEquals(1, f.maxSets)
    }

    @Test
    fun proSetOfEight() {
        val f = MatchFormat.PRO_SET_OF_EIGHT
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(8, 6))))
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(9, 7))))
        assertEquals(ScoreValidation.Valid(Side.B), f.validate(sets(set(8, 9, 4 to 7))))
        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(6, 4))), "6-4 não fecha pro-set")
        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(8, 7))))
        assertEquals(ScoreError.TIE_BREAK_REQUIRED, f.errorOf(sets(set(9, 8))))
        assertEquals(ScoreError.IMPOSSIBLE_SET_SCORE, f.errorOf(sets(set(9, 6))))
        assertEquals(Score.Sets(listOf(set(8, 0))), f.walkoverScore(Side.A))
    }

    @Test
    fun bestOfThreeWithMatchTieBreak() {
        val f = MatchFormat.BEST_OF_THREE_MATCH_TIE_BREAK
        assertEquals(3, f.maxSets)
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(6, 4), set(6, 3))))
        assertEquals(ScoreValidation.Valid(Side.B), f.validate(sets(set(6, 4), set(3, 6), SetScore.matchTieBreak(8, 10))))
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(4, 6), set(7, 6, 7 to 3), SetScore.matchTieBreak(12, 10))))

        assertEquals(ScoreError.MATCH_NOT_FINISHED, f.errorOf(sets(set(6, 4))))
        assertEquals(ScoreError.MATCH_NOT_FINISHED, f.errorOf(sets(set(6, 4), set(3, 6))))
        assertEquals(
            ScoreValidation.Invalid(ScoreError.INVALID_MATCH_TIE_BREAK, setIndex = 2),
            f.validate(sets(set(6, 4), set(3, 6), set(6, 3))),
            "o 3º set é super tie-break, não set normal",
        )
        assertEquals(ScoreError.INVALID_MATCH_TIE_BREAK, f.errorOf(sets(set(6, 4), set(3, 6), SetScore.matchTieBreak(10, 9))))
        assertEquals(ScoreError.INVALID_MATCH_TIE_BREAK, f.errorOf(sets(set(6, 4), set(3, 6), SetScore.matchTieBreak(13, 10))))
        assertEquals(ScoreError.INVALID_MATCH_TIE_BREAK, f.errorOf(sets(set(6, 4), set(3, 6), set(1, 0, 7 to 10))))
        assertEquals(ScoreError.INVALID_MATCH_TIE_BREAK, f.errorOf(sets(set(6, 4), set(3, 6), set(2, 0, 10 to 7))))
        assertEquals(ScoreError.NEGATIVE_VALUE, f.errorOf(sets(set(6, 4), set(3, 6), set(-1, 0, 10 to 7))))
        assertEquals(ScoreError.NEGATIVE_VALUE, f.errorOf(sets(set(6, 4), set(3, 6), set(1, 0, 10 to -2))))
        assertEquals(
            ScoreValidation.Invalid(ScoreError.SET_AFTER_MATCH_DECIDED, setIndex = 2),
            f.validate(sets(set(6, 4), set(6, 3), SetScore.matchTieBreak(10, 5))),
        )
        assertEquals(sets(set(0, 6), set(0, 6)), f.walkoverScore(Side.B))
    }

    @Test
    fun advantageSetWithoutTieBreak() {
        val f = MatchFormat(setsToWin = 1, gamesPerSet = 6, tieBreak = false)
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(6, 4))))
        assertEquals(ScoreValidation.Valid(Side.A), f.validate(sets(set(8, 6))), "set de vantagem passa de 6")
        assertEquals(ScoreValidation.Valid(Side.B), f.validate(sets(set(10, 12))))
        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(7, 6))))
        assertEquals(ScoreError.SET_NOT_FINISHED, f.errorOf(sets(set(5, 2))))
        assertEquals(ScoreError.IMPOSSIBLE_SET_SCORE, f.errorOf(sets(set(8, 5))))
        assertEquals(ScoreError.UNEXPECTED_TIE_BREAK, f.errorOf(sets(set(7, 6, 7 to 5))))
    }

    @Test
    fun walkover() {
        val f = MatchFormat.ONE_SET_OF_SIX
        assertEquals(sets(set(6, 0)), f.walkoverScore(Side.A))
        assertEquals(sets(set(0, 6)), f.walkoverScore(Side.B))
        val result = f.walkover(sideAId = 1, sideBId = 2, winner = Side.B)
        assertEquals(MatchResult(1, 2, MatchOutcome.Walkover(Side.B), sets(set(0, 6))), result)
        assertTrue(result.outcome.isFinished)
        assertEquals(Side.B, result.outcome.winner)
        assertTrue(f.isValid(f.walkoverScore(Side.A)), "o placar do W.O. é um placar válido")
        assertTrue(MatchFormat.BEST_OF_THREE_MATCH_TIE_BREAK.isValid(MatchFormat.BEST_OF_THREE_MATCH_TIE_BREAK.walkoverScore(Side.B)))
    }

    @Test
    fun outcomes() {
        assertFalse(MatchOutcome.InProgress.isFinished)
        assertNull(MatchOutcome.InProgress.winner)
        assertTrue(MatchOutcome.Draw.isFinished)
        assertNull(MatchOutcome.Draw.winner)
        assertEquals(Side.A, MatchOutcome.Decided(Side.A).winner)
        assertEquals(Side.B, Side.A.opposite)
        assertEquals(Side.A, Side.B.opposite)
    }

    @Test
    fun formatGuards() {
        assertFailsWith<IllegalArgumentException> { MatchFormat(setsToWin = 0, gamesPerSet = 6) }
        assertFailsWith<IllegalArgumentException> { MatchFormat(setsToWin = 1, gamesPerSet = 0) }
        assertFailsWith<IllegalArgumentException> { MatchFormat(setsToWin = 1, gamesPerSet = 6, tieBreakPoints = 0) }
        assertFailsWith<IllegalArgumentException> {
            MatchFormat(setsToWin = 1, gamesPerSet = 6, decidingSetMatchTieBreak = true)
        }
    }
}
