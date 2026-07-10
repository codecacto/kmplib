package br.com.codecacto.kmplib.ui.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Validação de faixas por dia + copiar dia — par mobile de `schedule.test.ts` da weblib. */
class WeeklyScheduleTest {

    @Test
    fun `parseRange aceita fim 2400 e rejeita inicio 2400`() {
        assertEquals(MinuteRange(1080, 1440), parseRange(TimeRange("18:00", "24:00")))
        assertEquals(null, parseRange(TimeRange("24:00", "24:00"))) // início 24:00 inválido
        assertEquals(null, parseRange(TimeRange("09:00", "")))
    }

    @Test
    fun `faixa valida sem problema incluindo salao que fecha meia-noite`() {
        assertTrue(validateDayRanges(listOf(TimeRange("18:00", "24:00"))).isEmpty())
        assertTrue(validateDayRanges(listOf(TimeRange("09:00", "18:00"))).isEmpty())
    }

    @Test
    fun `faixa vazia acusa Empty`() {
        val issues = validateDayRanges(listOf(TimeRange("", "18:00")))
        assertEquals(1, issues.size)
        assertEquals(RangeIssueKind.Empty, issues.first().kind)
    }

    @Test
    fun `fim menor ou igual ao inicio acusa Inverted`() {
        assertEquals(RangeIssueKind.Inverted, validateDayRanges(listOf(TimeRange("18:00", "09:00"))).first().kind)
        assertEquals(RangeIssueKind.Inverted, validateDayRanges(listOf(TimeRange("09:00", "09:00"))).first().kind)
    }

    @Test
    fun `almoco em duas faixas com fronteira aberta NAO sobrepoe`() {
        // 09:00-12:00 e 12:00-19:00 encostam mas não colidem
        val ok = validateDayRanges(listOf(TimeRange("09:00", "12:00"), TimeRange("12:00", "19:00")))
        assertTrue(ok.isEmpty())
        // 09:00-12:00 e 13:00-19:00 (almoço real): sem problema
        val lunch = validateDayRanges(listOf(TimeRange("09:00", "12:00"), TimeRange("13:00", "19:00")))
        assertTrue(lunch.isEmpty())
    }

    @Test
    fun `faixas que se cruzam acusam Overlap uma vez com indices ordenados`() {
        val issues = validateDayRanges(listOf(TimeRange("09:00", "13:00"), TimeRange("12:00", "19:00")))
        assertEquals(1, issues.count { it.kind == RangeIssueKind.Overlap })
        val overlap = issues.first { it.kind == RangeIssueKind.Overlap }
        assertEquals(0, overlap.index)
        assertEquals(1, overlap.with)
    }

    @Test
    fun `scheduleHasIssues detecta qualquer dia problematico`() {
        val ok = listOf(WeekdaySchedule(1, listOf(TimeRange("09:00", "18:00"))))
        assertFalse(scheduleHasIssues(ok))
        val bad = listOf(WeekdaySchedule(1, listOf(TimeRange("18:00", "09:00"))))
        assertTrue(scheduleHasIssues(bad))
    }

    @Test
    fun `normalizeSchedule ordena por weekday e descarta dias fechados`() {
        val input = listOf(
            WeekdaySchedule(3, listOf(TimeRange("09:00", "18:00"))),
            WeekdaySchedule(0, emptyList()), // fechado → descartado
            WeekdaySchedule(1, listOf(TimeRange("08:00", "12:00"))),
        )
        val out = normalizeSchedule(input)
        assertEquals(listOf(1, 3), out.map { it.weekday })
    }

    @Test
    fun `applyRangesToWeekdays copia para dias uteis sem mutar origem`() {
        val source = listOf(TimeRange("09:00", "18:00"))
        val input = listOf(WeekdaySchedule(1, source))
        val out = applyRangesToWeekdays(input, sourceWeekday = 1, ranges = source, targets = BUSINESS_WEEKDAYS)
        // seg..sex preenchidos
        assertEquals(listOf(1, 2, 3, 4, 5), out.map { it.weekday })
        assertTrue(out.all { it.ranges == source })
        // entrada intacta
        assertEquals(1, input.size)
    }

    @Test
    fun `applyRangesToWeekdays com ranges vazio fecha os alvos`() {
        val input = listOf(
            WeekdaySchedule(1, listOf(TimeRange("09:00", "18:00"))),
            WeekdaySchedule(2, listOf(TimeRange("09:00", "18:00"))),
        )
        val out = applyRangesToWeekdays(input, sourceWeekday = 1, ranges = emptyList(), targets = listOf(2))
        assertEquals(listOf(1), out.map { it.weekday }) // dia 2 fechado
    }
}
