package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.cinterop.googleads.GADMobileAds
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi

private const val TAG = "AdManager.ios"

@OptIn(ExperimentalForeignApi::class)
internal actual fun initializeAdsPlatform() {
    GADMobileAds.sharedInstance().startWithCompletionHandler { _ ->
        AppLogger.d(TAG, "AdMob SDK inicializado (iOS)")
    }
}

internal actual fun createInterstitialController(): InterstitialAdController = InterstitialAdController()
internal actual fun createAppOpenController(): AppOpenAdController = AppOpenAdController()

internal actual fun getTestBannerId(): String = AdManager.TestIds.IOS_BANNER
internal actual fun getTestInterstitialId(): String = AdManager.TestIds.IOS_INTERSTITIAL
internal actual fun getTestAppOpenId(): String = AdManager.TestIds.IOS_APP_OPEN

internal actual fun getPlatformBannerId(ids: AdUnitIds): String = ids.ios
internal actual fun getPlatformInterstitialId(ids: AdUnitIds): String = ids.ios
internal actual fun getPlatformAppOpenId(ids: AdUnitIds): String = ids.ios
