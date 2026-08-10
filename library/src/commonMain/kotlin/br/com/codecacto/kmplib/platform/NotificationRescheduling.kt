package br.com.codecacto.kmplib.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * O que fazer com cada agendamento persistido depois de um reboot, de uma atualização do app ou de
 * uma reabertura.
 *
 * [toSchedule] já vem com o `triggerAtMillis` **recalculado** — é só reagendar cada item.
 * [toShowNow] são os disparos que o aparelho perdeu enquanto estava desligado, mas ainda dentro da
 * janela de graça (ver [NotificationRescheduling.DEFAULT_MISSED_GRACE_MILLIS]).
 * [expiredIds] devem sair do registro: são disparos únicos que não valem mais.
 */
data class NotificationReschedulePlan(
    val toSchedule: List<ScheduledNotification>,
    val toShowNow: List<ScheduledNotification>,
    val expiredIds: List<Int>,
)

/**
 * Seleção de quais agendamentos ficam **registrados no sistema operacional** quando há um teto de
 * notificações pendentes (o caso do iOS, com 64 por app).
 */
data class NotificationWindow(
    /** Cabem no teto — devem estar registrados no SO. */
    val register: List<ScheduledNotification>,
    /** Não cabem agora — ficam só no registro da lib e entram quando os primeiros forem disparando. */
    val deferred: List<ScheduledNotification>,
)

/**
 * Regras **puras** de reagendamento — sem `Context`, sem `UNUserNotificationCenter`, sem relógio
 * implícito (o `now` entra por parâmetro). É o coração testável da restauração pós-boot: a decisão
 * "reagenda / mostra agora / joga fora" nunca muda de plataforma, só a execução muda.
 */
object NotificationRescheduling {

    /**
     * Janela de graça para um disparo perdido: **1 hora**.
     *
     * Por quê: se o aparelho estava desligado às 20:00 (hora da dose) e ligou às 20:05, o usuário
     * quer saber que a dose passou — avisar é melhor que silêncio. Já um lembrete de ontem
     * aparecendo hoje de manhã é ruído (e, em app de medicação, ruído perigoso: sugere tomar fora de
     * hora). Uma hora separa bem os dois casos.
     */
    const val DEFAULT_MISSED_GRACE_MILLIS: Long = 60L * 60L * 1000L

    /**
     * Teto de notificações pendentes registradas no iOS. O limite real da Apple é **64 por app**;
     * ficamos em 60 para deixar folga a notificações que o app agende fora da lib. Ultrapassado o
     * teto, o iOS **descarta silenciosamente** os pedidos excedentes — daí a janela.
     */
    const val IOS_PENDING_LIMIT: Int = 60

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    /**
     * Próximo instante (epoch millis) em que o relógio local marca `hour:minute`, a partir de [nowMillis].
     * Se o horário de hoje já passou, devolve o de amanhã.
     *
     * Usa [TimeZone] de verdade, então a virada de dia, o fim de mês e o horário de verão saem certos
     * — coisas que aritmética de "+24h" erra.
     */
    fun nextDailyTriggerMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)
        val todayTarget = LocalDateTime(now.date, LocalTime(safeHour, safeMinute))
            .toInstant(timeZone)
            .toEpochMilliseconds()
        if (todayTarget > nowMillis) return todayTarget

        // Amanhã: soma um dia de CALENDÁRIO na data local (não 24 h no instante).
        val tomorrow = Instant.fromEpochMilliseconds(nowMillis + MILLIS_PER_DAY)
            .toLocalDateTime(timeZone)
            .date
        return LocalDateTime(tomorrow, LocalTime(safeHour, safeMinute))
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }

    /**
     * Monta o plano de restauração para [stored].
     *
     * - **Diário**: sempre volta para a fila, com o próximo horário recalculado. Se o disparo de hoje
     *   foi perdido dentro da graça, entra também em `toShowNow`.
     * - **Único futuro**: reagendado como está.
     * - **Único perdido dentro da graça**: exibido agora e removido do registro.
     * - **Único perdido além da graça**: só removido — notificação de medicação atrasada de ontem não
     *   se "recupera", se descarta.
     */
    fun plan(
        stored: List<ScheduledNotification>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        graceMillis: Long = DEFAULT_MISSED_GRACE_MILLIS,
    ): NotificationReschedulePlan {
        val toSchedule = mutableListOf<ScheduledNotification>()
        val toShowNow = mutableListOf<ScheduledNotification>()
        val expiredIds = mutableListOf<Int>()

        stored.forEach { item ->
            val missedBy = nowMillis - item.triggerAtMillis
            val missedWithinGrace = missedBy in 1..graceMillis

            when (item.kind) {
                NotificationScheduleKind.DAILY -> {
                    if (missedWithinGrace) toShowNow += item
                    toSchedule += item.copy(
                        triggerAtMillis = nextDailyTriggerMillis(
                            hour = item.hour,
                            minute = item.minute,
                            nowMillis = nowMillis,
                            timeZone = timeZone,
                        ),
                    )
                }

                NotificationScheduleKind.ONE_SHOT -> when {
                    item.triggerAtMillis > nowMillis -> toSchedule += item
                    missedWithinGrace -> {
                        toShowNow += item
                        expiredIds += item.id
                    }

                    else -> expiredIds += item.id
                }
            }
        }

        return NotificationReschedulePlan(
            toSchedule = toSchedule.sortedBy { it.triggerAtMillis },
            toShowNow = toShowNow.sortedBy { it.triggerAtMillis },
            expiredIds = expiredIds,
        )
    }

    /**
     * Divide [items] entre o que cabe no teto do SO e o que fica esperando.
     *
     * Prioridade: **diários primeiro** (um único pedido repetitivo cobre disparos infinitos, então
     * deixá-lo de fora custaria muito mais caro), depois os únicos **mais próximos**. Itens já
     * vencidos não entram em nenhum dos dois — quem decide o que fazer com eles é [plan].
     */
    fun selectWindow(
        items: List<ScheduledNotification>,
        nowMillis: Long,
        limit: Int = IOS_PENDING_LIMIT,
    ): NotificationWindow {
        if (limit <= 0) return NotificationWindow(emptyList(), items)
        val pending = items.filter { it.isDaily || it.triggerAtMillis > nowMillis }
        val ordered = pending.sortedWith(
            compareByDescending<ScheduledNotification> { it.isDaily }.thenBy { it.triggerAtMillis },
        )
        return NotificationWindow(
            register = ordered.take(limit),
            deferred = ordered.drop(limit),
        )
    }
}
