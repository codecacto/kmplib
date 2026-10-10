package br.com.codecacto.kmplib.ui.compare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import br.com.codecacto.kmplib.platform.automation.exposeTestTagsAsResourceId
import br.com.codecacto.kmplib.ui.components.PhotoSource
import br.com.codecacto.kmplib.ui.components.SegmentedControl
import br.com.codecacto.kmplib.ui.components.SkeletonBox
import br.com.codecacto.kmplib.ui.components.rememberPhotoSourceRequest
import coil3.compose.AsyncImage
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ids de automação do comparador (Maestro). */
object PhotoCompareTestTags {
    /** O componente inteiro. */
    const val ROOT: String = "comparar"

    /** Grupo do seletor de modo; os segmentos são `comparar-modo-deslizar` e `comparar-modo-lado-a-lado`. */
    const val MODES: String = "comparar-modo"

    /** O quadro do deslizante (é nele que o leitor de tela ajusta a divisória). */
    const val SLIDER: String = "comparar-divisoria"

    /** O quadro do lado a lado. */
    const val SIDE_BY_SIDE: String = "comparar-lado-a-lado"

    /** Botão de tela cheia. */
    const val FULL_SCREEN: String = "comparar-btn-tela-cheia"

    /** Linha do tempo. */
    const val TIMELINE: String = "comparar-linha-do-tempo"

    /** Cartão de uma sessão na linha do tempo: `comparar-sessao-<id>`. */
    fun session(id: String): String = "comparar-sessao-$id"

    /** Chip de uma vista: `comparar-vista-<chave>`. */
    fun view(key: String): String = "comparar-vista-$key"
}

/** Medidas padrão. */
object PhotoCompareDefaults {
    /** Altura máxima do quadro fora da tela cheia: a foto inteira cabe sem rolar num telefone. */
    val MaxFrameHeight: Dp = 520.dp

    /** Espaço entre os dois quadros do lado a lado. */
    val SideBySideGap: Dp = 8.dp

    /** Diâmetro da alça da divisória — alvo de toque de 44 dp. */
    val GripSize: Dp = 44.dp
}

