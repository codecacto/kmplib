package br.com.codecacto.kmplib.platform

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * BroadcastReceiver para exibir notificações agendadas.
 *
 * Deve ser registrado no AndroidManifest.xml:
 * ```xml
 * <receiver
 *     android:name="br.com.codecacto.kmplib.platform.NotificationReceiver"
 *     android:exported="false" />
 * ```
 */
class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, 0)
        val title = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_TITLE) ?: ""
        val body = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_BODY) ?: ""
        val channelId = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_CHANNEL_ID)
            ?: AndroidNotificationScheduler.DEFAULT_CHANNEL_ID
        val data = (intent.getSerializableExtra(AndroidNotificationScheduler.EXTRA_DATA) as? HashMap<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                val v = value as? String ?: return@mapNotNull null
                k to v
            }
            ?.toMap()
            ?: emptyMap()
        AppLogger.d(TAG, "Recebido alarme para notificação: id=$id")

        val contentIntent = buildContentIntent(context, id, data)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setPriority(
                if (channelId == AndroidNotificationScheduler.CRITICAL_CHANNEL_ID)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            AppLogger.d(TAG, "Notificação exibida: id=$id")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Sem permissão para exibir notificação", e)
        }

        // Lembrete diário recorrente: reagenda o próximo disparo (amanhã, mesmo horário).
        if (intent.getBooleanExtra(AndroidNotificationScheduler.EXTRA_DAILY, false)) {
            rescheduleDaily(context, intent, id, title, body, channelId, data)
        } else {
            // Disparo único consumido: sai do registro persistente para não ser "restaurado" num
            // boot futuro (o plano de restauração o descartaria, mas registro sujo vira lixo eterno).
            runCatching { AndroidNotificationScheduleStore(context).remove(id) }
        }
    }

    private fun rescheduleDaily(
        context: Context,
        sourceIntent: Intent,
        id: Int,
        title: String,
        body: String,
        channelId: String,
        data: Map<String, String>
    ) {
        val hour = sourceIntent.getIntExtra(AndroidNotificationScheduler.EXTRA_HOUR, 8)
        val minute = sourceIntent.getIntExtra(AndroidNotificationScheduler.EXTRA_MINUTE, 0)
        val isCritical = sourceIntent.getBooleanExtra(AndroidNotificationScheduler.EXTRA_CRITICAL, false)
        val nextTrigger = AndroidNotificationScheduler.nextDailyTriggerMillis(hour, minute)

        val nextIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, id)
            putExtra(AndroidNotificationScheduler.EXTRA_TITLE, title)
            putExtra(AndroidNotificationScheduler.EXTRA_BODY, body)
            putExtra(AndroidNotificationScheduler.EXTRA_DATA, HashMap(data))
            putExtra(AndroidNotificationScheduler.EXTRA_CHANNEL_ID, channelId)
            putExtra(AndroidNotificationScheduler.EXTRA_DAILY, true)
            putExtra(AndroidNotificationScheduler.EXTRA_HOUR, hour)
            putExtra(AndroidNotificationScheduler.EXTRA_MINUTE, minute)
            putExtra(AndroidNotificationScheduler.EXTRA_CRITICAL, isCritical)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            }
            AppLogger.d(TAG, "Lembrete diário reagendado: id=$id, proximo=$nextTrigger")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao reagendar lembrete diário", e)
        }

        // Mantém o registro persistente apontando para o PRÓXIMO disparo: é ele que o
        // BootCompletedReceiver vai ler se o aparelho reiniciar antes da próxima dose.
        runCatching {
            AndroidNotificationScheduleStore(context).put(
                ScheduledNotification(
                    id = id,
                    title = title,
                    body = body,
                    kind = NotificationScheduleKind.DAILY,
                    triggerAtMillis = nextTrigger,
                    hour = hour,
                    minute = minute,
                    data = data,
                    channelId = channelId,
                    isCritical = isCritical,
                )
            )
        }
    }

    private fun buildContentIntent(
        context: Context,
        id: Int,
        data: Map<String, String>
    ): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.putExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, id)
        data.forEach { (key, value) ->
            launchIntent.putExtra(key, value)
        }

        return PendingIntent.getActivity(
            context,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}


