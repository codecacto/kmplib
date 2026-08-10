package br.com.codecacto.kmplib.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
         *
         * Delega à regra pura [NotificationRescheduling.nextDailyTriggerMillis] (testada em
         * `commonTest`) — o `Calendar` aqui serve só para informar "agora".
         */
        fun nextDailyTriggerMillis(
            hour: Int,
            minute: Int,
            now: java.util.Calendar = java.util.Calendar.getInstance()
        ): Long = NotificationRescheduling.nextDailyTriggerMillis(
            hour = hour,
            minute = minute,
            nowMillis = now.timeInMillis,
        )
    }

    private val context: Context?
        get() = NotificationSchedulerHolder.getContext()

    /** Espelho persistente dos agendamentos — é o que sobrevive ao boot (o AlarmManager não). */
    private val store: NotificationScheduleStore?
        get() = context?.let { AndroidNotificationScheduleStore(it) }

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
        val resolvedChannel = channelId ?: if (isCritical) CRITICAL_CHANNEL_ID else DEFAULT_CHANNEL_ID
        val triggerTime = scheduledTime.toEpochMilliseconds()

        scheduleExactAlarm(
            ctx = ctx,
            id = id,
            intent = buildAlarmIntent(
                ctx = ctx,
                id = id,
                title = title,
                body = body,
                data = data,
                channelId = resolvedChannel,
                daily = false,
                hour = -1,
                minute = -1,
                isCritical = isCritical,
            ),
            triggerTime = triggerTime,
        )

        store?.put(
            ScheduledNotification(
                id = id,
                title = title,
                body = body,
                kind = NotificationScheduleKind.ONE_SHOT,
                triggerAtMillis = triggerTime,
                data = data,
                channelId = resolvedChannel,
                isCritical = isCritical,
            )
        )
        AppLogger.d(TAG, "Notificação agendada: id=$id, time=$scheduledTime")
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

        scheduleExactAlarm(
            ctx = ctx,
            id = id,
            intent = buildAlarmIntent(
                ctx = ctx,
                id = id,
                title = title,
                body = body,
                data = data,
                channelId = resolvedChannel,
                daily = true,
                hour = hour,
                minute = minute,
                isCritical = isCritical,
            ),
            triggerTime = triggerTime,
        )

        store?.put(
            ScheduledNotification(
                id = id,
                title = title,
                body = body,
                kind = NotificationScheduleKind.DAILY,
                triggerAtMillis = triggerTime,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                data = data,
                channelId = resolvedChannel,
                isCritical = isCritical,
            )
        )
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
        store?.remove(id)
        AppLogger.d(TAG, "Notificação cancelada: id=$id")
    }

    /**
     * Cancela TODAS as notificações — agendadas e já exibidas.
     *
     * Até a 2.98.0 este método só dispensava as notificações **na bandeja**: os alarmes seguiam
     * armados no `AlarmManager` e voltavam a disparar, contrariando o próprio contrato documentado.
     * Com o registro persistente a lib passa a saber o que cancelar, e o método faz o que promete.
     */
    override fun cancelAllNotifications() {
        val ctx = context ?: return
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val currentStore = store

        currentStore?.all()?.forEach { scheduled ->
            val pendingIntent = PendingIntent.getBroadcast(
                ctx,
                scheduled.id,
                Intent(ctx, NotificationReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching { alarmManager.cancel(pendingIntent) }
                .onFailure { AppLogger.w(TAG, "Falha ao cancelar alarme id=${scheduled.id}: ${it.message}") }
        }
        currentStore?.clear()

        NotificationManagerCompat.from(ctx).cancelAll()
        AppLogger.d(TAG, "Todas as notificações canceladas (bandeja + alarmes agendados)")
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

    override fun scheduledNotifications(): List<ScheduledNotification> = store?.all().orEmpty()

    /**
     * Reagenda no `AlarmManager` tudo o que estiver no registro persistente.
     *
     * Chamado pelo [BootCompletedReceiver] (boot e atualização do app) e seguro de chamar na
     * abertura do app: é idempotente (`FLAG_UPDATE_CURRENT` sobrescreve o alarme de mesmo id).
     */
    override fun refreshScheduledNotifications() {
        val ctx = context ?: return
        val currentStore = store ?: return
        val stored = currentStore.all()
        if (stored.isEmpty()) return

        val plan = NotificationRescheduling.plan(
            stored = stored,
            nowMillis = System.currentTimeMillis(),
        )

        plan.toShowNow.forEach { missed ->
            showNotificationNow(
                id = missed.id,
                title = missed.title,
                body = missed.body,
                data = missed.data,
                channelId = missed.channelId,
            )
        }

        plan.expiredIds.forEach(currentStore::remove)

        plan.toSchedule.forEach { item ->
            scheduleExactAlarm(
                ctx = ctx,
                id = item.id,
                intent = buildAlarmIntent(
                    ctx = ctx,
                    id = item.id,
                    title = item.title,
                    body = item.body,
                    data = item.data,
                    channelId = item.channelId ?: DEFAULT_CHANNEL_ID,
                    daily = item.isDaily,
                    hour = item.hour,
                    minute = item.minute,
                    isCritical = item.isCritical,
                ),
                triggerTime = item.triggerAtMillis,
            )
            currentStore.put(item)
        }

        AppLogger.d(
            TAG,
            "Agendamentos restaurados: ${plan.toSchedule.size} reagendados, " +
                "${plan.toShowNow.size} perdidos exibidos, ${plan.expiredIds.size} expirados"
        )
    }

    override fun canScheduleExactAlarms(): Boolean {
        val ctx = context ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    override fun requestExactAlarmPermission() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (canScheduleExactAlarms()) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.onFailure {
            AppLogger.w(TAG, "Não foi possível abrir a tela de alarmes exatos: ${it.message}")
        }
    }

    override fun openBatteryOptimizationSettings() {
        val ctx = context ?: return
        runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        }.onFailure {
            AppLogger.w(TAG, "Não foi possível abrir a otimização de bateria: ${it.message}")
        }
    }

    private fun buildAlarmIntent(
        ctx: Context,
        id: Int,
        title: String,
        body: String,
        data: Map<String, String>,
        channelId: String,
        daily: Boolean,
        hour: Int,
        minute: Int,
        isCritical: Boolean,
    ): Intent = Intent(ctx, NotificationReceiver::class.java).apply {
        putExtra(EXTRA_NOTIFICATION_ID, id)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_BODY, body)
        putExtra(EXTRA_DATA, HashMap(data))
        putExtra(EXTRA_CHANNEL_ID, channelId)
        putExtra(EXTRA_DAILY, daily)
        putExtra(EXTRA_HOUR, hour)
        putExtra(EXTRA_MINUTE, minute)
        putExtra(EXTRA_CRITICAL, isCritical)
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
                    // Sem a permissão de alarme exato o disparo pode atrasar alguns minutos — melhor
                    // isso do que não agendar nada. O app pode oferecer `requestExactAlarmPermission()`.
                    AppLogger.w(TAG, "Sem permissão de alarme exato; agendando alarme inexato (id=$id)")
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
