package br.com.codecacto.kmplib.ui.tour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_back
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_done
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_next
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_pane
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_restart
import br.com.codecacto.kmplib.generated.resources.kmplib_tour_skip
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Textos dos botões do tour. Os defaults literais são pt-BR (teste/preview); na tela use
 * [rememberProductTourTexts], que segue o idioma do aparelho (pt-BR / pt-PT / en / es).
 *
 * @property progressPattern "N de M" com `%1$d` (passo) e `%2$d` (total).
 * @property restart rótulo do item de menu "Refazer o tour" — para o app usar no próprio menu.
 */
@Immutable
data class ProductTourTexts(
    val skip: String = "Pular",
    val back: String = "Voltar",
    val next: String = "Próximo",
    val done: String = "Começar a usar",
    val progressPattern: String = "%1\$d de %2\$d",
    val restart: String = "Refazer o tour",
    val paneTitle: String = "Tour guiado",
) {
    /** "3 de 7". */
    fun progress(current: Int, total: Int): String =
        progressPattern.replace("%1\$d", current.toString()).replace("%2\$d", total.toString())
}

/** [ProductTourTexts] no idioma do aparelho. */
@Composable
fun rememberProductTourTexts(): ProductTourTexts = ProductTourTexts(
    skip = kmpStringResource(Res.string.kmplib_tour_skip),
    back = kmpStringResource(Res.string.kmplib_tour_back),
    next = kmpStringResource(Res.string.kmplib_tour_next),
    done = kmpStringResource(Res.string.kmplib_tour_done),
    progressPattern = kmpStringResource(Res.string.kmplib_tour_progress),
    restart = kmpStringResource(Res.string.kmplib_tour_restart),
    paneTitle = kmpStringResource(Res.string.kmplib_tour_pane),
)

/**
 * Ids do tour para os flows Maestro. Estáveis: renomear quebra flow sem erro de build.
 * No último passo o botão principal é [CONCLUIR] (não [PROXIMO]).
 */
object ProductTourTestTags {
    const val CAMADA: String = "tour"
    const val BALAO: String = "tour-balao"
    const val PROGRESSO: String = "tour-progresso"
    const val TITULO: String = "tour-titulo"
    const val DESCRICAO: String = "tour-descricao"
    const val PULAR: String = "tour-btn-pular"
    const val VOLTAR: String = "tour-btn-voltar"
    const val PROXIMO: String = "tour-btn-proximo"
    const val CONCLUIR: String = "tour-btn-concluir"
}

/**
 * Medidas e cores do tour. Defaults = os do tour aprovado no portal do Vitalis (driver.js:
 * `overlayOpacity 0.55`, `stagePadding 6`, `stageRadius 10`), com as cores do `MaterialTheme` do
 * app — claro e escuro saem certos sem configurar nada.
 */
@Immutable
data class ProductTourStyle(
    val scrimColor: Color,
    val balloonColor: Color,
    val balloonContentColor: Color,
    val secondaryContentColor: Color,
    val highlightPadding: Dp = 6.dp,
    val highlightCornerRadius: Dp = 10.dp,
    val balloonMaxWidth: Dp = 360.dp,
    val screenMargin: Dp = 12.dp,
)

/** Defaults do tour. */
object ProductTourDefaults {
    /** Opacidade da camada escura (a do portal). */
    const val SCRIM_ALPHA: Float = 0.55f

    /** Espera antes de abrir sozinho no primeiro acesso — a tela termina de assentar (a do portal). */
    const val FIRST_RUN_DELAY_MILLIS: Long = 600L

    /** Estilo pelas cores do tema atual. */
    @Composable
    fun style(): ProductTourStyle {
        val cs = MaterialTheme.colorScheme
        return ProductTourStyle(
            scrimColor = cs.scrim.copy(alpha = SCRIM_ALPHA),
            balloonColor = cs.surfaceContainerHigh,
            balloonContentColor = cs.onSurface,
            secondaryContentColor = cs.onSurfaceVariant,
        )
    }
}

