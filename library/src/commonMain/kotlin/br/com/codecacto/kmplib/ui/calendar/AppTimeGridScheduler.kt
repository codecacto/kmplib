package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

/* ─────────────────────────── AppTimeGridScheduler (G-02a-M) ───────────────────────────
 * Par mobile do `TimeGridScheduler` da weblib e o CORAÇÃO da agenda. Grade temporal com N colunas de
 * recurso (um profissional por coluna) OU coluna única, blocos posicionados por MINUTO REAL
 * (`top`/`height` ∝ início e duração — NUNCA balde-de-hora do Influencer), LANES de sobreposição,
 * camadas de fora-de-expediente (sombra), bloqueios (hachura), buffer (rabo esmaecido), linha do
 * "agora" e JANELA derivada dos dados. Domínio-agnóstico (cor por `eventColors`/`renderEvent`),
 * acessível, tema 100% por tokens.
 *
 * Responsividade (`LocalIsCompact`): telefone mostra **1 recurso por vez** (chips no topo) ou timeline
 * única (0/1 recurso); tablet/desktop mostra as colunas lado a lado (scroll-x quando não cabem, com o
 * eixo de hora rolando junto — igual à weblib). Diferença de plataforma vs web: a web usa CSS grid com
 * `overflow-x-auto`; aqui é `Row(horizontalScroll)` header+corpo compartilhando o MESMO `ScrollState`.
 */

/** Cores de um evento na grade — o consumidor mapeia `status`→cor (AA sobre o tema). */
data class ScheduleEventColors(
    val container: Color,
    val content: Color,
    /** Acento da borda-esquerda (cor cheia do status). */
    val accent: Color,
)

/** Escopo do slot `renderEvent` (render custom do conteúdo de um bloco). */
data class ScheduleEventScope(
    val event: ScheduleEvent,
    val box: BlockBox,
    val laneIndex: Int,
    val laneCount: Int,
    val selected: Boolean,
    /** "HH:mm–HH:mm" já formatado. */
    val timeLabel: String,
    val colors: ScheduleEventColors,
)

/** Textos i18n do scheduler (defaults pt-BR). */
data class AppTimeGridTexts(
    val timeAxis: String = "Hora",
    val now: String = "Agora",
    val block: String = "Bloqueio",
)

/** Cores default de evento derivadas do tema (o app sobrepõe por status). */
@Composable
private fun defaultEventColors(): ScheduleEventColors = ScheduleEventColors(
    container = MaterialTheme.colorScheme.secondaryContainer,
    content = MaterialTheme.colorScheme.onSecondaryContainer,
    accent = MaterialTheme.colorScheme.primary,
)

private val SINGLE_COLUMN = ScheduleResource(id = "__single__", label = "")

/**
 * @param resources Colunas/recursos (um profissional por coluna). Vazio ⇒ **coluna única** (todos os
 *   eventos numa timeline, ex.: agenda pessoal do Influencer).
 * @param events Eventos posicionados por minuto real.
 * @param blocks Bloqueios/folgas (hachurados). `resourceId` nulo cobre todas as colunas.
 * @param window Janela horária explícita. `null` ⇒ **derivada** de `businessRanges` + dados.
 * @param businessRanges Expediente do dia p/ derivar a janela (não fixa 7–22).
 * @param hourHeight Altura de 1 hora. `Dp.Unspecified` ⇒ responsivo (compacto 56dp, senão 64dp).
 * @param slotStepMin Passo das linhas-guia / arredondamento do clique em área livre. Default 30.
 * @param minEventHeight Altura mínima de um bloco (alvo de toque). Default 44dp.
 * @param now "Agora" p/ desenhar a linha. `null` = sem linha (só desenha se dentro da janela).
 * @param eventColors Mapeia evento→cores (o app resolve status→cor AA). Default = tema.
 * @param renderEvent Conteúdo custom do bloco. `null` = rótulo + horário + descrição.
 * @param onEventClick Clique num evento.
 * @param onSlotClick Clique numa área livre (columnId, minuto arredondado ao `slotStepMin`).
 * @param selectedEventId Evento destacado (anel).
 * @param selectedResourceId (compacto) recurso visível; `null` = estado interno (1º recurso).
 * @param onResourceSelected (compacto) troca de recurso pelos chips.
 * @param minColumnWidth Largura mínima de coluna; abaixo dela vira scroll-x. Default 150dp.
 * @param emptyState Nó exibido quando não há NENHUM evento (grade segue visível).
 */
