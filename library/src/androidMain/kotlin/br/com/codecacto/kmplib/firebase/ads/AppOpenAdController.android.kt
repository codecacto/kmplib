package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.MonetizationManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

private const val TAG = "AppOpenAd.android"

actual class AppOpenAdController actual constructor() {
    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false

    actual fun load() {
        if (isLoading || appOpenAd != null) return

        val context = AdManagerHolder.getContext() ?: run {
            AppLogger.e(TAG, "Context não disponível para carregar app open ad")
            return
        }

        val adUnitId = AdManager.getAppOpenAdUnitId()
        if (adUnitId.isEmpty()) return

        isLoading = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoading = false
                    appOpenAd = ad
                    AppLogger.d(TAG, "App open ad carregado")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    appOpenAd = null
                    AppLogger.e(TAG, "Erro ao carregar app open ad: ${error.message}")
                }
            }
        )
    }

    actual fun show(onDismissed: () -> Unit) {
        if (!MonetizationManager.shouldShowAds.value) {
            onDismissed()
            return
        }

        val ad = appOpenAd
        val activity = AdManagerHolder.getActivity()

        if (ad == null || activity == null) {
            onDismissed()
            load()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                load()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                AppLogger.e(TAG, "Erro ao exibir app open ad: ${error.message}")
                appOpenAd = null
                load()
                onDismissed()
            }
        }

        ad.show(activity)
    }

    actual fun isLoaded(): Boolean = appOpenAd != null
}
