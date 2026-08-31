package br.com.codecacto.kmplib.ads.router

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import br.com.codecacto.kmplib.ads.BannerSize
import br.com.codecacto.kmplib.ads.custom.CustomBannerAd

/**
 * Banner gerenciado pelo [AdRouter]. Despacha em runtime entre house ads (CUSTOM) ou nada (OFF),
 * baseado na config remota do backend central (campo `banner`).
 *
 * O app usa este composable em vez de [CustomBannerAd] diretamente; o admin no portal liga/desliga
 * o banner sem precisar de release.
 *
 * Pre-requisito: chamar `AdRouter.initialize(projectSlug, httpClient, defaults)` no boot. Quando o
 * provider for CUSTOM, tambem precisa `CustomAdManager.initialize(...)`.
 *
 * @param size tamanho do banner (define o FORMATO pedido ao backend e a altura).
 * @param customHeight altura, quando se quer uma diferente da do [size].
 */
@Composable
fun ManagedBannerAd(
    modifier: Modifier = Modifier,
    size: BannerSize = BannerSize.STANDARD,
    customHeight: Dp = size.height,
) {
    val routing by AdRouter.routing.collectAsState()
    when (routing.banner) {
        AdProvider.CUSTOM -> CustomBannerAd(
            modifier = modifier,
            size = size,
            height = customHeight,
        )
        AdProvider.OFF -> Spacer(modifier = modifier)
    }
}
