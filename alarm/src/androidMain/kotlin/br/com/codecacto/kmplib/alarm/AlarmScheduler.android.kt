package br.com.codecacto.kmplib.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationManagerCompat
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger

private const val TAG = "KmpLibAlarm"

/**
 * Ícone da notificação do alarme (monocromático, barra de status). Sem ele, o ícone de despertador
 * do sistema.
 */
data class AlarmNotificationConfig(@DrawableRes val smallIconRes: Int? = null)

internal object AlarmHolder {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    var config: AlarmNotificationConfig = AlarmNotificationConfig()
        private set

    fun init(context: Context, config: AlarmNotificationConfig) {
        appContext = context.applicationContext
        this.config = config
    }

    /** O contexto registrado aqui ou, na falta, o do `initKmpLibCore`. */
    fun context(): Context = appContext ?: AndroidAppContext.get()
        ?: error("kmplib-alarm: chame initKmpLibAlarm(context) (ou initKmpLibCore) no Application.onCreate()")
}

/** Inicializa o `kmplib-alarm`. Chame no `Application.onCreate()`. */
fun initKmpLibAlarm(context: Context, config: AlarmNotificationConfig = AlarmNotificationConfig()) {
    AlarmHolder.init(context, config)
}

actual fun createAlarmScheduler(): AlarmScheduler = AndroidAlarmScheduler(AlarmHolder.context())

internal class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedulePatrolStart(alarmId: Int, triggerAtMillis: Long, label: String): AlarmResult =
        schedule(AlarmRecord(alarmId, AlarmKind.PATROL_START, triggerAtMillis, label))

    override fun scheduleReminder(alarmId: Int, triggerAtMillis: Long, label: String): AlarmResult =
        schedule(AlarmRecord(alarmId, AlarmKind.REMINDER, triggerAtMillis, label))

    override fun cancel(alarmId: Int) {
        alarmManager.cancel(firePendingIntent(context, alarmId, null))
        AlarmStore.remove(context, alarmId)
        NotificationManagerCompat.from(context).cancel(AlarmNotifications.NOTIFICATION_TAG, alarmId)
    }

    override fun cancelAll() {
        AlarmStore.all(context).forEach { cancel(it.id) }
    }

    override fun hasExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun openPermissionSettings(): Boolean {
        val pkg = Uri.parse("package:${context.packageName}")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            AppLogger.w(TAG, "Tela de alarmes indisponível")
            false
        }
    }

    private fun schedule(record: AlarmRecord): AlarmResult {
        val plan = planAndroidAlarm(
            kind = record.kind,
            canScheduleExact = hasExactAlarmPermission(),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
        AlarmStore.put(context, record)
        arm(context, alarmManager, record, plan.strategy)
        return plan.result
    }

    internal companion object {
        /** Programa [record] no AlarmManager com a [strategy]. */
        fun arm(context: Context, alarmManager: AlarmManager, record: AlarmRecord, strategy: AndroidAlarmStrategy) {
            val fire = firePendingIntent(context, record.id, record)
            try {
                when (strategy) {
                    AndroidAlarmStrategy.ALARM_CLOCK -> {
                        val show = AlarmNotifications.launchPendingIntent(context, record.id)
                        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(record.triggerAtMillis, show), fire)
                    }
                    AndroidAlarmStrategy.EXACT_ALLOW_WHILE_IDLE ->
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.triggerAtMillis, fire)
                    AndroidAlarmStrategy.WINDOW ->
                        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, record.triggerAtMillis, FALLBACK_WINDOW_MILLIS, fire)
                }
            } catch (e: SecurityException) {
                // A permissão de alarme exato foi revogada entre a leitura e o agendamento: janela.
                AppLogger.w(TAG, "Alarme exato recusado; usando janela")
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, record.triggerAtMillis, FALLBACK_WINDOW_MILLIS, fire)
            }
        }

        fun strategyFor(context: Context, alarmManager: AlarmManager, kind: AlarmKind): AndroidAlarmStrategy {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            return planAndroidAlarm(kind, exact, notificationsEnabled = true).strategy
        }

        /**
         * O PendingIntent do disparo. A identidade é o `requestCode` = id (os extras não contam para
         * a igualdade) — [record] `null` serve para cancelar.
         */
        fun firePendingIntent(context: Context, alarmId: Int, record: AlarmRecord?): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
            if (record != null) {
                intent.putExtra(EXTRA_ID, record.id)
                    .putExtra(EXTRA_KIND, record.kind.name)
                    .putExtra(EXTRA_LABEL, record.label)
            }
            return PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        const val ACTION_FIRE = "br.com.codecacto.kmplib.alarm.FIRE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_KIND = "alarm_kind"
        const val EXTRA_LABEL = "alarm_label"
    }
}

/** Canais e notificação do disparo. */
internal object AlarmNotifications {
    const val NOTIFICATION_TAG = "kmplib_alarm"
    const val CHANNEL_PATROL = "kmplib_alarm_patrol"
    const val CHANNEL_REMINDER = "kmplib_alarm_reminder"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val patrol = NotificationChannel(
            CHANNEL_PATROL,
            context.getString(R.string.kmplib_alarm_channel_patrol),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            // Som de DESPERTADOR, no volume de alarme: é ele que acorda o vigia.
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.kmplib_alarm_channel_reminder),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannels(listOf(patrol, reminder))
    }

    /** Abre o app no toque, com o id do alarme em [ALARM_ID_EXTRA]. */
    fun launchPendingIntent(context: Context, alarmId: Int): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(ALARM_ID_EXTRA, alarmId)
        return PendingIntent.getActivity(
            context,
            alarmId,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
