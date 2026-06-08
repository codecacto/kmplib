package br.com.codecacto.kmplib.ads.router

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ads.custom.CustomBannerAd
import br.com.codecacto.kmplib.firebase.ads.BannerAd

/**
 * Banner gerenciado pelo [AdRouter]. Despacha em runtime entre AdMob,
 * Custom Ads ou nada, baseado na config remota no Firestore
 * (campo `banner` no doc `app_ad_configs/{appId}`).
 *
 * O app usa este composable em vez de [BannerAd] (AdMob) ou
 * [CustomBannerAd] diretamente, e o admin no portal alterna o provider sem
 * precisar de release.
 *
 * Pre-requisito: chamar `AdRouter.initialize(appId, defaults)` no boot.
 * Quando o provider for CUSTOM, tambem precisa `CustomAdManager.initialize(...)`.
 * Quando for ADMOB, precisa `MonetizationManager.initialize(...)` com `AdConfig`.
 *
 * @param placementId so usado quando o provider for CUSTOM (ignorado pelo AdMob).
 * @param customHeight altura do CustomBannerAd. Ignorado quando provider e ADMOB
 *   (AdMob tem altura fixa de 50dp).
 */
@Composable
fun ManagedBannerAd(
    modifier: Modifier = Modifier,
    placementId: String? = null,
    customHeight: Dp = 60.dp,
) {
    val routing by AdRouter.routing.collectAsState()
    when (routing.banner) {
        AdProvider.ADMOB -> BannerAd(modifier = modifier)
        AdProvider.CUSTOM -> CustomBannerAd(
            modifier = modifier,
            placementId = placementId,
            height = customHeight,
        )
        AdProvider.OFF -> Spacer(modifier = modifier)
    }
}
