package br.com.codecacto.kmplib.ui.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Minuto-do-dia ↔ "HH:mm" incluindo o **fim do dia** ("24:00"), com a regra "24:00 só como fim".
 * Distinto de [CalendarTimeTest] (que cobre `parseTimeOfDay`/`formatTimeOfDay`, 0..1439).
 */
class DayBoundaryTimeTest {

    @Test
    fun `END_OF_DAY_MINUTE eh 1440`() {
        assertEquals(1440, END_OF_DAY_MINUTE)
    }

    @Test
    fun `formatDayMinute renderiza 1440 como 2400 e nao espelha`() {
        assertEquals("24:00", formatDayMinute(1440))
        assertEquals("24:00", formatDayMinute(1500)) // acima do fim de dia continua "24:00"
        assertEquals("00:00", formatDayMinute(0))
        assertEquals("09:30", formatDayMinute(570))
        assertEquals("23:59", formatDayMinute(1439))
        assertEquals("00:00", formatDayMinute(-10)) // negativo → "00:00"
    }

    @Test
    fun `parseDayMinute aceita 2400 apenas como fim`() {
        assertEquals(1440, parseDayMinute("24:00", DayTimeRole.End))
        assertNull(parseDayMinute("24:00", DayTimeRole.Start)) // 24:00 como início é inválido
    }

    @Test
    fun `parseDayMinute converte horarios normais nos dois papeis`() {
        assertEquals(540, parseDayMinute("09:00", DayTimeRole.Start))
        assertEquals(540, parseDayMinute("09:00", DayTimeRole.End))
        assertEquals(0, parseDayMinute(" 0:00 ", DayTimeRole.Start))
        assertEquals(1439, parseDayMinute("23:59", DayTimeRole.End))
    }

    @Test
    fun `parseDayMinute rejeita invalidos`() {
        assertNull(parseDayMinute("25:00", DayTimeRole.End))
        assertNull(parseDayMinute("09:60", DayTimeRole.End))
        assertNull(parseDayMinute("abc", DayTimeRole.End))
        assertNull(parseDayMinute("", DayTimeRole.Start))
    }

    @Test
    fun `formatDayMinute e parseDayMinute sao inversos incluindo 2400`() {
        for (m in listOf(0, 30, 540, 1080, 1410, 1439, 1440)) {
            val role = if (m == 1440) DayTimeRole.End else DayTimeRole.Start
            assertEquals(m, parseDayMinute(formatDayMinute(m), role), "roundtrip de $m")
        }
    }

    @Test
    fun `dayTimeOptions do fim inclui 2400 e do inicio nao`() {
        val end = dayTimeOptions(DayTimeRole.End, stepMin = 30)
        val start = dayTimeOptions(DayTimeRole.Start, stepMin = 30)

        assertEquals("00:00", end.first())
        assertEquals("24:00", end.last())
        assertTrue("24:00" in end)

        assertEquals("00:00", start.first())
        assertEquals("23:30", start.last())
        assertFalse("24:00" in start)
    }

    @Test
    fun `dayTimeOptions respeita o passo e clampa 5 a 60`() {
        assertEquals(49, dayTimeOptions(DayTimeRole.End, stepMin = 30).size) // 0..1440 passo 30 = 49 opções
        // passo inválido (0) é clampado para 5
        val step5 = dayTimeOptions(DayTimeRole.End, stepMin = 0)
        assertEquals("00:05", step5[1])
    }
}
