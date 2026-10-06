package br.com.codecacto.kmplib.ads.custom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import br.com.codecacto.kmplib.ads.stats.AdFormat as StatAdFormat
import br.com.codecacto.kmplib.ads.stats.AdProviderTag
import br.com.codecacto.kmplib.ads.stats.AdStats
import br.com.codecacto.kmplib.ads.AdDefaults
import br.com.codecacto.kmplib.ads.BannerSize
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
 * - Ids para automação: `ads-banner` no contêiner e `ads-banner-carregado` só com a arte pintada
 *   ([AdsTestTags]).
 *
 * Pre-requisito: chamar `CustomAdManager.initialize(...)` no boot do app.
 *
 * @param windowInsets folga do sistema aplicada embaixo da arte. Default = barra de gestos / home
 *   indicator, que é o certo no `bottomBar` do Scaffold. **No meio do conteúdo** (o banner grande do
 *   estado vazio, 2.254.0) passe `WindowInsets(0)`: o `innerPadding` do Scaffold já desconta a barra,
 *   e aplicar de novo deixaria uma faixa vazia embaixo do criativo.
 */
@Composable
fun CustomBannerAd(
    modifier: Modifier = Modifier,
    size: BannerSize = BannerSize.STANDARD,
    height: Dp? = null,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
    onAdClick: ((CustomAd) -> Unit)? = null,
) {
    val showAds by MonetizationManager.shouldShowAds.collectAsState()
    val ads by CustomAdManager.ads.collectAsState()

    if (!showAds) {
        Spacer(modifier = modifier)
        return
    }

    // Pede o tamanho preferido e, não havendo arte dele, ACEITA a do banner comum — a caixa segue
    // a proporção da arte escolhida (abaixo), então cair para a 6:1 não corta nada: só resulta num
    // banner mais baixo. Antes da 2.171.0 isto devolvia `null` e o rodapé ficava vazio, para não
    // esticar a arte errada; com a caixa acompanhando a arte, o motivo deixou de existir — e um app
    // cuja única receita é o house ad não pode deixar de exibir por falta de UMA variante.
    val ad = remember(ads, size) {
        BannerSize.fallbackChain(size).firstNotNullOfOrNull { fmt -> selectAd(ads, format = fmt) }
    }

    if (ad == null) {
        Spacer(modifier = modifier)
        return
    }

    // Impressao VIEWABLE: 1 registro por anuncio de fato VISTO — >=50% na tela por >=1 s continuo.
    // Antes isto era um `LaunchedEffect(ad.id, ad.imageUrl)`, que disparava a cada entrada na
    // composicao: troca de tela, volta do background, rotacao. Ver `rememberViewableImpressionModifier`.
    val viewableModifier = rememberViewableImpressionModifier(key = ad.id) {
        CustomAdManager.notifyImpression(ad)
        AdStats.recordImpression(AdProviderTag.CUSTOM, StatAdFormat.BANNER, ad.id)
    }

    // Carga da arte, para o id `ads-banner-carregado` (ver `AdsTestTags`). Chaveado no criativo:
    // trocar de anúncio volta a "carregando" até a arte nova pintar.
    var load by remember(ad.id, ad.imageUrl) { mutableStateOf(AdCreativeLoad.Loading) }

    // Dois nós, de propósito: o CONTÊINER leva o id `ads-banner` (montado = anúncio escolhido) e a
    // IMAGEM leva `ads-banner-carregado` só depois de pintada. O `clickable` fica na imagem, não no
    // contêiner — um pai clicável mescla a semântica dos filhos e, na mescla, a `testTag` do pai
    // vence: o id de "carregado" sumiria da árvore que o Maestro lê.
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Barra de gestos / home indicator. Com `targetSdk` 35+ o Android desenha edge-to-edge
            // à força, e o `bottomBar` do Scaffold NÃO recebe inset sozinho — sem isto, a faixa de
            // navegação fica por cima do rodapé do criativo. `windowInsetsPadding` respeita o
            // consumo de insets: no `bottomBar` ele aplica, no meio do conteúdo (onde o Scaffold já
            // consumiu) vira zero, então o mesmo composable serve nos dois lugares.
            .windowInsetsPadding(windowInsets)
            // Altura pela PROPORÇÃO da arte, não por um dp fixo. Com `height` fixa, o
            // `ContentScale.Crop` preenche a caixa e corta o que sobra na largura: uma faixa 6:1 numa
            // caixa de 360x90 perde um terço do criativo, e o que some é a borda — onde costuma
            // estar o nome do app. `height` continua existindo para quem precisa fixar por layout.
            .then(
                if (height != null) Modifier.height(height)
                else Modifier.aspectRatio(BannerSize.aspectRatioOf(ad.format, fallback = size)),
            )
            .then(viewableModifier)
            .testTag(AdsTestTags.BANNER),
    ) {
        val loadedTag = creativeTestTag(load, AdsTestTags.BANNER_CARREGADO)
        AsyncImage(
            model = ad.imageUrl,
            contentDescription = ad.title.ifBlank { "Anuncio" },
            contentScale = ContentScale.Crop,
            onState = { load = it.toAdCreativeLoad() },
            modifier = Modifier
                .fillMaxSize()
                .then(if (loadedTag != null) Modifier.testTag(loadedTag) else Modifier)
                .clickable {
                    CustomAdManager.notifyClick(ad)
                    AdStats.recordClick(AdProviderTag.CUSTOM, StatAdFormat.BANNER, ad.id)
                    onAdClick?.invoke(ad)
                    if (ad.targetUrl.isNotBlank()) {
                        getUrlLauncher().openUrl(ad.targetUrl)
                    }
                },
        )
    }
}
