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
}
