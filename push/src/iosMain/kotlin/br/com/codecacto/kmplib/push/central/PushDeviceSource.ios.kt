package br.com.codecacto.kmplib.push.central

import br.com.codecacto.kmplib.push.ApplePushBridge

actual fun platformPushDeviceSource(): PushDeviceSource = AppleApnsDeviceSource

private object AppleApnsDeviceSource : PushDeviceSource {
    override val platform: String = "ios"
    override val provider: String = "apns"

    override suspend fun token(): String? = ApplePushBridge.currentToken()?.trim()?.takeIf { it.isNotEmpty() }

    override fun fcmProjectId(): String? = null
}
