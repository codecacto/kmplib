package br.com.codecacto.kmplib.firebase.crashlytics

import android.content.pm.ApplicationInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics

class AndroidCrashlyticsService : CrashlyticsService {
    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    private val isDebug: Boolean by lazy {
        val context = CrashlyticsHolder.getContext()
        context?.applicationInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun logMessage(message: String) {
        crashlytics.log(message)
        if (isDebug) {
            println("Crashlytics: $message")
        }
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    override fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }

    override fun recordException(exception: Throwable) {
        crashlytics.recordException(exception)
        if (isDebug) {
            println("Crashlytics Exception: ${exception.message}")
            exception.printStackTrace()
        }
    }

    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
    }
}

actual fun getCrashlyticsService(): CrashlyticsService = AndroidCrashlyticsService()
