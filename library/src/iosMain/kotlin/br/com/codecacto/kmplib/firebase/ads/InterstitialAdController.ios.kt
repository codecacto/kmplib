package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.cinterop.googleads.GADFullScreenContentDelegateProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADFullScreenPresentingAdProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADInterstitialAd
import br.com.codecacto.kmplib.cinterop.googleads.GADRequest
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationManager
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
            override fun adDidRecordImpression(ad: GADFullScreenPresentingAdProtocol) {
                AdStats.recordImpression(AdProviderTag.ADMOB, StatAdFormat.INTERSTITIAL)
            }

            override fun adDidRecordClick(ad: GADFullScreenPresentingAdProtocol) {
                AdStats.recordClick(AdProviderTag.ADMOB, StatAdFormat.INTERSTITIAL)
            }

            override fun adDidDismissFullScreenContent(ad: GADFullScreenPresentingAdProtocol) {
                interstitialAd = null
                load()
                dispatch_async(dispatch_get_main_queue()) {
                    onDismissed()
                }
            }

            override fun ad(ad: GADFullScreenPresentingAdProtocol, didFailToPresentFullScreenContentWithError: NSError) {
                AppLogger.e(TAG, "Erro ao exibir interstitial: ${didFailToPresentFullScreenContentWithError.localizedDescription}")
                interstitialAd = null
                load()
                dispatch_async(dispatch_get_main_queue()) {
                    onDismissed()
                }
            }
        }

        ad.presentFromRootViewController(rootVC)
    }

    actual fun isLoaded(): Boolean = interstitialAd != null
}
