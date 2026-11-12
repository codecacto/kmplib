@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.platform

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Título e corpo de uma notificação planejada. */
data class NotificationContent(val title: String, val body: String)

/** Um disparo planejado: o dia (no fuso pedido) e o instante. */
data class PlannedNotification(val date: LocalDate, val at: Instant)

/**
 * Planejamento **puro** dos agendamentos com texto que muda (2.355.0) — testável sem aparelho.
 *
 * O iOS guarda no máximo **64 notificações pendentes por app** (as que passam disso são descartadas
 * em silêncio); o Android não tem teto, mas o padrão é o mesmo nas duas: uma janela de disparos ÚNICOS
 * à frente, reagendada a cada abertura do app.
 */
object NotificationPlanning {
    /** Teto do iOS para notificações pendentes do app. */
    const val IOS_PENDING_LIMIT: Int = 64

    /** Maior janela aceita por lembrete rolante — deixa espaço para outros lembretes no teto do iOS. */
    const val MAX_ROLLING_DAYS: Int = 30

    /** Janela padrão do lembrete rolante (duas semanas: cobre quem não abre o app por uns dias). */
    const val DEFAULT_ROLLING_DAYS: Int = 14

    /** Quantos meses à frente o lembrete mensal agenda. */
    const val DEFAULT_MONTHS_AHEAD: Int = 12

    /**
     * Os próximos [days] disparos diários às `hour:minute` no fuso [timeZone], a partir de [now]. O de
     * hoje entra só se o horário ainda não passou. [days] é limitado a 1..[MAX_ROLLING_DAYS].
     *
     * Horário que não existe no dia (pulo do horário de verão) cai no instante válido seguinte — é o
     * que `LocalDateTime.toInstant` faz.
     */
    fun rollingDaily(now: Instant, timeZone: TimeZone, days: Int, hour: Int, minute: Int): List<PlannedNotification> {
        val n = days.coerceIn(1, MAX_ROLLING_DAYS)
        val time = LocalTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val today = now.toLocalDateTime(timeZone).date
        var date = today
        if (date.atTime(time).toInstant(timeZone) <= now) date = date.plus(DatePeriod(days = 1))
        return List(n) { i ->
            val d = date.plus(DatePeriod(days = i))
            PlannedNotification(d, d.atTime(time).toInstant(timeZone))
        }
    }

    /**
     * Os próximos [months] disparos MENSAIS no dia [dayOfMonth] às `hour:minute`. Dia 29, 30 ou 31 em
     * mês mais curto cai no **último dia** do mês (o dízimo do dia 31 vence em 28/02, não pula fevereiro).
     * O deste mês entra só se ainda não passou.
     */
    fun monthly(now: Instant, timeZone: TimeZone, dayOfMonth: Int, hour: Int, minute: Int, months: Int = DEFAULT_MONTHS_AHEAD): List<PlannedNotification> {
        val dom = dayOfMonth.coerceIn(1, 31)
        val time = LocalTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val today = now.toLocalDateTime(timeZone).date
        val out = ArrayList<PlannedNotification>()
        var y = today.year
        var m = today.monthNumber
        while (out.size < months.coerceIn(1, 24)) {
            val d = LocalDate(y, m, minOf(dom, lastDayOfMonth(y, m)))
            val at = d.atTime(time).toInstant(timeZone)
            if (at > now) out += PlannedNotification(d, at)
            m++
            if (m > 12) { m = 1; y++ }
        }
        return out
    }

    /** Último dia do mês (28–31). Puro. */
    fun lastDayOfMonth(year: Int, month: Int): Int {
        val first = LocalDate(year, month, 1)
        return first.plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1)).dayOfMonth
    }

    /** Os ids de uma janela: `idBase`, `idBase + 1`, … — o mesmo `idBase` cancela/reagenda. */
    fun ids(idBase: Int, count: Int): List<Int> = List(count) { idBase + it }
}
