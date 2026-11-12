@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPlanningTest {

    private val tz = TimeZone.of("America/Cuiaba")

    @Test
    fun `janela rolante comeca hoje se o horario nao passou`() {
        val now = Instant.parse("2026-11-11T09:00:00Z") // 05:00 em Cuiabá
        val plan = NotificationPlanning.rollingDaily(now, tz, days = 3, hour = 7, minute = 0)
        assertEquals(listOf("2026-11-11", "2026-11-12", "2026-11-13"), plan.map { it.date.toString() })
        assertEquals(Instant.parse("2026-11-11T11:00:00Z"), plan.first().at)
    }

    @Test
    fun `janela rolante pula hoje se o horario passou e respeita o teto`() {
        val now = Instant.parse("2026-11-11T12:00:00Z") // 08:00 em Cuiabá
        val plan = NotificationPlanning.rollingDaily(now, tz, days = 2, hour = 7, minute = 0)
        assertEquals(LocalDate(2026, 11, 12), plan.first().date)
        assertEquals(NotificationPlanning.MAX_ROLLING_DAYS, NotificationPlanning.rollingDaily(now, tz, 999, 7, 0).size)
        assertEquals(1, NotificationPlanning.rollingDaily(now, tz, 0, 7, 0).size)
    }

    @Test
    fun `mensal no dia 31 cai no ultimo dia dos meses curtos`() {
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val plan = NotificationPlanning.monthly(now, tz, dayOfMonth = 31, hour = 9, minute = 0, months = 4)
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), plan.map { it.date.toString() })
        val leap = NotificationPlanning.monthly(Instant.parse("2028-02-01T12:00:00Z"), tz, 30, 9, 0, 1)
        assertEquals("2028-02-29", leap.single().date.toString())
    }

    @Test
    fun `mensal pula o mes corrente se ja passou e vira o ano`() {
        val now = Instant.parse("2026-12-20T12:00:00Z")
        val plan = NotificationPlanning.monthly(now, tz, dayOfMonth = 10, hour = 9, minute = 0, months = 2)
        assertEquals(listOf("2027-01-10", "2027-02-10"), plan.map { it.date.toString() })
        assertEquals(28, NotificationPlanning.lastDayOfMonth(2027, 2))
        assertEquals(29, NotificationPlanning.lastDayOfMonth(2028, 2))
    }

    private class Recorder : NotificationScheduler {
        val scheduled = mutableMapOf<Int, Pair<String, Instant>>()
        val cancelled = mutableListOf<Int>()
        override fun hasPermission() = true
        override fun requestPermission(onResult: (Boolean) -> Unit) = onResult(true)
        override fun scheduleNotification(id: Int, title: String, body: String, scheduledTime: Instant, data: Map<String, String>, channelId: String?, isCritical: Boolean, actions: List<NotificationAction>) {
            scheduled[id] = body to scheduledTime
        }
        override fun scheduleDailyNotification(id: Int, title: String, body: String, hour: Int, minute: Int, data: Map<String, String>, channelId: String?, isCritical: Boolean, actions: List<NotificationAction>) = Unit
        override fun cancelNotification(id: Int) { cancelled += id; scheduled.remove(id) }
        override fun cancelAllNotifications() = scheduled.clear()
        override fun showNotificationNow(id: Int, title: String, body: String, data: Map<String, String>, channelId: String?, actions: List<NotificationAction>) = Unit
    }

    @Test
    fun `agendamento rolante usa o texto de cada dia e refaz a janela`() {
        val r = Recorder()
        val now = Instant.parse("2026-11-11T09:00:00Z")
        val n = r.scheduleRollingDaily(1000, 7, 0, days = 3, timeZoneId = "America/Cuiaba", now = now) { d ->
            if (d.dayOfMonth == 12) null else NotificationContent("Versículo", "dia ${d.dayOfMonth}")
        }
        assertEquals(2, n)
        assertEquals(setOf(1000, 1002), r.scheduled.keys)
        assertEquals("dia 13", r.scheduled[1002]!!.first)
        assertEquals((1000 until 1000 + NotificationPlanning.MAX_ROLLING_DAYS).toList(), r.cancelled)
        r.scheduleMonthly(2000, 31, 9, 0, months = 2, timeZoneId = "America/Cuiaba", now = Instant.parse("2026-01-15T12:00:00Z")) { NotificationContent("Dízimo", it.toString()) }
        assertEquals("2026-02-28", r.scheduled[2001]!!.first)
    }
}
