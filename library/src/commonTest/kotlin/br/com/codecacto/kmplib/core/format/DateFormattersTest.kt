package br.com.codecacto.kmplib.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateFormattersTest {

    @Test
    fun formatDateBr_converts_iso_to_br() {
        assertEquals("05/03/2026", formatDateBr("2026-03-05"))
    }

    @Test
    fun formatDateBr_returns_input_when_not_iso() {
        assertEquals("05/03/2026", formatDateBr("05/03/2026"))
        assertEquals("not-a-date", formatDateBr("not-a-date"))
    }

    @Test
    fun parseDateBrToIso_converts_valid_br_date() {
        assertEquals("2026-03-05", parseDateBrToIso("05/03/2026"))
    }

    @Test
    fun parseDateBrToIso_returns_null_for_invalid_inputs() {
        assertNull(parseDateBrToIso(""))
        assertNull(parseDateBrToIso("2026-03-05"))
        assertNull(parseDateBrToIso("32/13/2026"))
    }

    @Test
    fun formatDateUS_converts_iso_to_us() {
        assertEquals("03/05/2026", formatDateUS("2026-03-05"))
    }

    @Test
    fun parseDateUSToIso_converts_valid_us_date() {
        assertEquals("2026-03-05", parseDateUSToIso("03/05/2026"))
    }

    @Test
    fun parseDateUSToIso_returns_null_for_invalid() {
        assertNull(parseDateUSToIso("13/32/2026"))
        assertNull(parseDateUSToIso("2026/03/05"))
    }

    @Test
    fun formatTime_zero_pads() {
        assertEquals("09:05", formatTime(9, 5))
        assertEquals("23:59", formatTime(23, 59))
        assertEquals("00:00", formatTime(0, 0))
    }

    @Test
    fun formatDateBrFromMillis_dashes_when_invalid() {
        assertEquals("-", formatDateBrFromMillis(0L))
        assertEquals("-", formatDateBrFromMillis(-1L))
    }

    @Test
    fun parseIsoDateToMillis_returns_null_for_invalid() {
        assertNull(parseIsoDateToMillis(""))
        assertNull(parseIsoDateToMillis("not-a-date"))
    }

    @Test
    fun parseIsoDateToMillis_then_formatIsoDateFromMillis_roundtrip() {
        val millis = parseIsoDateToMillis("2026-03-05")
        assertEquals("2026-03-05", formatIsoDateFromMillis(millis!!))
    }

    @Test
    fun weekdayNameBr_returns_capitalized_ptbr_name() {
        assertEquals("Sexta-Feira", weekdayNameBr("2026-07-17")) // sexta
        assertEquals("Sábado", weekdayNameBr("2026-07-18"))
        assertEquals("Domingo", weekdayNameBr("2026-07-19"))
        assertEquals("Segunda-Feira", weekdayNameBr("2026-07-20"))
        assertEquals("Terça-Feira", weekdayNameBr("2026-07-21"))
    }

    @Test
    fun weekdayNameBr_accepts_iso_datetime() {
        assertEquals("Sexta-Feira", weekdayNameBr("2026-07-17T08:29:00"))
    }

    @Test
    fun weekdayNameBr_null_for_invalid() {
        assertNull(weekdayNameBr("not-a-date"))
        assertNull(weekdayNameBr(""))
    }

    @Test
    fun formatDateWeekdayBr_weekday_plus_day_month() {
        assertEquals("Sexta-Feira, 17/07", formatDateWeekdayBr("2026-07-17"))
        assertEquals("Domingo, 01/03", formatDateWeekdayBr("2026-03-01"))
    }

    @Test
    fun formatDateWeekdayBr_accepts_iso_datetime() {
        assertEquals("Sexta-Feira, 17/07", formatDateWeekdayBr("2026-07-17T08:29:00"))
    }

    @Test
    fun formatDateWeekdayBr_returns_input_when_invalid() {
        assertEquals("not-a-date", formatDateWeekdayBr("not-a-date"))
    }

    @Test
    fun formatTimeFromIso_extracts_hh_mm_from_local_datetime() {
        assertEquals("08:29", formatTimeFromIso("2026-07-17T08:29:00"))
        assertEquals("08:29", formatTimeFromIso("2026-07-17T08:29"))
        assertEquals("00:05", formatTimeFromIso("2026-07-17T00:05:00"))
        assertEquals("23:59", formatTimeFromIso("2026-07-17T23:59:59"))
    }

    @Test
    fun formatTimeFromIso_handles_instant_with_offset() {
        // 08:29Z convertido para UTC deve continuar 08:29
        assertEquals("08:29", formatTimeFromIso("2026-07-17T08:29:00Z", timeZone = kotlinx.datetime.TimeZone.UTC))
    }

    @Test
    fun formatTimeFromIso_returns_input_when_invalid() {
        assertEquals("not-a-time", formatTimeFromIso("not-a-time"))
    }
}
