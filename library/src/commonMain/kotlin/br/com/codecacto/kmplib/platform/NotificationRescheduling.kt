package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
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

    private const val TAG = "NotificationRescheduling"

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
     * Próximo instante (epoch millis) em que o relógio de [timeZone] marca `hour:minute` **no dia da
     * semana [weekday]** (1 = segunda … 7 = domingo, ISO-8601), a partir de [nowMillis].
     *
     * Se hoje é o dia e o horário ainda não passou, é hoje; senão, é o mesmo dia da semana que vem.
     *
     * O avanço é feito em **dias de calendário** (`DatePeriod`), nunca somando 7 × 24 h ao instante:
     * na semana da virada do horário de verão o dia tem 23 ou 25 horas, e a aritmética de instante
     * desloca o culto de domingo em uma hora — justamente na semana em que a pessoa mais depende do
     * lembrete para não errar.
     */
    fun nextWeeklyTriggerMillis(
        weekday: Int,
        hour: Int,
        minute: Int,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val safeWeekday = weekday.coerceIn(1, 7)
        val time = LocalTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone)

        // Distância em dias até o próximo (ou o de hoje) dia da semana pedido.
        val diasAte = ((safeWeekday - now.date.dayOfWeek.isoDayNumber) + 7) % 7
        val candidato = LocalDateTime(now.date.plus(diasAte, DateTimeUnit.DAY), time)
            .toInstant(timeZone)
            .toEpochMilliseconds()
        if (candidato > nowMillis) return candidato

        // Hoje é o dia, mas o horário já passou: vai para a semana que vem.
        return LocalDateTime(now.date.plus(7, DateTimeUnit.DAY), time)
            .toInstant(timeZone)
            .toEpochMilliseconds()
    }

    /**
     * Resolve o fuso de um agendamento: o [id] IANA quando ele existe e a plataforma o conhece,
     * [fallback] em qualquer outro caso.
     *
     * **Nunca lança.** Um fuso que a plataforma não reconhece (base de tzdata velha, id digitado
     * errado no cadastro da cidade) faria o lembrete deixar de ser agendado — silêncio total, do
     * lado do usuário. Cair no fuso do aparelho com log de aviso erra no máximo o horário; não
     * agendar erra tudo.
     */
    fun zoneOf(id: String?, fallback: TimeZone = TimeZone.currentSystemDefault()): TimeZone {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallback
        return runCatching { TimeZone.of(trimmed) }.getOrElse {
            AppLogger.w(TAG, "Fuso desconhecido '$trimmed' — usando o do aparelho")
            fallback
        }
    }

    /**
     * Monta o plano de restauração para [stored].
     *
     * - **Recorrente (diário ou semanal)**: sempre volta para a fila, com o próximo horário
     *   recalculado — o semanal no fuso gravado nele ([ScheduledNotification.timeZoneId]), não no do
     *   parâmetro. Se o disparo foi perdido dentro da graça, entra também em `toShowNow`.
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
            // O adiamento manda quando existe: quem tocou "adiar 30 min" espera a notificação em 30
            // min, mesmo que o aparelho reinicie no meio.
            val effectiveTrigger = item.nextTriggerMillis
            val missedBy = nowMillis - effectiveTrigger
            val missedWithinGrace = missedBy in 1..graceMillis

            when (item.kind) {
                NotificationScheduleKind.DAILY, NotificationScheduleKind.WEEKLY -> {
                    if (item.isSnoozed && item.snoozedUntilMillis > nowMillis) {
                        // Adiamento ainda no futuro: reagenda como está (o horário regular fica
                        // guardado em hour/minute para depois).
                        toSchedule += item
                    } else {
                        if (missedWithinGrace) toShowNow += item
                        toSchedule += item.copy(
                            snoozedUntilMillis = 0L,
                            triggerAtMillis = nextRecurringTriggerMillis(item, nowMillis, timeZone),
                        )
                    }
                }

                NotificationScheduleKind.ONE_SHOT -> when {
                    effectiveTrigger > nowMillis -> toSchedule += item
                    missedWithinGrace -> {
                        toShowNow += item
                        expiredIds += item.id
                    }

                    else -> expiredIds += item.id
                }
            }
        }

        return NotificationReschedulePlan(
            toSchedule = toSchedule.sortedBy { it.nextTriggerMillis },
            toShowNow = toShowNow.sortedBy { it.nextTriggerMillis },
            expiredIds = expiredIds,
        )
    }

    /**
     * Próximo disparo de um agendamento **recorrente**, pela regra do seu [ScheduledNotification.kind].
     *
     * Fonte única do "quando é o próximo" — o receiver do Android, a restauração pós-boot e a fila do
     * iOS chamam esta função em vez de cada um repetir o cálculo. Um agendamento não recorrente
     * devolve o próprio `triggerAtMillis`.
     */
    fun nextRecurringTriggerMillis(
        item: ScheduledNotification,
        nowMillis: Long,
        fallbackTimeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long = when (item.kind) {
        NotificationScheduleKind.DAILY -> nextDailyTriggerMillis(
            hour = item.hour,
            minute = item.minute,
            nowMillis = nowMillis,
            timeZone = zoneOf(item.timeZoneId, fallbackTimeZone),
        )

        NotificationScheduleKind.WEEKLY -> nextWeeklyTriggerMillis(
            weekday = item.weekday,
            hour = item.hour,
            minute = item.minute,
            nowMillis = nowMillis,
            timeZone = zoneOf(item.timeZoneId, fallbackTimeZone),
        )

        NotificationScheduleKind.ONE_SHOT -> item.triggerAtMillis
    }

    /**
     * Divide [items] entre o que cabe no teto do SO e o que fica esperando.
     *
     * Prioridade: **recorrentes primeiro** (um único pedido repetitivo cobre disparos infinitos,
     * então deixá-lo de fora custaria muito mais caro), depois os únicos **mais próximos**. Itens já
     * vencidos não entram em nenhum dos dois — quem decide o que fazer com eles é [plan].
     */
    fun selectWindow(
        items: List<ScheduledNotification>,
        nowMillis: Long,
        limit: Int = IOS_PENDING_LIMIT,
    ): NotificationWindow {
        if (limit <= 0) return NotificationWindow(emptyList(), items)
        val pending = items.filter { it.isRecurring || it.nextTriggerMillis > nowMillis }
        val ordered = pending.sortedWith(
            compareByDescending<ScheduledNotification> { it.isRecurring }.thenBy { it.nextTriggerMillis },
        )
        return NotificationWindow(
            register = ordered.take(limit),
            deferred = ordered.drop(limit),
        )
    }
}
