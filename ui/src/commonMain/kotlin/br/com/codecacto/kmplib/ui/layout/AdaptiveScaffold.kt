package br.com.codecacto.kmplib.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.ui.tour.tourTarget
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_list_detail_show_list
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.components.BottomNavTestTags
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass

/**
 * Um destino de navegação do [AdaptiveScaffold].
 *
 * `selectedIcon`/`unselectedIcon` separados porque **estado nunca é só cor** (WCAG 1.4.1): o ativo é
 * preenchido, o inativo é vazado.
 *
 * Automação: o item (barra ou rail) leva o id `nav-item-<id>` ([BottomNavTestTags.item]) — no Maestro,
 * `tapOn: { id: "nav-item-<id>" }`. Tocar pelo rótulo casa com qualquer texto igual na tela ("Jogos"
 * da aba e o "Jogos" de um contador no conteúdo) e o toque cai no conteúdo. (2.245.0)
 *
 * Selo (2.314.0): `badge = NavBadge.Count(3)` ou `NavBadge.Dot()` — `Badge` do Material 3 sobre o
 * ícone na barra, no rail e no rail largo; o leitor de tela lê "Alertas, 3 notificações"
 * ([AdaptiveNavBadgeTexts]); o selo tem o id `nav-badge-<id>`.
 */
data class AdaptiveDestination(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Selo de contador/ponto sobre o ícone, nos três modos (2.314.0). `null` = sem selo. */
    val badge: NavBadge? = null,
)

/**
 * Chassi que **troca a navegação de lugar** conforme a classe de janela — não a estica.
 *
 * | Classe | Navegação | Por quê |
 * |---|---|---|
 * | COMPACTA | barra inferior | alvo no alcance do polegar |
 * | MÉDIA | navigation rail (ícone + rótulo curto) | barra inferior num tablet põe o alvo a 25 cm do polegar |
 * | EXPANDIDA | rail **largo**, com rótulo ao lado do ícone | há largura sobrando; esconder o rótulo é desperdício |
 *
 * ## Por que isto não é "responsivo"
 *
 * Responsivo é a mesma árvore com outro `padding`. Aqui a barra inferior **deixa de existir** e o
 * conteúdo passa a dividir a tela na horizontal — é outra composição, com outra ordem de foco e
 * outro alvo de toque. Foi exatamente essa diferença que o fundador pediu em 16/ago/2026:
 * *"não só expandir e deixar responsivo… eu quero um layout próprio pra tablet e pra celulares"*.
 *
 * ## O que este componente NÃO faz
 *
 * Ele não decide o conteúdo. Duas colunas de conteúdo são [ListDetailScaffold] — separado de
 * propósito, porque nem toda tela de tablet é mestre-detalhe (uma leitura longa, por exemplo, quer
 * uma coluna centralizada e larga, não duas).
 *
 * ## Trilho desenhado pelo projeto (2.322.0, GAP-VIT-T01)
 *
 * Projeto com protótipo de tablet desenha o trilho dele **sem reescrever a navegação** — insets, ordem
 * de foco, selo, papel de aba e os ids `nav-item-<id>` continuam da lib:
 *
 * ```kotlin
 * AdaptiveScaffold(
 *     destinations = destinos, selectedId = atual, onSelect = ::ir,
 *     railStyle = AdaptiveRailStyle(
 *         expandedLayout = AdaptiveRailLayout.LABELED,  // 88 dp com rótulo também na paisagem
 *         width = 88.dp,
 *         containerColor = cores.superficie,
 *         dividerColor = cores.linha,
 *         item = AdaptiveRailItemStyle(selectedIndicatorColor = cores.primariaSuave, …),
 *     ),
 *     railHeader = { MarcaDoApp(onClick = { ir("hoje") }); BotaoNovoAgendamento() },
 *     railFooter = { ItemConfiguracoes() },
 * ) { padding -> … }
 * ```
 *
 * @param railHeader topo FIXO do trilho (marca, ação principal). Só no trilho (MÉDIA/EXPANDIDA) — no
 *   celular, a ação principal mora na tela.
 * @param railFooter rodapé FIXO do trilho (Configurações, conta), encostado embaixo.
 * @param railStyle desenho do contêiner e — via [AdaptiveRailStyle.item] — dos itens. O default é o
 *   trilho de sempre (rail largo na EXPANDIDA).
 * @param railItem desenho LIVRE de cada item (vence [AdaptiveRailStyle.item]). Recebe o destino, se
 *   está ativo e o layout; `NavIcon(tint)` desenha o ícone com o selo. Toque, id e acessibilidade são
 *   da lib.
 */
