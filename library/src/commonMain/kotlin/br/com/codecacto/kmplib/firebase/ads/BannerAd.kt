package br.com.codecacto.kmplib.firebase.ads

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Banner ad que respeita o estado [AdManager.adsEnabled].
 * Quando ads estão desabilitados, renderiza um [Spacer] vazio.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val adsEnabled by AdManager.adsEnabled.collectAsState()

    if (adsEnabled) {
        BannerAdPlatform(modifier = modifier.fillMaxWidth().height(50.dp))
    } else {
        Spacer(modifier = modifier)
    }
}

@Composable
internal expect fun BannerAdPlatform(modifier: Modifier)
