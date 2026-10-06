package br.com.codecacto.kmplib.ads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import br.com.codecacto.kmplib.ads.custom.AdsTestTags
import br.com.codecacto.kmplib.ads.router.ManagedBannerAd
import br.com.codecacto.kmplib.ui.components.EmptyState

/*
 * Banner que ACOMPANHA O CONTEÚDO de uma lista (2.254.0).
 *
 * Lista vazia sobra tela: o estado vazio ocupa um terço dela, e um banner de rodapé 6:1 deixa o resto
 * em branco. Lista com itens é o contrário — cada centímetro é leitura, e o banner tem de ser o
 * pequeno, no rodapé, com o último item terminando acima dele.
 *
 * Por isso o lugar e o tamanho saem do ESTADO da lista, e nunca de um chute:
 *
 * | estado da lista | rodapé (`bottomBar`)          | estado vazio                         |
 * |-----------------|-------------------------------|--------------------------------------|
 * | carregando      | nada (só a folga da barra)    | —                                    |
 * | vazia           | nada (só a folga da barra)    | QUADRADO ou GRANDE, pelo espaço medido |
 * | com itens       | `STANDARD`                    | —                                    |
 *
 * "Carregando" NÃO é "vazio": mostrar o grande enquanto carrega e trocar pelo pequeno quando os itens
 * chegam faria o banner piscar e contar duas impressões por uma abertura. Enquanto carrega, nada.
 */

/** O que a lista está mostrando — decide onde e em que tamanho entra o banner. */
enum class ListAdState {
    /** Ainda carregando: nenhum banner (nem o grande, nem o pequeno). */
    LOADING,

    /** Carregou e não há item nenhum: banner grande DENTRO do estado vazio. */
    EMPTY,

    /**
     * Há conteúdo na tela: banner `STANDARD` no rodapé. Vale também para ERRO e para "busca sem
     * resultado" — nesses o usuário está agindo (tentando de novo, digitando), e um quadrado que
     * aparece e some a cada tecla seria o banner trocando de tamanho em laço.
     */
    CONTENT,
}

/**
 * [ListAdState] a partir dos dois booleanos que todo `State` de lista já tem. `isEmpty` é o vazio
 * **legítimo** (carregou, sem erro, zero itens) — não o vazio-por-busca nem o erro.
 */
fun listAdStateOf(isLoading: Boolean, isEmpty: Boolean): ListAdState = when {
    isLoading -> ListAdState.LOADING
    isEmpty -> ListAdState.EMPTY
    else -> ListAdState.CONTENT
}

/**
 * Tamanho do banner do **rodapé** para o estado da lista; `null` = rodapé sem banner (carregando, ou
 * vazia — aí o banner está no estado vazio, via [EmptyStateWithBannerAd]).
 */
fun footerBannerSizeFor(state: ListAdState): BannerSize? = when (state) {
    ListAdState.CONTENT -> BannerSize.STANDARD
    ListAdState.LOADING, ListAdState.EMPTY -> null
}

/**
 * Rodapé de uma tela de lista: o `bottomBar` do Scaffold. Com itens, o banner `STANDARD`; carregando
 * ou vazia, só a folga da barra de gestos — a mesma base do banner desligado, então o `innerPadding`
 * da lista nunca passa por baixo da barra do sistema.
 *
 * ```kotlin
 * val adState = listAdStateOf(state.isLoading, state.isEmpty)
 * Scaffold(bottomBar = { ListFooterBannerAd(adState) }) { innerPadding ->
 *     when {
 *         state.isLoading -> Loading()
 *         state.isEmpty -> EmptyStateWithBannerAd(icon, "Nenhuma favorita", Modifier.padding(innerPadding))
 *         else -> LazyColumn(Modifier.padding(innerPadding)) { … }
 *     }
 * }
 * ```
 */
