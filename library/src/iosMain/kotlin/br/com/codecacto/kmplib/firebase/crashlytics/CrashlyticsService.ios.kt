package br.com.codecacto.kmplib.firebase.crashlytics

import platform.Foundation.NSLog

/**
 * Implementação iOS do CrashlyticsService.
 *
 * Usa NSLog para logging. O Firebase Crashlytics real no iOS é configurado
 * via SDK nativo (CocoaPods/SPM) no projeto Xcode do app.
 * Erros capturados aqui serão logados no console; o SDK nativo do Firebase
 * captura crashes automaticamente.
 */
class IosCrashlyticsService : CrashlyticsService {

    override fun logMessage(message: String) {
        NSLog("Crashlytics: %@", message)
    }

    override fun setCustomKey(key: String, value: String) {
        NSLog("Crashlytics setCustomKey: %@ = %@", key, value)
    }

    override fun setUserId(userId: String) {
        NSLog("Crashlytics setUserId: %@", userId)
    }

    override fun recordException(exception: Throwable) {
        NSLog("Crashlytics recordException: %@", exception.message ?: "Unknown error")
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        NSLog("Crashlytics collection enabled: %@", enabled.toString())
    }
}

actual fun getCrashlyticsService(): CrashlyticsService = IosCrashlyticsService()
