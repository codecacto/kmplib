package br.com.codecacto.kmplib.firebase.crashlytics

interface CrashlyticsService {
    fun logMessage(message: String)
    fun setCustomKey(key: String, value: String)
    fun setUserId(userId: String)
    fun recordException(exception: Throwable)
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
}

expect fun getCrashlyticsService(): CrashlyticsService
