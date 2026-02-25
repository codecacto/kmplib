package br.com.codecacto.kmplib.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
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


