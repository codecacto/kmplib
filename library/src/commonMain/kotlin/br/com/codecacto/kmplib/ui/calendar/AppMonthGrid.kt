package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import kotlinx.datetime.LocalDate

/* ─────────────────────────── AppMonthGrid (visão MÊS) ───────────────────────────
 * Par mobile do `MonthGrid` da weblib e a peça que faltava para a migração do Influencer (o
 * `AppTimeGridScheduler` cobre Dia/Semana — grade temporal; a visão MÊS é uma grade de 6 semanas em
 * células, outra natureza). Overview do mês: nº de eventos por dia + dots por evento e, no
 * expanded, até N mini-blocos por célula. Domínio-agnóstico (cor por `eventColors`, igual ao
 * scheduler — o consumidor mapeia `status`→cor), acessível, tema 100% por tokens.
 *
 * Responsividade (`LocalIsCompact`): telefone mostra só o número + contagem + dots; tablet/desktop
 * mostra os mini-blocos com rótulo e o "+N mais". Semana começa no DOMINGO (padrão da casa).
 */

/** Rótulos curtos de dia da semana (domingo→sábado), pt-BR default. */
val DEFAULT_WEEKDAY_LABELS: List<String> = listOf("D", "S", "T", "Q", "Q", "S", "S")

/** Textos i18n do `AppMonthGrid` (defaults pt-BR). */
data class AppMonthGridTexts(
    val moreLabel: (Int) -> String = { n -> "+$n mais" },
    val dayContentDescription: (day: Int, count: Int) -> String = { day, count -> "$day — $count evento(s)" },
)

/**
 * @param month Qualquer data do mês exibido (o mês é derivado dela).
 * @param events Eventos do período (distribuídos por dia do `start`).
 * @param selectedDate Dia destacado (seleção). `null` = nenhum.
 * @param today "Hoje" (destaque de círculo no número). `null` = sem destaque de hoje.
 * @param onSelectDay Clique numa célula/dia.
 * @param onEventClick Clique num mini-bloco (só expanded). Quando dado, o mini-bloco vira botão e o
 *   clique NÃO borbulha para `onSelectDay` (abre o item direto — paridade com o mês do Influencer).
 * @param eventColors Mapeia evento→cores (o app resolve status→cor). Default = accent do tema.
 * @param maxPerCell Máx. de mini-blocos/dots por célula. Default 3.
 * @param weekdayLabels Rótulos de dia da semana (7, dom→sáb).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppMonthGrid(
    month: LocalDate,
    events: List<ScheduleEvent>,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null,
    today: LocalDate? = null,
    onSelectDay: ((LocalDate) -> Unit)? = null,
    onEventClick: ((ScheduleEvent) -> Unit)? = null,
    eventColors: ((ScheduleEvent) -> ScheduleEventColors)? = null,
    maxPerCell: Int = 3,
    weekdayLabels: List<String> = DEFAULT_WEEKDAY_LABELS,
    texts: AppMonthGridTexts = AppMonthGridTexts(),
) {
    val isCompact = LocalIsCompact.current
    val fallbackColors = defaultEventColors()
    val resolveColors: (ScheduleEvent) -> ScheduleEventColors = eventColors ?: { fallbackColors }
    val accentOf: (ScheduleEvent) -> Color = { resolveColors(it).accent }

    val cells = remember(month) { monthGridCells(month) }
    val byDay = remember(events) { groupEventsByDay(events) }
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column {
            // Cabeçalho dos dias da semana.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 6 linhas × 7 colunas.
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        val items = byDay[cell.date].orEmpty()
                        MonthDayCell(
                            cell = cell,
                            items = items,
                            isSelected = selectedDate != null && cell.date == selectedDate,
                            isToday = today != null && cell.date == today,
                            isCompact = isCompact,
                            maxPerCell = maxPerCell,
                            accentOf = accentOf,
                            borderColor = borderColor,
                            onSelectDay = onSelectDay,
                            onEventClick = onEventClick,
                            texts = texts,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDayCell(
    cell: MonthCell,
    items: List<ScheduleEvent>,
    isSelected: Boolean,
    isToday: Boolean,
    isCompact: Boolean,
    maxPerCell: Int,
    accentOf: (ScheduleEvent) -> Color,
    borderColor: Color,
    onSelectDay: ((LocalDate) -> Unit)?,
    onEventClick: ((ScheduleEvent) -> Unit)?,
    texts: AppMonthGridTexts,
    modifier: Modifier = Modifier,
) {
    val shown = items.take(maxPerCell)
    val more = items.size - shown.size
    val cellBg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        !cell.inMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .heightIn(min = if (isCompact) 60.dp else 116.dp)
            .background(cellBg)
            .then(
                if (onSelectDay != null) Modifier.clickable { onSelectDay(cell.date) } else Modifier,
            )
            .padding(4.dp)
            .clearAndSetSemantics {
                contentDescription = texts.dayContentDescription(cell.date.dayOfMonth, items.size)
            },
    ) {
        // Borda superior/esquerda desenhada via um contorno leve na própria célula.
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Número do dia + contagem.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .then(
                            if (isToday) {
                                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cell.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.onPrimary
                            !cell.inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (items.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = items.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            if (isCompact) {
                // Só dots.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentOf(e)),
                        )
                    }
                }
            } else {
                // Mini-blocos com rótulo.
                shown.forEach { e ->
                    MiniEventBlock(
                        event = e,
                        accent = accentOf(e),
                        onEventClick = onEventClick,
                    )
                }
                if (more > 0) {
                    Text(
                        text = texts.moreLabel(more),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniEventBlock(
    event: ScheduleEvent,
    accent: Color,
    onEventClick: ((ScheduleEvent) -> Unit)?,
) {
    Surface(
        onClick = { onEventClick?.invoke(event) },
        enabled = onEventClick != null,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = event.label ?: ""
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(
                text = event.label ?: "",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
