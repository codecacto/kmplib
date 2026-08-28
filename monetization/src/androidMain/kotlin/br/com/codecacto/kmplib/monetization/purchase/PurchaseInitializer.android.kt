package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.core.util.AppLogger
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure

internal actual object PurchaseInitializer {

    private const val TAG = "PurchaseInitializer.android"

    actual fun initialize(config: PurchaseConfig, userId: String?) {
        Purchases.logLevel = if (config.debugMode) LogLevel.DEBUG else LogLevel.WARN

        Purchases.configure(apiKey = config.androidApiKey) {
            appUserId = userId
        }

        AppLogger.d(TAG, "RevenueCat inicializado (Android, debug=${config.debugMode})")
    }
}

internal actual fun currentStore(): String = "play_store"
