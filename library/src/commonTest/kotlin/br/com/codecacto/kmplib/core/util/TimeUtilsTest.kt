package br.com.codecacto.kmplib.core.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeUtilsTest {

    // ========================
    // formatTimestamp Tests
    // ========================

    @Test
    fun `formatTimestamp with default pattern`() {
        // 2024-01-15 14:30:45
        val timestamp = 1705330245000L
        val result = TimeUtils.formatTimestamp(timestamp)
        // Result depends on system timezone, so we just check format
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")))
    }

    @Test
    fun `formatTimestamp with dd-MM-yyyy pattern`() {
        val timestamp = 1705330245000L // 2024-01-15 14:30:45
        val result = TimeUtils.formatTimestamp(timestamp, "dd-MM-yyyy")
        assertTrue(result.matches(Regex("""\d{2}-\d{2}-\d{4}""")))
    }

    @Test
    fun `formatTimestamp with full pattern including seconds`() {
        val timestamp = 1705330245000L
        val result = TimeUtils.formatTimestamp(timestamp, "dd/MM/yyyy HH:mm:ss")
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2}""")))
    }

    @Test
    fun `formatTimestamp with yyyy-MM-dd pattern`() {
        val timestamp = 1705330245000L
        val result = TimeUtils.formatTimestamp(timestamp, "yyyy-MM-dd")
        assertTrue(result.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    // ========================
    // formatDateBrazilian Tests
    // ========================

    @Test
    fun `formatDateBrazilian returns correct format`() {
        val timestamp = 1705330245000L
        val result = TimeUtils.formatDateBrazilian(timestamp)
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4}""")))
    }

    // ========================
    // formatDateTimeBrazilian Tests
    // ========================

    @Test
    fun `formatDateTimeBrazilian returns correct format`() {
        val timestamp = 1705330245000L
        val result = TimeUtils.formatDateTimeBrazilian(timestamp)
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")))
    }

    // ========================
    // getRelativeTime Tests
    // ========================

    @Test
    fun `getRelativeTime for 30 seconds ago`() {
        val now = currentTimeMillis()
        val thirtySecondsAgo = now - (30 * 1000)
        val result = TimeUtils.getRelativeTime(thirtySecondsAgo, now)
        assertEquals("há 30 segundos", result)
    }

    @Test
    fun `getRelativeTime for 1 second ago uses singular`() {
        val now = currentTimeMillis()
        val oneSecondAgo = now - 1000
        val result = TimeUtils.getRelativeTime(oneSecondAgo, now)
        assertEquals("há 1 segundo", result)
    }

    @Test
    fun `getRelativeTime for 5 minutes ago`() {
        val now = currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(fiveMinutesAgo, now)
        assertEquals("há 5 minutos", result)
    }

    @Test
    fun `getRelativeTime for 1 minute ago uses singular`() {
        val now = currentTimeMillis()
        val oneMinuteAgo = now - (60 * 1000)
        val result = TimeUtils.getRelativeTime(oneMinuteAgo, now)
        assertEquals("há 1 minuto", result)
    }

    @Test
    fun `getRelativeTime for 3 hours ago`() {
        val now = currentTimeMillis()
        val threeHoursAgo = now - (3 * 60 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(threeHoursAgo, now)
        assertEquals("há 3 horas", result)
    }

    @Test
    fun `getRelativeTime for 1 hour ago uses singular`() {
        val now = currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(oneHourAgo, now)
        assertEquals("há 1 hora", result)
    }

    @Test
    fun `getRelativeTime for 2 days ago`() {
        val now = currentTimeMillis()
        val twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(twoDaysAgo, now)
        assertEquals("há 2 dias", result)
    }

    @Test
    fun `getRelativeTime for 1 day ago uses singular`() {
        val now = currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(oneDayAgo, now)
        assertEquals("há 1 dia", result)
    }

    @Test
    fun `getRelativeTime for future time`() {
        val now = currentTimeMillis()
        val in30Minutes = now + (30 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(in30Minutes, now)
        assertEquals("em 30 minutos", result)
    }

    @Test
    fun `getRelativeTime for future 1 hour uses singular`() {
        val now = currentTimeMillis()
        val in1Hour = now + (60 * 60 * 1000)
        val result = TimeUtils.getRelativeTime(in1Hour, now)
        assertEquals("em 1 hora", result)
    }

    @Test
    fun `getRelativeTime for now with 0 seconds difference`() {
        val now = currentTimeMillis()
        val result = TimeUtils.getRelativeTime(now, now)
        assertEquals("há 0 segundos", result)
    }

    // ========================
    // isToday Tests
    // ========================

    @Test
    fun `isToday returns true for current timestamp`() {
        val now = currentTimeMillis()
        assertTrue(TimeUtils.isToday(now, now))
    }

    @Test
    fun `isToday returns true for earlier today`() {
        val now = currentTimeMillis()
        val morningToday = TimeUtils.setTime(now, 8, 0, 0)
        assertTrue(TimeUtils.isToday(morningToday, now))
    }

    @Test
    fun `isToday returns false for yesterday`() {
        val now = currentTimeMillis()
        val yesterday = TimeUtils.addDays(now, -1)
        assertFalse(TimeUtils.isToday(yesterday, now))
    }

    @Test
    fun `isToday returns false for tomorrow`() {
        val now = currentTimeMillis()
        val tomorrow = TimeUtils.addDays(now, 1)
        assertFalse(TimeUtils.isToday(tomorrow, now))
    }

    // ========================
    // isYesterday Tests
    // ========================

    @Test
    fun `isYesterday returns true for one day ago`() {
        val now = currentTimeMillis()
        val yesterday = TimeUtils.addDays(now, -1)
        assertTrue(TimeUtils.isYesterday(yesterday, now))
    }

    @Test
    fun `isYesterday returns false for today`() {
        val now = currentTimeMillis()
        assertFalse(TimeUtils.isYesterday(now, now))
    }

    @Test
    fun `isYesterday returns false for two days ago`() {
        val now = currentTimeMillis()
        val twoDaysAgo = TimeUtils.addDays(now, -2)
        assertFalse(TimeUtils.isYesterday(twoDaysAgo, now))
    }

    // ========================
    // startOfDay Tests
    // ========================

    @Test
    fun `startOfDay returns midnight`() {
        val timestamp = 1705330245000L // 2024-01-15 14:30:45
        val startOfDay = TimeUtils.startOfDay(timestamp)

        val dateTime = Instant.fromEpochMilliseconds(startOfDay)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(0, dateTime.hour)
        assertEquals(0, dateTime.minute)
        assertEquals(0, dateTime.second)
    }

    @Test
    fun `startOfDay preserves the date`() {
        val timestamp = 1705330245000L
        val startOfDay = TimeUtils.startOfDay(timestamp)

        val originalDate = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = Instant.fromEpochMilliseconds(startOfDay)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

        assertEquals(originalDate, startDate)
    }

    // ========================
    // endOfDay Tests
    // ========================

    @Test
    fun `endOfDay returns 23-59-59`() {
        val timestamp = 1705330245000L
        val endOfDay = TimeUtils.endOfDay(timestamp)

        val dateTime = Instant.fromEpochMilliseconds(endOfDay)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(23, dateTime.hour)
        assertEquals(59, dateTime.minute)
        assertEquals(59, dateTime.second)
    }

    @Test
    fun `endOfDay preserves the date`() {
        val timestamp = 1705330245000L
        val endOfDay = TimeUtils.endOfDay(timestamp)

        val originalDate = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val endDate = Instant.fromEpochMilliseconds(endOfDay)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

        assertEquals(originalDate, endDate)
    }

    @Test
    fun `endOfDay is later than startOfDay`() {
        val timestamp = currentTimeMillis()
        val start = TimeUtils.startOfDay(timestamp)
        val end = TimeUtils.endOfDay(timestamp)

        assertTrue(end > start)
    }

    // ========================
    // addDays Tests
    // ========================

    @Test
    fun `addDays adds positive days correctly`() {
        val timestamp = 1705330245000L
        val threeDaysLater = TimeUtils.addDays(timestamp, 3)

        val originalDate = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val newDate = Instant.fromEpochMilliseconds(threeDaysLater)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        // Verify day difference (approximately)
        val diff = threeDaysLater - timestamp
        val daysDiff = diff / (24 * 60 * 60 * 1000)
        assertTrue(daysDiff in 2..4) // Account for timezone/DST variations
    }

    @Test
    fun `addDays subtracts negative days correctly`() {
        val timestamp = 1705330245000L
        val threeDaysEarlier = TimeUtils.addDays(timestamp, -3)

        assertTrue(threeDaysEarlier < timestamp)
    }

    @Test
    fun `addDays with 0 returns same timestamp approximately`() {
        val timestamp = currentTimeMillis()
        val result = TimeUtils.addDays(timestamp, 0)

        // Should be same or very close (within a few milliseconds)
        assertTrue(kotlin.math.abs(result - timestamp) < 1000)
    }

    @Test
    fun `addDays preserves time of day`() {
        val timestamp = 1705330245000L // 14:30:45
        val oneDayLater = TimeUtils.addDays(timestamp, 1)

        val originalTime = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val newTime = Instant.fromEpochMilliseconds(oneDayLater)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(originalTime.hour, newTime.hour)
        assertEquals(originalTime.minute, newTime.minute)
        assertEquals(originalTime.second, newTime.second)
    }

    // ========================
    // setTime Tests
    // ========================

    @Test
    fun `setTime changes time correctly`() {
        val timestamp = 1705330245000L // 14:30:45
        val newTimestamp = TimeUtils.setTime(timestamp, 10, 15, 30)

        val dateTime = Instant.fromEpochMilliseconds(newTimestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(10, dateTime.hour)
        assertEquals(15, dateTime.minute)
        assertEquals(30, dateTime.second)
    }

    @Test
    fun `setTime preserves date`() {
        val timestamp = 1705330245000L
        val newTimestamp = TimeUtils.setTime(timestamp, 20, 0, 0)

        val originalDate = Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val newDate = Instant.fromEpochMilliseconds(newTimestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

        assertEquals(originalDate, newDate)
    }

    @Test
    fun `setTime with default second parameter`() {
        val timestamp = currentTimeMillis()
        val newTimestamp = TimeUtils.setTime(timestamp, 12, 30)

        val dateTime = Instant.fromEpochMilliseconds(newTimestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(12, dateTime.hour)
        assertEquals(30, dateTime.minute)
        assertEquals(0, dateTime.second)
    }

    // ========================
    // parseDate Tests
    // ========================

    @Test
    fun `parseDate with yyyy-MM-dd HH-mm pattern`() {
        val result = TimeUtils.parseDate("2024-01-15 14:30", "yyyy-MM-dd HH:mm")
        val dateTime = Instant.fromEpochMilliseconds(result)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(2024, dateTime.year)
        assertEquals(1, dateTime.monthNumber)
        assertEquals(15, dateTime.dayOfMonth)
        assertEquals(14, dateTime.hour)
        assertEquals(30, dateTime.minute)
    }

    @Test
    fun `parseDate with dd-MM-yyyy HH-mm pattern`() {
        val result = TimeUtils.parseDate("15/01/2024 14:30", "dd/MM/yyyy HH:mm")
        val dateTime = Instant.fromEpochMilliseconds(result)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(2024, dateTime.year)
        assertEquals(1, dateTime.monthNumber)
        assertEquals(15, dateTime.dayOfMonth)
        assertEquals(14, dateTime.hour)
        assertEquals(30, dateTime.minute)
    }

    @Test
    fun `parseDate with dd-MM-yyyy pattern`() {
        val result = TimeUtils.parseDate("15/01/2024", "dd/MM/yyyy")
        val dateTime = Instant.fromEpochMilliseconds(result)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        assertEquals(2024, dateTime.year)
        assertEquals(1, dateTime.monthNumber)
        assertEquals(15, dateTime.dayOfMonth)
        assertEquals(0, dateTime.hour)
        assertEquals(0, dateTime.minute)
    }

    @Test
    fun `parseDate throws exception for invalid format`() {
        assertFailsWith<IllegalArgumentException> {
            TimeUtils.parseDate("invalid-date", "dd/MM/yyyy")
        }
    }

    @Test
    fun `parseDate throws exception for unsupported pattern`() {
        assertFailsWith<IllegalArgumentException> {
            TimeUtils.parseDate("2024-01-15", "yyyy/MM/dd")
        }
    }

    @Test
    fun `parseDate throws exception for mismatched pattern`() {
        assertFailsWith<IllegalArgumentException> {
            TimeUtils.parseDate("15/01/2024", "yyyy-MM-dd HH:mm")
        }
    }

    // ========================
    // getMonthNamePtBr Tests
    // ========================

    @Test
    fun `getMonthNamePtBr returns correct names`() {
        assertEquals("janeiro", getMonthNamePtBr(1))
        assertEquals("fevereiro", getMonthNamePtBr(2))
        assertEquals("março", getMonthNamePtBr(3))
        assertEquals("abril", getMonthNamePtBr(4))
        assertEquals("maio", getMonthNamePtBr(5))
        assertEquals("junho", getMonthNamePtBr(6))
        assertEquals("julho", getMonthNamePtBr(7))
        assertEquals("agosto", getMonthNamePtBr(8))
        assertEquals("setembro", getMonthNamePtBr(9))
        assertEquals("outubro", getMonthNamePtBr(10))
        assertEquals("novembro", getMonthNamePtBr(11))
        assertEquals("dezembro", getMonthNamePtBr(12))
    }

    @Test
    fun `getMonthNamePtBr returns empty for invalid month`() {
        assertEquals("", getMonthNamePtBr(0))
        assertEquals("", getMonthNamePtBr(13))
        assertEquals("", getMonthNamePtBr(-1))
    }

    // ========================
    // Instant Extension Tests
    // ========================

    @Test
    fun `formatDateShort returns correct format`() {
        val instant = Instant.fromEpochMilliseconds(1705330245000L)
        val result = instant.formatDateShort()
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4}""")))
    }

    @Test
    fun `formatDateLong returns correct format`() {
        val instant = Instant.fromEpochMilliseconds(1705330245000L)
        val result = instant.formatDateLong()
        assertTrue(result.contains(" de "))
        assertTrue(result.matches(Regex("""\d+ de \w+ de \d{4}""")))
    }

    @Test
    fun `formatDateTime returns correct format`() {
        val instant = Instant.fromEpochMilliseconds(1705330245000L)
        val result = instant.formatDateTime()
        assertTrue(result.matches(Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")))
    }

    // ========================
    // Integration Tests
    // ========================

    @Test
    fun `parse and format round trip`() {
        val original = "15/01/2024 14:30"
        val timestamp = TimeUtils.parseDate(original, "dd/MM/yyyy HH:mm")
        val formatted = TimeUtils.formatTimestamp(timestamp, "dd/MM/yyyy HH:mm")
        assertEquals(original, formatted)
    }

    @Test
    fun `addDays and isToday work together`() {
        val now = currentTimeMillis()
        val yesterday = TimeUtils.addDays(now, -1)
        val tomorrow = TimeUtils.addDays(now, 1)

        assertTrue(TimeUtils.isToday(now, now))
        assertFalse(TimeUtils.isToday(yesterday, now))
        assertFalse(TimeUtils.isToday(tomorrow, now))
    }

    @Test
    fun `startOfDay and endOfDay span full day`() {
        val now = currentTimeMillis()
        val start = TimeUtils.startOfDay(now)
        val end = TimeUtils.endOfDay(now)

        assertTrue(TimeUtils.isToday(start, now))
        assertTrue(TimeUtils.isToday(end, now))
        assertTrue(end > start)

        // Should be approximately 24 hours apart
        val diff = end - start
        assertTrue(diff >= 86399000) // 23:59:59
        assertTrue(diff < 86401000) // Just over 24 hours
    }
}
