@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.push

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlin.native.ObjCName

/**
 * Bridge **APNs-direto** do iOS: recebe do `AppDelegate` (Swift) o device token e as notificações,
 * e roteia tudo para o [PushNotificationListener] do app — **sem FCM e sem `GoogleService-Info.plist`**.
 *
 * No piloto own-stack o token entregue ao backend é o **device token APNs (hex)** — o KMPNotifier
 * sai do caminho no iOS. O Kotlin define o contrato; o Swift alimenta este objeto a partir dos
 * callbacks oficiais da Apple (`UNUserNotificationCenter` + `registerForRemoteNotifications`).
 *
 * ### Passo a passo no `AppDelegate` (Swift)
 * ```swift
 * import KmpLib
 *
 * // 1) Registrar para push (peça permissão antes)
 * UNUserNotificationCenter.current().delegate = self
 * UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
 *     guard granted else { return }
 *     DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
 * }
 *
 * // 2) Device token → hex → bridge
 * func application(_ app: UIApplication,
 *                  didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
 *     let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
 *     ApplePushBridge.shared.onApnsToken(hexToken: hex)   // → PushNotificationListener.onNewToken
 * }
 * func application(_ app: UIApplication,
 *                  didFailToRegisterForRemoteNotificationsWithError error: Error) {
 *     ApplePushBridge.shared.onApnsRegistrationFailed(message: error.localizedDescription)
 * }
 *
 * // 3a) FOREGROUND: notificação chega com o app aberto
 * func userNotificationCenter(_ center: UNUserNotificationCenter,
 *                             willPresent notification: UNNotification,
 *                             withCompletionHandler completionHandler:
 *                               @escaping (UNNotificationPresentationOptions) -> Void) {
 *     let info = flatten(notification.request.content.userInfo)   // [String: String]
 *     ApplePushBridge.shared.onRemoteNotification(userInfo: info, wasTapped: false)
 *     completionHandler([.banner, .sound])                        // ou entregue via LocalPushNotifier
 * }
 *
 * // 3b) BACKGROUND-TAP e 3c) COLD-START-TAP: usuário toca a notificação (mesmo callback)
 * func userNotificationCenter(_ center: UNUserNotificationCenter,
 *                             didReceive response: UNNotificationResponse,
 *                             withCompletionHandler completionHandler: @escaping () -> Void) {
 *     let info = flatten(response.notification.request.content.userInfo)
 *     ApplePushBridge.shared.onRemoteNotification(userInfo: info, wasTapped: true)  // → onNotificationClicked (deep link)
 *     completionHandler()
 * }
 * ```
 * `flatten(_:)` deve converter o `userInfo` (que pode aninhar `aps.alert.title/body`) em
 * `[String: String]`; as chaves `title`/`body`/`aps.alert.title`/`aps.alert.body` são reconhecidas
 * por [PushPayload]. O cold-start-tap cai no MESMO `didReceive response` (chamado após o launch),
 * então os 3 casos preservam o deep link sem código extra.
 */
@ObjCName("ApplePushBridge")
object ApplePushBridge {
    private var listener: PushNotificationListener = NoOpPushNotificationListener
    private var cachedToken: String? = null

    /** Registrado pela lib ([ApplePushNotificationService]) — não exposto ao Swift. */
    internal fun setListener(listener: PushNotificationListener) {
        this.listener = listener
    }

    /** Limpa o token em cache (usado por `deleteToken()`). */
    internal fun clearToken() {
        cachedToken = null
    }

    /** Device token APNs (hex) em cache; `null` até o `didRegisterForRemoteNotifications`. */
    fun currentToken(): String? = cachedToken

    /** `didRegisterForRemoteNotificationsWithDeviceToken` → token hex. */
    fun onApnsToken(hexToken: String) {
        cachedToken = hexToken
        listener.onNewToken(hexToken)
    }

    /** `didFailToRegisterForRemoteNotificationsWithError`. */
    fun onApnsRegistrationFailed(message: String) {
        AppLogger.w("ApplePushBridge", "Falha ao registrar no APNs: $message")
    }

    /**
     * Notificação remota recebida. `wasTapped = false` (foreground) →
     * [PushNotificationListener.onPushNotificationWithPayloadData]; `wasTapped = true`
     * (background/cold-start tap) → [PushNotificationListener.onNotificationClicked] (deep link).
     */
    fun onRemoteNotification(userInfo: Map<String, String>, wasTapped: Boolean) {
        PushEventRouter.dispatch(listener, userInfo, wasTapped)
    }
}