@Composable
fun AdaptiveScaffold(
    destinations: List<AdaptiveDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    windowSizeClass: WindowSizeClass = LocalWindowSizeClass.current,
    badgeTexts: AdaptiveNavBadgeTexts = rememberAdaptiveNavBadgeTexts(),
    railHeader: (@Composable ColumnScope.() -> Unit)? = null,
    railFooter: (@Composable ColumnScope.() -> Unit)? = null,
    railStyle: AdaptiveRailStyle = AdaptiveRailStyle(),
    railItem: (@Composable AdaptiveRailItemScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!windowSizeClass.temNavegacaoLateral) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = {
                AdaptiveBottomBar(destinations, selectedId, onSelect, badgeTexts)
            },
            content = content,
        )
        return
    }

    // Tablet: a navegação vira coluna à esquerda e o conteúdo ocupa o resto. O `Row` externo é o que
    // faz o rail e o conteúdo dividirem a ALTURA — num `Scaffold` com `bottomBar` eles dividiriam a
    // vertical, que é o layout de telefone esticado.
    val insetsDoRail = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val layoutDoTrilho = adaptiveRailLayoutFor(windowSizeClass, railStyle.expandedLayout)
        ?: AdaptiveRailLayout.LABELED
    Row(modifier = modifier.fillMaxSize()) {
        AdaptiveRail(
            destinations = destinations,
            selectedId = selectedId,
            onSelect = onSelect,
            layout = layoutDoTrilho,
            badgeTexts = badgeTexts,
            style = railStyle,
            header = railHeader,
            footer = railFooter,
            itemSlot = railItem,
        )
        if (railStyle.dividerColor != Color.Unspecified) {
            Box(Modifier.fillMaxHeight().width(1.dp).background(railStyle.dividerColor))
        }
        Scaffold(
            // O rail já descontou a borda inicial (barra de navegação de 3 botões em paisagem,
            // recorte da câmera): o Scaffold não pode somá-la de novo no conteúdo.
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .consumeWindowInsets(insetsDoRail.only(WindowInsetsSides.Start)),
            topBar = topBar,
            content = content,
        )
    }
}