/**
 * O tour guiado dos menus: escurece a tela, destaca um alvo de cada vez e mostra um balão com
 * título, frase, "N de M" e **Pular · Voltar · Próximo** ("Começar a usar" no último).
 *
 * Envolva a **tela raiz** (o `Scaffold` com top bar, bottom navigation e banner), dentro do
 * `AppTheme`: a camada cobre tudo o que o host cobre, inclusive as barras, então o destaque
 * enxerga um item da barra de baixo. O host deve ocupar a janela (edge-to-edge) — o balão desvia da
 * barra de status e da barra de gestos pelos `WindowInsets.safeDrawing`.
 *
 * ```kotlin
 * val tour = rememberProductTourState()
 * ProductTourHost(
 *     state = tour,
 *     firstRunKey = "tour.inicio.v1",        // abre sozinho UMA vez neste aparelho
 *     firstRunSteps = passosDoTour,
 *     firstRunEnabled = usuarioLogado,
 * ) { MeuScaffold(onRefazerTour = { tour.start(passosDoTour) }) }
 * ```
 *
 * Comportamento:
 * - **Primeira vez**: com [firstRunKey], abre sozinho [ProductTourDefaults.FIRST_RUN_DELAY_MILLIS]
 *   depois de a tela aparecer, se ainda não foi visto; concluir OU pular grava "visto"
 *   ([ProductTourFirstRun]). Build de teste (`AutomationMode`) não abre sozinho — o flow que quer
 *   o tour toca em "Refazer o tour".
 * - **Alvo fora da tela** no começo: o passo é pulado e sai da contagem.
 * - **Toque fora do balão** pula o tour (conta como "Pular" — grava "visto"), e o **voltar do
 *   sistema** também. Arrastar fora do balão não faz nada.
 * - **Acessibilidade**: a tela por baixo sai da árvore de semântica enquanto o tour está aberto
 *   (como num diálogo); o balão é um painel nomeado, o título é cabeçalho e anunciado a cada passo,
 *   e o foco vai para o botão principal.
 * - **Movimento**: o destaque desliza de um alvo ao outro; com "reduzir movimento" do aparelho, salta.
 *
 * @param state de [rememberProductTourState].
 * @param firstRunKey chave do "já viu" (versione: `"tour.inicio.v1"`); `null` = nunca abre sozinho.
 * @param firstRunSteps os passos do tour automático.
 * @param firstRunEnabled segure em `false` enquanto a tela não é a certa (ex.: antes do login).
 * @param suppressFirstRunInAutomation `false` deixa o tour automático abrir também em build de teste.
 * @param firstRunPreferences onde gravar o "já viu" (teste); default = as preferências da lib.
 */
@Composable
fun ProductTourHost(
    state: ProductTourState,
    modifier: Modifier = Modifier,
    firstRunKey: String? = null,
    firstRunSteps: List<TourStep> = emptyList(),
    firstRunEnabled: Boolean = true,
    suppressFirstRunInAutomation: Boolean = true,
    firstRunPreferences: AppPreferences? = null,
    texts: ProductTourTexts = rememberProductTourTexts(),
    style: ProductTourStyle = ProductTourDefaults.style(),
    content: @Composable () -> Unit,
) {
    val registry = state.registry

    // Início: espera a tela assentar (dois quadros: composição → layout → posição reportada) e só
    // então filtra os passos pelo que está visível.
    LaunchedEffect(state, state.startToken) {
        if (!state.isStarting) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        state.commitStart()
    }

    if (firstRunKey != null) {
        val firstRun = remember(firstRunKey, firstRunPreferences) {
            ProductTourFirstRun(firstRunKey, firstRunPreferences ?: appPreferences())
        }
        val scope = rememberCoroutineScope()
        DisposableEffect(state, firstRun) {
            val listener: (TourEndReason) -> Unit = { reason ->
                if (reason != TourEndReason.NOTHING_TO_SHOW) scope.launch { firstRun.markSeen() }
            }
            state.addEndListener(listener)
            onDispose { state.removeEndListener(listener) }
        }
        LaunchedEffect(state, firstRun, firstRunEnabled, firstRunSteps, suppressFirstRunInAutomation) {
            if (!firstRunEnabled || firstRunSteps.isEmpty()) return@LaunchedEffect
            if (suppressFirstRunInAutomation && AutomationMode.suppressesAutomaticPrompts) return@LaunchedEffect
            if (firstRun.hasBeenSeen()) return@LaunchedEffect
            delay(ProductTourDefaults.FIRST_RUN_DELAY_MILLIS)
            if (state.isActive || state.isStarting) return@LaunchedEffect
            state.start(firstRunSteps)
        }
    }

    CompositionLocalProvider(LocalTourTargetRegistry provides registry) {
        Box(
            modifier = modifier.onGloballyPositioned { registry.hostOrigin = it.positionInRoot() },
            propagateMinConstraints = true,
        ) {
            Box(
                modifier = if (state.isActive) Modifier.clearAndSetSemantics { } else Modifier,
                propagateMinConstraints = true,
            ) {
                content()
            }
            TourBackHandler(enabled = state.isActive, onBack = state::skip)
            AnimatedVisibility(
                visible = state.isActive,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(tween(if (LocalReduceMotion.current) 0 else 180)),
                exit = fadeOut(tween(if (LocalReduceMotion.current) 0 else 150)),
            ) {
                TourOverlay(state = state, texts = texts, style = style)
            }
        }
    }
}

