package br.com.codecacto.kmplib.firebase.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
internal actual fun BannerAdPlatform(modifier: Modifier) {
    val adUnitId = AdManager.getBannerAdUnitId()
    if (adUnitId.isEmpty()) return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                adListener = object : AdListener() {
                    override fun onAdImpression() {
                        AdStats.recordImpression(AdProviderTag.ADMOB, StatAdFormat.BANNER)
                    }
                    override fun onAdClicked() {
                        AdStats.recordClick(AdProviderTag.ADMOB, StatAdFormat.BANNER)
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
