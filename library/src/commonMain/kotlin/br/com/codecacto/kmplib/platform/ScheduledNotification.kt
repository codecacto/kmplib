package br.com.codecacto.kmplib.platform

import kotlinx.serialization.Serializable

/** Como um agendamento se repete. */
@Serializable
enum class NotificationScheduleKind {
    /** Dispara UMA vez, no instante [ScheduledNotification.triggerAtMillis]. */
    ONE_SHOT,

    /** Dispara TODO dia no horário local `hour:minute`. */
    DAILY,
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
     * Em [NotificationScheduleKind.DAILY] é o **próximo** disparo calculado (recalculado a cada
     * disparo e a cada boot); em [NotificationScheduleKind.ONE_SHOT] é o instante pedido.
     */
    val triggerAtMillis: Long,
    /** Hora local do disparo diário (0..23); `-1` quando não se aplica. */
    val hour: Int = -1,
    /** Minuto local do disparo diário (0..59); `-1` quando não se aplica. */
    val minute: Int = -1,
    val data: Map<String, String> = emptyMap(),
    val channelId: String? = null,
    val isCritical: Boolean = false,
) {
    val isDaily: Boolean get() = kind == NotificationScheduleKind.DAILY
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
