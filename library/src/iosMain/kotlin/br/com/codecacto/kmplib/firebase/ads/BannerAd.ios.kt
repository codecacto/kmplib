package br.com.codecacto.kmplib.firebase.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.cinterop.googleads.GADAdSizeBanner
import br.com.codecacto.kmplib.cinterop.googleads.GADBannerView
import br.com.codecacto.kmplib.cinterop.googleads.GADBannerViewDelegateProtocol
import br.com.codecacto.kmplib.cinterop.googleads.GADRequest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun BannerAdPlatform(modifier: Modifier) {
    val adUnitId = AdManager.getBannerAdUnitId()
    if (adUnitId.isEmpty()) return

    UIKitView(
        modifier = modifier,
        factory = {
            val bannerView = GADBannerView(GADAdSizeBanner.readValue())
            bannerView.adUnitID = adUnitId
            bannerView.rootViewController = getRootViewController()

            bannerView.delegate = object : NSObject(), GADBannerViewDelegateProtocol {
                override fun bannerViewDidRecordImpression(bannerView: GADBannerView) {
                    AdStats.recordImpression(AdProviderTag.ADMOB, StatAdFormat.BANNER)
                }

                override fun bannerViewDidRecordClick(bannerView: GADBannerView) {
                    AdStats.recordClick(AdProviderTag.ADMOB, StatAdFormat.BANNER)
                }
            }

            bannerView.loadRequest(GADRequest.request())
            bannerView
        }
    )
}