/** O passo, os números e o retângulo que o overlay desenha — guardados para a animação de saída. */
private data class TourFrame(
    val step: TourStep,
    val number: Int,
    val total: Int,
    val canGoBack: Boolean,
    val isLast: Boolean,
)

/** O que o overlay lembra entre composições (último passo e último destaque) — sem ser estado. */
private class TourOverlayMemory {
    var frame: TourFrame? = null
    var hole: Rect? = null
}

@Composable
private fun TourOverlay(
    state: ProductTourState,
    texts: ProductTourTexts,
    style: ProductTourStyle,
) {
    // Durante o fade-out o estado já não tem passo: segura o último quadro desenhado. Memória
    // simples (não é estado): só serve à composição seguinte, sem invalidar nada ao escrever.
    val memory = remember { TourOverlayMemory() }
    state.currentStep?.let { step ->
        memory.frame = TourFrame(step, state.currentNumber, state.stepCount, state.canGoBack, state.isLastStep)
    }
    val frame = memory.frame ?: return

    val padPx = with(LocalDensity.current) { style.highlightPadding.toPx() }
    val targetBounds: Rect? = frame.step.targetId?.let { state.registry.boundsOf(it) }?.inflate(padPx)

    if (targetBounds != null) memory.hole = targetBounds
    val reduceMotion = LocalReduceMotion.current
    val rectSpec: AnimationSpec<Rect> = if (reduceMotion) snap() else tween(260)
    val hole by animateValueAsState(
        targetValue = targetBounds ?: memory.hole ?: Rect.Zero,
        typeConverter = Rect.VectorConverter,
        animationSpec = rectSpec,
        label = "tourHole",
    )
    val holeAlpha by animateFloatAsState(
        targetValue = if (targetBounds != null) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "tourHoleAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ProductTourTestTags.CAMADA)
            // Toque simples fora do balão = Pular. A tela por baixo não recebe nada: o toque vai só
            // ao irmão de cima que tem entrada (esta camada). NÃO consumir todo evento aqui: o clique
            // de Pular/Próximo é cancelado quando um movimento do mesmo toque chega consumido, e
            // aparelho que reporta o tremor do dedo como MOVE perdia todo toque nos botões (2.334.0).
            .pointerInput(state) { detectTapGestures(onTap = { state.skip() }) },
    ) {
        val radiusPx = with(LocalDensity.current) { style.highlightCornerRadius.toPx() }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Camada própria: o recorte (DstOut) apaga só a cortina, não a tela do app por baixo.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(style.scrimColor)
            if (holeAlpha > 0f && hole.width > 0f && hole.height > 0f) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = holeAlpha),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
        TourBalloonLayout(
            target = targetBounds,
            style = style,
        ) {
            TourBalloon(frame = frame, texts = texts, style = style, state = state)
        }
    }
}

private val ArrowWidth = 18.dp
private val ArrowHeight = 9.dp
private val ArrowGap = 4.dp

/**
 * Mede o balão e o põe acima/abaixo do alvo pela regra pura [placeTourBalloon]; as duas setas são
 * compostas e só a do lado escolhido é posicionada.
 */