@Composable
private fun AdaptiveBottomBar(
    destinations: List<AdaptiveDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    badgeTexts: AdaptiveNavBadgeTexts,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // Barra PRÓPRIA no `bottomBar`: o Scaffold não lhe dá inset nenhum. Com o edge-to-edge
            // (forçado no Android 15; sempre no iOS) os destinos ficavam sob a barra de gestos / o
            // home indicator. O fundo pinta atrás dela; os itens começam acima. (2.235.0)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        destinations.forEach { destino ->
            val ativo = destino.id == selectedId
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = ativo,
                        role = Role.Tab,
                        onClick = { onSelect(destino.id) },
                    )
                    .descricaoDoSelo(destino, badgeTexts)
                    .testTag(BottomNavTestTags.item(destino.id))
                    .tourTarget(BottomNavTestTags.item(destino.id))
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AdaptiveBadgedIcon(destino.id, destino.badge) {
                    Icon(
                        imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                        contentDescription = null,
                        tint = corDoItem(ativo),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = destino.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = corDoItem(ativo),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A coluna de navegação do tablet.
 *
 * [AdaptiveRailLayout.WIDE] põe o rótulo **ao lado** do ícone; [AdaptiveRailLayout.LABELED] põe
 * embaixo, com a coluna estreita. A diferença não é estética: numa tela de 1280dp, uma coluna de 80dp
 * com rótulo espremido desperdiça a largura que existe justamente para isso — mas um protótipo que
 * mantém o mesmo trilho nas duas orientações pede LABELED também na EXPANDIDA (2.322.0).
 *
 * Estrutura (2.322.0): cabeçalho fixo (`railHeader`: marca, ação principal) → destinos (rolam DENTRO
 * do trilho quando não cabem) → rodapé fixo (`railFooter`: Configurações, conta). A ordem de foco é
 * essa mesma, de cima para baixo.
 */
@Composable
private fun AdaptiveRail(
    destinations: List<AdaptiveDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    layout: AdaptiveRailLayout,
    badgeTexts: AdaptiveNavBadgeTexts,
    style: AdaptiveRailStyle,
    header: (@Composable ColumnScope.() -> Unit)?,
    footer: (@Composable ColumnScope.() -> Unit)?,
    itemSlot: (@Composable AdaptiveRailItemScope.() -> Unit)?,
) {
    val largo = layout == AdaptiveRailLayout.WIDE
    val fundoDoTrilho = style.containerColor.takeOrElse { MaterialTheme.colorScheme.surfaceContainerLow }
    val alinhamento = if (largo) Alignment.Start else Alignment.CenterHorizontally
    val largura = when {
        style.width != Dp.Unspecified -> Modifier.width(style.width)
        else -> Modifier.widthIn(min = if (largo) 220.dp else 88.dp)
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .then(largura)
            .background(fundoDoTrilho)
            // A coluna vai do topo ao rodapé da janela: o fundo pinta atrás da status bar e da barra
            // de navegação, e os destinos começam abaixo/acima delas — e depois da barra de 3 botões
            // ou do recorte da câmera, quando eles ficam na borda inicial (paisagem). (2.235.0)
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
            )
            .padding(vertical = style.verticalPadding, horizontal = 8.dp),
        horizontalAlignment = alinhamento,
    ) {
        if (header != null) header()
        Column(
            // Rolagem própria: com muitos destinos num tablet em retrato, a coluna precisa rolar
            // DENTRO de si mesma — sem isso o último item fica inalcançável. Cabeçalho e rodapé ficam
            // fixos fora dela.
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = alinhamento,
            verticalArrangement = Arrangement.spacedBy(style.itemSpacing),
        ) {
            destinations.forEach { destino ->
                AdaptiveRailEntry(
                    destino = destino,
                    ativo = destino.id == selectedId,
                    onSelect = onSelect,
                    layout = layout,
                    badgeTexts = badgeTexts,
                    fundoDoTrilho = fundoDoTrilho,
                    itemStyle = style.item,
                    itemSlot = itemSlot,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (footer != null) footer()
    }
}

/** O que a lib garante em TODO item do trilho, desenhe quem desenhar: toque, id, tour e selo. */
private fun Modifier.itemDoTrilho(
    destino: AdaptiveDestination,
    ativo: Boolean,
    onSelect: (String) -> Unit,
    badgeTexts: AdaptiveNavBadgeTexts,
): Modifier = this
    .selectable(selected = ativo, role = Role.Tab, onClick = { onSelect(destino.id) })
    .descricaoDoSelo(destino, badgeTexts)
    .testTag(BottomNavTestTags.item(destino.id))
    .tourTarget(BottomNavTestTags.item(destino.id))

@Composable
private fun AdaptiveRailEntry(
    destino: AdaptiveDestination,
    ativo: Boolean,
    onSelect: (String) -> Unit,
    layout: AdaptiveRailLayout,
    badgeTexts: AdaptiveNavBadgeTexts,
    fundoDoTrilho: androidx.compose.ui.graphics.Color,
    itemStyle: AdaptiveRailItemStyle?,
    itemSlot: (@Composable AdaptiveRailItemScope.() -> Unit)?,
) {
    val largo = layout == AdaptiveRailLayout.WIDE
    if (itemSlot != null) {
        val scope = AdaptiveRailItemScope(destino, ativo, layout)
        Box(
            modifier = (if (largo) Modifier.fillMaxWidth() else Modifier)
                .itemDoTrilho(destino, ativo, onSelect, badgeTexts),
            contentAlignment = Alignment.Center,
        ) { scope.itemSlot() }
        return
    }
    if (itemStyle != null) {
        AdaptiveRailStyledEntry(destino, ativo, onSelect, largo, badgeTexts, itemStyle)
        return
    }

    // Desenho de sempre (até a 2.320.0) — idêntico para quem não passa estilo.
    val fundo = if (ativo) MaterialTheme.colorScheme.secondaryContainer else fundoDoTrilho
    if (largo) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(fundo)
                .itemDoTrilho(destino, ativo, onSelect, badgeTexts)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveBadgedIcon(destino.id, destino.badge) {
                Icon(
                    imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                    contentDescription = null,
                    tint = corDoItem(ativo),
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = destino.label,
                style = MaterialTheme.typography.labelLarge,
                color = corDoItem(ativo),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(fundo)
                .itemDoTrilho(destino, ativo, onSelect, badgeTexts)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AdaptiveBadgedIcon(destino.id, destino.badge) {
                Icon(
                    imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                    contentDescription = null,
                    tint = corDoItem(ativo),
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = destino.label,
                style = MaterialTheme.typography.labelSmall,
                color = corDoItem(ativo),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Item na anatomia do `NavigationRailItem` do M3, com as medidas/cores do [AdaptiveRailItemStyle]. */
@Composable
private fun AdaptiveRailStyledEntry(
    destino: AdaptiveDestination,
    ativo: Boolean,
    onSelect: (String) -> Unit,
    largo: Boolean,
    badgeTexts: AdaptiveNavBadgeTexts,
    st: AdaptiveRailItemStyle,
) {
    val cores = MaterialTheme.colorScheme
    val indicador = st.selectedIndicatorColor.takeOrElse { cores.secondaryContainer }
    val corIcone = if (ativo) st.selectedIconColor.takeOrElse { cores.onSecondaryContainer }
    else st.unselectedIconColor.takeOrElse { cores.onSurfaceVariant }
    val corRotulo = if (ativo) st.selectedLabelColor.takeOrElse { cores.onSurface }
    else st.unselectedLabelColor.takeOrElse { cores.onSurfaceVariant }
    val baseRotulo = st.labelStyle
        ?: if (largo) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium
    val estiloRotulo = if (ativo && st.selectedLabelFontWeight != null) {
        baseRotulo.copy(fontWeight = st.selectedLabelFontWeight)
    } else {
        baseRotulo
    }
    val icone: @Composable () -> Unit = {
        AdaptiveBadgedIcon(destino.id, destino.badge) {
            Icon(
                imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                contentDescription = null,
                tint = corIcone,
                modifier = Modifier.size(st.iconSize),
            )
        }
    }
    if (largo) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(st.indicatorShape)
                .background(if (ativo) indicador else androidx.compose.ui.graphics.Color.Transparent)
                .itemDoTrilho(destino, ativo, onSelect, badgeTexts)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icone()
            Text(destino.label, style = estiloRotulo, color = corRotulo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(
        modifier = Modifier
            .width(st.itemWidth)
            .itemDoTrilho(destino, ativo, onSelect, badgeTexts)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(st.labelSpacing),
    ) {
        Box(
            modifier = Modifier
                .size(width = st.indicatorWidth, height = st.indicatorHeight)
                .clip(st.indicatorShape)
                .background(if (ativo) indicador else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { icone() }
        Text(
            text = destino.label,
            style = estiloRotulo,
            color = corRotulo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Alertas, 3 notificações" no nó do item (o selo em si é mudo). Sem selo, nada muda. */
private fun Modifier.descricaoDoSelo(destino: AdaptiveDestination, texts: AdaptiveNavBadgeTexts): Modifier {
    val descricao = texts.describe(destino.label, destino.badge) ?: return this
    return semantics { contentDescription = descricao }
}

@Composable
private fun corDoItem(ativo: Boolean) = if (ativo) {
    MaterialTheme.colorScheme.onSecondaryContainer
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * O que um painel do [ListDetailScaffold] sabe sobre **onde** está sendo desenhado.
 *
 * Escopo de receptor (`ColumnScope`, `LazyItemScope`, `ThreePaneScaffoldScope` do próprio AndroidX)
 * é a forma que o Compose usa para um container informar seus slots — e aqui ela é a **única**
 * forma que não quebra ninguém: um parâmetro `(Boolean) -> Unit` numa sobrecarga nova deixaria toda
 * chamada existente (`lista = { … }`, sem parâmetro declarado) **ambígua**, e ~20 apps parariam de
 * compilar. O escopo entra sem tocar em nenhuma chamada.
 */
@Immutable
interface ListDetailPaneScope {

    /**
     * `true` quando este painel é a **tela inteira** — em [WindowSizeClass.COMPACTA] e em
     * [WindowSizeClass.MEDIA].
     *
     * É aí, e só aí, que o detalhe precisa oferecer o caminho de volta.
     */
    val emPainelUnico: Boolean

    /**
     * `true` quando a lista está RECOLHIDA (EXPANDIDA com `listaRecolhida = true` e um item
     * escolhido) — o detalhe ocupa a largura toda. (2.322.0, GAP-VIT-T04)
     */
    val listaRecolhida: Boolean get() = false

    /** `true` quando o [ListDetailScaffold] recebeu `onListaRecolhidaChange` e está em dois painéis. */
    val podeRecolherLista: Boolean get() = false

    /**
     * Recolhe (ou mostra) a lista — chama o `onListaRecolhidaChange` do [ListDetailScaffold]. Ponha o
     * botão de recolher no cabeçalho da sua lista quando [podeRecolherLista]; o de mostrar de volta, a
     * lib desenha na faixa da lista recolhida.
     */
    fun alternarLista() {}
}

private object PainelUnicoScope : ListDetailPaneScope {
    override val emPainelUnico: Boolean = true
}

private object DoisPaineisScope : ListDetailPaneScope {
    override val emPainelUnico: Boolean = false
}

private class DoisPaineisRecolhivelScope(
    override val listaRecolhida: Boolean,
    private val onChange: (Boolean) -> Unit,
) : ListDetailPaneScope {
    override val emPainelUnico: Boolean = false
    override val podeRecolherLista: Boolean = true
    override fun alternarLista() = onChange(!listaRecolhida)
}

/**
 * Textos da faixa da lista recolhida do [ListDetailScaffold] (4 idiomas por
 * [rememberListDetailTexts]).
 */
@Immutable
data class ListDetailTexts(
    val showList: String = "Mostrar lista",
)

@Composable
fun rememberListDetailTexts(): ListDetailTexts = ListDetailTexts(
    showList = kmpStringResource(Res.string.kmplib_list_detail_show_list),
)

/** Ids de automação do [ListDetailScaffold]. */
object ListDetailTestTags {
    /** Faixa da lista recolhida (EXPANDIDA). */
    const val COLLAPSED_STRIP: String = "lista-detalhe-faixa"
    /** Botão "Mostrar lista" da faixa. */
    const val SHOW_LIST: String = "lista-detalhe-btn-mostrar"
}

/**
 * A lista está recolhida DE FATO? Só em dois painéis, só com algo escolhido (sem seleção, a lista é o
 * único caminho para escolher — recolhê-la deixaria a tela sem saída). Pura.
 */
fun isListCollapsed(windowSizeClass: WindowSizeClass, temSelecao: Boolean, listaRecolhida: Boolean): Boolean =
    windowSizeClass.temDoisPaineis && temSelecao && listaRecolhida

/**
 * Mestre-detalhe adaptativo — **dois painéis** quando cabe, uma pilha de navegação quando não cabe.
 *
 * ## O ponto que faz isto funcionar
 *
 * Em COMPACTA e MÉDIA, o detalhe **substitui** a lista; em EXPANDIDA os dois convivem. Mas o
 * `selecionado` é o MESMO estado nos dois casos — quem chama guarda um id, não "estou na tela de
 * detalhe". É isso que faz a rotação retrato↔paisagem preservar a seleção em vez de voltar para a
 * lista, que é o defeito clássico de mestre-detalhe feito com duas rotas.
 *
 * ## Por que os slots ganharam [ListDetailPaneScope] (GAP-NCX-T-04, 2.151.0)
 *
 * O componente **sabe** se está em painel único; até a 2.150.x ele não contava. Quem consome
 * precisava recalcular a regra por fora só para decidir se o detalhe leva seta de voltar — e a
 * regra tem duas formas parecidas, uma certa e uma errada:
 *
 * - `!classe.temDoisPaineis` ✅ (COMPACTA **e** MÉDIA são painel único)
 * - `classe == WindowSizeClass.COMPACTA` ❌ (esquece o tablet em RETRATO)
 *
 * Com a segunda, o detalhe de um tablet em retrato nasce **sem seta de voltar** e a pessoa fica
 * presa nele ao girar o aparelho — foi o que aconteceu no NeuroCoreX. Todo app com mestre-detalhe
 * reescreveria essa linha, com uma chance em duas de errar igual; agora o valor chega pronto, em
 * [ListDetailPaneScope.emPainelUnico].
 *
 * **Nada muda para quem já consumia**: os slots continuam sendo lambdas sem parâmetro, e quem não
 * lê `emPainelUnico` não precisa saber que ele existe.
 *
 * ```kotlin
 * ListDetailScaffold(
 *     temSelecao = idSelecionado != null,
 *     lista = { ListaDeItens(onSelecionar = { idSelecionado = it }) },
 *     detalhe = {
 *         Detalhe(
 *             item = idSelecionado,
 *             // A seta de voltar só existe quando o detalhe SUBSTITUIU a lista.
 *             onVoltar = if (emPainelUnico) ({ idSelecionado = null }) else null,
 *         )
 *     },
 *     vazio = { EmptyState(...) },   // só aparece em dois painéis, quando nada foi escolhido
 * )
 * ```
 *
 * @param lista o painel mestre.
 * @param detalhe o painel de detalhe.
 * @param vazio o que mostrar no painel de detalhe antes de escolher algo. Só existe em dois
 *   painéis — logo o `emPainelUnico` dele é sempre `false`.
 * @param fracaoDaLista fração da largura que a lista ocupa em dois painéis (ignorada com
 *   [larguraDaLista]).
 * @param larguraDaLista largura FIXA da lista em dois painéis (2.322.0, GAP-VIT-T04). Fração faz a
 *   lista mudar de tamanho entre um tablet de 1024 dp e outro de 1366 dp; o protótipo desenha em dp
 *   (Vitalis: 360 dp). `Dp.Unspecified` = usa [fracaoDaLista], como antes.
 * @param listaRecolhida recolhe a lista em EXPANDIDA e dá a largura toda ao detalhe — só vale com algo
 *   escolhido ([isListCollapsed]). Estado do chamador (guarde no ViewModel ou em `rememberSaveable`
 *   para sobreviver à rotação).
 * @param onListaRecolhidaChange liga o recolher: o escopo passa a ter [ListDetailPaneScope.podeRecolherLista]
 *   e [ListDetailPaneScope.alternarLista] (ponha o botão de recolher no cabeçalho da lista), e a lib
 *   desenha, no lugar da lista recolhida, uma faixa de [larguraDaFaixaRecolhida] com "Mostrar lista".
 *   `null` = não recolhível (o de sempre).
 */
@Composable
fun ListDetailScaffold(
    temSelecao: Boolean,
    lista: @Composable ListDetailPaneScope.() -> Unit,
    detalhe: @Composable ListDetailPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    vazio: @Composable ListDetailPaneScope.() -> Unit = {},
    fracaoDaLista: Float = 0.38f,
    windowSizeClass: WindowSizeClass = LocalWindowSizeClass.current,
    larguraDaLista: Dp = Dp.Unspecified,
    listaRecolhida: Boolean = false,
    onListaRecolhidaChange: ((Boolean) -> Unit)? = null,
    larguraDaFaixaRecolhida: Dp = 48.dp,
    textos: ListDetailTexts = rememberListDetailTexts(),
) {
    // Painel único é `!temDoisPaineis` — COMPACTA **e** MÉDIA. Escrever `== COMPACTA` aqui é o
    // defeito que este parâmetro existe para nunca mais ser reescrito por quem chama.
    if (!windowSizeClass.temDoisPaineis) {
        // O detalhe SUBSTITUI a lista. Quem controla a volta continua sendo o chamador (só ele sabe
        // o que "voltar" significa ali) — o que a lib faz é dizer QUANDO ela é necessária.
        Box(modifier = modifier.fillMaxSize()) {
            if (temSelecao) PainelUnicoScope.detalhe() else PainelUnicoScope.lista()
        }
        return
    }

    val recolhida = isListCollapsed(windowSizeClass, temSelecao, listaRecolhida)
    val scope: ListDetailPaneScope = if (onListaRecolhidaChange != null) {
        DoisPaineisRecolhivelScope(recolhida, onListaRecolhidaChange)
    } else {
        DoisPaineisScope
    }
    val larguraFixa = larguraDaLista != Dp.Unspecified
    val reduzirMovimento = LocalReduceMotion.current

    Row(modifier = modifier.fillMaxSize()) {
        // Lista: some com animação horizontal ao recolher (sem animação com "reduzir movimento").
        AnimatedVisibility(
            visible = !recolhida,
            enter = if (reduzirMovimento) EnterTransition.None else expandHorizontally(),
            exit = if (reduzirMovimento) ExitTransition.None else shrinkHorizontally(),
            modifier = if (larguraFixa) Modifier.fillMaxHeight() else Modifier.fillMaxHeight().weight(fracaoDaLista),
        ) {
            Row(if (larguraFixa) Modifier.fillMaxHeight() else Modifier.fillMaxSize()) {
                Box(
                    modifier = if (larguraFixa) {
                        Modifier.fillMaxHeight().width(larguraDaLista)
                    } else {
                        Modifier.fillMaxHeight().weight(1f)
                    },
                ) { scope.lista() }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        if (recolhida && onListaRecolhidaChange != null) {
            FaixaDaListaRecolhida(
                largura = larguraDaFaixaRecolhida,
                rotulo = textos.showList,
                onMostrar = { onListaRecolhidaChange(false) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(if (larguraFixa || recolhida) 1f else 1f - fracaoDaLista),
        ) {
            if (temSelecao) scope.detalhe() else scope.vazio()
        }
    }
}

/** Faixa fina que fica no lugar da lista recolhida, com o "Mostrar lista". */
@Composable
private fun FaixaDaListaRecolhida(largura: Dp, rotulo: String, onMostrar: () -> Unit) {
    Row(Modifier.fillMaxHeight().testTag(ListDetailTestTags.COLLAPSED_STRIP)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(largura)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onMostrar, modifier = Modifier.testTag(ListDetailTestTags.SHOW_LIST)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = rotulo)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
