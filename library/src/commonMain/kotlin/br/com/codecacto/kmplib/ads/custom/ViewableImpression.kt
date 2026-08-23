package br.com.codecacto.kmplib.ads.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.delay

/** Fracao minima da area do anuncio visivel na janela para comecar a contar (criterio MRC/IAB). */
internal const val VIEWABLE_MIN_FRACTION: Float = 0.5f

/** Tempo continuo, em milissegundos, que a fracao minima precisa se manter. */
internal const val VIEWABLE_MIN_DURATION_MS: Long = 1_000L

/**
 * Fracao da area do layout que esta de fato visivel na janela.
 *
 * `boundsInWindow()` ja devolve o retangulo **recortado** pela janela e pelos clips dos pais, entao
 * a razao entre essa area e a area total do layout e a fracao visivel. Layout de area zero (ainda
 * nao medido, ou escondido) devolve 0.
 */
internal fun visibleFractionOf(totalWidth: Int, totalHeight: Int, visibleWidth: Float, visibleHeight: Float): Float {
    val total = totalWidth.toFloat() * totalHeight.toFloat()
    if (total <= 0f) return 0f
    val visible = visibleWidth.coerceAtLeast(0f) * visibleHeight.coerceAtLeast(0f)
    return (visible / total).coerceIn(0f, 1f)
}

/**
 * Modifier que dispara [onViewable] **uma unica vez**, quando o elemento fica de fato visto.
 *
 * ## O problema que isto corrige
 *
 * Ate a 2.139.x o `CustomBannerAd` registrava a impressao num `LaunchedEffect(ad.id, ad.imageUrl)`,
 * ou seja, **toda vez que o composable entrava na composicao**: troca de tela, volta do background,
 * rotacao, retorno pela pilha de navegacao. Nao havia criterio de visibilidade nem de tempo. Um app
 * com banner em cinco telas contava cinco impressoes de uma navegacao normal.
 *
 * O efeito medido (Super 8, 21/ago/2026): **164 impressoes de banner em tres minutos**, distribuidas
 * em 3 anuncios. Nenhuma sessao real tem esse perfil — era navegacao de teste contada por render. O
 * numero inflava sozinho e o CTR (cliques ÷ impressoes) afundava junto, ou seja, a metrica errava na
 * direcao que faz decidir mal.
 *
 * ## O criterio (MRC/IAB), o mesmo que a weblib ja usava
 *
 * **≥50% dos pixels visiveis por ≥1 segundo continuo.** O relogio e continuo: sair da tela antes de
 * completar zera a contagem, entao **rolar rapido pelo anuncio nao conta** — que e exatamente o
 * ponto. Depois de contabilizado nao dispara de novo enquanto a [key] nao mudar: rolar para cima e
 * para baixo nao multiplica a impressao.
 *
 * O par web e `useViewableImpression` da weblib (`@codecacto/weblib/ads`). Manter os dois criterios
 * iguais e o que permite somar impressao de app e de site na mesma coluna de
 * `monitoramento.ad_stats` sem comparar coisas diferentes.
 *
 * @param key identidade da exibicao — normalmente o id do anuncio. Trocar a key libera nova contagem.
 * @param enabled quando `false`, nada e observado nem disparado.
 * @param onViewable chamado no maximo uma vez por [key].
 */
@Composable
internal fun rememberViewableImpressionModifier(
    key: Any?,
    enabled: Boolean = true,
    onViewable: () -> Unit,
): Modifier {
    var visibleFraction by remember(key) { mutableStateOf(0f) }
    var alreadyCounted by remember(key) { mutableStateOf(false) }

    val isVisible = enabled && !alreadyCounted && visibleFraction >= VIEWABLE_MIN_FRACTION

    LaunchedEffect(key, isVisible) {
        if (!isVisible) return@LaunchedEffect
        // Relogio continuo: se o elemento sair da tela, `isVisible` vira false, este efeito e
        // cancelado e a contagem recomeca do zero na proxima vez.
        delay(VIEWABLE_MIN_DURATION_MS)
        alreadyCounted = true
        onViewable()
    }

    if (!enabled) return Modifier
    return Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        visibleFraction = visibleFractionOf(
            totalWidth = coords.size.width,
            totalHeight = coords.size.height,
            visibleWidth = bounds.width,
            visibleHeight = bounds.height,
        )
    }
}
