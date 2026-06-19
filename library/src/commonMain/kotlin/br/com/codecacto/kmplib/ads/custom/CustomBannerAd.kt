package br.com.codecacto.kmplib.ads.custom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.platform.getUrlLauncher
import coil3.compose.AsyncImage

/**
 * Banner customizado retangular (house ad) vindo do backend central (apps-api).
 *
 * - Respeita [MonetizationManager.shouldShowAds] (oculta quando usuario e premium
 *   ou a monetizacao desliga ads).
 * - Filtra anuncios por formato `"banner"` e escolhe um por rotacao simples.
 * - No clique abre [CustomAd.targetUrl] no navegador via [getUrlLauncher].
 *
 * Pre-requisito: chamar `CustomAdManager.initialize(...)` no boot do app.
 */
@Composable
fun CustomBannerAd(
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    onAdClick: ((CustomAd) -> Unit)? = null,
) {
    val showAds by MonetizationManager.shouldShowAds.collectAsState()
    val ads by CustomAdManager.ads.collectAsState()

    if (!showAds) {
        Spacer(modifier = modifier)
        return
    }

    val ad = remember(ads) {
        selectAd(ads, format = CustomAd.FORMAT_BANNER)
    }

    if (ad == null) {
        Spacer(modifier = modifier)
        return
    }

    LaunchedEffect(ad.id, ad.imageUrl) {
        CustomAdManager.notifyImpression(ad)
        AdStats.recordImpression(AdProviderTag.CUSTOM, StatAdFormat.BANNER, ad.id)
    }

    AsyncImage(
        model = ad.imageUrl,
        contentDescription = ad.title.ifBlank { "Anuncio" },
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clickable {
                CustomAdManager.notifyClick(ad)
                AdStats.recordClick(AdProviderTag.CUSTOM, StatAdFormat.BANNER, ad.id)
                onAdClick?.invoke(ad)
                if (ad.targetUrl.isNotBlank()) {
                    getUrlLauncher().openUrl(ad.targetUrl)
                }
            }
    )
}
