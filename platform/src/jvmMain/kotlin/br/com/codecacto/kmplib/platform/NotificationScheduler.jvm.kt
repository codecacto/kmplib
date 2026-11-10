package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.datetime.Instant

/**
 * Desktop (2.353.0): **sem notificação agendada da lib no PC**. O SO não agenda notificação para um
 * processo JVM fechado, e a notificação com o app aberto pertence à bandeja que o PRÓPRIO app desenha
 * (`Tray` + `TrayState.sendNotification` do Compose Desktop) — a lib não cria um segundo ícone na
 * bandeja. Por isso [hasPermission] responde `false` (quem respeita a permissão não agenda) e cada
 * chamada de agendamento deixa aviso no log, nunca finge ter agendado.
 */
internal object DesktopNotificationScheduler : NotificationScheduler {
    private const val TAG = "NotificationScheduler"
    private fun naoSuportado(op: String) = AppLogger.w(TAG, "$op não existe no desktop — use o Tray do Compose Desktop")

    override fun hasPermission(): Boolean = false
    override fun requestPermission(onResult: (Boolean) -> Unit) = onResult(false)
    override fun scheduleNotification(
        id: Int, title: String, body: String, scheduledTime: Instant, data: Map<String, String>,
        channelId: String?, isCritical: Boolean, actions: List<NotificationAction>,
    ) = naoSuportado("scheduleNotification")
    override fun scheduleDailyNotification(
        id: Int, title: String, body: String, hour: Int, minute: Int, data: Map<String, String>,
        channelId: String?, isCritical: Boolean, actions: List<NotificationAction>,
    ) = naoSuportado("scheduleDailyNotification")
    override fun scheduleWeeklyNotification(
        id: Int, title: String, body: String, weekday: Int, hour: Int, minute: Int, timeZoneId: String?,
        data: Map<String, String>, channelId: String?, isCritical: Boolean, actions: List<NotificationAction>,
    ) = naoSuportado("scheduleWeeklyNotification")
    override fun cancelNotification(id: Int) = Unit
    override fun cancelAllNotifications() = Unit
    override fun showNotificationNow(
        id: Int, title: String, body: String, data: Map<String, String>, channelId: String?,
        actions: List<NotificationAction>,
    ) = naoSuportado("showNotificationNow")
    override fun canScheduleExactAlarms(): Boolean = false
}

actual fun getNotificationScheduler(): NotificationScheduler = DesktopNotificationScheduler