/**
 * ============================================================================
 *  COMPARADOR DE FOTOS — controle deslizante, lado a lado e linha do tempo
 * ============================================================================
 *
 * O par mobile do `PhotoCompare` da weblib (`GAP-VIT-K02`), com o MESMO contrato de dados
 * ([CompareSession]/[ComparePhoto], `@Serializable`) e as MESMAS regras ([CompareLogic.kt]: ordem
 * pela data, par inicial, toque na linha do tempo, casamento por vista). Nasceu no Vitalis (fotos de
 * evolução do paciente) e serve a qualquer "antes e depois" — obra, avaliação, região do corpo.
 *
 * ```kotlin
 * val estado = rememberPhotoCompareState()
 * var telaCheia by remember { mutableStateOf(false) }
 * PhotoCompare(
 *     sessions = sessoes,
 *     state = estado,
 *     loading = carregando,
 *     photoSource = { foto -> PhotoSource.authenticated(api, foto.src, accountId = sessao.userId) },
 *     onFullScreen = { telaCheia = true },
 * )
 * if (telaCheia) PhotoCompareDialog(sessoes, onDismiss = { telaCheia = false }, state = estado, photoSource = …)
 * ```
 *
 * ## O que já vem
 * - **Dois modos** ([CompareMode]): deslizante (a foto de DEPOIS por cima, recortada à direita da
 *   divisória) e lado a lado (duas colunas, também no telefone). A **linha do tempo**
 *   ([CompareTimeline]) escolhe o par, nos dois modos — com mais de duas sessões, como na weblib.
 * - **Divisória arrastável e acessível**: arrasto horizontal em qualquer ponto da foto (a rolagem
 *   vertical da tela continua passando — ver [compareDragIntent]); leitor de tela ajusta pelo
 *   `setProgress` (TalkBack: deslizar para cima/baixo; VoiceOver: item ajustável) e anuncia "40%
 *   antes, 60% depois".
 * - **Zoom e pan SINCRONIZADOS**: pinça com dois dedos e duplo toque ampliam as DUAS fotos juntas
 *   (um [CompareTransform] só, aplicado às duas camadas / aos dois quadros) — comparar o mesmo detalhe
 *   é o motivo do zoom. Ampliado, o arrasto de um dedo move a imagem; a alça continua arrastável.
 * - **Tela cheia** ("mostrar ao paciente") pelo [PhotoCompareDialog], com o mesmo estado.
 * - **Proporção fixa** do quadro, esqueleto até a foto carregar, aviso se ela falhar, aviso quando
 *   a sessão não tem aquela vista; título anunciado ao trocar o par (`liveRegion`).
 * - **Foto privada** por [photoSource] (`PhotoSource.authenticated`: Bearer, sem cache de disco).
 *
 * @param sessions sessões em qualquer ordem — a lib ordena pela `date`. Sessão sem foto não entra.
 * @param state modo, par, posição, vista e zoom ([rememberPhotoCompareState]).
 * @param modes modos oferecidos. Com um só, o seletor de modo não aparece.
 * @param showTimeline linha do tempo. `null` (default) = só com MAIS de duas sessões.
 * @param aspectRatio proporção do quadro (largura/altura). Default 3:4.
 * @param maxFrameHeight altura máxima do quadro.
 * @param fit [CompareFit.CONTAIN] (default, a foto inteira) ou [CompareFit.COVER].
 * @param showTitle mostra o título ("Comparando 12/03/2026 com 12/10/2026").
 * @param title título próprio no lugar do padrão.
 * @param loading esqueleto na MESMA casca enquanto as sessões chegam (senão a lista vazia do
 *   primeiro instante diria "Nenhuma foto ainda." para quem tem fotos).
 * @param emptyContent o que aparece sem nenhuma sessão com foto. Default: cartão "Nenhuma foto ainda.".
 * @param photoSource como carregar cada foto. Default: `PhotoSource.Url(foto.src)`.
 * @param timeZone fuso para data COM hora. Data civil ("AAAA-MM-DD") não depende dele.
 * @param formatDate formata uma data crua. Default: formato da região do aparelho.
 * @param onFullScreen quando informado, mostra o botão de tela cheia.
 * @param texts textos ([rememberPhotoCompareTexts]).
 */
@Composable
fun PhotoCompare(
    sessions: List<CompareSession>,
    modifier: Modifier = Modifier,
    state: PhotoCompareState = rememberPhotoCompareState(),
    modes: List<CompareMode> = CompareMode.ALL,
    showTimeline: Boolean? = null,
    aspectRatio: Float = DEFAULT_COMPARE_ASPECT_RATIO,
    maxFrameHeight: Dp = PhotoCompareDefaults.MaxFrameHeight,
    fit: CompareFit = CompareFit.CONTAIN,
    showTitle: Boolean = true,
    title: String? = null,
    loading: Boolean = false,
    emptyContent: (@Composable () -> Unit)? = null,
    photoSource: (ComparePhoto) -> PhotoSource = { PhotoSource.Url(it.src) },
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    formatDate: ((String) -> String)? = null,
    onFullScreen: (() -> Unit)? = null,
    texts: PhotoCompareTexts = rememberPhotoCompareTexts(),
) {
    PhotoCompareLayout(
        sessions = sessions,
        modifier = modifier,
        state = state,
        modes = modes,
        showTimeline = showTimeline,
        aspectRatio = aspectRatio,
        maxFrameHeight = maxFrameHeight,
        fillHeight = false,
        fit = fit,
        showTitle = showTitle,
        title = title,
        loading = loading,
        emptyContent = emptyContent,
        photoSource = photoSource,
        timeZone = timeZone,
        formatDate = formatDate,
        onFullScreen = onFullScreen,
        texts = texts,
        headerEnd = null,
    )
}

/**
 * O comparador em TELA CHEIA — o "mostrar ao paciente". O quadro ocupa a altura que sobra; passe o
 * MESMO [state] do [PhotoCompare] da tela para abrir (e voltar) no mesmo par, vista, divisória e zoom.
 *
 * Fecha pelo X (`dialogo-btn-fechar`) ou pelo voltar do sistema; tocar fora não fecha (é tela cheia,
 * e o toque é o gesto de arrastar a divisória).
 */
