package br.com.codecacto.kmplib.push

import com.mmk.kmpnotifier.notification.NotifierManager

/**
 * Android: o push segue via **KMPNotifier sobre FCM**. Requer `initFirebaseForPush(...)` +
 * `NotifierManager.initialize(...)` já chamados no entry point do app.
 */
actual fun createPushNotificationService(
    listener: PushNotificationListener,
): PushNotificationService = KmpPushNotificationService(listener)

/** Android: notificação local delega ao `LocalNotifier` do KMPNotifier (canais já configurados). */
actual fun createLocalPushNotifier(): LocalPushNotifier = KmpNotifierLocalPushNotifier()

private class KmpNotifierLocalPushNotifier : LocalPushNotifier {
    override fun notify(notification: LocalPushNotification): Int {
        NotifierManager.getLocalNotifier().notify(
            id = notification.id,
            title = notification.title.orEmpty(),
            body = notification.body.orEmpty(),
            payloadData = notification.payloadData,
        )
        return notification.id
    }

    override fun remove(id: Int) {
        NotifierManager.getLocalNotifier().remove(id)
    }

    override fun removeAll() {
        NotifierManager.getLocalNotifier().removeAll()
    }
}
