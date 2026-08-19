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
        isCritical: Boolean,
        actions: List<NotificationAction>
    ) {
        val ctx = context ?: return
        val item = ScheduledNotification(
            id = id,
            title = title,
            body = body,
            kind = NotificationScheduleKind.ONE_SHOT,
            triggerAtMillis = scheduledTime.toEpochMilliseconds(),
            data = data,
            channelId = channelId ?: if (isCritical) CRITICAL_CHANNEL_ID else DEFAULT_CHANNEL_ID,
            isCritical = isCritical,
            actions = NotificationActionRules.distinctActions(actions),
        )

        NotificationAlarms.arm(ctx, item)
        store?.put(item)
        AppLogger.d(TAG, "Notificação agendada: id=$id, time=$scheduledTime, ações=${item.actions.size}")
    }

    override fun scheduleDailyNotification(
        id: Int,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean,
        actions: List<NotificationAction>
    ) {
        val ctx = context ?: return
        val item = ScheduledNotification(
            id = id,
            title = title,
            body = body,
            kind = NotificationScheduleKind.DAILY,
            triggerAtMillis = nextDailyTriggerMillis(hour, minute),
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            data = data,
            channelId = channelId ?: if (isCritical) CRITICAL_CHANNEL_ID else DEFAULT_CHANNEL_ID,
            isCritical = isCritical,
            actions = NotificationActionRules.distinctActions(actions),
        )

        NotificationAlarms.arm(ctx, item)
        store?.put(item)
        AppLogger.d(
            TAG,
            "Lembrete diário agendado: id=$id, horario=$hour:$minute, proximo=${item.triggerAtMillis}",
        )
    }

    override fun scheduleWeeklyNotification(
        id: Int,
        title: String,
        body: String,
        weekday: Int,
        hour: Int,
        minute: Int,
        timeZoneId: String?,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean,
        actions: List<NotificationAction>
    ) {
        val ctx = context ?: return
        val item = ScheduledNotification(
            id = id,
            title = title,
            body = body,
            kind = NotificationScheduleKind.WEEKLY,
            triggerAtMillis = 0L,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            weekday = weekday.coerceIn(1, 7),
            timeZoneId = timeZoneId?.trim()?.takeIf { it.isNotEmpty() },
            data = data,
            channelId = channelId ?: if (isCritical) CRITICAL_CHANNEL_ID else DEFAULT_CHANNEL_ID,
            isCritical = isCritical,
            actions = NotificationActionRules.distinctActions(actions),
        ).let { base ->
            // O próximo disparo sai da MESMA regra pura que o receiver e a restauração pós-boot usam
            // (inclusive o fuso gravado no agendamento) — duas contas do "quando é o próximo" é como
            // se produz um lembrete que dispara certo hoje e errado depois do primeiro disparo.
            base.copy(
                triggerAtMillis = NotificationRescheduling.nextRecurringTriggerMillis(
                    item = base,
                    nowMillis = System.currentTimeMillis(),
                ),
            )
        }

        NotificationAlarms.arm(ctx, item)
        store?.put(item)
        AppLogger.d(
            TAG,
            "Lembrete semanal agendado: id=$id, dia=$weekday, horario=$hour:$minute, " +
                "fuso=${item.timeZoneId ?: "aparelho"}, proximo=${item.triggerAtMillis}",
        )
    }

    override fun cancelNotification(id: Int) {
        val ctx = context ?: return

        NotificationAlarms.cancel(ctx, id)
        NotificationManagerCompat.from(ctx).cancel(id)
        store?.remove(id)
        AppLogger.d(TAG, "Notificação cancelada: id=$id")
    }

    /**
     * Adia um agendamento existente — o mesmo caminho do botão "Adiar" da notificação.
     *
     * Reusa o registro persistente: nada de id novo, nada de agendamento paralelo. Um id que não
     * está no registro é no-op com aviso — a lib não inventa um lembrete que ela não agendou.
     */
    override fun snoozeNotification(id: Int, minutes: Int) {
        val ctx = context ?: return
        val currentStore = store ?: return
        val item = currentStore.get(id)
        if (item == null) {
            AppLogger.w(TAG, "Adiar id=$id: agendamento não está no registro — nada a fazer")
            return
        }
        val snoozed = NotificationActionRules.applySnooze(item, minutes, System.currentTimeMillis())
        NotificationAlarms.arm(ctx, snoozed)
        currentStore.put(snoozed)
        NotificationManagerCompat.from(ctx).cancel(id)
        AppLogger.d(TAG, "Notificação id=$id adiada em $minutes min (próximo=${snoozed.nextTriggerMillis})")
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
        val currentStore = store

        currentStore?.all()?.forEach { scheduled -> NotificationAlarms.cancel(ctx, scheduled.id) }
        currentStore?.clear()

        NotificationManagerCompat.from(ctx).cancelAll()
        AppLogger.d(TAG, "Todas as notificações canceladas (bandeja + alarmes agendados)")
    }

    override fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String>,
        channelId: String?,
        actions: List<NotificationAction>
    ) {
        val ctx = context ?: return

        if (!hasPermission()) {
            AppLogger.w(TAG, "Sem permissão para exibir notificação")
            return
        }

        NotificationPresenter.show(
            ctx = ctx,
            item = ScheduledNotification(
                id = id,
                title = title,
                body = body,
                kind = NotificationScheduleKind.ONE_SHOT,
                triggerAtMillis = System.currentTimeMillis(),
                data = data,
                channelId = channelId ?: DEFAULT_CHANNEL_ID,
                actions = NotificationActionRules.distinctActions(actions),
            ),
        )
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
                // O disparo perdido chega com os MESMOS botões do disparo normal: a pessoa que
                // religou o celular 20 min depois da dose continua podendo marcar e adiar dali.
                actions = missed.actions,
            )
        }

        plan.expiredIds.forEach(currentStore::remove)

        plan.toSchedule.forEach { item ->
            NotificationAlarms.arm(ctx, item)
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

}

actual fun getNotificationScheduler(): NotificationScheduler {
    NotificationSchedulerHolder.getContext()
        ?: throw IllegalStateException("NotificationSchedulerHolder nao foi inicializado. Chame NotificationSchedulerHolder.init(context) no Application.onCreate()")
    return AndroidNotificationScheduler()
}
