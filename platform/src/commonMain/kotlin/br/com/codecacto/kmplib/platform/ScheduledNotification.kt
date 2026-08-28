package br.com.codecacto.kmplib.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Formato de serialização do registro de agendamentos (e do payload que viaja no `Intent` do
 * Android).
 *
 * `ignoreUnknownKeys` + `encodeDefaults` é o par que garante compatibilidade nos **dois** sentidos:
 * um registro gravado por versão anterior é lido sem os campos novos (que assumem o default), e um
 * registro gravado por versão nova não quebra se o app for revertido.
 */
internal val notificationScheduleJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Como um agendamento se repete. */
@Serializable
enum class NotificationScheduleKind {
    /** Dispara UMA vez, no instante [ScheduledNotification.triggerAtMillis]. */
    ONE_SHOT,

    /** Dispara TODO dia no horário local `hour:minute`. */
    DAILY,

    /**
     * Dispara TODA semana no dia [ScheduledNotification.weekday] (1 = segunda … 7 = domingo,
     * ISO-8601), no horário `hour:minute` — do fuso de [ScheduledNotification.timeZoneId] quando ele
     * existe, do aparelho quando não (2.125.0).
     */
    WEEKLY,
}

/**
 * Um agendamento de notificação local **persistido pela lib**.
 *
 * ### Por que a lib guarda isto
 * No Android, um alarme vive dentro do `AlarmManager` — e o `AlarmManager` é **zerado no boot**.
 * Sem um registro próprio, depois que o aparelho reinicia não há como saber o que havia agendado:
 * o lembrete simplesmente **desaparece em silêncio** e só volta se o usuário abrir o app. Para um
 * app de dose de medicação isso não é detalhe técnico, é falha de produto.
 *
 * O espelho persistido é o que permite ao [BootCompletedReceiver] (Android) reagendar tudo depois do
 * boot/atualização, e ao iOS manter a fila dentro do teto de 64 notificações pendentes por app.
 *
 * `equals` é estrutural (data class), então reagendar o mesmo id sobrescreve sem duplicar.
 */
@Serializable
data class ScheduledNotification(
    /** Id único (o mesmo usado em `scheduleNotification`/`cancelNotification`). */
    val id: Int,
    val title: String,
    val body: String,
    val kind: NotificationScheduleKind,
    /**
     * Próximo disparo, em epoch millis.
     *
     * Em [NotificationScheduleKind.DAILY] e [NotificationScheduleKind.WEEKLY] é o **próximo** disparo
     * calculado (recalculado a cada disparo e a cada boot); em [NotificationScheduleKind.ONE_SHOT] é
     * o instante pedido.
     */
    val triggerAtMillis: Long,
    /** Hora local do disparo recorrente (0..23); `-1` quando não se aplica. */
    val hour: Int = -1,
    /** Minuto local do disparo recorrente (0..59); `-1` quando não se aplica. */
    val minute: Int = -1,
    /**
     * Dia da semana do disparo [NotificationScheduleKind.WEEKLY] — **1 = segunda … 7 = domingo**
     * (ISO-8601, o mesmo do `kotlinx.datetime.DayOfWeek.isoDayNumber`); `-1` quando não se aplica.
     */
    val weekday: Int = -1,
    /**
     * Fuso (IANA, ex.: `"America/Cuiaba"`) em que `hour:minute` deve ser lido; `null` = o do
     * aparelho (2.125.0).
     *
     * Existe porque o lembrete **semanal** costuma estar preso a um LUGAR, não à pessoa: o culto de
     * domingo às 19:00 é 19:00 na cidade da igreja, e o morador que viajou continua querendo ser
     * avisado a tempo de assistir — não uma hora fora. O lembrete diário (dose de remédio) é o caso
     * oposto, e por isso segue no fuso do aparelho, sem este campo.
     *
     * Fuso desconhecido pela plataforma **cai no do aparelho com log de aviso** — nunca deixa de
     * agendar.
     */
    val timeZoneId: String? = null,
    val data: Map<String, String> = emptyMap(),
    val channelId: String? = null,
    val isCritical: Boolean = false,
    /**
     * Botões da notificação (2.100.0).
     *
     * Persistidos junto com o resto **de propósito**: sem isto, um lembrete restaurado depois do
     * reboot voltaria sem os botões — a pessoa teria "Adiar 30 min" na segunda-feira e não teria na
     * terça, sem explicação. Campo **novo com default**, então um registro gravado por versão
     * anterior continua sendo lido sem erro (e volta simplesmente sem ações).
     */
    val actions: List<NotificationAction> = emptyList(),
    /**
     * Instante (epoch millis) para o qual este disparo foi **adiado**; `0` = não adiado (2.100.0).
     *
     * Existe como campo separado para o adiamento **não apagar** o horário regular: num lembrete
     * diário, [triggerAtMillis]/[hour]/[minute] continuam valendo para os próximos dias enquanto
     * este campo carrega só o disparo de hoje que o usuário empurrou para frente. É o que faz
     * "adiar" não matar a recorrência — e o que permite reabrir o app depois de um reboot no meio do
     * adiamento sem perder o lembrete.
     */
    val snoozedUntilMillis: Long = 0L,
) {
    val isDaily: Boolean get() = kind == NotificationScheduleKind.DAILY

    val isWeekly: Boolean get() = kind == NotificationScheduleKind.WEEKLY

    /** Repete sozinho até ser cancelado — diário ou semanal. */
    val isRecurring: Boolean get() = isDaily || isWeekly

    /** `true` quando há um adiamento pendente. */
    val isSnoozed: Boolean get() = snoozedUntilMillis > 0L

    /**
     * O instante em que este agendamento deve disparar de fato: o adiamento quando existe, o horário
     * regular quando não. **Use este** ao armar o alarme/trigger — nunca [triggerAtMillis] cru.
     */
    val nextTriggerMillis: Long
        get() = if (snoozedUntilMillis > 0L) snoozedUntilMillis else triggerAtMillis
}

/**
 * Registro persistente dos agendamentos feitos pela lib.
 *
 * Implementações reais: `SharedPreferences` no Android e `NSUserDefaults` no iOS (ambas
 * **síncronas** de propósito — a leitura acontece dentro de um `BroadcastReceiver` de boot, onde não
 * há escopo de corrotina nem tempo para I/O assíncrono). Para teste/fake, use
 * [InMemoryNotificationScheduleStore].
 *
 * Não guarda nada além do necessário para reagendar: título, corpo, horário e o `data` que o app já
 * passou. **Não é banco de domínio** — quem precisa de histórico de doses guarda no próprio app.
 */
interface NotificationScheduleStore {
    fun all(): List<ScheduledNotification>
    fun get(id: Int): ScheduledNotification?
    fun put(notification: ScheduledNotification)
    fun remove(id: Int)
    fun clear()
}

/** Store em memória — para testes e para degradar sem quebrar quando não há armazenamento. */
class InMemoryNotificationScheduleStore(
    initial: List<ScheduledNotification> = emptyList(),
) : NotificationScheduleStore {

    private val items = LinkedHashMap<Int, ScheduledNotification>().apply {
        initial.forEach { put(it.id, it) }
    }

    override fun all(): List<ScheduledNotification> = items.values.toList()
    override fun get(id: Int): ScheduledNotification? = items[id]
    override fun put(notification: ScheduledNotification) {
        items[notification.id] = notification
    }

    override fun remove(id: Int) {
        items.remove(id)
    }

    override fun clear() {
        items.clear()
    }
}
