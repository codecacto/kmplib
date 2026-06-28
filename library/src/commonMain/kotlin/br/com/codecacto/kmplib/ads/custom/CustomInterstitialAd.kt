package br.com.codecacto.kmplib.ads.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.platform.getUrlLauncher
import coil3.compose.AsyncImage

/**
 * Anuncio interstitial customizado (tela cheia, house ad) vindo do backend central (apps-api).
 *
 * - Renderizado como [Dialog] full-screen quando [show] e `true`.
 * - Respeita [MonetizationManager.shouldShowAds] (chama [onDismiss] imediatamente
 *   se ads estao desligados).
 * - Filtra por formato `"interstitial"` e escolhe um por rotacao simples.
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
 *     onDismiss = { showAd = false }
 * )
 * ```
 */
@Composable
fun CustomInterstitialAd(
    show: Boolean,
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

    val ad = remember(ads) {
        selectAd(ads, format = CustomAd.FORMAT_INTERSTITIAL)
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
                .background(Color.Black)
        ) {
            // Imagem em TELA CHEIA (padrão 9:16 retrato no app). Crop preenche a tela
            // toda; o criativo deve ser gerado em 1080×1920 (ver specs no painel de Anúncios).
            AsyncImage(
                model = ad.imageUrl,
                contentDescription = ad.title.ifBlank { "Anuncio" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = handleClick)
            )

            // Sem título/CTA: a arte já traz o botão. Clicar em qualquer ponto abre a URL
            // (igual ao banner). Só o "X" para fechar.

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
