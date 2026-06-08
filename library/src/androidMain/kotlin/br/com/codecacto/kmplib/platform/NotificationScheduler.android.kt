package br.com.codecacto.kmplib.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.datetime.Instant
import java.lang.ref.WeakReference

/**
 * Holder para o contexto do Android.
 * Deve ser inicializado no Application.onCreate().
 */
object NotificationSchedulerHolder {
    private var contextRef: WeakReference<Context>? = null
    private var activityRef: WeakReference<FragmentActivity>? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
    internal fun getActivity(): FragmentActivity? = activityRef?.get()

    fun setActivity(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity() {
        activityRef = null
    }

    fun setPermissionCallback(callback: (Boolean) -> Unit) {
        permissionCallback = callback
    }

    fun notifyPermissionResult(granted: Boolean) {
        permissionCallback?.invoke(granted)
        permissionCallback = null
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == AndroidNotificationScheduler.PERMISSION_REQUEST_CODE) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            notifyPermissionResult(granted)
        }
    }
}

class AndroidNotificationScheduler : NotificationScheduler {

    companion object {
        private const val TAG = "NotificationScheduler"
        const val DEFAULT_CHANNEL_ID = "default_channel"
        const val CRITICAL_CHANNEL_ID = "critical_channel"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TITLE = "notification_title"
        const val EXTRA_BODY = "notification_body"
        const val EXTRA_DATA = "notification_data"
        const val EXTRA_CHANNEL_ID = "notification_channel_id"
        const val EXTRA_DAILY = "notification_daily"
        const val EXTRA_HOUR = "notification_hour"
        const val EXTRA_MINUTE = "notification_minute"
        const val EXTRA_CRITICAL = "notification_critical"
        const val PERMISSION_REQUEST_CODE = 9923

        /**
         * Calcula o próximo timestamp (epoch millis) para o horário local hour:minute.
         * Se o horário de hoje já passou (com base em [now]), retorna o de amanhã.
         */
        fun nextDailyTriggerMillis(
            hour: Int,
            minute: Int,
            now: java.util.Calendar = java.util.Calendar.getInstance()
        ): Long {
            val target = now.clone() as java.util.Calendar
            target.set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            target.set(java.util.Calendar.MINUTE, minute.coerceIn(0, 59))
            target.set(java.util.Calendar.SECOND, 0)
            target.set(java.util.Calendar.MILLISECOND, 0)
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis
        }
    }

    private val context: Context?
        get() = NotificationSchedulerHolder.getContext()

    init {
        createDefaultChannels()
    }

    private fun createDefaultChannels() {
        val ctx = context ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = ctx.getSystemService(NotificationManager::class.java)

            // Canal padrão
            val defaultChannel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                "Notificações",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações gerais do aplicativo"
            }
            notificationManager.createNotificationChannel(defaultChannel)

            // Canal crítico (tenta bypassar DND)
            val criticalChannel = NotificationChannel(
                CRITICAL_CHANNEL_ID,
                "Notificações Importantes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações urgentes que podem ignorar o modo não perturbe"
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(criticalChannel)
        }
    }

    override fun hasPermission(): Boolean {
        val ctx = context ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        val ctx = context
        if (ctx == null) {
            onResult(false)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = NotificationSchedulerHolder.getActivity()
            if (activity == null) {
                AppLogger.w(TAG, "Activity nao disponivel para solicitar permissao de notificacao")
                onResult(false)
                return
            }

            NotificationSchedulerHolder.setPermissionCallback(onResult)
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        } else {
            onResult(NotificationManagerCompat.from(ctx).areNotificationsEnabled())
        }
    }

    override fun scheduleNotification(
        id: Int,
        title: String,
        body: String,
        scheduledTime: Instant,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean
    ) {
        val ctx = context ?: return

        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(ctx, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_DATA, HashMap(data))
            putExtra(EXTRA_CHANNEL_ID, channelId ?: if (isCritical) CRITICAL_CHANNEL_ID else DEFAULT_CHANNEL_ID)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = scheduledTime.toEpochMilliseconds()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            AppLogger.d(TAG, "Notificação agendada: id=$id, time=$scheduledTime")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao agendar notificação", e)
        }
    }

    override fun scheduleDailyNotification(
        id: Int,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean
    ) {
        val ctx = context ?: return

        val triggerTime = nextDailyTriggerMillis(hour, minute)
        val resolvedChannel = channelId ?: if (isCritical) CRITICAL_CHANNEL_ID else DEFAULT_CHANNEL_ID

        val intent = Intent(ctx, NotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_DATA, HashMap(data))
            putExtra(EXTRA_CHANNEL_ID, resolvedChannel)
            // Marcadores de recorrência para o receiver reagendar o próximo dia.
            putExtra(EXTRA_DAILY, true)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_CRITICAL, isCritical)
        }

        scheduleExactAlarm(ctx, id, intent, triggerTime)
        AppLogger.d(TAG, "Lembrete diário agendado: id=$id, horario=$hour:$minute, proximo=$triggerTime")
    }

    override fun cancelNotification(id: Int) {
        val ctx = context ?: return

        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(ctx, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        NotificationManagerCompat.from(ctx).cancel(id)
        AppLogger.d(TAG, "Notificação cancelada: id=$id")
    }

    override fun cancelAllNotifications() {
        val ctx = context ?: return
        NotificationManagerCompat.from(ctx).cancelAll()
        AppLogger.d(TAG, "Todas as notificações canceladas")
    }

    override fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String>,
        channelId: String?
    ) {
        val ctx = context ?: return

        if (!hasPermission()) {
            AppLogger.w(TAG, "Sem permissão para exibir notificação")
            return
        }

        val contentIntent = buildContentIntent(ctx, id, data)
        val notification = NotificationCompat.Builder(ctx, channelId ?: DEFAULT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
            AppLogger.d(TAG, "Notificação exibida: id=$id")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Sem permissão para exibir notificação", e)
        }
    }

    private fun scheduleExactAlarm(ctx: Context, id: Int, intent: Intent, triggerTime: Long) {
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao agendar alarme exato", e)
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
        launchIntent.putExtra(EXTRA_NOTIFICATION_ID, id)
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

actual fun getNotificationScheduler(): NotificationScheduler {
    NotificationSchedulerHolder.getContext()
        ?: throw IllegalStateException("NotificationSchedulerHolder nao foi inicializado. Chame NotificationSchedulerHolder.init(context) no Application.onCreate()")
    return AndroidNotificationScheduler()
}

