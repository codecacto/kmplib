package br.com.codecacto.kmplib.push

import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.PayloadData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * KMPNotifier-backed push service.
 *
 * Apps remain responsible for calling NotifierManager.initialize(...) in each
 * platform entry point and for handling token sync, topics and deep links.
 */
class KmpPushNotificationService(
    private val listener: PushNotificationListener = NoOpPushNotificationListener,
    emitCurrentTokenOnStart: Boolean = false,
) : PushNotificationService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        NotifierManager.addListener(object : NotifierManager.Listener {
            override fun onNewToken(token: String) {
                listener.onNewToken(token)
            }

            override fun onPushNotification(title: String?, body: String?) {
                listener.onPushNotification(title, body)
            }

            override fun onPayloadData(data: PayloadData) {
                listener.onPayloadData(data.toStringMap())
            }

            override fun onPushNotificationWithPayloadData(
                title: String?,
                body: String?,
                data: PayloadData,
            ) {
                listener.onPushNotificationWithPayloadData(title, body, data.toStringMap())
            }

            override fun onNotificationClicked(data: PayloadData) {
                listener.onNotificationClicked(data.toStringMap())
            }
        })

        if (emitCurrentTokenOnStart) {
            scope.launch {
                getToken()?.let(listener::onNewToken)
            }
        }
    }

    override suspend fun getToken(): String? = runCatching {
        NotifierManager.getPushNotifier().getToken()
    }.getOrNull()

    override suspend fun deleteToken(): Result<Unit> = runCatching {
        NotifierManager.getPushNotifier().deleteMyToken()
    }

    override suspend fun subscribeToTopic(topic: String): Result<Unit> = runCatching {
        NotifierManager.getPushNotifier().subscribeToTopic(topic)
    }

    override suspend fun unsubscribeFromTopic(topic: String): Result<Unit> = runCatching {
        NotifierManager.getPushNotifier().unSubscribeFromTopic(topic)
    }
}

private fun PayloadData.toStringMap(): Map<String, String> =
    entries.associate { (key, value) -> key to value?.toString().orEmpty() }
