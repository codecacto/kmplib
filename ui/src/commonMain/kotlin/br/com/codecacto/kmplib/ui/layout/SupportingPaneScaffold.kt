package br.com.codecacto.kmplib.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_supporting_pane_close
import br.com.codecacto.kmplib.generated.resources.kmplib_supporting_pane_collapse
import br.com.codecacto.kmplib.generated.resources.kmplib_supporting_pane_expand
import br.com.codecacto.kmplib.generated.resources.kmplib_supporting_pane_title
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// SupportingPaneScaffold (2.322.0, GAP-VIT-T02) — o *supporting pane* do Material 3 adaptativo
// (`SupportingPaneScaffold` do androidx.compose.material3.adaptive, que não publica para iOS), com
// o comportamento do protótipo do tablet do Vitalis: editor no centro, apoio clínico AO LADO.
// ---------------------------------------------------------------------------------------------

/** Como o painel de apoio está sendo mostrado agora. */
enum class SupportingPaneMode {
    /** EXPANDIDA, aberto: principal + apoio lado a lado. */
    SIDE_BY_SIDE,

    /** EXPANDIDA, recolhido num trilho fino de ícones: o principal ganha a largura. */
    COLLAPSED,

    /** MÉDIA/COMPACTA, aberto: o apoio entra pela direita, por cima, com véu. */
    OVERLAY,

    /** MÉDIA/COMPACTA, fechado: só o principal. */
    HIDDEN,
}

/**
 * Modo do painel para a classe de janela e o estado. Pura, testável.
 *
 * EXPANDIDA decide por [expandedOpen] (e nunca recolhe se não for [collapsible]); MÉDIA e COMPACTA por
 * [overlayOpen] — dois estados separados de propósito: o painel aberto ao lado na paisagem NÃO pode
 * reaparecer cobrindo o editor quando o tablet vira retrato.
 */
fun supportingPaneModeFor(
    windowSizeClass: WindowSizeClass,
    expandedOpen: Boolean,
    overlayOpen: Boolean,
    collapsible: Boolean = true,
): SupportingPaneMode = if (windowSizeClass == WindowSizeClass.EXPANDIDA) {
    if (expandedOpen || !collapsible) SupportingPaneMode.SIDE_BY_SIDE else SupportingPaneMode.COLLAPSED
} else {
    if (overlayOpen) SupportingPaneMode.OVERLAY else SupportingPaneMode.HIDDEN
}

/** Aba efetiva: a escolhida, se existir entre [tabs]; senão a primeira; sem abas, `null`. Pura. */
fun resolveSupportingPaneTab(tabs: List<SupportingPaneTab>, selectedId: String?): String? =
    tabs.firstOrNull { it.id == selectedId }?.id ?: tabs.firstOrNull()?.id

/**
 * Arrastar o painel sobreposto em direção à borda o fecha? Fecha passando de [fraction] da largura ou
 * soltando rápido ([velocityThresholdPx] px/s). [dragOffsetPx] e [velocityPx] são positivos no sentido
 * de FECHAR (para a borda). Pura.
 */
fun shouldDismissSupportingPane(
    dragOffsetPx: Float,
    velocityPx: Float,
    paneWidthPx: Float,
    fraction: Float = 0.35f,
    velocityThresholdPx: Float = 1200f,
): Boolean = dragOffsetPx > paneWidthPx * fraction || velocityPx > velocityThresholdPx

/** Uma aba do painel de apoio (Histórico, Exames, Pré-consulta, Arquivos…). */
@Immutable
data class SupportingPaneTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: NavBadge? = null,
)

/**
 * Estado do painel de apoio — sobrevive à rotação e à morte do processo (`rememberSaveable` em
 * [rememberSupportingPaneState]).
 *
 * @property isExpandedOpen EXPANDIDA: aberto ao lado (`true`) ou recolhido no trilho fino.
 * @property isOverlayOpen MÉDIA/COMPACTA: sobreposto aberto.
 * @property selectedTabId aba escolhida (`null` = a primeira).
 */
