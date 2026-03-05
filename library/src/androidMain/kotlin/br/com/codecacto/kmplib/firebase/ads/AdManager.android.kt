package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.core.util.AppLogger
import com.google.android.gms.ads.MobileAds

private const val TAG = "AdManager.android"

internal actual fun initializeAdsPlatform() {
    val context = AdManagerHolder.getContext()
    if (context == null) {
        AppLogger.e(TAG, "Context não disponível. Chame KmpLib.init(context) antes.")
        return
    }
    MobileAds.initialize(context) {
        AppLogger.d(TAG, "AdMob SDK inicializado")
    }
}

internal actual fun createInterstitialController(): InterstitialAdController = InterstitialAdController()
internal actual fun createAppOpenController(): AppOpenAdController = AppOpenAdController()

internal actual fun getTestBannerId(): String = AdManager.TestIds.ANDROID_BANNER
internal actual fun getTestInterstitialId(): String = AdManager.TestIds.ANDROID_INTERSTITIAL
internal actual fun getTestAppOpenId(): String = AdManager.TestIds.ANDROID_APP_OPEN

internal actual fun getPlatformBannerId(ids: AdUnitIds): String = ids.android
internal actual fun getPlatformInterstitialId(ids: AdUnitIds): String = ids.android
internal actual fun getPlatformAppOpenId(ids: AdUnitIds): String = ids.android