@Composable
fun AppTimeGridScheduler(
    events: List<ScheduleEvent>,
    modifier: Modifier = Modifier,
    resources: List<ScheduleResource> = emptyList(),
    blocks: List<ScheduleBlock> = emptyList(),
    window: BusinessWindow? = null,
    businessRanges: List<WorkRange> = emptyList(),
    hourHeight: Dp = Dp.Unspecified,
    slotStepMin: Int = 30,
    minEventHeight: Dp = 44.dp,
    now: kotlinx.datetime.LocalDateTime? = null,
    eventColors: ((ScheduleEvent) -> ScheduleEventColors)? = null,
    renderEvent: (@Composable (ScheduleEventScope) -> Unit)? = null,
    onEventClick: ((ScheduleEvent) -> Unit)? = null,
    onSlotClick: ((columnId: String, minute: Int) -> Unit)? = null,
    selectedEventId: String? = null,
    selectedResourceId: String? = null,
    onResourceSelected: ((String) -> Unit)? = null,
    minColumnWidth: Dp = 150.dp,
    texts: AppTimeGridTexts = AppTimeGridTexts(),
    emptyState: (@Composable () -> Unit)? = null,
) {
    val isCompact = LocalIsCompact.current
    val fallbackColors = defaultEventColors()
    val resolveColors: (ScheduleEvent) -> ScheduleEventColors = eventColors ?: { fallbackColors }
    val allColumns = if (resources.isNotEmpty()) resources else listOf(SINGLE_COLUMN)
    val isSingle = allColumns.size == 1 && allColumns[0].id == SINGLE_COLUMN.id

    val effectiveWindow = remember(window, businessRanges, events, blocks) {
        window ?: computeTimeWindow(
            ComputeWindowOptions(businessRanges = businessRanges, events = events, blocks = blocks),
        )
    }

    val resolvedHourHeight = if (hourHeight != Dp.Unspecified) hourHeight else if (isCompact) 56.dp else 64.dp
    val unitPerMinute = resolvedHourHeight.value / 60f
    val totalHeightDp = ((effectiveWindow.endMin - effectiveWindow.startMin) * unitPerMinute).dp
    val ticks = remember(effectiveWindow) { hourTicks(effectiveWindow, 60) }

    // Recurso visível no modo compacto (1 coluna por vez).
    val compactMultiResource = isCompact && !isSingle && allColumns.size > 1
    var internalSelected by rememberSaveable { mutableStateOf(allColumns.first().id) }
    val activeResourceId = selectedResourceId ?: internalSelected
    val columns: List<ScheduleResource> = if (compactMultiResource) {
        listOf(allColumns.firstOrNull { it.id == activeResourceId } ?: allColumns.first())
    } else {
        allColumns
    }

    val nowMin = now?.let { minutesOfDay(it) }
        ?.takeIf { it in effectiveWindow.startMin..effectiveWindow.endMin }

    // Distribui eventos e bloqueios por coluna.
    val eventsByColumn = remember(columns, events, isSingle) {
        val map = LinkedHashMap<String, MutableList<ScheduleEvent>>()
        for (c in columns) map[c.id] = ArrayList()
        for (e in events) {
            val key = if (isSingle) SINGLE_COLUMN.id else e.resourceId
            (if (key != null) map[key] else null)?.add(e)
        }
        map
    }
    val blocksByColumn = remember(columns, blocks, isSingle) {
        val map = LinkedHashMap<String, MutableList<ScheduleBlock>>()
        for (c in columns) map[c.id] = ArrayList()
        for (b in blocks) {
            if (b.resourceId == null || isSingle) {
                for (c in columns) map[c.id]?.add(b)
            } else {
                map[b.resourceId]?.add(b)
            }
        }
        map
    }

    val timeAxisWidth = 52.dp
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints {
            val availableForCols = maxWidth - timeAxisWidth
            val columnWidth: Dp = when {
                compactMultiResource || isSingle -> availableForCols
                else -> {
                    val even = availableForCols / columns.size
                    if (even >= minColumnWidth) even else minColumnWidth
                }
            }
            val needsHScroll = !isSingle && !compactMultiResource &&
                (columnWidth * columns.size) > availableForCols

            Column {
                // Chips de recurso (modo compacto multi-recurso).
                if (compactMultiResource) {
                    ResourceChips(
                        resources = allColumns,
                        selectedId = activeResourceId,
                        onSelect = { id ->
                            if (onResourceSelected != null) onResourceSelected(id) else internalSelected = id
                        },
                    )
                }

                // Cabeçalho (fixo verticalmente; rola junto na horizontal).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (needsHScroll) Modifier.horizontalScroll(hScroll) else Modifier)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Box(
                        modifier = Modifier.width(timeAxisWidth).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = texts.timeAxis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    columns.forEach { col ->
                        ColumnHeader(col = col, isSingle = isSingle, width = columnWidth)
                    }
                }

                // Corpo (rola na vertical; compartilha o hScroll do cabeçalho).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(vScroll)
                        .then(if (needsHScroll) Modifier.horizontalScroll(hScroll) else Modifier),
                ) {
                    // Eixo de tempo.
                    Box(modifier = Modifier.width(timeAxisWidth).height(totalHeightDp)) {
                        ticks.forEach { t ->
                            Text(
                                text = formatTimeOfDay(t),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .offset(y = ((t - effectiveWindow.startMin) * unitPerMinute).dp - 7.dp)
                                    .padding(end = 6.dp)
                                    .fillMaxWidth(),
                            )
                        }
                        if (nowMin != null) {
                            NowChip(
                                label = formatTimeOfDay(nowMin),
                                modifier = Modifier.offset(
                                    y = ((nowMin - effectiveWindow.startMin) * unitPerMinute).dp - 7.dp,
                                ),
                            )
                        }
                    }

                    // Colunas de recurso.
                    columns.forEach { col ->
                        ResourceColumn(
                            column = col,
                            width = columnWidth,
                            totalHeight = totalHeightDp,
                            window = effectiveWindow,
                            unitPerMinute = unitPerMinute,
                            slotStepMin = slotStepMin,
                            minEventHeight = minEventHeight,
                            events = eventsByColumn[col.id].orEmpty(),
                            blocks = blocksByColumn[col.id].orEmpty(),
                            nowMin = nowMin,
                            isSingle = isSingle,
                            eventColors = resolveColors,
                            renderEvent = renderEvent,
                            onEventClick = onEventClick,
                            onSlotClick = onSlotClick,
                            selectedEventId = selectedEventId,
                            texts = texts,
                        )
                    }
                }
            }

            // Estado vazio sobreposto (grade segue visível atrás).
            if (events.isEmpty() && emptyState != null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
                    contentAlignment = Alignment.TopCenter,
                ) { emptyState() }
            }
        }
    }
}

