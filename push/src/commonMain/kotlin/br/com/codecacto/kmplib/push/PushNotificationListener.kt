package br.com.codecacto.kmplib.push

/**
 * App-level callbacks for push notification events.
 *
 * The payload is exposed as plain strings so app code does not depend on the
 * notification provider type.
 */
interface PushNotificationListener {
    fun onNewToken(token: String) = Unit

    fun onPushNotification(title: String?, body: String?) = Unit

    fun onPayloadData(data: Map<String, String>) = Unit

    fun onPushNotificationWithPayloadData(
        title: String?,
        body: String?,
        data: Map<String, String>,
    ) = Unit

    fun onNotificationClicked(data: Map<String, String>) = Unit
}

object NoOpPushNotificationListener : PushNotificationListener