@Stable
class SupportingPaneState(
    initialExpandedOpen: Boolean = true,
    initialOverlayOpen: Boolean = false,
    initialTabId: String? = null,
) {
    var isExpandedOpen: Boolean by mutableStateOf(initialExpandedOpen)
    var isOverlayOpen: Boolean by mutableStateOf(initialOverlayOpen)
    var selectedTabId: String? by mutableStateOf(initialTabId)

    fun selectTab(tabId: String) {
        selectedTabId = tabId
    }

    /** Abre ao lado (EXPANDIDA), opcionalmente numa aba. */
    fun expand(tabId: String? = null) {
        tabId?.let { selectedTabId = it }
        isExpandedOpen = true
    }

    /** Recolhe no trilho fino (EXPANDIDA). */
    fun collapse() {
        isExpandedOpen = false
    }

    /** Abre por cima (MÉDIA/COMPACTA), opcionalmente numa aba. */
    fun openOverlay(tabId: String? = null) {
        tabId?.let { selectedTabId = it }
        isOverlayOpen = true
    }

    fun closeOverlay() {
        isOverlayOpen = false
    }

    companion object {
        val Saver: Saver<SupportingPaneState, Any> = listSaver(
            save = { listOf(it.isExpandedOpen, it.isOverlayOpen, it.selectedTabId) },
            restore = {
                SupportingPaneState(
                    initialExpandedOpen = it[0] as Boolean,
                    initialOverlayOpen = it[1] as Boolean,
                    initialTabId = it[2] as String?,
                )
            },
        )

        internal const val PREFS_PREFIX = "kmplib.supporting_pane."
    }
}

/**
 * Estado do painel, guardado na rotação. Com [persistKey], o aberto/recolhido da EXPANDIDA e a aba
 * também ficam **no aparelho** (`AppPreferences` da lib) — "o médico recolheu o apoio, abre recolhido
 * amanhã". O sobreposto da MÉDIA nunca é persistido (abriria cobrindo o editor).
 */
@Composable
fun rememberSupportingPaneState(
    initiallyOpen: Boolean = true,
    initialTabId: String? = null,
    persistKey: String? = null,
): SupportingPaneState {
    val state = rememberSaveable(saver = SupportingPaneState.Saver) {
        SupportingPaneState(initialExpandedOpen = initiallyOpen, initialTabId = initialTabId)
    }
    if (persistKey != null) {
        // Lido UMA vez por instância salva: depois da rotação o estado já é o certo e não pode ser
        // sobrescrito pelo que estava no disco.
        var carregado by rememberSaveable(persistKey) { mutableStateOf(false) }
        LaunchedEffect(persistKey) {
            val prefs = runCatching { appPreferences() }.getOrNull() ?: return@LaunchedEffect
            val base = SupportingPaneState.PREFS_PREFIX + persistKey.trim()
            if (!carregado) {
                runCatching {
                    if (prefs.has("$base.open")) state.isExpandedOpen = prefs.getBoolean("$base.open", initiallyOpen)
                    prefs.getString("$base.tab", "").takeIf { it.isNotEmpty() }?.let { state.selectedTabId = it }
                }
                carregado = true
            }
            snapshotFlow { state.isExpandedOpen to state.selectedTabId }
                .distinctUntilChanged()
                .collect { (aberto, aba) ->
                    runCatching {
                        prefs.setBoolean("$base.open", aberto)
                        if (aba != null) prefs.setString("$base.tab", aba) else prefs.remove("$base.tab")
                    }
                }
        }
    }
    return state
}

/**
 * O que os slots do [SupportingPaneScaffold] sabem — e podem pedir.
 *
 * O principal usa [mode] para decidir se mostra o botão "Apoio" (só faz sentido em
 * [SupportingPaneMode.HIDDEN]/[SupportingPaneMode.OVERLAY]) e chama [showPane]. O painel lê
 * [selectedTabId] para saber o que desenhar.
 */
@Stable
interface SupportingPaneScope {
    val mode: SupportingPaneMode
    val isPaneVisible: Boolean
        get() = mode == SupportingPaneMode.SIDE_BY_SIDE || mode == SupportingPaneMode.OVERLAY
    val selectedTabId: String?