@Composable
private fun ResourceChips(
    resources: List<ScheduleResource>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        resources.forEach { r ->
            val selected = r.id == selectedId
            Surface(
                onClick = { onSelect(r.id) },
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(40.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = r.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(col: ScheduleResource, isSingle: Boolean, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(if (isSingle) 8.dp else 48.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!isSingle) {
            Column {
                Text(
                    text = col.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (col.subtitle != null) {
                    Text(
                        text = col.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NowChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun ResourceColumn(
    column: ScheduleResource,
    width: Dp,
    totalHeight: Dp,
    window: BusinessWindow,
    unitPerMinute: Float,
    slotStepMin: Int,
    minEventHeight: Dp,
    events: List<ScheduleEvent>,
    blocks: List<ScheduleBlock>,
    nowMin: Int?,
    isSingle: Boolean,
    eventColors: (ScheduleEvent) -> ScheduleEventColors,
    renderEvent: (@Composable (ScheduleEventScope) -> Unit)?,
    onEventClick: ((ScheduleEvent) -> Unit)?,
    onSlotClick: ((columnId: String, minute: Int) -> Unit)?,
    selectedEventId: String?,
    texts: AppTimeGridTexts,
) {
    val localDensity = LocalDensity.current
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val offHoursColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val blockColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val nowColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val laidOut = remember(events) { packEventLanes(events) }
    val offHours = remember(window, column.availableRanges) { offHoursRanges(window, column.availableRanges) }

    Box(
        modifier = Modifier
            .width(width)
            .height(totalHeight)
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (onSlotClick != null) {
                    Modifier.pointerInput(window, unitPerMinute, slotStepMin) {
                        detectTapGestures { offset ->
                            val dpY = with(localDensity) { offset.y.toDp() }.value
                            val rawMin = window.startMin + (dpY / unitPerMinute)
                            val snapped = (kotlin.math.round(rawMin / slotStepMin) * slotStepMin).toInt()
                            onSlotClick(column.id, snapped)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // Linhas-guia por passo.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepPx = (slotStepMin * unitPerMinute).dp.toPx()
            if (stepPx > 0f) {
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = guideColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += stepPx
                }
            }
        }

        // Fora-de-expediente (sombra).
        offHours.forEach { r ->
            Box(
                modifier = Modifier
                    .offset(y = ((r.startMin - window.startMin) * unitPerMinute).dp)
                    .height(((r.endMin - r.startMin) * unitPerMinute).dp)
                    .fillMaxWidth()
                    .background(offHoursColor),
            )
        }

        // Bloqueios (hachura).
        blocks.forEach { b ->
            val range = toMinuteRange(b.start, b.end)
            val box = positionInWindow(range, window, unitPerMinute, 8f)
            HatchedBlock(
                top = box.top.dp,
                height = box.height.dp,
                hatched = b.variant == ScheduleBlockVariant.Block,
                baseColor = blockColor,
                lineColor = borderColor,
                label = b.label,
                contentDescription = texts.block + (b.label?.let { ": $it" } ?: ""),
            )
        }

        // Linha do "agora".
        if (nowMin != null) {
            Box(
                modifier = Modifier
                    .offset(y = ((nowMin - window.startMin) * unitPerMinute).dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(nowColor)
                    .clearAndSetSemantics { contentDescription = texts.now },
            )
        }

        // Eventos (por minuto real, em lanes).
        laidOut.forEach { lo ->
            val positioned = positionInWindow(lo.range, window, unitPerMinute, minEventHeight.value)
            val laneWidth = width / lo.laneCount
            val selected = selectedEventId != null && lo.event.id == selectedEventId
            val colors = eventColors(lo.event)
            val timeLabel = "${formatTimeOfDay(lo.range.startMin)}–${formatTimeOfDay(lo.range.endMin)}"

            // Buffer (rabo esmaecido).
            if (lo.event.bufferMin > 0) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = laneWidth * lo.laneIndex + 2.dp,
                            y = (positioned.top + positioned.height).dp,
                        )
                        .width(laneWidth - 4.dp)
                        .height((lo.event.bufferMin * unitPerMinute).dp)
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(colors.container.copy(alpha = 0.35f)),
                )
            }

            EventBlock(
                scope = ScheduleEventScope(
                    event = lo.event,
                    box = positioned,
                    laneIndex = lo.laneIndex,
                    laneCount = lo.laneCount,
                    selected = selected,
                    timeLabel = timeLabel,
                    colors = colors,
                ),
                xOffset = laneWidth * lo.laneIndex + 2.dp,
                width = laneWidth - 4.dp,
                borderColor = if (selected) nowColor else borderColor,
                columnLabel = if (isSingle) null else column.label,
                onClick = onEventClick,
                renderEvent = renderEvent,
            )
        }
    }
}

@Composable
private fun HatchedBlock(
    top: Dp,
    height: Dp,
    hatched: Boolean,
    baseColor: Color,
    lineColor: Color,
    label: String?,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .offset(y = top)
            .fillMaxWidth()
            .height(height)
            .background(baseColor)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        if (hatched) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gap = 10f * density
                val strokeW = 1.5f * density
                var x = -size.height
                while (x < size.width) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(x, size.height),
                        end = androidx.compose.ui.geometry.Offset(x + size.height, 0f),
                        strokeWidth = strokeW,
                    )
                    x += gap
                }
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EventBlock(
    scope: ScheduleEventScope,
    xOffset: Dp,
    width: Dp,
    borderColor: Color,
    columnLabel: String?,
    onClick: ((ScheduleEvent) -> Unit)?,
    renderEvent: (@Composable (ScheduleEventScope) -> Unit)?,
) {
    val a11y = listOfNotNull(
        scope.event.label,
        scope.event.description,
        scope.timeLabel,
        scope.event.status,
        columnLabel,
    ).joinToString(", ")

    Surface(
        onClick = { onClick?.invoke(scope.event) },
        enabled = onClick != null,
        color = scope.colors.container,
        contentColor = scope.colors.content,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (scope.selected) 2.dp else 1.dp,
            color = borderColor,
        ),
        modifier = Modifier
            .offset(x = xOffset, y = scope.box.top.dp)
            .width(width)
            .height(scope.box.height.dp)
            .clearAndSetSemantics { contentDescription = a11y },
    ) {
        Row(Modifier.fillMaxSize()) {
            // Acento (borda-esquerda cheia do status).
            Box(Modifier.width(4.dp).fillMaxHeight().background(scope.colors.accent))
            Box(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                if (renderEvent != null) {
                    renderEvent(scope)
                } else {
                    Column {
                        if (scope.event.label != null) {
                            Text(
                                text = scope.event.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = scope.timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scope.colors.content.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                        if (scope.event.description != null && scope.box.height > 44f) {
                            Text(
                                text = scope.event.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = scope.colors.content.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
