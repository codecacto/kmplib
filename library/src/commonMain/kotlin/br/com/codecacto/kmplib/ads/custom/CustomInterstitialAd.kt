package br.com.codecacto.kmplib.ads.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.platform.getUrlLauncher
import coil3.compose.AsyncImage

/**
 * Anuncio interstitial customizado (tela cheia) vindo do Firestore.
 *
 * - Renderizado como [Dialog] full-screen quando [show] e `true`.
 * - Respeita [MonetizationManager.shouldShowAds] (chama [onDismiss] imediatamente
 *   se ads estao desligados).
 * - Filtra por [placementId] (se nao-nulo) e formato `"interstitial"`.
 * - Botao "X" no canto superior direito dispara [onDismiss].
 * - Clique na imagem ou no botao CTA abre [CustomAd.targetUrl] e dispara [onDismiss].
 *
 * Padrao de uso:
 * ```kotlin
 * var showAd by remember { mutableStateOf(false) }
 *
 * Button(onClick = { showAd = true }) { Text("Mostrar") }
 *
 * CustomInterstitialAd(
 *     show = showAd,
 *     placementId = "after_level_finish",
 *     onDismiss = { showAd = false }
 * )
 * ```
 */
@Composable
fun CustomInterstitialAd(
    show: Boolean,
    placementId: String? = null,
    onDismiss: () -> Unit,
    onAdClick: ((CustomAd) -> Unit)? = null,
) {
    val showAds by MonetizationManager.shouldShowAds.collectAsState()
    val ads by CustomAdManager.ads.collectAsState()

    if (!show) return

    if (!showAds) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val ad = remember(ads, placementId) {
        selectAd(ads, format = CustomAd.FORMAT_INTERSTITIAL, placementId = placementId)
    }

    if (ad == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    LaunchedEffect(ad.id, ad.imageUrl) {
        CustomAdManager.notifyImpression(ad)
        AdStats.recordImpression(AdProviderTag.CUSTOM, StatAdFormat.INTERSTITIAL, ad.id)
    }

    val handleClick: () -> Unit = {
        CustomAdManager.notifyClick(ad)
        AdStats.recordClick(AdProviderTag.CUSTOM, StatAdFormat.INTERSTITIAL, ad.id)
        onAdClick?.invoke(ad)
        if (ad.targetUrl.isNotBlank()) {
            getUrlLauncher().openUrl(ad.targetUrl)
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (ad.title.isNotBlank()) {
                    Text(
                        text = ad.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                AsyncImage(
                    model = ad.imageUrl,
                    contentDescription = ad.title.ifBlank { "Anuncio" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable(onClick = handleClick)
                )

                if (ad.ctaLabel.isNotBlank()) {
                    Button(
                        onClick = handleClick,
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text(ad.ctaLabel)
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fechar",
                    tint = Color.White
                )
            }
        }
    }
}