@Composable
private fun TourBalloonLayout(
    target: Rect?,
    style: ProductTourStyle,
    balloon: @Composable () -> Unit,
) {
    val insets = WindowInsets.safeDrawing
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            balloon()
            TourArrow(pointsUp = true, color = style.balloonColor)
            TourArrow(pointsUp = false, color = style.balloonColor)
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bounds = IntRect(
            left = insets.getLeft(this, LayoutDirection.Ltr),
            top = insets.getTop(this),
            right = width - insets.getRight(this, LayoutDirection.Ltr),
            bottom = height - insets.getBottom(this),
        )
        val margin = style.screenMargin.roundToPx()
        val maxW = (bounds.width - 2 * margin).coerceAtLeast(0).coerceAtMost(style.balloonMaxWidth.roundToPx())
        val maxH = (bounds.height - 2 * margin).coerceAtLeast(0)
        val balloonPlaceable = measurables[0].measure(Constraints(maxWidth = maxW, maxHeight = maxH))
        val upArrow = measurables[1].measure(Constraints())
        val downArrow = measurables[2].measure(Constraints())
        val gap = ArrowHeight.roundToPx() + ArrowGap.roundToPx()
        val corner = 16.dp.roundToPx()
        val placement = placeTourBalloon(
            target = target?.let {
                IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
            },
            balloon = IntSize(balloonPlaceable.width, balloonPlaceable.height),
            bounds = bounds,
            gap = gap,
            margin = margin,
            arrowInset = corner + upArrow.width / 2,
        )
        layout(width, height) {
            balloonPlaceable.place(placement.offset)
            val ax = placement.arrowCenterX
            if (ax != null) {
                val x = placement.offset.x + ax - upArrow.width / 2
                when (placement.side) {
                    TourBalloonSide.BELOW -> upArrow.place(x, placement.offset.y - upArrow.height + 1)
                    TourBalloonSide.ABOVE -> downArrow.place(x, placement.offset.y + balloonPlaceable.height - 1)
                    TourBalloonSide.CENTER -> Unit
                }
            }
        }
    }
}

@Composable
private fun TourArrow(pointsUp: Boolean, color: Color) {
    Canvas(Modifier.size(ArrowWidth, ArrowHeight)) {
        val path = Path().apply {
            if (pointsUp) {
                moveTo(0f, size.height); lineTo(size.width / 2f, 0f); lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f); lineTo(size.width / 2f, size.height); lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun TourBalloon(
    frame: TourFrame,
    texts: ProductTourTexts,
    style: ProductTourStyle,
    state: ProductTourState,
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(frame.step, frame.isLast) {
        // Teclado/D-pad e leitor de tela começam no botão principal de cada passo.
        runCatching { primaryFocus.requestFocus() }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = style.balloonColor,
        contentColor = style.balloonContentColor,
        shadowElevation = 8.dp,
        modifier = Modifier
            .testTag(ProductTourTestTags.BALAO)
            // Toque no balão (fora dos botões) não chega à camada, que o leria como "Pular". Só o
            // soltar é consumido — depois de os botões já o terem tratado (passe Main: filho antes).
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { if (it.changedToUp()) it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .semantics {
                paneTitle = texts.paneTitle
                isTraversalGroup = true
            },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = texts.progress(frame.number, frame.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = style.secondaryContentColor,
                    modifier = Modifier.testTag(ProductTourTestTags.PROGRESSO),
                )
                Text(
                    text = frame.step.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .testTag(ProductTourTestTags.TITULO)
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                Text(
                    text = frame.step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = style.secondaryContentColor,
                    modifier = Modifier.testTag(ProductTourTestTags.DESCRICAO),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!frame.isLast) {
                    TextButton(
                        onClick = state::skip,
                        modifier = Modifier.testTag(ProductTourTestTags.PULAR),
                    ) {
                        Text(texts.skip, color = style.secondaryContentColor, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (frame.canGoBack) {
                    TextButton(
                        onClick = state::back,
                        modifier = Modifier.testTag(ProductTourTestTags.VOLTAR),
                    ) {
                        Text(texts.back, textAlign = TextAlign.Center)
                    }
                }
                Button(
                    onClick = state::next,
                    modifier = Modifier
                        .focusRequester(primaryFocus)
                        .testTag(if (frame.isLast) ProductTourTestTags.CONCLUIR else ProductTourTestTags.PROXIMO),
                ) {
                    Text(if (frame.isLast) texts.done else texts.next, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * O voltar do sistema enquanto o tour está aberto PULA o tour (sem isto ele desempilharia a
 * navegação escondida por baixo).
 *
 * `@Suppress("DEPRECATION")`: o `BackHandler` multiplataforma está marcado em favor do
 * `NavigationEventHandler`, e o `navigationevent-compose` não publica variante Kotlin/Native —
 * mesmo motivo do `FullScreenNotice`.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun TourBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
