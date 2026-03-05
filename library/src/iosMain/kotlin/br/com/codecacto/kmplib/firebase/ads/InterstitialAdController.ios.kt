package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.cinterop.googleads.GADFullScreenContentDelegateProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADFullScreenPresentingAdProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADInterstitialAd
import br.com.codecacto.kmplib.cinterop.googleads.GADRequest
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationManager
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.darwin.NSObject

private const val TAG = "InterstitialAd.ios"

@OptIn(ExperimentalForeignApi::class)
actual class InterstitialAdController actual constructor() {
    private var interstitialAd: GADInterstitialAd? = null
    private var isLoading = false

    actual fun load() {
        if (isLoading || interstitialAd != null) return

        val adUnitId = AdManager.getInterstitialAdUnitId()
        if (adUnitId.isEmpty()) return

        isLoading = true
        GADInterstitialAd.loadWithAdUnitID(
            adUnitID = adUnitId,
            request = GADRequest.request(),
            completionHandler = { ad, error ->
                isLoading = false
                if (error != null) {
                    interstitialAd = null
                    AppLogger.e(TAG, "Erro ao carregar interstitial: ${error.localizedDescription}")
                } else if (ad != null) {
                    interstitialAd = ad
                    AppLogger.d(TAG, "Interstitial carregado")
                }
            }
        )
    }

    actual fun show(onDismissed: () -> Unit) {
        if (!MonetizationManager.shouldShowAds.value) {
            onDismissed()
            return
        }

        val ad = interstitialAd ?: run {
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
                interstitialAd = null
                load()
                onDismissed()
            }

            override fun ad(ad: GADFullScreenPresentingAdProtocol, didFailToPresentFullScreenContentWithError: NSError) {
                AppLogger.e(TAG, "Erro ao exibir interstitial: ${didFailToPresentFullScreenContentWithError.localizedDescription}")
                interstitialAd = null
                load()
                onDismissed()
            }
        }

        ad.presentFromRootViewController(rootVC)
    }

    actual fun isLoaded(): Boolean = interstitialAd != null
}
