package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da aritmética de tempo pura do calendário — par mobile de `time.test.ts` da weblib. */
class CalendarTimeTest {

    private fun dt(hh: Int, mm: Int) = LocalDateTime(2026, 7, 9, hh, mm)

    @Test
    fun `formatTimeOfDay formata HHmm`() {
        assertEquals("09:05", formatTimeOfDay(545))
        assertEquals("00:00", formatTimeOfDay(0))
        assertEquals("23:59", formatTimeOfDay(1439))
        assertEquals("00:30", formatTimeOfDay(1470)) // espelha no dia (mod 24h)
    }

    @Test
    fun `parseTimeOfDay valida faixas`() {
        assertEquals(545, parseTimeOfDay("09:05"))
        assertEquals(0, parseTimeOfDay(" 0:00 "))
        assertNull(parseTimeOfDay("24:00"))
        assertNull(parseTimeOfDay("09:60"))
        assertNull(parseTimeOfDay("abc"))
    }

    @Test
    fun `maskTime aplica mascara progressiva`() {
        assertEquals("0", maskTime("0"))
        assertEquals("09", maskTime("09"))
        assertEquals("09:3", maskTime("093"))
        assertEquals("09:30", maskTime("0930"))
        assertEquals("09:30", maskTime("09:30abc"))
    }

    @Test
    fun `durationMinutes calcula parede local e piso 0`() {
        assertEquals(90, durationMinutes(dt(9, 0), dt(10, 30)))
        assertEquals(0, durationMinutes(dt(10, 0), dt(9, 0))) // fim < início → 0
        // cruza meia-noite
        assertEquals(120, durationMinutes(LocalDateTime(2026, 7, 9, 23, 0), LocalDateTime(2026, 7, 10, 1, 0)))
    }

    @Test
    fun `toMinuteRange usa dia do inicio e soma duracao`() {
        assertEquals(MinuteRange(540, 630), toMinuteRange(dt(9, 0), dt(10, 30)))
        // cruza meia-noite → endMin > 1440
        val r = toMinuteRange(LocalDateTime(2026, 7, 9, 23, 30), LocalDateTime(2026, 7, 10, 0, 30))
        assertEquals(1410, r.startMin)
        assertEquals(1470, r.endMin)
    }

    @Test
    fun `rangesOverlap - fronteira aberta`() {
        assertTrue(rangesOverlap(MinuteRange(540, 600), MinuteRange(570, 630)))
        assertFalse(rangesOverlap(MinuteRange(540, 570), MinuteRange(570, 600))) // encosta, não sobrepõe
    }

    @Test
    fun `floor e ceil to step`() {
        assertEquals(480, floorToStep(510, 60))
        assertEquals(540, ceilToStep(510, 60))
        assertEquals(480, floorToStep(480, 60))
        assertEquals(480, ceilToStep(480, 60))
    }

    @Test
    fun `parseCalendarDateTime coage ISO ignorando offset`() {
        assertEquals(LocalDateTime(2026, 7, 9, 9, 30), parseCalendarDateTime("2026-07-09T09:30:00-03:00"))
        assertEquals(LocalDateTime(2026, 7, 9, 9, 30), parseCalendarDateTime("2026-07-09T09:30Z"))
        assertEquals(LocalDateTime(2026, 7, 9, 0, 0), parseCalendarDateTime("2026-07-09"))
        assertNull(parseCalendarDateTime(null))
        assertNull(parseCalendarDateTime(""))
        assertNull(parseCalendarDateTime("nao-e-data"))
    }
}
