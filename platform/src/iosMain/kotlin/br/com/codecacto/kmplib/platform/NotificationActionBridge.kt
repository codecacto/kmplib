@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.native.ObjCName
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationDismissActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBadge
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Recebe no iOS o toque nos **botões** de uma notificação local e devolve a resposta ao caminho
 * comum da lib ([NotificationActions] para ação de app, adiamento executado internamente).
 *
 * ### Por que existe um bridge, e não só um delegate instalado pela lib
 * O `UNUserNotificationCenter` aceita **um único delegate por processo**. Num app que usa o módulo
 * `push` da kmplib, esse delegate já é o `AppDelegate` em Swift (ver `ApplePushBridge`) — instalar
 * outro por baixo o derrubaria e o app pararia de receber push. Então a lib segue o mesmo padrão do
 * push: **quem é delegate chama o bridge**.
 *
 * ```swift
 * // AppDelegate.swift
 * func userNotificationCenter(_ center: UNUserNotificationCenter,
 *                             didReceive response: UNNotificationResponse,
 *                             withCompletionHandler completionHandler: @escaping () -> Void) {
 *     let handled = NotificationActionBridge.shared.onNotificationResponse(
 *         requestIdentifier: response.notification.request.identifier,
 *         actionIdentifier: response.actionIdentifier
 *     )
 *     if !handled {
 *         // não era uma ação da kmplib — trate o seu push aqui
 *         ApplePushBridge.shared.onRemoteNotification(
 *             userInfo: response.notification.request.content.userInfo as? [String: Any] ?? [:],
 *             wasTapped: true
 *         )
 *     }
 *     completionHandler()
 * }
 * ```
 *
 * ### App que NÃO usa push
 * Não precisa de Swift nenhum: chame [installNotificationActionDelegate] no bootstrap. Ele instala o
 * delegate da lib **só se ainda não houver um** — nunca substitui o do app.
 */
@ObjCName("NotificationActionBridge")
object NotificationActionBridge {

    private const val TAG = "NotificationAction"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Trata a resposta a uma notificação.
     *
     * @param requestIdentifier `response.notification.request.identifier`.
     * @param actionIdentifier `response.actionIdentifier`.
     * @return `true` se a resposta era de uma notificação agendada pela kmplib (e foi tratada aqui);
     *   `false` quando não é assunto da lib — aí o app segue com o próprio tratamento (push, deep
     *   link), sem duplicar nada.
     */
    fun onNotificationResponse(requestIdentifier: String, actionIdentifier: String): Boolean {
        val notificationId = IosNotificationRequests.notificationIdOf(requestIdentifier) ?: return false
        val scheduler = IosNotificationScheduler()
        val item = scheduler.scheduledNotifications().firstOrNull { it.id == notificationId }

        // Deslizar para dispensar não é ação: o usuário mandou a notificação embora e pronto.
        if (actionIdentifier == UNNotificationDismissActionIdentifier) return item != null

        // Toque no CORPO: comportamento histórico (abre o app). Só vira evento se o app declarou
        // interesse — a lib não inventa uma ação que ninguém pediu.
        if (actionIdentifier == UNNotificationDefaultActionIdentifier) {
            if (item == null) return false
            dispatch(notificationId, NotificationActionEvent.DEFAULT_ACTION_ID, item.data)
            return true
        }

        if (item == null) {
            AppLogger.w(TAG, "Ação '$actionIdentifier' sem agendamento conhecido (id=$notificationId)")
            return false
        }

        val action = NotificationActionRules.actionOf(item, actionIdentifier)
        if (action != null && action.isSnooze) {
            scheduler.snoozeNotification(notificationId, action.snoozeMinutes)
            return true
        }

        if (action == null) {
            AppLogger.w(TAG, "Ação '$actionIdentifier' não está declarada no agendamento id=$notificationId")
        }
        dispatch(notificationId, actionIdentifier, item.data)
        return true
    }

    private fun dispatch(notificationId: Int, actionId: String, data: Map<String, String>) {
        scope.launch {
            NotificationActions.dispatch(
                NotificationActionEvent(
                    notificationId = notificationId,
                    actionId = actionId,
                    data = data,
                ),
            )
        }
    }
}

/**
 * Delegate mínimo da lib, para app que **não** usa o módulo `push` (e portanto não tem delegate
 * próprio em Swift).
 *
 * Encaminha a resposta ao [NotificationActionBridge] e exibe a notificação também com o app em
 * primeiro plano — sem isso, o iOS **não mostra** notificação local enquanto o app está aberto, e a
 * dose que dispara com o app na tela passaria despercebida.
 */
class KmpLibNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        runCatching {
            NotificationActionBridge.onNotificationResponse(
                requestIdentifier = didReceiveNotificationResponse.notification.request.identifier,
                actionIdentifier = didReceiveNotificationResponse.actionIdentifier,
            )
        }
        withCompletionHandler()
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound or
                UNNotificationPresentationOptionBadge,
        )
    }
}

/**
 * Instala o delegate da lib **se ainda não houver um**, e devolve `true` quando instalou.
 *
 * Chame no bootstrap do iOS (o mesmo ponto onde o app chama `NotificationActions.setHandler`).
 * Quando o app já tem delegate próprio (caso de quem usa push), esta função **não faz nada** e o
 * caminho correto é chamar o [NotificationActionBridge] de dentro do delegate Swift.
 */
fun installNotificationActionDelegate(): Boolean {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    if (center.delegate != null) {
        AppLogger.d(
            "NotificationAction",
            "Delegate de notificação já definido pelo app — chame NotificationActionBridge de lá",
        )
        return false
    }
    center.delegate = KmpLibNotificationDelegate()
    return true
}
