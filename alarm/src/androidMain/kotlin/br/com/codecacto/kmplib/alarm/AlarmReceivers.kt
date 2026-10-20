package br.com.codecacto.kmplib.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.codecacto.kmplib.core.util.AppLogger

/** Mostra a notificação do alarme que disparou e o tira do registro. */
class AlarmReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission") // areNotificationsEnabled conferido antes de notificar.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidAlarmScheduler.ACTION_FIRE) return
        val id = intent.getIntExtra(AndroidAlarmScheduler.EXTRA_ID, Int.MIN_VALUE)
        if (id == Int.MIN_VALUE) return
        val kind = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_KIND)
            ?.let { name -> AlarmKind.entries.firstOrNull { it.name == name } } ?: AlarmKind.REMINDER
        val label = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_LABEL).orEmpty()
        AlarmStore.remove(context, id)

        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) {
            AppLogger.w("KmpLibAlarm", "Alarme $id disparou com as notificações desligadas")
            return
        }
        AlarmNotifications.ensureChannels(context)
        val patrol = kind == AlarmKind.PATROL_START
        val notification = NotificationCompat.Builder(
            context,
            if (patrol) AlarmNotifications.CHANNEL_PATROL else AlarmNotifications.CHANNEL_REMINDER,
        )
            .setSmallIcon(AlarmHolder.config.smallIconRes ?: android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label.ifBlank { null })
            .setContentText(
                context.getString(if (patrol) R.string.kmplib_alarm_text_patrol else R.string.kmplib_alarm_text_reminder),
            )
            .setCategory(if (patrol) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(AlarmNotifications.launchPendingIntent(context, id))
            .build()
        notifications.notify(AlarmNotifications.NOTIFICATION_TAG, id, notification)
    }
}

/**
 * Reagenda os alarmes do registro depois do boot, da atualização do app e da mudança da permissão
 * de alarme exato (concedida: os que estavam em janela viram exatos).
 */
class AlarmRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Unit
            else -> return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val (keep, drop) = recordsToRestore(AlarmStore.all(context), System.currentTimeMillis())
        drop.forEach { AlarmStore.remove(context, it.id) }
        keep.forEach { record ->
            AndroidAlarmScheduler.arm(
                context,
                alarmManager,
                record,
                AndroidAlarmScheduler.strategyFor(context, alarmManager, record.kind),
            )
        }
    }
}

/** O registro dos alarmes agendados (SharedPreferences): fonte do reagendamento pós-boot. */
internal object AlarmStore {
    private const val PREFS = "kmplib_alarm"
    private const val PREFIX = "alarm."

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(context: Context, record: AlarmRecord) {
        prefs(context).edit().putString(PREFIX + record.id, record.encode()).commit()
    }

    fun remove(context: Context, id: Int) {
        prefs(context).edit().remove(PREFIX + id).commit()
    }

    fun all(context: Context): List<AlarmRecord> =
        prefs(context).all.mapNotNull { (key, value) ->
            val id = key.removePrefix(PREFIX).takeIf { key.startsWith(PREFIX) }?.toIntOrNull()
                ?: return@mapNotNull null
            AlarmRecord.decode(id, value as? String)
        }
}
