package br.com.codecacto.kmplib.media

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioPlayerBarTest {

    @Test
    fun zeroOrNegative_returnsZeroFormatted() {
        assertEquals("0:00", formatPlayerTime(0))
        assertEquals("0:00", formatPlayerTime(-1))
        assertEquals("0:00", formatPlayerTime(-50_000))
    }

    @Test
    fun subMinute_padsSeconds() {
        assertEquals("0:05", formatPlayerTime(5_000))
        assertEquals("0:42", formatPlayerTime(42_000))
        // arredonda para baixo (truncamento de ms)
        assertEquals("0:09", formatPlayerTime(9_999))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("1:00", formatPlayerTime(60_000))
        assertEquals("3:05", formatPlayerTime(185_000))
        assertEquals("12:34", formatPlayerTime(754_000))
    }

    @Test
    fun overOneHour_includesHours() {
        assertEquals("1:00:00", formatPlayerTime(3_600_000))
        assertEquals("1:02:03", formatPlayerTime(3_723_000))
        assertEquals("2:00:30", formatPlayerTime(7_230_000))
    }
}
