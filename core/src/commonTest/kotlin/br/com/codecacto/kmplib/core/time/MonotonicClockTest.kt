package br.com.codecacto.kmplib.core.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonotonicClockTest {

    @Test
    fun relogioDaPlataformaNaoVoltaEMilisDerivaDosNanos() {
        val a = MonotonicClock.elapsedNanos()
        val b = MonotonicClock.elapsedNanos()
        assertTrue(b >= a, "monotônico: $b < $a")
        assertTrue(a >= 0L)
        val ms = MonotonicClock.elapsedMillis()
        assertTrue(ms >= a / 1_000_000L)
    }

    @Test
    fun fonteInjetavelEMillisSince() {
        var agora = 5_000_000_000L
        val fonte = MonotonicTimeSource { agora }
        assertEquals(5_000L, fonte.elapsedMillis())
        assertEquals(0L, fonte.millisSince(5_000L))
        agora += 1_500_000_000L
        assertEquals(1_500L, fonte.millisSince(5_000L))
        assertNull(fonte.millisSince(9_999_999L), "fonte menor que a âncora = aparelho reiniciou")
    }
}
