package br.com.codecacto.kmplib.alarm

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.Foundation.NSURL
import kotlin.concurrent.AtomicInt

private const val TAG = "KmpLibAlarm"
private const val ID_PREFIX = "kmplib.alarm."

/** Estado das notificações, lido assíncrono: -1 = ainda não lido; 0 = permitido/indeciso; 1 = negado. */
private val notificationsDenied = AtomicInt(-1)

actual fun createAlarmScheduler(): AlarmScheduler = IosAlarmScheduler().also { it.refreshSettings() }

@OptIn(ExperimentalForeignApi::class)
private fun isAtLeast(major: Long): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        cValue<NSOperatingSystemVersion> {
            majorVersion = major
            minorVersion = 0
            patchVersion = 0
        },
    )

/**
 * iOS: AlarmKit pela [AlarmKitBridge] no início de ronda (iOS 26+, ponte registrada e autorizada);
 * senão notificação local **Time Sensitive** — fura o Foco e o resumo agendado (com o entitlement
 * `com.apple.developer.usernotifications.time-sensitive` no app).
 */
internal class IosAlarmScheduler : AlarmScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val supportsTimeSensitive = isAtLeast(15)
    private val supportsAlarmKit = isAtLeast(26)

    fun refreshSettings() {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            notificationsDenied.value = if (settings?.authorizationStatus == UNAuthorizationStatusDenied) 1 else 0
        }
    }

    override fun schedulePatrolStart(alarmId: Int, triggerAtMillis: Long, label: String): AlarmResult {
        val bridge = AlarmKitBridgeRegistry.bridge
        if (supportsAlarmKit && bridge != null && bridge.isAuthorized()) {
            // Um agendamento só: tira a notificação antiga do mesmo id antes de entregar ao AlarmKit.
            removeNotification(alarmId)
            if (bridge.schedule(alarmId, triggerAtMillis, label)) return AlarmResult.Scheduled
            AppLogger.w(TAG, "AlarmKit recusou o alarme $alarmId; usando notificação Time Sensitive")
        }
        return scheduleNotification(alarmId, triggerAtMillis, label, AlarmKind.PATROL_START)
    }

    override fun scheduleReminder(alarmId: Int, triggerAtMillis: Long, label: String): AlarmResult =
        scheduleNotification(alarmId, triggerAtMillis, label, AlarmKind.REMINDER)

    override fun cancel(alarmId: Int) {
        removeNotification(alarmId)
        if (supportsAlarmKit) AlarmKitBridgeRegistry.bridge?.cancel(alarmId)
    }

    override fun cancelAll() {
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ids = requests.orEmpty()
                .mapNotNull { (it as? UNNotificationRequest)?.identifier }
                .filter { it.startsWith(ID_PREFIX) }
            center.removePendingNotificationRequestsWithIdentifiers(ids)
            center.removeDeliveredNotificationsWithIdentifiers(ids)
            if (supportsAlarmKit) {
                val bridge = AlarmKitBridgeRegistry.bridge
                ids.mapNotNull { it.removePrefix(ID_PREFIX).toIntOrNull() }.forEach { bridge?.cancel(it) }
            }
        }
    }

    /** iOS não tem permissão de "alarme exato": a notificação no horário só depende de não estar negada. */
    override fun hasExactAlarmPermission(): Boolean {
        refreshSettings()
        return notificationsDenied.value != 1
    }

    override fun openPermissionSettings(): Boolean {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return false
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }

    private fun removeNotification(alarmId: Int) {
        val id = listOf(ID_PREFIX + alarmId)
        center.removePendingNotificationRequestsWithIdentifiers(id)
        center.removeDeliveredNotificationsWithIdentifiers(id)
    }

    private fun scheduleNotification(alarmId: Int, triggerAtMillis: Long, label: String, kind: AlarmKind): AlarmResult {
        refreshSettings()
        if (notificationsDenied.value == 1) return AlarmResult.PermissionDenied

        val content = UNMutableNotificationContent().apply {
            setTitle(label)
            setBody(if (kind == AlarmKind.PATROL_START) patrolBody() else reminderBody())
            setSound(UNNotificationSound.defaultSound())
            setUserInfo(mapOf<Any?, Any?>(ALARM_ID_EXTRA to alarmId, "alarmKind" to kind.name))
            if (supportsTimeSensitive) {
                setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive)
            }
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = ID_PREFIX + alarmId,
            content = content,
            trigger = calendarTrigger(triggerAtMillis),
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) AppLogger.e(TAG, "Alarme $alarmId não agendado: código ${error.code}")
        }
        return AlarmResult.Scheduled
    }

    private fun calendarTrigger(atMillis: Long): UNCalendarNotificationTrigger {
        val date = NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0)
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
            fromDate = date,
        )
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
    }

    private fun patrolBody(): String = when (languageCode()) {
        "en" -> "Time to start the patrol."
        "es" -> "Es hora de iniciar la ronda."
        else -> "Hora de iniciar a ronda."
    }

    private fun reminderBody(): String = when (languageCode()) {
        "en" -> "Patrol reminder."
        "es" -> "Recordatorio de la ronda."
        else -> "Lembrete da ronda."
    }

    private fun languageCode(): String =
        br.com.codecacto.kmplib.core.locale.appLanguageTag().substringBefore('-')
}