    /** Mostra o painel no modo da janela atual (ao lado na EXPANDIDA; por cima na MÉDIA). */
    fun showPane(tabId: String? = null)

    /** Recolhe (EXPANDIDA, se recolhível) ou fecha o sobreposto. */
    fun hidePane()

    fun togglePane() = if (isPaneVisible) hidePane() else showPane()
}

private class SupportingPaneScopeImpl(
    override val mode: SupportingPaneMode,
    override val selectedTabId: String?,
    private val state: SupportingPaneState,
    private val collapsible: Boolean,
) : SupportingPaneScope {
    override fun showPane(tabId: String?) {
        when (mode) {
            SupportingPaneMode.SIDE_BY_SIDE, SupportingPaneMode.COLLAPSED -> state.expand(tabId)
            SupportingPaneMode.OVERLAY, SupportingPaneMode.HIDDEN -> state.openOverlay(tabId)
        }
    }

    override fun hidePane() {
        when (mode) {
            SupportingPaneMode.SIDE_BY_SIDE, SupportingPaneMode.COLLAPSED -> if (collapsible) state.collapse()
            SupportingPaneMode.OVERLAY, SupportingPaneMode.HIDDEN -> state.closeOverlay()
        }
    }
}

/** Textos do painel (4 idiomas por [rememberSupportingPaneTexts]). */
@Immutable
data class SupportingPaneTexts(
    /** Título do painel para o leitor de tela (anúncio do painel) e do cabeçalho sem abas. */
    val paneTitle: String = "Painel de apoio",
    val collapse: String = "Recolher painel",
    val expand: String = "Abrir painel",
    val close: String = "Fechar painel",
)

@Composable
fun rememberSupportingPaneTexts(): SupportingPaneTexts = SupportingPaneTexts(
    paneTitle = kmpStringResource(Res.string.kmplib_supporting_pane_title),
    collapse = kmpStringResource(Res.string.kmplib_supporting_pane_collapse),
    expand = kmpStringResource(Res.string.kmplib_supporting_pane_expand),
    close = kmpStringResource(Res.string.kmplib_supporting_pane_close),
)

/** Cores do painel; `Color.Unspecified` = papel do tema. */
@Immutable
data class SupportingPaneColors(
    /** Fundo do painel; default `surfaceContainerLow`. */
    val paneContainer: Color = Color.Unspecified,
    /** Fundo do cabeçalho (abas) e do trilho recolhido; default `surface`. */
    val headerContainer: Color = Color.Unspecified,
    /** Borda entre o principal e o painel; default `outlineVariant`. */
    val divider: Color = Color.Unspecified,
    /** Véu do sobreposto; default `scrim` a 32 %. */
    val scrim: Color = Color.Unspecified,
    /** Aba ativa (texto e indicador); default `primary`. */
    val selectedTab: Color = Color.Unspecified,
    /** Aba inativa; default `onSurfaceVariant`. */
    val unselectedTab: Color = Color.Unspecified,
)

/** Ids de automação do [SupportingPaneScaffold]. */
object SupportingPaneTestTags {
    const val PANE: String = "painel-apoio"
    const val SCRIM: String = "painel-apoio-veu"
    const val COLLAPSED_RAIL: String = "painel-apoio-trilho"
    const val TOGGLE: String = "painel-apoio-btn-alternar"
    const val CLOSE: String = "painel-apoio-btn-fechar"
    fun tab(id: String): String = "painel-apoio-aba-$id"
    fun railTab(id: String): String = "painel-apoio-trilho-$id"
}

