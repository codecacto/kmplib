package br.com.codecacto.kmplib.platform

import android.content.Context
import android.content.SharedPreferences
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Registro persistente dos agendamentos, em `SharedPreferences`.
 *
 * **Por que SharedPreferences e não DataStore/SQLDelight:** este store é lido de dentro de um
 * `BroadcastReceiver` de `BOOT_COMPLETED`, que roda **fora de qualquer escopo de corrotina** e tem
 * poucos segundos de vida. `SharedPreferences` é a única das três com leitura síncrona confiável
 * nesse contexto — e o volume é minúsculo (dezenas de linhas de texto). Usar DataStore aqui
 * obrigaria a um `runBlocking` no receiver, que é exatamente o que a documentação do Android manda
 * não fazer.
 *
 * A escrita usa `apply()` (assíncrona, mas durável): o custo de um `commit()` síncrono a cada
 * agendamento não se justifica, e o pior caso é perder o registro de um alarme agendado nos
 * milissegundos anteriores a um crash.
 */
internal class AndroidNotificationScheduleStore(
    context: Context,
) : NotificationScheduleStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun read(): Map<Int, ScheduledNotification> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyMap()
        return try {
            json.decodeFromString<List<ScheduledNotification>>(raw).associateBy { it.id }
        } catch (e: Exception) {
            // Registro corrompido (formato antigo, escrita interrompida): melhor recomeçar vazio do
            // que derrubar o boot do app inteiro por causa de um JSON quebrado.
            AppLogger.e(TAG, "Registro de notificações ilegível — descartando", e)
            prefs.edit().remove(KEY_ITEMS).apply()
            emptyMap()
        }
    }

    private fun write(items: Map<Int, ScheduledNotification>) {
        val raw = json.encodeToString(items.values.sortedBy { it.triggerAtMillis })
        prefs.edit().putString(KEY_ITEMS, raw).apply()
    }

    companion object {
        private const val TAG = "NotificationStore"
        private const val PREFS_NAME = "kmplib_scheduled_notifications"
        private const val KEY_ITEMS = "items"

        /**
         * Tolerante a campo novo/desconhecido: um registro gravado por versão anterior é lido sem
         * perder tudo (campo novo assume o default). Fonte única em `notificationScheduleJson`, a
         * mesma usada pelo payload que viaja no `Intent` do alarme.
         */
        private val json = notificationScheduleJson
    }
}
