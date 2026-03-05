package br.com.codecacto.kmplib.firebase.ads

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.monetization.MonetizationManager

/**
 * Banner ad que respeita o estado de monetizacao.
 *
 * Exibe o banner apenas quando [MonetizationManager.shouldShowAds] e true.
 * No modo Freemium, se o usuario e premium, o banner nao aparece.
 * No modo PremiumOnly, nunca aparece.
 * No modo AdsOnly, segue o Remote Config.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val showAds by MonetizationManager.shouldShowAds.collectAsState()

    if (showAds) {
        BannerAdPlatform(modifier = modifier.fillMaxWidth().height(50.dp))
    } else {
        Spacer(modifier = modifier)
    }
}

@Composable
internal expect fun BannerAdPlatform(modifier: Modifier)
