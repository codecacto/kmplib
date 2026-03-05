package br.com.codecacto.kmplib.firebase.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import br.com.codecacto.kmplib.cinterop.googleads.GADBannerView
import br.com.codecacto.kmplib.cinterop.googleads.GADAdSizeBanner
import br.com.codecacto.kmplib.cinterop.googleads.GADRequest
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun BannerAdPlatform(modifier: Modifier) {
    val adUnitId = AdManager.getBannerAdUnitId()
    if (adUnitId.isEmpty()) return

    UIKitView(
        modifier = modifier,
        factory = {
            val bannerView = GADBannerView(adSize = GADAdSizeBanner)
            bannerView.adUnitID = adUnitId
            bannerView.rootViewController = getRootViewController()

            bannerView.loadRequest(GADRequest.request())
            bannerView
        }
    )
}
