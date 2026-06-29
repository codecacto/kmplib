package br.com.codecacto.kmplib.ads.custom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Modo de fechamento do [CustomInterstitialAd]. **Definido por quem chama** (o app), NAO pelo anuncio.
 *
 * - [IMMEDIATE]: o "X" de fechar aparece imediatamente (comportamento padrao, tipo banner em tela cheia).
 * - [TIMED]: uma barra de progresso bem fina no topo enche durante [CustomAd.durationSeconds] segundos
 *   (default 5s, configurado no cadastro do anuncio); o "X" so aparece quando a contagem termina e o
 *   fechamento por "voltar" fica bloqueado ate la.
 */
enum class InterstitialCloseMode {
    IMMEDIATE,
    TIMED,
}

/**
 * Anuncio interstitial customizado (tela cheia, house ad) vindo do backend central (apps-api).
 *
 * - Renderizado como [Dialog] full-screen quando [show] e `true`.
 * - Imagem em TELA CHEIA com [ContentScale.Crop] (criativo recomendado 1080x1920, 9:16).
 * - Respeita [MonetizationManager.shouldShowAds] (chama [onDismiss] imediatamente se ads estao off).
 * - Filtra por formato `"interstitial"` e escolhe um por rotacao simples.
 * - [closeMode] decide se o "X" aparece na hora ([InterstitialCloseMode.IMMEDIATE]) ou apos uma
 *   contagem regressiva com barra de progresso ([InterstitialCloseMode.TIMED]).
 * - Clique na imagem abre [CustomAd.targetUrl] e dispara [onDismiss] (vale nos dois modos).
 *
 * Padrao de uso:
 * ```kotlin
 * var showAd by remember { mutableStateOf(false) }
 *
 * Button(onClick = { showAd = true }) { Text("Mostrar") }
 *
 * // Banner em tela cheia (fecha na hora):
 * CustomInterstitialAd(show = showAd, onDismiss = { showAd = false })
 *
 * // Com contagem regressiva (X so apos o tempo do anuncio, default 5s):
 * CustomInterstitialAd(
 *     show = showAd,
 *     onDismiss = { showAd = false },
 *     closeMode = InterstitialCloseMode.TIMED,
 * )
 * ```
 */
@Composable
fun CustomInterstitialAd(
    show: Boolean,
    onDismiss: () -> Unit,
    closeMode: InterstitialCloseMode = InterstitialCloseMode.IMMEDIATE,
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

    // Modo TIMED: barra de progresso no topo enche em durationSeconds; so entao libera o fechar.
    // Modo IMMEDIATE: pode fechar de cara.
    val timed = closeMode == InterstitialCloseMode.TIMED
    val progress = remember(ad.id) { Animatable(0f) }
    var canClose by remember(ad.id, closeMode) { mutableStateOf(!timed) }

    LaunchedEffect(ad.id, closeMode) {
        if (timed) {
            val seconds = ad.durationSeconds.coerceAtLeast(1)
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = seconds * 1000, easing = LinearEasing),
            )
            canClose = true
        }
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
        onDismissRequest = { if (canClose) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Enquanto a contagem nao termina (TIMED), nao deixa fechar pelo "voltar".
            dismissOnBackPress = canClose,
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
            // (igual ao banner).

            // TIMED: régua de progresso bem fina no topo enquanto a contagem corre.
            if (timed && !canClose) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            // "X" para fechar: imediato no IMMEDIATE; só após a contagem no TIMED.
            if (canClose) {
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
}
