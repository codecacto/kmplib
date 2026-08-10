package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import platform.Foundation.NSUserDefaults

/**
 * Registro persistente dos agendamentos no iOS, em `NSUserDefaults`.
 *
 * ### Por que o iOS também precisa de registro, se o sistema já persiste
 * O `UNUserNotificationCenter` **de fato** guarda as notificações agendadas e as mantém através de
 * reboot — nesse ponto o iOS não tem o problema do Android. O registro existe por outro motivo: o
 * **teto de 64 notificações pendentes por app**. Passando disso, o iOS simplesmente **descarta os
 * pedidos excedentes, sem erro**. Um protocolo de 26 dias com dose de 12/12h pede 52 disparos só de
 * dose, mais os avisos de aproximação — encosta no teto com facilidade.
 *
 * Com o espelho, a lib registra no sistema só a janela dos próximos disparos
 * ([NotificationRescheduling.IOS_PENDING_LIMIT]) e reabastece a fila quando o app abre.
 */
internal class IosNotificationScheduleStore : NotificationScheduleStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun all(): List<ScheduledNotification> = read().values.toList()

    override fun get(id: Int): ScheduledNotification? = read()[id]

    override fun put(notification: ScheduledNotification) {
        val current = read().toMutableMap()
        current[notification.id] = notification
        write(current)
    }

    override fun remove(id: Int) {
        val current = read().toMutableMap()
        if (current.remove(id) != null) write(current)
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY_ITEMS)
    }

    private fun read(): Map<Int, ScheduledNotification> {
        val raw = defaults.stringForKey(KEY_ITEMS) ?: return emptyMap()
        return try {
            json.decodeFromString<List<ScheduledNotification>>(raw).associateBy { it.id }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Registro de notificações ilegível — descartando", e)
            defaults.removeObjectForKey(KEY_ITEMS)
            emptyMap()
        }
    }

    private fun write(items: Map<Int, ScheduledNotification>) {
        val raw = json.encodeToString(items.values.sortedBy { it.triggerAtMillis })
        defaults.setObject(raw, KEY_ITEMS)
    }

    private companion object {
        const val TAG = "NotificationStore"
        const val KEY_ITEMS = "kmplib_scheduled_notifications"

        val json = notificationScheduleJson
    }
}
