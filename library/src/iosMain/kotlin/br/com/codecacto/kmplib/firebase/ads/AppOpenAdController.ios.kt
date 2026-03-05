package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.cinterop.googleads.GADAppOpenAd
import br.com.codecacto.kmplib.cinterop.googleads.GADFullScreenContentDelegateProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADFullScreenPresentingAdProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADRequest
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.darwin.NSObject

private const val TAG = "AppOpenAd.ios"

@OptIn(ExperimentalForeignApi::class)
actual class AppOpenAdController actual constructor() {
    private var appOpenAd: GADAppOpenAd? = null
    private var isLoading = false

    actual fun load() {
        if (isLoading || appOpenAd != null) return

        val adUnitId = AdManager.getAppOpenAdUnitId()
        if (adUnitId.isEmpty()) return

        isLoading = true
        GADAppOpenAd.loadWithAdUnitID(
            adUnitID = adUnitId,
            request = GADRequest.request(),
            completionHandler = { ad, error ->
                isLoading = false
                if (error != null) {
                    appOpenAd = null
                    AppLogger.e(TAG, "Erro ao carregar app open ad: ${error.localizedDescription}")
                } else if (ad != null) {
                    appOpenAd = ad
                    AppLogger.d(TAG, "App open ad carregado")
                }
            }
        )
    }

    actual fun show(onDismissed: () -> Unit) {
        if (!AdManager.adsEnabled.value) {
            onDismissed()
            return
        }

        val ad = appOpenAd ?: run {
            onDismissed()
            load()
            return
        }

        val rootVC = getRootViewController() ?: run {
            onDismissed()
            load()
            return
        }

        ad.fullScreenContentDelegate = object : NSObject(), GADFullScreenContentDelegateProtocol {
            override fun adDidDismissFullScreenContent(ad: GADFullScreenPresentingAdProtocol) {
                appOpenAd = null
                load()
                onDismissed()
            }

            override fun ad(ad: GADFullScreenPresentingAdProtocol, didFailToPresentFullScreenContentWithError: NSError) {
                AppLogger.e(TAG, "Erro ao exibir app open ad: ${didFailToPresentFullScreenContentWithError.localizedDescription}")
                appOpenAd = null
                load()
                onDismissed()
            }
        }

        ad.presentFromRootViewController(rootVC)
    }

    actual fun isLoaded(): Boolean = appOpenAd != null
}
