package br.com.codecacto.kmplib.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lembrete local **semanal** (2.125.0) — o "quando é o próximo domingo às 18:30".
 *
 * Puro como o resto do reagendamento: o "agora" entra por parâmetro. É a mesma conta que o
 * `AlarmManager` do Android usa ao armar e ao reagendar depois de cada disparo, e que ordena a fila
 * de 64 pendentes do iOS.
 */
class NotificationWeeklyTest {

    private val cuiaba = TimeZone.of("America/Cuiaba")   // UTC-4, SEM horário de verão
    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    private fun localOf(millis: Long, zone: TimeZone) =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)

    private fun weekly(
        id: Int,
        weekday: Int,
        hour: Int,
        minute: Int,
        timeZoneId: String? = null,
        triggerAt: String = "2026-08-23T22:00:00Z",
    ) = ScheduledNotification(
        id = id,
        title = "Culto",
        body = "Começa em 30 minutos",
        kind = NotificationScheduleKind.WEEKLY,
        triggerAtMillis = millis(triggerAt),
        hour = hour,
        minute = minute,
        weekday = weekday,
        timeZoneId = timeZoneId,
    )

    // ── nextWeeklyTriggerMillis ──────────────────────────────────────────────────────────────────

    @Test
    fun `hoje e o dia e o horario ainda nao passou - dispara hoje`() {
        // Domingo, 23/08/2026, 14:00 em Cuiabá (17:00Z). Culto às 18:30.
        val agora = millis("2026-08-23T18:00:00Z")
        val proximo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 7, hour = 18, minute = 30, nowMillis = agora, timeZone = cuiaba,
        )
        val local = localOf(proximo, cuiaba)
        assertEquals(23, local.dayOfMonth)
        assertEquals(18, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun `hoje e o dia mas o horario ja passou - vai para a semana que vem`() {
        // Domingo, 23/08/2026, 20:00 em Cuiabá — o culto das 18:30 já foi.
        val agora = millis("2026-08-24T00:00:00Z")
        val proximo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 7, hour = 18, minute = 30, nowMillis = agora, timeZone = cuiaba,
        )
        val local = localOf(proximo, cuiaba)
        assertEquals(30, local.dayOfMonth) // domingo seguinte
        assertEquals(18, local.hour)
        assertEquals(7, local.dayOfWeek.isoDayNumber)
    }

    @Test
    fun `dia da semana ainda por vir nesta semana`() {
        // Segunda, 24/08/2026, 09:00 em Cuiabá. Culto de quarta (weekday 3) às 19:00.
        val agora = millis("2026-08-24T13:00:00Z")
        val proximo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 3, hour = 19, minute = 0, nowMillis = agora, timeZone = cuiaba,
        )
        val local = localOf(proximo, cuiaba)
        assertEquals(26, local.dayOfMonth)
        assertEquals(19, local.hour)
    }

    @Test
    fun `dia da semana ja passou nesta semana - da a volta`() {
        // Sexta, 28/08/2026, 09:00 em Cuiabá. Culto de quarta: só na semana que vem.
        val agora = millis("2026-08-28T13:00:00Z")
        val proximo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 3, hour = 19, minute = 0, nowMillis = agora, timeZone = cuiaba,
        )
        assertEquals(2, localOf(proximo, cuiaba).dayOfMonth) // 02/09, quarta seguinte
    }

    @Test
    fun `o horario e lido no fuso pedido, nao no do aparelho`() {
        // Mesmo instante, dois fusos: 19:00 em Cuiabá é 20:00 em São Paulo.
        val agora = millis("2026-08-24T13:00:00Z")
        val emCuiaba = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 3, hour = 19, minute = 0, nowMillis = agora, timeZone = cuiaba,
        )
        val emSaoPaulo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 3, hour = 19, minute = 0, nowMillis = agora, timeZone = saoPaulo,
        )
        assertTrue(emCuiaba > emSaoPaulo, "19:00 em Cuiabá acontece DEPOIS de 19:00 em São Paulo")
        assertEquals(19, localOf(emCuiaba, cuiaba).hour)
        assertEquals(19, localOf(emSaoPaulo, saoPaulo).hour)
    }

    @Test
    fun `a semana avanca em dias de calendario, nao em 7 x 24h`() {
        // A conta tem de valer também num fuso com histórico de horário de verão: o disparo seguinte
        // é sempre o MESMO horário de parede, nunca "o instante + 168 h".
        val agora = millis("2026-08-23T23:00:00Z")
        val proximo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 7, hour = 19, minute = 0, nowMillis = agora, timeZone = saoPaulo,
        )
        val depois = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 7, hour = 19, minute = 0, nowMillis = proximo + 1, timeZone = saoPaulo,
        )
        assertEquals(19, localOf(depois, saoPaulo).hour)
        assertEquals(0, localOf(depois, saoPaulo).minute)
    }

    @Test
    fun `dia fora da faixa nao explode - e ajustado para dentro`() {
        val agora = millis("2026-08-24T13:00:00Z")
        val proximo = NotificationRescheduling.nextWeeklyTriggerMillis(
            weekday = 9, hour = 25, minute = 90, nowMillis = agora, timeZone = cuiaba,
        )
        val local = localOf(proximo, cuiaba)
        assertEquals(7, local.dayOfWeek.isoDayNumber)
        assertEquals(23, local.hour)
        assertEquals(59, local.minute)
    }

    // ── zoneOf ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `fuso desconhecido cai no do aparelho em vez de lancar`() {
        assertEquals(cuiaba, NotificationRescheduling.zoneOf("Nao/Existe", fallback = cuiaba))
        assertEquals(cuiaba, NotificationRescheduling.zoneOf(null, fallback = cuiaba))
        assertEquals(cuiaba, NotificationRescheduling.zoneOf("   ", fallback = cuiaba))
        assertEquals(saoPaulo, NotificationRescheduling.zoneOf("America/Sao_Paulo", fallback = cuiaba))
    }

    // ── plan() ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `semanal volta para a fila depois do boot, com o proximo disparo recalculado`() {
        val agora = millis("2026-08-24T13:00:00Z") // segunda 09:00 em Cuiabá
        val plano = NotificationRescheduling.plan(
            stored = listOf(weekly(id = 1, weekday = 7, hour = 18, minute = 30, timeZoneId = "America/Cuiaba")),
            nowMillis = agora,
        )
        assertEquals(1, plano.toSchedule.size)
        assertTrue(plano.expiredIds.isEmpty(), "recorrente nunca expira")
        assertEquals(30, localOf(plano.toSchedule.first().triggerAtMillis, cuiaba).dayOfMonth)
    }

    @Test
    fun `o recalculo do semanal usa o fuso GRAVADO, nao o do aparelho`() {
        val agora = millis("2026-08-24T13:00:00Z")
        val plano = NotificationRescheduling.plan(
            stored = listOf(weekly(id = 1, weekday = 3, hour = 19, minute = 0, timeZoneId = "America/Cuiaba")),
            nowMillis = agora,
            timeZone = saoPaulo, // "aparelho" em outro fuso
        )
        assertEquals(19, localOf(plano.toSchedule.first().triggerAtMillis, cuiaba).hour)
    }

    @Test
    fun `disparo semanal perdido dentro da graca e exibido, e o proximo fica agendado`() {
        // Culto das 18:30 de domingo; o aparelho voltou 18:50 (20 min depois).
        val disparo = "2026-08-23T22:30:00Z"
        val agora = millis("2026-08-23T22:50:00Z")
        val plano = NotificationRescheduling.plan(
            stored = listOf(
                weekly(id = 1, weekday = 7, hour = 18, minute = 30, timeZoneId = "America/Cuiaba", triggerAt = disparo),
            ),
            nowMillis = agora,
        )
        assertEquals(listOf(1), plano.toShowNow.map { it.id })
        assertEquals(1, plano.toSchedule.size)
        assertEquals(30, localOf(plano.toSchedule.first().triggerAtMillis, cuiaba).dayOfMonth)
    }

    @Test
    fun `disparo semanal perdido ha dias nao e exibido - so reagendado`() {
        val plano = NotificationRescheduling.plan(
            stored = listOf(
                weekly(id = 1, weekday = 7, hour = 18, minute = 30, timeZoneId = "America/Cuiaba", triggerAt = "2026-08-16T22:30:00Z"),
            ),
            nowMillis = millis("2026-08-24T13:00:00Z"),
        )
        assertTrue(plano.toShowNow.isEmpty(), "culto da semana passada não se avisa hoje")
        assertEquals(1, plano.toSchedule.size)
    }

    // ── selectWindow() ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `no teto do iOS o semanal tem a mesma prioridade do diario`() {
        val agora = millis("2026-08-24T13:00:00Z")
        val unico = ScheduledNotification(
            id = 99,
            title = "Aviso",
            body = "…",
            kind = NotificationScheduleKind.ONE_SHOT,
            triggerAtMillis = agora + 1000,
        )
        val janela = NotificationRescheduling.selectWindow(
            items = listOf(unico, weekly(id = 1, weekday = 7, hour = 18, minute = 30)),
            nowMillis = agora,
            limit = 1,
        )
        assertEquals(listOf(1), janela.register.map { it.id }, "o recorrente entra antes do único")
        assertEquals(listOf(99), janela.deferred.map { it.id })
    }

    @Test
    fun `nextRecurringTriggerMillis escolhe a regra pelo tipo`() {
        val agora = millis("2026-08-24T13:00:00Z")
        val semanal = weekly(id = 1, weekday = 3, hour = 19, minute = 0, timeZoneId = "America/Cuiaba")
        assertEquals(26, localOf(NotificationRescheduling.nextRecurringTriggerMillis(semanal, agora), cuiaba).dayOfMonth)

        val umaVez = ScheduledNotification(
            id = 2, title = "x", body = "y",
            kind = NotificationScheduleKind.ONE_SHOT, triggerAtMillis = agora + 5_000,
        )
        assertEquals(agora + 5_000, NotificationRescheduling.nextRecurringTriggerMillis(umaVez, agora))
    }
}
