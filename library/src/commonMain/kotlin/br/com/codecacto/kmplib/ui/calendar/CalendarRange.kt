package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/* ─────────────────────────── navegação de datas (PURA) ───────────────────────────
 * Espelha `calendar/range.ts` da weblib. Helpers de calendário para o consumidor montar o range de
 * fetch (`from`/`to`) por visão e navegar o cursor. Semana começa no DOMINGO (padrão da casa, igual
 * à weblib e ao Influencer). Puro `LocalDate`, sem fuso, sem `Instant`.
 */

/** `date + n` dias. */
fun addDays(date: LocalDate, days: Int): LocalDate =
    if (days >= 0) date.plus(DatePeriod(days = days)) else date.minus(DatePeriod(days = -days))

/** Domingo da semana de `date`. */
fun startOfWeek(date: LocalDate): LocalDate {
    // isoDayNumber: Segunda=1 … Domingo=7. Dias desde o domingo anterior = isoDayNumber % 7.
    val daysFromSunday = date.dayOfWeek.isoDayNumber % 7
    return addDays(date, -daysFromSunday)
}

/** Primeiro dia do mês de `date`. */
fun startOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

/** Último dia do mês de `date`. */
fun endOfMonth(date: LocalDate): LocalDate =
    startOfMonth(date).plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

/** Mesmo dia (ano/mês/dia). */
fun isSameDay(a: LocalDate, b: LocalDate): Boolean = a == b

/** Range `[from, to]` (inclusive) da visão — dia = 1 dia; semana = dom→sáb; mês = grade de 6 semanas. */
fun calendarRange(mode: CalendarViewMode, cursor: LocalDate): Pair<LocalDate, LocalDate> =
    when (mode) {
        CalendarViewMode.Day -> cursor to cursor
        CalendarViewMode.Week -> {
            val start = startOfWeek(cursor)
            start to addDays(start, 6)
        }
        CalendarViewMode.Month -> {
            // Cobre os 42 dias (6 semanas) da grade do mês.
            val gridStart = startOfWeek(startOfMonth(cursor))
            gridStart to addDays(gridStart, 41)
        }
    }

/** Avança/recua o cursor conforme a visão (dia±1, semana±7, mês±1). */
fun navigateCursor(mode: CalendarViewMode, cursor: LocalDate, delta: Int): LocalDate =
    when (mode) {
        CalendarViewMode.Day -> addDays(cursor, delta)
        CalendarViewMode.Week -> addDays(cursor, delta * 7)
        CalendarViewMode.Month ->
            if (delta >= 0) {
                startOfMonth(cursor).plus(DatePeriod(months = delta))
            } else {
                startOfMonth(cursor).minus(DatePeriod(months = -delta))
            }
    }
