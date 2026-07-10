package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testes da lógica pura da visão MÊS (par mobile do `MonthGrid` da weblib). */
class CalendarMonthTest {

    // 2026-07-09 (quinta). Julho/2026 começa numa quarta → grade abre no domingo 28/06.
    private val julho = LocalDate(2026, 7, 9)

    private fun ev(id: String, y: Int, mo: Int, d: Int, h: Int, min: Int): ScheduleEvent =
        ScheduleEvent(
            id = id,
            start = LocalDateTime(y, mo, d, h, min),
            end = LocalDateTime(y, mo, d, h + 1, min),
        )

    @Test
    fun `monthGridCells produz 42 celulas comecando no domingo`() {
        val cells = monthGridCells(julho)
        assertEquals(42, cells.size)
        assertEquals(LocalDate(2026, 6, 28), cells.first().date)
        assertEquals(addDays(LocalDate(2026, 6, 28), 41), cells.last().date)
    }

    @Test
    fun `inMonth marca spill do mes anterior e seguinte`() {
        val cells = monthGridCells(julho)
        // 28/06 é spill (mês anterior).
        assertTrue(!cells.first { it.date == LocalDate(2026, 6, 28) }.inMonth)
        // 09/07 é do mês.
        assertTrue(cells.first { it.date == LocalDate(2026, 7, 9) }.inMonth)
        // dias do mês somam exatamente 31 (julho).
        assertEquals(31, cells.count { it.inMonth })
    }

    @Test
    fun `groupEventsByDay agrupa por dia do inicio e ordena por horario`() {
        val eventos = listOf(
            ev("b", 2026, 7, 9, 14, 30),
            ev("a", 2026, 7, 9, 8, 0),
            ev("c", 2026, 7, 10, 9, 0),
        )
        val grouped = groupEventsByDay(eventos)
        assertEquals(listOf("a", "b"), grouped[LocalDate(2026, 7, 9)]!!.map { it.id })
        assertEquals(listOf("c"), grouped[LocalDate(2026, 7, 10)]!!.map { it.id })
        assertEquals(null, grouped[LocalDate(2026, 7, 11)])
    }
}
