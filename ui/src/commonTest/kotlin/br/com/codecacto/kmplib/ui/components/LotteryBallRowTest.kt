package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LotteryBallRowTest {

    // ========================
    // ballRevealDelayMillis (stagger)
    // ========================

    @Test
    fun firstBall_hasNoDelay() {
        assertEquals(0, ballRevealDelayMillis(index = 0, count = 6))
    }

    @Test
    fun singleBall_hasNoDelay() {
        assertEquals(0, ballRevealDelayMillis(index = 0, count = 1))
    }

    @Test
    fun delayIncreasesMonotonicallyWithIndex() {
        val delays = (0 until 6).map { ballRevealDelayMillis(index = it, count = 6) }
        for (i in 1 until delays.size) {
            assertTrue(delays[i] >= delays[i - 1], "delays devem ser não-decrescentes: $delays")
        }
    }

    @Test
    fun lastBallStartsInTimeToFinishWithinTotal() {
        // A última esfera deve começar até (total - perBall) para o reveal caber no total alvo.
        val total = DEFAULT_REVEAL_TOTAL_MILLIS
        val lastDelay = ballRevealDelayMillis(index = 5, count = 6, totalMillis = total)
        assertTrue(
            lastDelay + PER_BALL_ANIM_MILLIS <= total,
            "início da última ($lastDelay) + anim ($PER_BALL_ANIM_MILLIS) deve caber em $total",
        )
    }

    @Test
    fun delayNeverNegativeWhenTotalSmallerThanPerBall() {
        // Total menor que a animação de uma esfera → janela 0, todos os atrasos = 0 (sem negativo).
        repeat(6) { i ->
            val d = ballRevealDelayMillis(index = i, count = 6, totalMillis = 100, perBallMillis = 260)
            assertEquals(0, d)
        }
    }

    @Test
    fun negativeIndexClampsToZero() {
        assertEquals(0, ballRevealDelayMillis(index = -1, count = 6))
    }

    // ========================
    // ballRowCount (grid)
    // ========================

    @Test
    fun rowCount_sixInThreeColumns() {
        assertEquals(2, ballRowCount(count = 6, columns = 3))
    }

    @Test
    fun rowCount_roundsUpPartialRow() {
        assertEquals(3, ballRowCount(count = 5, columns = 2)) // 2 + 2 + 1
    }

    @Test
    fun rowCount_singleRowWhenColumnsCoverAll() {
        assertEquals(1, ballRowCount(count = 6, columns = 6))
    }

    @Test
    fun rowCount_zeroForEmptyOrInvalid() {
        assertEquals(0, ballRowCount(count = 0, columns = 3))
        assertEquals(0, ballRowCount(count = 6, columns = 0))
    }
}
