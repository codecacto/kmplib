package br.com.codecacto.kmplib.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Os textos da [VideoTimeline] e do [VideoFrameStepControls] — quase tudo é o que o leitor de tela
 * diz. Defaults em pt-BR, como os do [VideoPlayerTexts] (o módulo de vídeo ainda não tem recursos
 * próprios); app em outro idioma passa os seus, de `stringResource`.
 */
data class VideoTimelineTexts(
    /** Nome da trilha ("Linha do tempo do vídeo"). */
    val timeline: String = "Linha do tempo do vídeo",
    /** Posição lida pela trilha: recebe "0:12" e "1:00". */
    val position: (current: String, total: String) -> String = { atual, total -> "$atual de $total" },
    /** Uma marca: recebe o tempo ("0:12") e o rótulo da marca (ou `null`). */
    val marker: (time: String, label: String?) -> String = { tempo, rotulo ->
        if (rotulo.isNullOrBlank()) "Marca em $tempo" else "Marca em $tempo: $rotulo"
    },
    /** Grupo de marcas sobrepostas: recebe a quantidade e o tempo da primeira. */
    val markerGroup: (count: Int, time: String) -> String = { n, tempo -> "$n marcas a partir de $tempo" },
    val nextMarker: String = "Próxima marca",
    val previousMarker: String = "Marca anterior",
    /** Ação de criar marca na posição atual: recebe o tempo. */
    val addMarkerAt: (time: String) -> String = { tempo -> "Adicionar marca em $tempo" },
    val previousFrame: String = "Quadro anterior",
    val nextFrame: String = "Próximo quadro",
)

/**
 * As cores da [VideoTimeline]. Ao contrário do [VideoPlayerColors], vêm do **tema**: a linha do
 * tempo fica abaixo do vídeo, sobre a superfície do app, e não por cima do quadro.
 */
@Immutable
data class VideoTimelineColors(
    val track: Color,
    val buffered: Color,
    val progress: Color,
    val thumb: Color,
    val marker: Color,
    /** O contorno do ponto da marca — separa o ponto da trilha e do progresso. */
    val markerBorder: Color,
    val selectedMarker: Color,
    val label: Color,
)

/** Defaults da [VideoTimeline]. */
object VideoTimelineDefaults {
    /** Altura da faixa de toque da trilha: 48 dp, o alvo mínimo. */
    val TouchHeight: Dp = 48.dp

    /** Distância mínima entre dois pontos de marca antes de virarem um grupo: meio alvo de toque. */
    val MarkerMinSpacing: Dp = 24.dp

    @Composable
    fun colors(
        track: Color = MaterialTheme.colorScheme.surfaceVariant,
        buffered: Color = MaterialTheme.colorScheme.outlineVariant,
        progress: Color = MaterialTheme.colorScheme.primary,
        thumb: Color = MaterialTheme.colorScheme.primary,
        marker: Color = MaterialTheme.colorScheme.tertiary,
        markerBorder: Color = MaterialTheme.colorScheme.surface,
        selectedMarker: Color = MaterialTheme.colorScheme.error,
        label: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): VideoTimelineColors = VideoTimelineColors(
        track = track,
        buffered = buffered,
        progress = progress,
        thumb = thumb,
        marker = marker,
        markerBorder = markerBorder,
        selectedMarker = selectedMarker,
        label = label,
    )
}

/** Ids estáveis para o Maestro — nunca o texto, que muda nos 4 idiomas. */
object VideoTimelineTestTags {
    const val ROOT: String = "video-linha-do-tempo"
    const val TRACK: String = "video-linha-do-tempo-trilho"
    const val PREVIOUS_FRAME: String = "video-btn-quadro-anterior"
    const val NEXT_FRAME: String = "video-btn-quadro-proximo"

    /** O ponto de uma marca (o grupo leva o id da primeira). */
    fun marker(id: String): String = "video-marca-$id"
}

