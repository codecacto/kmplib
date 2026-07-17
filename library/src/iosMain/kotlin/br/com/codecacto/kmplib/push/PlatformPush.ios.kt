package br.com.codecacto.kmplib.push

import br.com.codecacto.kmplib.core.util.AppLogger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS: push via **APNs-direto** (sem FCM/`GoogleService-Info.plist`). O [ApplePushBridge] alimenta
 * o [PushNotificationListener] a partir do `AppDelegate`.
 */
actual fun createPushNotificationService(
    listener: PushNotificationListener,
): PushNotificationService = ApplePushNotificationService(listener)

/** iOS: notificação local via `UNUserNotificationCenter` (fluxo oficial Apple). */
actual fun createLocalPushNotifier(): LocalPushNotifier = AppleLocalPushNotifier()

/**
 * Implementação iOS do [PushNotificationService] sobre o [ApplePushBridge].
 *
 * - `getToken()` → device token APNs (hex) em cache no bridge.
 * - `deleteToken()` → limpa o token local (com APNs-direto, "esquecer" o token é responsabilidade
 *   do backend; o cliente só descarta o cache — sinalize a remoção ao servidor você mesmo).
 * - `subscribeToTopic`/`unsubscribeFromTopic` → **no-op** que sucede: APNs não tem tópicos do lado
 *   do cliente (é conceito do FCM). No caminho own-stack o roteamento por tópico é feito no
 *   **backend**, associando token ↔ tópico; falhar aqui quebraria código de app compartilhado.
 */
class ApplePushNotificationService(
    listener: PushNotificationListener = NoOpPushNotificationListener,
) : PushNotificationService {

    init {
        ApplePushBridge.setListener(listener)
    }

    override suspend fun getToken(): String? = ApplePushBridge.currentToken()

    override suspend fun deleteToken(): Result<Unit> = runCatching { ApplePushBridge.clearToken() }

    override suspend fun subscribeToTopic(topic: String): Result<Unit> {
        AppLogger.w(
            "ApplePush",
            "subscribeToTopic('$topic') é no-op no APNs-direto — o roteamento por tópico é do backend (token↔tópico).",
        )
        return Result.success(Unit)
    }

    override suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        AppLogger.w(
            "ApplePush",
            "unsubscribeFromTopic('$topic') é no-op no APNs-direto — o roteamento por tópico é do backend (token↔tópico).",
        )
        return Result.success(Unit)
    }
}

private class AppleLocalPushNotifier : LocalPushNotifier {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun notify(notification: LocalPushNotification): Int {
        val content = UNMutableNotificationContent().apply {
            notification.title?.let { setTitle(it) }
            notification.body?.let { setBody(it) }
            setSound(UNNotificationSound.defaultSound())
            if (notification.payloadData.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST")
                setUserInfo(notification.payloadData.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = notification.id.toString(),
            content = content,
            trigger = null, // null = entrega imediata
        )
        center.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e("ApplePush", "Erro ao exibir notificação local: ${error.localizedDescription}")
            }
        }
        return notification.id
    }

    override fun remove(id: Int) {
        center.removeDeliveredNotificationsWithIdentifiers(listOf(id.toString()))
    }

    override fun removeAll() {
        center.removeAllDeliveredNotifications()
    }
}
