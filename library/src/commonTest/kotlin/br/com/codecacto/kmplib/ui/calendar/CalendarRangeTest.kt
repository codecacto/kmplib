package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Testes da navegação de datas pura — par mobile de `range.test.ts` da weblib (semana começa domingo). */
class CalendarRangeTest {

    // 2026-07-09 é uma quinta-feira.
    private val quinta = LocalDate(2026, 7, 9)

    @Test
    fun `addDays nao muta e soma dias`() {
        assertEquals(LocalDate(2026, 7, 12), addDays(quinta, 3))
        assertEquals(LocalDate(2026, 7, 6), addDays(quinta, -3))
        assertEquals(LocalDate(2026, 8, 1), addDays(LocalDate(2026, 7, 31), 1))
    }

    @Test
    fun `startOfWeek volta ao domingo`() {
        // domingo da semana de quinta 09/07 = 05/07
        assertEquals(LocalDate(2026, 7, 5), startOfWeek(quinta))
        // domingo continua no próprio domingo
        assertEquals(LocalDate(2026, 7, 5), startOfWeek(LocalDate(2026, 7, 5)))
    }

    @Test
    fun `startOfMonth e endOfMonth`() {
        assertEquals(LocalDate(2026, 7, 1), startOfMonth(quinta))
        assertEquals(LocalDate(2026, 7, 31), endOfMonth(quinta))
        assertEquals(LocalDate(2026, 2, 28), endOfMonth(LocalDate(2026, 2, 15)))
    }

    @Test
    fun `calendarRange por visao`() {
        assertEquals(quinta to quinta, calendarRange(CalendarViewMode.Day, quinta))
        assertEquals(LocalDate(2026, 7, 5) to LocalDate(2026, 7, 11), calendarRange(CalendarViewMode.Week, quinta))
        // Mês: grade de 42 dias a partir do domingo anterior a 01/07 (01/07 é quarta → domingo 28/06)
        val (from, to) = calendarRange(CalendarViewMode.Month, quinta)
        assertEquals(LocalDate(2026, 6, 28), from)
        assertEquals(addDays(LocalDate(2026, 6, 28), 41), to)
    }

    @Test
    fun `navigateCursor dia semana mes`() {
        assertEquals(LocalDate(2026, 7, 10), navigateCursor(CalendarViewMode.Day, quinta, 1))
        assertEquals(LocalDate(2026, 7, 2), navigateCursor(CalendarViewMode.Week, quinta, -1))
        assertEquals(LocalDate(2026, 8, 1), navigateCursor(CalendarViewMode.Month, quinta, 1))
        assertEquals(LocalDate(2026, 6, 1), navigateCursor(CalendarViewMode.Month, quinta, -1))
    }
}
