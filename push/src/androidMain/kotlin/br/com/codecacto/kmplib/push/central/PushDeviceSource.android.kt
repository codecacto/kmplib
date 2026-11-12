package br.com.codecacto.kmplib.push.central

import com.google.firebase.FirebaseApp
import com.mmk.kmpnotifier.notification.NotifierManager

actual fun platformPushDeviceSource(): PushDeviceSource = AndroidFcmDeviceSource

private object AndroidFcmDeviceSource : PushDeviceSource {
    override val platform: String = "android"
    override val provider: String = "fcm"

    override suspend fun token(): String? = runCatching {
        NotifierManager.getPushNotifier().getToken()
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    // O FCM (FirebaseMessaging.getInstance()) usa a app DEFAULT — é ela que emitiu o token.
    override fun fcmProjectId(): String? = runCatching {
        FirebaseApp.getInstance().options.projectId
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
