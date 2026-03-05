package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val TAG = "InterstitialAd.android"

actual class InterstitialAdController actual constructor() {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    actual fun load() {
        if (isLoading || interstitialAd != null) return

        val context = AdManagerHolder.getContext() ?: run {
            AppLogger.e(TAG, "Context não disponível para carregar interstitial")
            return
        }

        val adUnitId = AdManager.getInterstitialAdUnitId()
        if (adUnitId.isEmpty()) return

        isLoading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                    AppLogger.d(TAG, "Interstitial carregado")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                    AppLogger.e(TAG, "Erro ao carregar interstitial: ${error.message}")
                }
            }
        )
    }

    actual fun show(onDismissed: () -> Unit) {
        if (!MonetizationManager.shouldShowAds.value) {
            onDismissed()
            return
        }

        val ad = interstitialAd
        val activity = AdManagerHolder.getActivity()

        if (ad == null || activity == null) {
            onDismissed()
            load()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                load()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                AppLogger.e(TAG, "Erro ao exibir interstitial: ${error.message}")
                interstitialAd = null
                load()
                onDismissed()
            }
        }

        ad.show(activity)
    }

    actual fun isLoaded(): Boolean = interstitialAd != null
}
