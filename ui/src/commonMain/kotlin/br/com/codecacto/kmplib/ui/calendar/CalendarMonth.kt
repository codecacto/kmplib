package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDate

/* ─────────────────────────── grade de mês (lógica PURA) ───────────────────────────
 * Núcleo puro do `AppMonthGrid` — par mobile de `MonthGrid.tsx` da weblib. Deriva as 42 células (6
 * semanas, domingo→sábado) da grade do mês e agrupa os eventos por dia do início (parede local).
 * Sem Compose, sem fuso, sem `Instant` — reusa `calendarRange`/`addDays`/`minutesOfDay` já existentes.
 */

/** Uma célula da grade mensal. `inMonth=false` = dia de "spill" do mês anterior/seguinte. */
data class MonthCell(val date: LocalDate, val inMonth: Boolean)

/**
 * As 42 células (6 semanas × 7 dias, começando no DOMINGO) da grade do mês que contém [cursor].
 * Espelha o `buildCalendar` da weblib; usa `calendarRange(Month, cursor)` para o início da grade.
 */
fun monthGridCells(cursor: LocalDate): List<MonthCell> {
    val (gridStart, _) = calendarRange(CalendarViewMode.Month, cursor)
    return (0 until 42).map { i ->
        val d = addDays(gridStart, i)
        MonthCell(date = d, inMonth = d.year == cursor.year && d.monthNumber == cursor.monthNumber)
    }
}

/**
 * Agrupa eventos pelo DIA do início (`start.date`, parede local); cada lista fica ordenada por
 * horário de início. Espelha o `groupByDay` da weblib. Determinístico.
 */
fun groupEventsByDay(events: List<ScheduleEvent>): Map<LocalDate, List<ScheduleEvent>> {
    val map = LinkedHashMap<LocalDate, MutableList<ScheduleEvent>>()
    for (e in events) map.getOrPut(e.start.date) { ArrayList() }.add(e)
    for (list in map.values) list.sortBy { minutesOfDay(it.start) }
    return map
}