/**
 * Conteúdo principal + **painel de apoio**, adaptativo:
 *
 * | Classe | Painel |
 * |---|---|
 * | EXPANDIDA | ao lado ([paneWidth]); recolhe num trilho fino ([collapsedWidth]) com os ícones das abas — tocar num ícone reabre naquela aba |
 * | MÉDIA | por cima, entra pela direita ([overlayPaneWidth]) com véu; fecha pelo véu, pelo "×", pelo voltar do sistema ou arrastando para a borda |
 * | COMPACTA | igual à MÉDIA, na largura toda |
 *
 * Aberto/recolhido, sobreposto e aba vivem em [state] (`rememberSaveable`): girar o aparelho não perde
 * nada; ao trocar de classe (retrato ↔ paisagem) o sobreposto fecha — senão o editor reabriria coberto.
 *
 * Acessibilidade: o painel é um **painel** (`paneTitle` — o TalkBack anuncia quando ele aparece) e um
 * grupo de travessia; aberto por cima, o principal sai da árvore de acessibilidade (é modal) e o foco
 * vai para o "×".
 *
 * ```kotlin
 * val apoio = rememberSupportingPaneState(persistKey = "consulta")
 * SupportingPaneScaffold(
 *     state = apoio,
 *     tabs = listOf(
 *         SupportingPaneTab("historico", "Histórico", Icons.Outlined.History),
 *         SupportingPaneTab("exames", "Exames", Icons.Outlined.Science),
 *     ),
 *     supportingPane = { when (selectedTabId) { "exames" -> Exames(); else -> Historico() } },
 * ) {
 *     Editor(
 *         // botão "Apoio" só quando o painel não está ao lado
 *         onAbrirApoio = if (mode == SupportingPaneMode.HIDDEN) ({ showPane() }) else null,
 *     )
 * }
 * ```
 *
 * @param supportingPane conteúdo da aba ativa ([SupportingPaneScope.selectedTabId]). Rola por conta dele.
 * @param tabs abas do cabeçalho; vazio = cabeçalho com [title] (ou [SupportingPaneTexts.paneTitle]).
 * @param header cabeçalho PRÓPRIO do protótipo, no lugar do padrão (abas + recolher/fechar). Use
 *   `hidePane()` do escopo no botão de fechar.
 * @param collapsible `false` = na EXPANDIDA o painel nunca recolhe (sem botão, sem trilho fino).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
fun SupportingPaneScaffold(
    state: SupportingPaneState,
    supportingPane: @Composable SupportingPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<SupportingPaneTab> = emptyList(),
    title: String? = null,
    header: (@Composable SupportingPaneScope.() -> Unit)? = null,
    paneWidth: Dp = 380.dp,
    overlayPaneWidth: Dp = 400.dp,
    collapsedWidth: Dp = 48.dp,
    collapsible: Boolean = true,
    windowSizeClass: WindowSizeClass = LocalWindowSizeClass.current,
    colors: SupportingPaneColors = SupportingPaneColors(),
    texts: SupportingPaneTexts = rememberSupportingPaneTexts(),
    badgeTexts: AdaptiveNavBadgeTexts = rememberAdaptiveNavBadgeTexts(),
    content: @Composable SupportingPaneScope.() -> Unit,
) {
    // Troca de classe (girar o tablet) fecha o sobreposto. A classe anterior é SALVA: a recriação da
    // Activity na rotação não pode ser lida como "mesma classe".
    var classeAnterior by rememberSaveable { mutableStateOf(windowSizeClass.name) }
    val classeMudou = classeAnterior != windowSizeClass.name
    if (classeMudou) {
        // Gravar estado durante a composição é proibido; o quadro atual já ignora o sobreposto.
        SideEffect {
            classeAnterior = windowSizeClass.name
            state.isOverlayOpen = false
        }
    }

    val mode = supportingPaneModeFor(
        windowSizeClass = windowSizeClass,
        expandedOpen = state.isExpandedOpen,
        overlayOpen = state.isOverlayOpen && !classeMudou,
        collapsible = collapsible,
    )
    val abaAtual = resolveSupportingPaneTab(tabs, state.selectedTabId)
    val scope = SupportingPaneScopeImpl(mode, abaAtual, state, collapsible)

    val cores = MaterialTheme.colorScheme
    val fundoPainel = colors.paneContainer.takeOrElse { cores.surfaceContainerLow }
    val fundoCabecalho = colors.headerContainer.takeOrElse { cores.surface }
    val borda = colors.divider.takeOrElse { cores.outlineVariant }
    val tituloDoPainel = title ?: texts.paneTitle

    val painel: @Composable (Modifier, Boolean) -> Unit = { mod, sobreposto ->
        val focoNoFechar = remember { FocusRequester() }
        if (sobreposto) {
            LaunchedEffect(Unit) { runCatching { focoNoFechar.requestFocus() } }
        }
        Column(
            modifier = mod
                .background(fundoPainel)
                .testTag(SupportingPaneTestTags.PANE)
                .semantics {
                    paneTitle = tituloDoPainel
                    isTraversalGroup = true
                },
        ) {
            if (header != null) {
                scope.header()
            } else {
                CabecalhoDoPainel(
                    tabs = tabs,
                    abaAtual = abaAtual,
                    titulo = tituloDoPainel,
                    sobreposto = sobreposto,
                    mostrarAcao = sobreposto || collapsible,
                    fundo = fundoCabecalho,
                    colors = colors,
                    texts = texts,
                    badgeTexts = badgeTexts,
                    focoNoFechar = focoNoFechar,
                    onSelect = state::selectTab,
                    onAcao = scope::hidePane,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { scope.supportingPane() }
        }
    }

    when (mode) {
        SupportingPaneMode.SIDE_BY_SIDE, SupportingPaneMode.COLLAPSED -> Row(modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { scope.content() }
            Box(Modifier.fillMaxHeight().width(1.dp).background(borda))
            if (mode == SupportingPaneMode.SIDE_BY_SIDE) {
                painel(Modifier.width(paneWidth).fillMaxHeight(), false)
            } else {
                TrilhoRecolhido(
                    largura = collapsedWidth,
                    tabs = tabs,
                    fundo = fundoCabecalho,
                    texts = texts,
                    badgeTexts = badgeTexts,
                    onExpandir = { state.expand() },
                    onAba = { state.expand(it) },
                )
            }
        }

        SupportingPaneMode.OVERLAY, SupportingPaneMode.HIDDEN -> BoxWithConstraints(modifier.fillMaxSize()) {
            val aberto = mode == SupportingPaneMode.OVERLAY
            val reduzir = LocalReduceMotion.current
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val larguraDoSobreposto = if (windowSizeClass == WindowSizeClass.COMPACTA) {
                maxWidth
            } else {
                minOf(overlayPaneWidth, maxWidth)
            }

            Box(
                Modifier
                    .fillMaxSize()
                    // Por cima é modal: o principal sai da árvore de acessibilidade enquanto o painel
                    // está aberto, senão o leitor de tela navegaria por baixo do véu.
                    .then(if (aberto) Modifier.clearAndSetSemantics { } else Modifier),
            ) { scope.content() }

            AnimatedVisibility(
                visible = aberto,
                enter = if (reduzir) EnterTransition.None else fadeIn(),
                exit = if (reduzir) ExitTransition.None else fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.scrim.takeOrElse { cores.scrim.copy(alpha = 0.32f) })
                        .testTag(SupportingPaneTestTags.SCRIM)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = state::closeOverlay,
                        )
                        .semantics {
                            contentDescription = texts.close
                            onClick { state.closeOverlay(); true }
                        },
                )
            }

            BackHandler(enabled = aberto, onBack = state::closeOverlay)

            val densidade = LocalDensity.current
            var arrasto by remember { mutableFloatStateOf(0f) }
            val coroutine = rememberCoroutineScope()
            val larguraPx = with(densidade) { larguraDoSobreposto.toPx() }
            // Positivo = em direção à borda (fechar), nas duas direções de layout.
            val sentido = if (rtl) -1f else 1f
            val arrastavel = rememberDraggableState { delta ->
                arrasto = (arrasto + delta * sentido).coerceAtLeast(0f)
            }

            AnimatedVisibility(
                visible = aberto,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = if (reduzir) EnterTransition.None else slideInHorizontally { if (rtl) -it else it },
                exit = if (reduzir) ExitTransition.None else slideOutHorizontally { if (rtl) -it else it },
            ) {
                LaunchedEffect(Unit) { arrasto = 0f }
                Surface(
                    shadowElevation = 8.dp,
                    color = fundoPainel,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(larguraDoSobreposto)
                        .offset { IntOffset((arrasto * sentido).roundToInt(), 0) }
                        .draggable(
                            state = arrastavel,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocidade ->
                                if (shouldDismissSupportingPane(arrasto, velocidade * sentido, larguraPx)) {
                                    state.closeOverlay()
                                } else {
                                    coroutine.launch { animate(arrasto, 0f) { v, _ -> arrasto = v } }
                                }
                            },
                        ),
                ) {
                    painel(Modifier.fillMaxSize(), true)
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDoPainel(
    tabs: List<SupportingPaneTab>,
    abaAtual: String?,
    titulo: String,
    sobreposto: Boolean,
    mostrarAcao: Boolean,
    fundo: Color,
    colors: SupportingPaneColors,
    texts: SupportingPaneTexts,
    badgeTexts: AdaptiveNavBadgeTexts,
    focoNoFechar: FocusRequester,
    onSelect: (String) -> Unit,
    onAcao: () -> Unit,
) {
    val cores = MaterialTheme.colorScheme
    val ativa = colors.selectedTab.takeOrElse { cores.primary }
    val inativa = colors.unselectedTab.takeOrElse { cores.onSurfaceVariant }
    Column(Modifier.fillMaxWidth().background(fundo)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tabs.isNotEmpty()) {
                val indice = tabs.indexOfFirst { it.id == abaAtual }.coerceAtLeast(0)
                PrimaryScrollableTabRow(
                    selectedTabIndex = indice,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = ativa,
                    edgePadding = 8.dp,
                    divider = {},
                    minTabWidth = 0.dp,
                ) {
                    tabs.forEachIndexed { i, aba ->
                        val descricao = badgeTexts.describe(aba.label, aba.badge)
                        Tab(
                            selected = i == indice,
                            onClick = { onSelect(aba.id) },
                            selectedContentColor = ativa,
                            unselectedContentColor = inativa,
                            modifier = Modifier
                                .testTag(SupportingPaneTestTags.tab(aba.id))
                                .then(if (descricao != null) Modifier.semantics { contentDescription = descricao } else Modifier),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = aba.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val selo = aba.badge
                                if (selo.isVisible()) {
                                    Spacer(Modifier.width(6.dp))
                                    // Mudo: o número já está na descrição da aba.
                                    val mudo = Modifier.clearAndSetSemantics { }
                                    when (selo) {
                                        is NavBadge.Dot -> Badge(modifier = mudo)
                                        is NavBadge.Count -> Badge(modifier = mudo) {
                                            Text(navBadgeLabel(selo.count, selo.max).orEmpty())
                                        }
                                        null -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = cores.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                )
            }
            if (mostrarAcao) {
                IconButton(
                    onClick = onAcao,
                    modifier = Modifier
                        .focusRequester(focoNoFechar)
                        .testTag(if (sobreposto) SupportingPaneTestTags.CLOSE else SupportingPaneTestTags.TOGGLE),
                ) {
                    Icon(
                        imageVector = if (sobreposto) Icons.Filled.Close else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (sobreposto) texts.close else texts.collapse,
                        tint = cores.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider.takeOrElse { cores.outlineVariant })
    }
}

@Composable
private fun TrilhoRecolhido(
    largura: Dp,
    tabs: List<SupportingPaneTab>,
    fundo: Color,
    texts: SupportingPaneTexts,
    badgeTexts: AdaptiveNavBadgeTexts,
    onExpandir: () -> Unit,
    onAba: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = largura)
            .width(largura)
            .background(fundo)
            .testTag(SupportingPaneTestTags.COLLAPSED_RAIL)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onExpandir, modifier = Modifier.testTag(SupportingPaneTestTags.TOGGLE)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = texts.expand)
        }
        tabs.forEach { aba ->
            IconButton(
                onClick = { onAba(aba.id) },
                modifier = Modifier
                    .testTag(SupportingPaneTestTags.railTab(aba.id))
                    .semantics { contentDescription = badgeTexts.describe(aba.label, aba.badge) ?: aba.label },
            ) {
                AdaptiveBadgedIcon(aba.id, aba.badge) {
                    Icon(aba.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
