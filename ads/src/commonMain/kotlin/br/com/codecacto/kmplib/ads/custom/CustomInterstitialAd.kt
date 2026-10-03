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
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.ads.AdDefaults
import br.com.codecacto.kmplib.ads.router.AdRouter
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.theme.WithTestTagsAsResourceId
import coil3.compose.AsyncImage
import kotlin.time.Duration

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
 * - Respeita [MonetizationManager.shouldShowAds] (chama [onDismiss] imediatamente se ads estao off,
 *   e fecha se virar premium com o anuncio na tela).
 * - Filtra por formato `"interstitial"` e escolhe um por rotacao simples.
 * - **Espera a primeira carga dos anuncios** (2.236.0): pedido feito antes de a lista do apps-api
 *   chegar (o intersticial "ao abrir") aguarda ate [firstLoadTimeout] em vez de desistir no primeiro
 *   frame. Nada e desenhado durante a espera — a tela de baixo segue usavel. Sem criativo ou com o
 *   teto estourado, chama [onDismiss] sem contar impressao.
 * - [onShown] dispara so quando o anuncio de fato aparece — e o lugar de contar frequencia ("uma
 *   vez por sessao"); [onDismiss] chega nos dois casos (exibido e fechado, ou pulado).
 * - Intersticial que **segura uma navegacao** (abre e so navega no `onDismiss`) pode passar
 *   `firstLoadTimeout = Duration.ZERO` para nunca atrasar o passo seguinte nos primeiros segundos.
 * - [closeMode] decide se o "X" aparece na hora ([InterstitialCloseMode.IMMEDIATE]) ou apos uma
 *   contagem regressiva com barra de progresso ([InterstitialCloseMode.TIMED]).
 * - Clique na imagem abre [CustomAd.targetUrl] e dispara [onDismiss] (vale nos dois modos).
 * - **Só entra na tela com a arte pronta** (2.246.0): escolhido o anúncio, a imagem é baixada e
 *   decodificada ANTES do diálogo abrir, até [creativeLoadTimeout]. Imagem que não carrega (URL fora
 *   do ar, rede caída, teto estourado) = intersticial pulado, sem impressão — nunca a tela preta com
 *   só o "X". A impressão conta quando a arte foi pintada.
 * - Ids para automação: `ads-interstitial` (contêiner), `ads-interstitial-carregado` (só com a arte
 *   pintada) e `ads-btn-fechar-interstitial` ([AdsTestTags]).
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
    onShown: ((CustomAd) -> Unit)? = null,
    firstLoadTimeout: Duration = AdDefaults.INTERSTITIAL_FIRST_LOAD_TIMEOUT,
    creativeLoadTimeout: Duration = AdDefaults.INTERSTITIAL_CREATIVE_LOAD_TIMEOUT,
) {
    InterstitialAdHost(
        show = show,
        onDismiss = onDismiss,
        closeMode = closeMode,
        onAdClick = onAdClick,
        onShown = onShown,
        firstLoadTimeout = firstLoadTimeout,
        creativeLoadTimeout = creativeLoadTimeout,
        withRouting = false,
    )
}

/**
 * Corpo comum de [CustomInterstitialAd] e do `ManagedInterstitialAd` (que passa [withRouting]).
 *
 * Enquanto a decisão espera a primeira carga ([decideInterstitial]), **nada é desenhado**: a tela
 * de baixo continua viva e respondendo ao toque — o anúncio só entra por cima quando o criativo
 * existe. Pular (premium, `off`, sem criativo, teto estourado) chama [onDismiss] sem contar
 * impressão e sem [onShown].
 */