@Composable
fun ListFooterBannerAd(state: ListAdState, modifier: Modifier = Modifier) {
    val size = footerBannerSizeFor(state)
    if (size != null) {
        ManagedBannerAd(modifier = modifier.fillMaxWidth(), size = size)
    } else {
        Spacer(modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
    }
}

/** Tamanho e largura do banner do estado vazio, decididos pelo espaço medido. */
data class EmptyStateBannerSpec(val size: BannerSize, val width: Dp)

/**
 * Escolhe o banner do estado vazio pelo espaço **medido** que sobra abaixo do texto — função pura.
 *
 * - **Quadrado** quando o lado que cabe (o menor entre a largura útil e a altura livre) é pelo menos
 *   [minSquareSide]. O lado vira a largura: numa sobra de 330×300 sai um quadrado de 300, inteiro.
 * - **Grande** (3:1) na largura útil quando o quadrado não cabe mas a faixa cabe.
 * - **Padrão** (6:1) quando nem a faixa cabe (tela muito baixa, paisagem): o estado vazio rola, e o
 *   banner continua lá — o app que vive de house ad não perde a exibição.
 *
 * Altura infinita (dentro de uma rolagem) = sem limite: quadrado na largura útil.
 */
fun emptyStateBannerSpec(
    availableWidth: Dp,
    availableHeight: Dp,
    maxWidth: Dp = AdDefaults.EMPTY_STATE_BANNER_MAX_WIDTH,
    minSquareSide: Dp = AdDefaults.EMPTY_STATE_SQUARE_MIN_SIDE,
): EmptyStateBannerSpec {
    val width = min(availableWidth, maxWidth).coerceAtLeast(0.dp)
    val squareSide = min(width, availableHeight)
    return when {
        squareSide >= minSquareSide -> EmptyStateBannerSpec(BannerSize.SQUARE, squareSide)
        width / BannerSize.LARGE.aspectRatio <= availableHeight -> EmptyStateBannerSpec(BannerSize.LARGE, width)
        else -> EmptyStateBannerSpec(BannerSize.STANDARD, width)
    }
}

/** Respiro lateral e inferior do banner do estado vazio. */
private val EMPTY_STATE_BANNER_MARGIN: Dp = 16.dp

/**
 * **Estado vazio com o banner grande** ocupando o espaço que sobra — o par do [ListFooterBannerAd].
 *
 * O [emptyContent] (o `EmptyState` da tela) é medido primeiro e nunca é empurrado para fora: o banner
 * recebe só a altura que restou, e [emptyStateBannerSpec] escolhe quadrado, grande ou padrão por ela.
 * Texto e banner ficam centralizados juntos. Rola quando a altura é limitada — funciona como filho do
 * `RefreshableBox` (puxar para atualizar) sem `ScrollableFillBox` em volta (não embrulhe: são dois
 * roláveis aninhados).
 *
 * Sem anúncio para mostrar (premium, roteamento `off`, nenhum criativo) o banner não ocupa altura e a
 * tela fica igual a um `EmptyState` comum. Impressão: a mesma VIEWABLE de sempre (≥50% por ≥1 s, uma
 * por exibição). Ids `ads-banner`/`ads-banner-carregado` valem aqui também; o lugar leva
 * `ads-estado-vazio-banner` ([AdsTestTags.BANNER_ESTADO_VAZIO]).
 */
@Composable
fun EmptyStateWithBannerAd(
    modifier: Modifier = Modifier,
    maxBannerWidth: Dp = AdDefaults.EMPTY_STATE_BANNER_MAX_WIDTH,
    minSquareSide: Dp = AdDefaults.EMPTY_STATE_SQUARE_MIN_SIDE,
    emptyContent: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bounded = constraints.hasBoundedHeight
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    // `heightIn(min = viewport)` DEPOIS do scroll: o filho do `verticalScroll` mede com
                    // altura infinita, e o mínimo é o que faz o peso abaixo receber "o que sobrou".
                    if (bounded) Modifier.verticalScroll(scrollState).heightIn(min = viewport) else Modifier,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            emptyContent()
            // `fill = false`: o slot RECEBE a altura que sobrou como teto, mas ocupa só a do banner —
            // é o que deixa texto + banner centralizados juntos em vez de o texto colar no topo.
            val slot = if (bounded) Modifier.weight(1f, fill = false) else Modifier
            BoxWithConstraints(
                modifier = slot.fillMaxWidth().testTag(AdsTestTags.BANNER_ESTADO_VAZIO),
                contentAlignment = Alignment.TopCenter,
            ) {
                val spec = emptyStateBannerSpec(
                    availableWidth = maxWidth - EMPTY_STATE_BANNER_MARGIN * 2,
                    availableHeight = if (constraints.hasBoundedHeight) maxHeight - EMPTY_STATE_BANNER_MARGIN else Dp.Infinity,
                    maxWidth = maxBannerWidth,
                    minSquareSide = minSquareSide,
                )
                ManagedBannerAd(
                    modifier = Modifier.width(spec.width),
                    size = spec.size,
                    // O `innerPadding` do Scaffold já desconta a barra do sistema (o rodapé da lista
                    // vazia é o `ListFooterBannerAd`, que a reserva) — aplicar de novo sobraria faixa.
                    windowInsets = WindowInsets(0),
                )
            }
        }
    }
}

/**
 * Atalho de [EmptyStateWithBannerAd] com o `EmptyState` da lib — os mesmos parâmetros dele.
 *
 * ```kotlin
 * state.isEmpty -> EmptyStateWithBannerAd(
 *     icon = Icons.Filled.FavoriteBorder,
 *     title = "Nenhuma favorita ainda",
 *     description = "Toque em 👍 numa piada para guardá-la aqui.",
 *     modifier = Modifier.padding(innerPadding),
 * )
 * ```
 */
@Composable
fun EmptyStateWithBannerAd(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
    maxBannerWidth: Dp = AdDefaults.EMPTY_STATE_BANNER_MAX_WIDTH,
    minSquareSide: Dp = AdDefaults.EMPTY_STATE_SQUARE_MIN_SIDE,
) {
    EmptyStateWithBannerAd(
        modifier = modifier,
        maxBannerWidth = maxBannerWidth,
        minSquareSide = minSquareSide,
    ) {
        EmptyState(
            icon = icon,
            title = title,
            description = description,
            action = action,
        )
    }
}
