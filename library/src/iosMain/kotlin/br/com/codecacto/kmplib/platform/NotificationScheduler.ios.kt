package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlin.concurrent.AtomicInt

@Suppress("UNCHECKED_CAST")
class IosNotificationScheduler : NotificationScheduler {

    companion object {
        private const val TAG = "NotificationScheduler"
    }

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
    private val permissionGranted = AtomicInt(0) // 0 = unknown, 1 = granted, -1 = denied

    /**
     * Verifica se tem permissão para notificações.
     *
     * NOTA: Na primeira chamada, retorna o valor em cache ou `false` como fallback
     * seguro, pois a verificação real é assíncrona. Para garantir um resultado
     * preciso, chame `requestPermission()` primeiro ou use `checkPermission(callback)`.
     */
    override fun hasPermission(): Boolean {
        // Retorna cache se disponível
        if (permissionGranted.value != 0) {
            return permissionGranted.value == 1
        }

        // Inicia verificação assíncrona para atualizar cache
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            permissionGranted.value = if (granted) 1 else -1
        }

        // Retorna false como fallback seguro (melhor pedir permissão do que assumir que tem)
        return false
    }

    /**
     * Verifica permissão de forma assíncrona (recomendado).
     */
    fun checkPermission(onResult: (Boolean) -> Unit) {
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            permissionGranted.value = if (granted) 1 else -1
            onResult(granted)
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        val options = UNAuthorizationOptionAlert or
                UNAuthorizationOptionSound or
                UNAuthorizationOptionBadge

        notificationCenter.requestAuthorizationWithOptions(options) { granted, error ->
            permissionGranted.value = if (granted) 1 else -1
            if (error != null) {
                AppLogger.e(TAG, "Erro ao solicitar permissão: ${error.localizedDescription}")
            }
            onResult(granted)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun scheduleNotification(
        id: Int,
        title: String,
        body: String,
        scheduledTime: Instant,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(
                if (isCritical) UNNotificationSound.defaultCriticalSound()
                else UNNotificationSound.defaultSound()
            )
            if (data.isNotEmpty()) {
                setUserInfo(data.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }

        // Converter Instant para DateComponents
        val localDateTime = scheduledTime.toLocalDateTime(TimeZone.currentSystemDefault())
        val dateComponents = NSDateComponents().apply {
            year = localDateTime.year.toLong()
            month = localDateTime.monthNumber.toLong()
            day = localDateTime.dayOfMonth.toLong()
            hour = localDateTime.hour.toLong()
            minute = localDateTime.minute.toLong()
            second = localDateTime.second.toLong()
        }

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents,
            repeats = false
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id.toString(),
            content = content,
            trigger = trigger
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(TAG, "Erro ao agendar notificação: ${error.localizedDescription}")
            } else {
                AppLogger.d(TAG, "Notificação agendada: id=$id")
            }
        }
    }

    override fun cancelNotification(id: Int) {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(id.toString()))
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(id.toString()))
        AppLogger.d(TAG, "Notificação cancelada: id=$id")
    }

    override fun cancelAllNotifications() {
        notificationCenter.removeAllPendingNotificationRequests()
        notificationCenter.removeAllDeliveredNotifications()
        AppLogger.d(TAG, "Todas as notificações canceladas")
    }

    override fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String>,
        channelId: String?
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
            if (data.isNotEmpty()) {
                setUserInfo(data.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id.toString(),
            content = content,
            trigger = null // null = imediato
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(TAG, "Erro ao exibir notificação: ${error.localizedDescription}")
            } else {
                AppLogger.d(TAG, "Notificação exibida: id=$id")
            }
        }
    }
}

actual fun getNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()
