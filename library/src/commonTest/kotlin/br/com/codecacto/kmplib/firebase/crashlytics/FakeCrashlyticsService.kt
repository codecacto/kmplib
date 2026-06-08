package br.com.codecacto.kmplib.firebase.crashlytics

/**
 * Fake de [CrashlyticsService] para uso em testes. Registra todas as chamadas
 * para asserts.
 */
class FakeCrashlyticsService : CrashlyticsService {

    val messages = mutableListOf<String>()
    val customKeys = mutableMapOf<String, String>()
    val recordedExceptions = mutableListOf<Throwable>()
    // Renomeado de `userId` para evitar clash de assinatura JVM com
    // `override fun setUserId(String)` (o setter sintético `setUserId(String?)`
    // do `var userId` colidia, quebrando a compilação do testDebugUnitTest).
    var capturedUserId: String? = null
    var collectionEnabled: Boolean = true

    override fun logMessage(message: String) {
        messages.add(message)
    }

    override fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
    }

    override fun setUserId(userId: String) {
        this.capturedUserId = userId
    }

    override fun recordException(exception: Throwable) {
        recordedExceptions.add(exception)
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }
}