/**
 * **Linha do tempo com marcas** (2.286.0) — a barra de revisão de vídeo: progresso, buffer e um
 * ponto por marca ([VideoMarker]); tocar no ponto pula para ele; tocar ou arrastar na trilha busca.
 *
 * Nasceu da correção de execução do App do Personal: o personal marca "joelho para dentro" em 0:12
 * e o aluno, no próprio app, toca no ponto e o vídeo vai ao quadro exato (a busca do
 * [VideoPlayerState] é exata). Fica **fora** do quadro do vídeo, embaixo dele, com as cores do tema.
 *
 * ```kotlin
 * val player = rememberVideoPlayerState(VideoMedia(url = video.url))
 * VideoPlayer(player, Modifier.fillMaxWidth().aspectRatio(9f / 16f))
 * VideoTimeline(
 *     state = player,
 *     markers = comentarios.map { VideoMarker(it.id, it.atMs, label = it.resumo) },
 *     onAddAt = { ms -> viewModel.onAction(Action.Comentar(ms)) },   // só no app do personal
 * )
 * VideoFrameStepControls(player)
 * val daVez = videoMarkerAt(marcas, player.positionMillis)           // cartão do comentário
 * ```
 *
 * ## Criar marca ([onAddAt])
 * Presente, a trilha aceita **toque longo** (cria no ponto tocado) e oferece ao leitor de tela a
 * ação "Adicionar marca em 0:12" (na posição atual). Gesto escondido não basta como único caminho:
 * a tela do app deve ter também o botão visível ("Comentar aqui") chamando a mesma ação com
 * `positionMillis`. Ausente (`null`), nada disso existe — é a linha do tempo de quem só assiste.
 *
 * ## Acessibilidade
 * A trilha é um controle de progresso (`setProgress` busca; anuncia "0:12 de 1:00") com as ações
 * "Próxima marca"/"Marca anterior"; cada ponto é um botão de 48 dp com o tempo e o rótulo. Pontos
 * que se sobreporiam viram um grupo ("3 marcas a partir de 0:12") — [clusterVideoMarkers].
 *
 * @param positionMillis posição atual (do [VideoPlayerState.positionMillis]).
 * @param durationMillis duração; `0` = ainda desconhecida (a trilha fica inerte).
 * @param markers as marcas, em qualquer ordem — [normalizeVideoMarkers] ordena e grampeia.
 * @param onSeek busca pedida pela trilha (toque, arrasto, leitor de tela, navegação entre marcas).
 * @param onMarkerClick toque num ponto; o default busca o instante da marca.
 * @param onAddAt cria uma marca no instante recebido; `null` = sem criação.
 * @param selectedMarkerId a marca destacada (o comentário aberto), ou `null`.
 * @param seekWhileDragging buscar durante o arrasto (o quadro acompanha o dedo) ou só ao soltar.
 */