@Composable
internal fun InterstitialAdHost(
    show: Boolean,
    onDismiss: () -> Unit,
    closeMode: InterstitialCloseMode,
    onAdClick: ((CustomAd) -> Unit)?,
    onShown: ((CustomAd) -> Unit)?,
    firstLoadTimeout: Duration,
    creativeLoadTimeout: Duration = AdDefaults.INTERSTITIAL_CREATIVE_LOAD_TIMEOUT,
    withRouting: Boolean,
) {
    val showAds by MonetizationManager.shouldShowAds.collectAsState()

    if (!show) return

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // Escolhido UMA vez por pedido: a lista mudar com o anúncio na tela não troca o criativo.
    var chosen by remember { mutableStateOf<CustomAd?>(null) }

    val loadCreative = rememberCreativeLoader()

    LaunchedEffect(Unit) {
        val decision = decideInterstitial(
            showAds = MonetizationManager.shouldShowAds,
            ads = CustomAdManager.ads,
            adsLoad = CustomAdManager.loadState,
            timeout = firstLoadTimeout,
            routing = if (withRouting) AdRouter.routing else null,
            routingLoad = if (withRouting) AdRouter.loadState else null,
        )
        when (decision) {
            // 2.246.0: só entra na tela com a ARTE pronta — antes, o diálogo abria preto (só o "X")
            // enquanto a imagem baixava, e para sempre se ela falhasse.
            is InterstitialDecision.Show ->
                if (awaitInterstitialCreative(decision.ad.imageUrl, creativeLoadTimeout, loadCreative)) {
                    chosen = decision.ad
                } else {
                    currentOnDismiss()
                }
            is InterstitialDecision.Skip -> currentOnDismiss()
        }
    }

    val ad = chosen ?: return

    // Virou premium com o anúncio na tela (a assinatura chegou depois): fecha.
    if (!showAds) {
        LaunchedEffect(Unit) { currentOnDismiss() }
        return
    }

    InterstitialAdDialog(
        ad = ad,
        onDismiss = onDismiss,
        closeMode = closeMode,
        onAdClick = onAdClick,
        onShown = onShown,
    )
}

@Composable
private fun InterstitialAdDialog(
    ad: CustomAd,
    onDismiss: () -> Unit,
    closeMode: InterstitialCloseMode,
    onAdClick: ((CustomAd) -> Unit)?,
    onShown: ((CustomAd) -> Unit)?,
) {

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

    // Carga da arte, para o id `ads-interstitial-carregado` (ver `AdsTestTags`).
    var load by remember(ad.id, ad.imageUrl) { mutableStateOf(AdCreativeLoad.Loading) }

    // A impressão (e o `onShown`) contam quando a ARTE foi pintada, não quando o diálogo montou
    // (2.246.0). A arte já vem pré-carregada pelo host, então isso acontece no primeiro frame; se
    // mesmo assim falhar (cache despejado + rede caída), o anúncio fecha em vez de ficar preto.
    LaunchedEffect(ad.id, ad.imageUrl, load) {
        when (load) {
            AdCreativeLoad.Loaded -> {
                CustomAdManager.notifyImpression(ad)
                AdStats.recordImpression(AdProviderTag.CUSTOM, StatAdFormat.INTERSTITIAL, ad.id)
                onShown?.invoke(ad)
            }
            AdCreativeLoad.Failed -> onDismiss()
            AdCreativeLoad.Loading -> Unit
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
        // O `Dialog` é OUTRA JANELA no Android (outro `AndroidComposeView`, árvore de semântica
        // própria): o `testTagsAsResourceId` que o `AppTheme` liga na raiz NÃO chega aqui, e sem
        // re-ligar os ids deste anúncio — inclusive o "X" de fechar, desde a 2.166.0 — ficavam
        // invisíveis ao Maestro. No iOS é no-op (a tag já vira `accessibilityIdentifier`).
        WithTestTagsAsResourceId {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag(AdsTestTags.INTERSTITIAL)
            ) {
                // Imagem em TELA CHEIA (padrão 9:16 retrato no app). Crop preenche a tela
                // toda; o criativo deve ser gerado em 1080×1920 (ver specs no painel de Anúncios).
                // O id de "carregado" vai NESTE nó (o clicável), não no contêiner — ver `CustomBannerAd`.
                val loadedTag = creativeTestTag(load, AdsTestTags.INTERSTITIAL_CARREGADO)
                AsyncImage(
                    model = ad.imageUrl,
                    contentDescription = ad.title.ifBlank { "Anuncio" },
                    contentScale = ContentScale.Crop,
                    onState = { load = it.toAdCreativeLoad() },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (loadedTag != null) Modifier.testTag(loadedTag) else Modifier)
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
                            .testTag(AdsTestTags.BTN_FECHAR_INTERSTITIAL)
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
}