@Composable
fun PhotoCompareDialog(
    sessions: List<CompareSession>,
    onDismiss: () -> Unit,
    state: PhotoCompareState = rememberPhotoCompareState(),
    modes: List<CompareMode> = CompareMode.ALL,
    showTimeline: Boolean? = null,
    aspectRatio: Float = DEFAULT_COMPARE_ASPECT_RATIO,
    fit: CompareFit = CompareFit.CONTAIN,
    showTitle: Boolean = true,
    title: String? = null,
    photoSource: (ComparePhoto) -> PhotoSource = { PhotoSource.Url(it.src) },
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    formatDate: ((String) -> String)? = null,
    texts: PhotoCompareTexts = rememberPhotoCompareTexts(),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // Outra janela: religa o `testTagsAsResourceId` (ver `DialogTestTags`).
                .exposeTestTagsAsResourceId()
                .testTag(DialogTestTags.CONTAINER),
            color = MaterialTheme.colorScheme.surface,
        ) {
            PhotoCompareLayout(
                sessions = sessions,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
                state = state,
                modes = modes,
                showTimeline = showTimeline,
                aspectRatio = aspectRatio,
                maxFrameHeight = Dp.Infinity,
                fillHeight = true,
                fit = fit,
                showTitle = showTitle,
                title = title,
                loading = false,
                emptyContent = null,
                photoSource = photoSource,
                timeZone = timeZone,
                formatDate = formatDate,
                onFullScreen = null,
                texts = texts,
                headerEnd = {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag(DialogTestTags.BTN_FECHAR)) {
                        Icon(Icons.Filled.Close, contentDescription = texts.close)
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoCompareLayout(
    sessions: List<CompareSession>,
    modifier: Modifier,
    state: PhotoCompareState,
    modes: List<CompareMode>,
    showTimeline: Boolean?,
    aspectRatio: Float,
    maxFrameHeight: Dp,
    fillHeight: Boolean,
    fit: CompareFit,
    showTitle: Boolean,
    title: String?,
    loading: Boolean,
    emptyContent: (@Composable () -> Unit)?,
    photoSource: (ComparePhoto) -> PhotoSource,
    timeZone: TimeZone,
    formatDate: ((String) -> String)?,
    onFullScreen: (() -> Unit)?,
    texts: PhotoCompareTexts,
    headerEnd: (@Composable () -> Unit)?,
) {
    val fmt: (String) -> String = formatDate ?: { formatCompareDate(it, timeZone) }
    val sorted = remember(sessions, timeZone) { comparableSessions(sessions, timeZone) }
    val ids = remember(sorted) { sorted.map { it.id } }
    val views = remember(sorted, texts) { compareViews(sorted, texts.viewFallback) }
    val offered = CompareMode.ALL.filter { it in modes }.ifEmpty { CompareMode.ALL }
    val currentMode = if (state.mode in offered) state.mode else offered.first()
    val currentPair = normalizeComparePair(ids, state.pair)
    val beforeSession = if (currentPair != null) sorted.firstOrNull { it.id == currentPair.before } else sorted.firstOrNull()
    val afterSession = if (currentPair != null) sorted.firstOrNull { it.id == currentPair.after } else null
    val currentView = resolveCompareView(views, listOf(beforeSession, afterSession), state.view)
    val timelineVisible = showTimeline ?: (sorted.size > 2)

    val columnModifier = modifier.fillMaxWidth().testTag(PhotoCompareTestTags.ROOT)

    // ── Carregando: a mesma casca, com esqueleto no lugar do que depende dos dados ────────────────
    if (loading) {
        Column(
            modifier = columnModifier.semantics { contentDescription = texts.loading },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBox(Modifier.width(200.dp).height(20.dp))
            CompareFrameBox(aspectRatio, maxFrameHeight, fillHeight = false) {
                SkeletonBox(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            }
            if (showTimeline != false) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { SkeletonBox(Modifier.width(120.dp).height(64.dp), shape = RoundedCornerShape(8.dp)) }
                }
            }
        }
        return
    }

    // ── Vazio ─────────────────────────────────────────────────────────────────────────────────────
    if (sorted.isEmpty()) {
        Box(columnModifier) {
            if (emptyContent != null) {
                emptyContent()
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = texts.empty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
        return
    }

    val viewPicker: @Composable () -> Unit = {
        if (views.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics { contentDescription = texts.views },
            ) {
                views.forEach { v ->
                    val available = photoForView(beforeSession, v.key) != null || photoForView(afterSession, v.key) != null
                    FilterChip(
                        selected = v.key == currentView,
                        onClick = { state.view = v.key; state.resetZoom() },
                        enabled = available,
                        label = { Text(v.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag(PhotoCompareTestTags.view(v.key)),
                    )
                }
            }
        }
    }

    val photoOf: (CompareSession?) -> ComparePhoto? = { photoForView(it, currentView) }
    val dateOf: (CompareSession, ComparePhoto?) -> String = { s, p -> fmt(p?.takenAt ?: s.date) }

    // ── Uma sessão só: a foto, e a consequência dita uma vez ──────────────────────────────────────
    if (currentPair == null || beforeSession == null || afterSession == null) {
        val only = sorted.first()
        val photo = photoOf(only)
        Column(columnModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (showTitle) {
                        Text(
                            text = title ?: texts.singleTitle(fmt(only.date)),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = texts.singleHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                headerEnd?.invoke()
            }
            viewPicker()
            CompareFrameBox(aspectRatio, maxFrameHeight, fillHeight, Modifier.weightIf(fillHeight, this)) {
                ComparePhotoLayer(photo, photoSource, fit, state.transform, texts)
                CompareBadge(dateOf(only, photo), Modifier.align(Alignment.TopStart))
            }
        }
        return
    }

    val beforePhoto = photoOf(beforeSession)
    val afterPhoto = photoOf(afterSession)
    val beforeDate = dateOf(beforeSession, beforePhoto)
    val afterDate = dateOf(afterSession, afterPhoto)
    val heading = title ?: texts.title(fmt(beforeSession.date), fmt(afterSession.date))

    Column(columnModifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Cabeçalho: título vivo + tela cheia / fechar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (showTitle) {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            if (onFullScreen != null) {
                IconButton(onClick = onFullScreen, modifier = Modifier.testTag(PhotoCompareTestTags.FULL_SCREEN)) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = texts.fullScreen)
                }
            }
            headerEnd?.invoke()
        }
        if (offered.size > 1) {
            SegmentedControl(
                options = offered.map { if (it == CompareMode.SLIDER) texts.modeSlider else texts.modeSideBySide },
                selectedIndex = offered.indexOf(currentMode),
                onOptionSelected = { state.mode = offered[it] },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = texts.modes },
                testTag = PhotoCompareTestTags.MODES,
                optionTestKeys = offered.map { if (it == CompareMode.SLIDER) "deslizar" else "lado-a-lado" },
            )
        }
        viewPicker()
        val frameModifier = Modifier.weightIf(fillHeight, this)
        when (currentMode) {
            CompareMode.SLIDER -> CompareSliderFrame(
                state = state,
                beforePhoto = beforePhoto,
                afterPhoto = afterPhoto,
                beforeBadge = texts.beforeBadge(beforeDate),
                afterBadge = texts.afterBadge(afterDate),
                accessibleName = texts.sliderBetween(beforeDate, afterDate),
                aspectRatio = aspectRatio,
                maxFrameHeight = maxFrameHeight,
                fillHeight = fillHeight,
                fit = fit,
                photoSource = photoSource,
                texts = texts,
                modifier = frameModifier,
            )
            CompareMode.SIDE_BY_SIDE -> CompareSideBySide(
                state = state,
                beforePhoto = beforePhoto,
                afterPhoto = afterPhoto,
                beforeBadge = texts.beforeBadge(beforeDate),
                afterBadge = texts.afterBadge(afterDate),
                aspectRatio = aspectRatio,
                maxFrameHeight = maxFrameHeight,
                fillHeight = fillHeight,
                fit = fit,
                photoSource = photoSource,
                texts = texts,
                modifier = frameModifier,
            )
        }
        if (timelineVisible) {
            CompareTimeline(
                sessions = sorted,
                pair = currentPair,
                onPairChange = { state.pair = it; state.resetZoom() },
                timeZone = timeZone,
                formatDate = formatDate,
                texts = texts,
            )
        }
    }
}

/** `weight(1f)` só na tela cheia — o quadro ocupa a altura que sobra. */
private fun Modifier.weightIf(fill: Boolean, scope: ColumnScope): Modifier =
    if (fill) with(scope) { this@weightIf.weight(1f) } else this

/**
 * Linha do tempo de SESSÕES tocáveis — escolhe o par a comparar (o `CompareTimeline` da weblib).
 *
 * Um cartão por sessão (papel · data · descrição), da mais antiga para a mais recente, numa tira
 * rolável na horizontal. As duas do par levam o papel escrito ("Antes"/"Depois") e o destaque; tocar
 * noutra troca a ponta pela regra do protótipo ([nextComparePair]) — o "antes" nunca fica depois do
 * "depois". Leitor de tela: botão com estado selecionado e o papel no nome ("12/03/2026, Antes").
 *
 * @param pair o par comparado. `null`/inválido = o par inicial.
 * @param onPairChange chamado só quando o par MUDA.
 */
@Composable
fun CompareTimeline(
    sessions: List<CompareSession>,
    pair: ComparePair?,
    onPairChange: (ComparePair) -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    formatDate: ((String) -> String)? = null,
    texts: PhotoCompareTexts = rememberPhotoCompareTexts(),
) {
    val fmt: (String) -> String = formatDate ?: { formatCompareDate(it, timeZone) }
    val sorted = remember(sessions, timeZone) { comparableSessions(sessions, timeZone) }
    val ids = remember(sorted) { sorted.map { it.id } }
    val current = normalizeComparePair(ids, pair)
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // Padding simétrico: a rolagem não recorta a borda do primeiro cartão.
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .semantics { contentDescription = texts.timeline }
            .testTag(PhotoCompareTestTags.TIMELINE),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sorted.forEach { session ->
            val role = when (session.id) {
                current?.before -> texts.before
                current?.after -> texts.after
                else -> null
            }
            val selected = role != null
            val date = fmt(session.date)
            val sub = listOfNotNull(session.label, session.description).filter { it.isNotBlank() }.joinToString(" · ")
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) scheme.primaryContainer else scheme.surface,
                border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 200.dp)
                    .testTag(PhotoCompareTestTags.session(session.id))
                    .clickable(role = Role.Button) {
                        if (current == null) return@clickable
                        val next = nextComparePair(ids, current, session.id)
                        if (next != current) onPairChange(next)
                    }
                    .semantics {
                        this.selected = selected
                        contentDescription = listOfNotNull(date, role, sub.takeIf { it.isNotEmpty() }).joinToString(", ")
                    },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // A linha do papel existe mesmo vazia: os cartões ficam alinhados pela data.
                    Text(
                        text = role ?: " ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) scheme.onPrimaryContainer else scheme.primary,
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    )
                    if (sub.isNotEmpty()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Quadros ───────────────────────────────────────────────────────────────────────────────────────

/** Quadro de proporção fixa, centralizado; [content] recebe o tamanho em px. */
@Composable
private fun CompareFrameBox(
    aspectRatio: Float,
    maxFrameHeight: Dp,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val density = LocalDensity.current
        val maxH = when {
            fillHeight && constraints.hasBoundedHeight -> constraints.maxHeight.toFloat()
            maxFrameHeight == Dp.Infinity -> Float.POSITIVE_INFINITY
            else -> with(density) { maxFrameHeight.toPx() }
        }
        val (w, h) = compareFrameSize(aspectRatio, constraints.maxWidth.toFloat(), maxH)
        Box(
            modifier = frameModifier
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .graphicsLayer { clip = true; shape = RoundedCornerShape(12.dp) },
            content = content,
        )
    }
}

@Composable
private fun CompareSliderFrame(
    state: PhotoCompareState,
    beforePhoto: ComparePhoto?,
    afterPhoto: ComparePhoto?,
    beforeBadge: String,
    afterBadge: String,
    accessibleName: String,
    aspectRatio: Float,
    maxFrameHeight: Dp,
    fillHeight: Boolean,
    fit: CompareFit,
    photoSource: (ComparePhoto) -> PhotoSource,
    texts: PhotoCompareTexts,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val gripHalf = with(density) { (PhotoCompareDefaults.GripSize / 2).toPx() }
    var frameSize by remember { mutableStateOf(Size.Zero) }
    val (beforeShare, afterShare) = compareVisibleShares(state.position)
    val zoomed = state.transform.isZoomed

    CompareFrameBox(
        aspectRatio = aspectRatio,
        maxFrameHeight = maxFrameHeight,
        fillHeight = fillHeight,
        modifier = modifier,
        frameModifier = Modifier
            .testTag(PhotoCompareTestTags.SLIDER)
            .compareGestures(state, sliderGripHalfPx = gripHalf, onSize = { frameSize = it })
            .semantics {
                contentDescription = accessibleName
                stateDescription = texts.sliderValue(beforeShare, afterShare)
                progressBarRangeInfo = ProgressBarRangeInfo(state.position, 0f..100f, steps = 19)
                setProgress { target ->
                    state.position = target
                    true
                }
                if (zoomed) {
                    customActions = listOf(CustomAccessibilityAction(texts.resetZoom) { state.resetZoom(); true })
                }
            },
    ) {
        // Antes: a foto inteira, por baixo.
        ComparePhotoLayer(beforePhoto, photoSource, fit, state.transform, texts)
        // Depois: por cima, recortada à DIREITA da divisória (lido no desenho — arrastar não recompõe).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = RightOfShape(state.position / 100f)
                },
        ) {
            ComparePhotoLayer(afterPhoto, photoSource, fit, state.transform, texts)
        }
        CompareBadge(beforeBadge, Modifier.align(Alignment.TopStart))
        CompareBadge(afterBadge, Modifier.align(Alignment.TopEnd))
        // Linha da divisória (até a borda) + alça (para a uma alça da borda, para continuar inteira).
        val width = frameSize.width
        if (width > 0f) {
            val x = width * state.position / 100f
            val lineWidthPx = with(density) { 2.dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((x - lineWidthPx / 2f).roundToInt(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
            )
            val gripX = x.coerceIn(gripHalf, (width - gripHalf).coerceAtLeast(gripHalf))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((gripX - gripHalf).roundToInt(), 0) }
                    .size(PhotoCompareDefaults.GripSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun CompareSideBySide(
    state: PhotoCompareState,
    beforePhoto: ComparePhoto?,
    afterPhoto: ComparePhoto?,
    beforeBadge: String,
    afterBadge: String,
    aspectRatio: Float,
    maxFrameHeight: Dp,
    fillHeight: Boolean,
    fit: CompareFit,
    photoSource: (ComparePhoto) -> PhotoSource,
    texts: PhotoCompareTexts,
    modifier: Modifier,
) {
    val zoomed = state.transform.isZoomed
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .testTag(PhotoCompareTestTags.SIDE_BY_SIDE)
            .semantics {
                if (zoomed) {
                    customActions = listOf(CustomAccessibilityAction(texts.resetZoom) { state.resetZoom(); true })
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val density = LocalDensity.current
        val gapPx = with(density) { PhotoCompareDefaults.SideBySideGap.toPx() }
        val maxH = when {
            fillHeight && constraints.hasBoundedHeight -> constraints.maxHeight.toFloat()
            maxFrameHeight == Dp.Infinity -> Float.POSITIVE_INFINITY
            else -> with(density) { maxFrameHeight.toPx() }
        }
        val columnWidth = ((constraints.maxWidth - gapPx) / 2f).coerceAtLeast(0f)
        val (w, h) = compareFrameSize(aspectRatio, columnWidth, maxH)
        val wDp = with(density) { w.toDp() }
        val hDp = with(density) { h.toDp() }
        // Os gestos ficam na LINHA inteira: pinça ou arrasto em qualquer das duas move as duas.
        Row(
            modifier = Modifier.compareGestures(state, sliderGripHalfPx = null, frameSizeOverride = Size(w, h)),
            horizontalArrangement = Arrangement.spacedBy(PhotoCompareDefaults.SideBySideGap),
        ) {
            listOf(beforePhoto to beforeBadge, afterPhoto to afterBadge).forEach { (photo, badge) ->
                Box(
                    Modifier
                        .size(wDp, hDp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .graphicsLayer { clip = true; shape = RoundedCornerShape(12.dp) },
                ) {
                    ComparePhotoLayer(photo, photoSource, fit, state.transform, texts)
                    CompareBadge(badge, Modifier.align(Alignment.TopStart))
                }
            }
        }
    }
}

/** Recorte "à direita de [fraction] da largura" — a camada de DEPOIS do deslizante. */
private class RightOfShape(private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(size.width * fraction.coerceIn(0f, 1f), 0f, size.width, size.height))
}

/**
 * Gestos do quadro: pinça (2 dedos) = zoom/pan das DUAS fotos; um dedo = divisória (deslizante, ou
 * qualquer lugar perto da alça), ou pan quando ampliado; vertical em escala 1 passa adiante (a tela
 * rola). Duplo toque alterna o zoom.
 */
private fun Modifier.compareGestures(
    state: PhotoCompareState,
    sliderGripHalfPx: Float?,
    frameSizeOverride: Size? = null,
    onSize: ((Size) -> Unit)? = null,
): Modifier = this
    .then(
        if (onSize != null) {
            Modifier.onSizeChanged { onSize(Size(it.width.toFloat(), it.height.toFloat())) }
        } else {
            Modifier
        },
    )
    .pointerInput(state, sliderGripHalfPx, frameSizeOverride) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val w = frameSizeOverride?.width ?: size.width.toFloat()
            val h = frameSizeOverride?.height ?: size.height.toFloat()
            val start = down.position
            var intent = CompareDragIntent.UNDECIDED
            var transforming = false
            val nearGrip = sliderGripHalfPx != null &&
                abs(start.x - w * state.position / 100f) <= sliderGripHalfPx
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) transforming = true
                if (transforming) {
                    state.transform = applyCompareTransform(
                        state.transform, event.calculateZoom(),
                        event.calculatePan().x, event.calculatePan().y, w, h,
                    )
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else {
                    val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (intent == CompareDragIntent.UNDECIDED) {
                        val total = change.position - start
                        intent = compareDragIntent(total.x, total.y)
                    }
                    val zoomedNow = state.transform.isZoomed
                    when {
                        sliderGripHalfPx != null && intent == CompareDragIntent.HORIZONTAL && (nearGrip || !zoomedNow) -> {
                            comparePositionFromPointer(change.position.x, w)?.let { state.position = it }
                            change.consume()
                        }
                        zoomedNow && intent != CompareDragIntent.UNDECIDED -> {
                            val d = change.positionChange()
                            state.transform = applyCompareTransform(state.transform, 1f, d.x, d.y, w, h)
                            change.consume()
                        }
                        // Escala 1 + vertical: não consome — quem rola a tela fica com o gesto.
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(state) {
        detectTapGestures(onDoubleTap = { state.transform = toggleCompareZoom(state.transform) })
    }

// ── Camada de foto e rótulo ───────────────────────────────────────────────────────────────────────

@Composable
private fun ComparePhotoLayer(
    photo: ComparePhoto?,
    photoSource: (ComparePhoto) -> PhotoSource,
    fit: CompareFit,
    transform: CompareTransform,
    texts: PhotoCompareTexts,
) {
    if (photo == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = texts.missingPhoto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val source = remember(photo.id, photo.src) { photoSource(photo) }
    var status by remember(photo.id, photo.src) { mutableStateOf(PhotoLoad.LOADING) }
    val latest by rememberUpdatedState(transform)
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = rememberPhotoSourceRequest(source),
            contentDescription = photo.alt,
            contentScale = if (fit == CompareFit.COVER) ContentScale.Crop else ContentScale.Fit,
            onLoading = { status = PhotoLoad.LOADING },
            onSuccess = { status = PhotoLoad.READY },
            onError = { status = PhotoLoad.FAILED },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = latest.scale
                    scaleY = latest.scale
                    translationX = latest.offsetX
                    translationY = latest.offsetY
                },
        )
        when (status) {
            PhotoLoad.LOADING -> SkeletonBox(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            PhotoLoad.FAILED -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = texts.loadError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            PhotoLoad.READY -> Unit
        }
    }
}

private enum class PhotoLoad { LOADING, READY, FAILED }

/** Rótulo sobre a foto ("antes · 12/03/2026"): fundo do tema invertido, lê sobre foto clara e escura. */
@Composable
private fun CompareBadge(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