@Composable
fun VideoTimeline(
    positionMillis: Long,
    durationMillis: Long,
    markers: List<VideoMarker>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onMarkerClick: (VideoMarker) -> Unit = { onSeek(it.atMillis) },
    onAddAt: ((Long) -> Unit)? = null,
    bufferedMillis: Long = 0L,
    selectedMarkerId: String? = null,
    enabled: Boolean = true,
    showTimeLabels: Boolean = true,
    seekWhileDragging: Boolean = true,
    colors: VideoTimelineColors = VideoTimelineDefaults.colors(),
    texts: VideoTimelineTexts = VideoTimelineTexts(),
) {
    val ativo = enabled && durationMillis > 0L
    val marcas = remember(markers, durationMillis) { normalizeVideoMarkers(markers, durationMillis) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnAddAt by rememberUpdatedState(onAddAt)

    // Durante o arrasto a bolinha segue o dedo, não a posição do player (que chega atrasada).
    var arrastandoEm by remember { mutableStateOf<Long?>(null) }
    val exibida = arrastandoEm ?: positionMillis
    val fracao = videoProgressOf(exibida, durationMillis)

    Column(modifier = modifier.fillMaxWidth().testTag(VideoTimelineTestTags.ROOT)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(VideoTimelineDefaults.TouchHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            val larguraDp = maxWidth
            val larguraPx = with(LocalDensity.current) { larguraDp.toPx() }
            val espacoMinimoPx = with(LocalDensity.current) { VideoTimelineDefaults.MarkerMinSpacing.toPx() }
            val grupos = remember(marcas, durationMillis, larguraPx, espacoMinimoPx) {
                clusterVideoMarkers(marcas, durationMillis, larguraPx, espacoMinimoPx)
            }
            val tempoAtual = formatVideoTime(exibida)
            val tempoTotal = formatVideoTime(durationMillis)

            // ---------------------------------------------------------------- trilha
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VideoTimelineDefaults.TouchHeight)
                    .testTag(VideoTimelineTestTags.TRACK)
                    .semantics {
                        contentDescription = texts.timeline
                        stateDescription = texts.position(tempoAtual, tempoTotal)
                        progressBarRangeInfo = ProgressBarRangeInfo(fracao, 0f..1f)
                        if (!ativo) {
                            disabled()
                        } else {
                            setProgress { alvo ->
                                currentOnSeek((alvo.coerceIn(0f, 1f).toDouble() * durationMillis).toLong())
                                true
                            }
                            customActions = buildList {
                                add(
                                    CustomAccessibilityAction(texts.nextMarker) {
                                        val proxima = nextVideoMarker(marcas, positionMillis) ?: return@CustomAccessibilityAction false
                                        currentOnSeek(proxima.atMillis)
                                        true
                                    },
                                )
                                add(
                                    CustomAccessibilityAction(texts.previousMarker) {
                                        val anterior = previousVideoMarker(marcas, positionMillis) ?: return@CustomAccessibilityAction false
                                        currentOnSeek(anterior.atMillis)
                                        true
                                    },
                                )
                                val criar = currentOnAddAt
                                if (criar != null) {
                                    add(
                                        CustomAccessibilityAction(texts.addMarkerAt(formatVideoTime(positionMillis))) {
                                            criar(positionMillis)
                                            true
                                        },
                                    )
                                }
                            }
                        }
                    }
                    .pointerInput(ativo, durationMillis, larguraPx) {
                        if (!ativo) return@pointerInput
                        detectTapGestures(
                            onTap = { ponto -> currentOnSeek(timelineMillisAt(ponto.x, larguraPx, durationMillis)) },
                            onLongPress = { ponto ->
                                currentOnAddAt?.invoke(timelineMillisAt(ponto.x, larguraPx, durationMillis))
                            },
                        )
                    }
                    .pointerInput(ativo, durationMillis, larguraPx, seekWhileDragging) {
                        if (!ativo) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { ponto ->
                                arrastandoEm = timelineMillisAt(ponto.x, larguraPx, durationMillis)
                            },
                            onDragEnd = {
                                arrastandoEm?.let { currentOnSeek(it) }
                                arrastandoEm = null
                            },
                            onDragCancel = { arrastandoEm = null },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val alvo = timelineMillisAt(change.position.x, larguraPx, durationMillis)
                                arrastandoEm = alvo
                                if (seekWhileDragging) currentOnSeek(alvo)
                            },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Trilha, buffer e progresso: decoração — a semântica está na caixa de fora.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TRACK_HEIGHT)
                        .clip(RoundedCornerShape(50))
                        .background(colors.track)
                        .clearAndSetSemantics { },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(videoProgressOf(bufferedMillis, durationMillis))
                            .height(TRACK_HEIGHT)
                            .background(colors.buffered),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(fracao)
                            .height(TRACK_HEIGHT)
                            .background(colors.progress),
                    )
                }
                if (ativo) {
                    Box(
                        Modifier
                            .offset(x = larguraDp * fracao - THUMB_SIZE / 2)
                            .size(THUMB_SIZE)
                            .clip(CircleShape)
                            .background(colors.thumb)
                            .clearAndSetSemantics { },
                    )
                }
            }

            // ---------------------------------------------------------------- marcas
            if (durationMillis > 0L) {
                grupos.forEach { grupo ->
                    val selecionada = grupo.markers.any { it.id == selectedMarkerId }
                    val tempo = formatVideoTime(grupo.atMillis)
                    val descricao = if (grupo.markers.size == 1) {
                        texts.marker(tempo, grupo.first.label)
                    } else {
                        texts.markerGroup(grupo.markers.size, tempo)
                    }
                    val corDoPonto = if (selecionada) {
                        colors.selectedMarker
                    } else {
                        grupo.first.color.takeOrElse { colors.marker }
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = larguraDp * grupo.fraction - VideoTimelineDefaults.TouchHeight / 2)
                            .size(VideoTimelineDefaults.TouchHeight)
                            .clickable(enabled = enabled, role = Role.Button) { onMarkerClick(grupo.first) }
                            .clearAndSetSemantics {
                                contentDescription = descricao
                                role = Role.Button
                                if (enabled) onClick { onMarkerClick(grupo.first); true } else disabled()
                            }
                            .testTag(VideoTimelineTestTags.marker(grupo.first.id)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val tamanho = if (selecionada) MARKER_SIZE_SELECTED else MARKER_SIZE
                        Box(
                            Modifier
                                .size(tamanho)
                                .clip(CircleShape)
                                .background(corDoPonto)
                                .border(2.dp, colors.markerBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (grupo.markers.size > 1) {
                                Text(
                                    text = if (grupo.markers.size > 9) "9+" else "${grupo.markers.size}",
                                    color = colors.markerBorder,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTimeLabels) {
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatVideoTime(exibida), color = colors.label, style = MaterialTheme.typography.labelSmall)
                Text(formatVideoTime(durationMillis), color = colors.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * A [VideoTimeline] ligada a um [VideoPlayerState]: posição, duração e buffer vêm dele, e a busca
 * vai para ele ([VideoPlayerState.seekTo], exata).
 */
@Composable
fun VideoTimeline(
    state: VideoPlayerState,
    markers: List<VideoMarker>,
    modifier: Modifier = Modifier,
    onMarkerClick: (VideoMarker) -> Unit = { state.seekTo(it.atMillis) },
    onAddAt: ((Long) -> Unit)? = null,
    selectedMarkerId: String? = null,
    enabled: Boolean = true,
    showTimeLabels: Boolean = true,
    seekWhileDragging: Boolean = true,
    colors: VideoTimelineColors = VideoTimelineDefaults.colors(),
    texts: VideoTimelineTexts = VideoTimelineTexts(),
) {
    VideoTimeline(
        positionMillis = state.positionMillis,
        durationMillis = state.durationMillis,
        markers = markers,
        onSeek = state::seekTo,
        modifier = modifier,
        onMarkerClick = onMarkerClick,
        onAddAt = onAddAt,
        bufferedMillis = state.bufferedMillis,
        selectedMarkerId = selectedMarkerId,
        enabled = enabled,
        showTimeLabels = showTimeLabels,
        seekWhileDragging = seekWhileDragging,
        colors = colors,
        texts = texts,
    )
}

/**
 * Os botões de **quadro a quadro** (2.286.0): "Quadro anterior" e "Próximo quadro", 48 dp cada,
 * chamando [VideoPlayerState.stepFrame] (que pausa). Para a barra de ferramentas de revisão, fora
 * do quadro do vídeo; a cor é a do conteúdo em volta ([LocalContentColor]).
 */
@Composable
fun VideoFrameStepControls(
    state: VideoPlayerState,
    modifier: Modifier = Modifier,
    texts: VideoTimelineTexts = VideoTimelineTexts(),
    tint: Color = LocalContentColor.current,
) {
    val podeAndar = state.durationMillis > 0L
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { state.stepFrame(-1) },
            enabled = podeAndar,
            modifier = Modifier.testTag(VideoTimelineTestTags.PREVIOUS_FRAME),
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = texts.previousFrame, tint = tint)
        }
        IconButton(
            onClick = { state.stepFrame(1) },
            enabled = podeAndar,
            modifier = Modifier.padding(start = 4.dp).testTag(VideoTimelineTestTags.NEXT_FRAME),
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = texts.nextFrame, tint = tint)
        }
    }
}

private val TRACK_HEIGHT = 4.dp
private val THUMB_SIZE = 14.dp
private val MARKER_SIZE = 14.dp
private val MARKER_SIZE_SELECTED = 18.dp
