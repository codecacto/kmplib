package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

// ---------------------------------------------------------------------------
// Gráfico de barras VERTICAIS para Compose Multiplatform (Android/iOS/Desktop),
// commonMain puro (sem expect/actual, sem dependência de gráficos externa).
//
// Promovido a partir do contorno local `RevenueBarChart` do Influencer (Fase 4
// Fatia A) — 3º consumidor do padrão (Influencer + MeuFrete G-MF-M-04 + Locador
// G-M1). Espelha a API "data + cor + altura + emptyMessage" do `SimpleBarChart`
// da weblib, mas em barras verticais (layout de dashboard mobile "últimos N
// meses"). Cobre 1 série ([BarChart]) e ≥2 séries empilhadas ([StackedBarChart]).
//
// Regras de plataforma: cores via tokens do tema (`MaterialTheme.colorScheme` —
// nenhuma cor hardcoded); responsividade via `LocalIsCompact`; cálculo de escala
// em função pura testável; texto de valor formatado por callback do chamador
// (a lib não conhece moeda/domínio).
// ---------------------------------------------------------------------------

/**
 * Um ponto do gráfico de barras de série única.
 *
 * @param label Rótulo do eixo X (ex.: "Jan", "Caminhão A"). Exibido sob a barra.
 * @param value Valor numérico (>= 0). Valores negativos são tratados como 0.
 */
data class BarChartEntry(
    val label: String,
    val value: Double,
)

/** Um segmento de uma barra empilhada (uma "série" dentro daquela barra). */
data class BarSegment(
    /** Valor numérico do segmento (>= 0). Negativos tratados como 0. */
    val value: Double,
    /** Cor do segmento. Quando `null`, o [StackedBarChart] usa a cor da série pela ordem. */
    val color: Color? = null,
)

/** Uma barra empilhada: rótulo do eixo X + segmentos (de baixo para cima na ordem da lista). */
data class StackedBarEntry(
    val label: String,
    val segments: List<BarSegment>,
)

/** Item de legenda (ponto colorido + texto). */
data class ChartLegendItem(
    val label: String,
    val color: Color,
)

/** Maior valor entre as entradas (para escalar as barras). 0 quando vazio/tudo zero. */
internal fun List<BarChartEntry>.barChartMax(): Double =
    maxOfOrNull { it.value.coerceAtLeast(0.0) } ?: 0.0

/** Maior total empilhado entre as barras (para escalar). 0 quando vazio/tudo zero. */
internal fun List<StackedBarEntry>.stackedBarChartMax(): Double =
    maxOfOrNull { entry -> entry.segments.sumOf { it.value.coerceAtLeast(0.0) } } ?: 0.0

/**
 * Gráfico de barras verticais de **série única** (Compose MP, commonMain puro).
 *
 * Cada barra tem altura proporcional ao maior valor, rótulo abaixo e (opcional) o valor formatado
 * acima. Estado vazio (sem dados ou tudo zero) exibe [emptyMessage] centralizado. A cor padrão é
 * `MaterialTheme.colorScheme.primary` (tema da kmplib); a trilha de fundo usa `surfaceVariant`.
 *
 * ```kotlin
 * BarChart(
 *     data = listOf(BarChartEntry("Jan", 1200.0), BarChartEntry("Fev", 800.0)),
 *     valueFormatter = { "R$ ${it.toInt()}" },
 * )
 * ```
 *
 * @param data Entradas (recomendado até ~12 — ex.: meses do ano).
 * @param modifier Modificador externo.
 * @param barColor Cor das barras. Default: `colorScheme.primary`.
 * @param chartHeight Altura da área das barras (expandido). No compacto é reduzida ~25%.
 * @param valueFormatter Quando informado, exibe o valor formatado acima de cada barra (oculto no
 *   compacto para não poluir). A lib não formata moeda — o chamador decide o texto.
 * @param emptyMessage Texto exibido quando não há dados (ou todos os valores são 0).
 */
@Composable
fun BarChart(
    data: List<BarChartEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 128.dp,
    valueFormatter: ((Double) -> String)? = null,
    emptyMessage: String = "Sem dados para exibir.",
) {
    val max = data.barChartMax()
    if (data.isEmpty() || max <= 0.0) {
        EmptyChart(emptyMessage, chartHeight, modifier)
        return
    }

    val compact = LocalIsCompact.current
    val barAreaHeight = if (compact) chartHeight * 0.75f else chartHeight
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { entry ->
            val value = entry.value.coerceAtLeast(0.0)
            val fraction = (value / max).coerceIn(0.0, 1.0)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact && valueFormatter != null) {
                    Text(
                        text = if (value > 0.0) valueFormatter(value) else "",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .width(if (compact) 16.dp else 24.dp)
                        .height(barAreaHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(trackColor.copy(alpha = 0.35f)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.toFloat())
                            .clip(RoundedCornerShape(6.dp))
                            .background(barColor),
                    )
                }
                Spacer(Modifier.height(4.dp))
                BarLabel(entry.label)
            }
        }
    }
}

/**
 * Gráfico de barras verticais **empilhadas** (≥2 séries por barra) — Compose MP, commonMain puro.
 *
 * Cada barra é dividida em segmentos (de baixo para cima na ordem de [StackedBarEntry.segments]),
 * com altura total proporcional ao maior total entre as barras. As cores dos segmentos vêm de
 * [BarSegment.color] ou, quando `null`, de [seriesColors] pela posição da série (default
 * `primary`/`secondary`/`tertiary`). Cobre o caso "recebido + a receber" do dashboard do Influencer.
 *
 * Combine com [ChartLegend] para a legenda das séries.
 *
 * ```kotlin
 * StackedBarChart(
 *     data = months.map { m ->
 *         StackedBarEntry(m.label, listOf(BarSegment(m.received), BarSegment(m.toReceive)))
 *     },
 *     valueFormatter = { "R$ ${it.toInt()}" },
 * )
 * ```
 *
 * @param data Barras empilhadas (recomendado até ~12).
 * @param modifier Modificador externo.
 * @param seriesColors Cores por índice de série (usadas quando o segmento não traz cor própria).
 *   Default: `[primary, secondary, tertiary]` do tema. Cicla se houver mais séries que cores.
 * @param chartHeight Altura da área das barras (expandido). No compacto é reduzida ~25%.
 * @param valueFormatter Quando informado, exibe o TOTAL formatado acima de cada barra (oculto no
 *   compacto).
 * @param emptyMessage Texto exibido quando não há dados (ou todos os totais são 0).
 */
@Composable
fun StackedBarChart(
    data: List<StackedBarEntry>,
    modifier: Modifier = Modifier,
    seriesColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    ),
    chartHeight: Dp = 128.dp,
    valueFormatter: ((Double) -> String)? = null,
    emptyMessage: String = "Sem dados para exibir.",
) {
    val max = data.stackedBarChartMax()
    if (data.isEmpty() || max <= 0.0) {
        EmptyChart(emptyMessage, chartHeight, modifier)
        return
    }

    val compact = LocalIsCompact.current
    val barAreaHeight = if (compact) chartHeight * 0.75f else chartHeight
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fallback = seriesColors.ifEmpty { listOf(MaterialTheme.colorScheme.primary) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        data.forEach { entry ->
            val values = entry.segments.map { it.value.coerceAtLeast(0.0) }
            val total = values.sum()
            val totalFraction = (total / max).coerceIn(0.0, 1.0)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!compact && valueFormatter != null) {
                    Text(
                        text = if (total > 0.0) valueFormatter(total) else "",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .width(if (compact) 16.dp else 24.dp)
                        .height(barAreaHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(trackColor.copy(alpha = 0.35f)),
                    )
                    // Coluna empilhada: o ÚLTIMO segmento da lista fica no topo.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(totalFraction.toFloat())
                            .clip(RoundedCornerShape(6.dp)),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        entry.segments.indices.reversed().forEach { i ->
                            val segValue = values[i]
                            if (segValue <= 0.0 || total <= 0.0) return@forEach
                            val segColor = entry.segments[i].color ?: fallback[i % fallback.size]
                            val weight = (segValue / total).toFloat().coerceAtLeast(0.0001f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(weight)
                                    .background(segColor),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                BarLabel(entry.label)
            }
        }
    }
}

/**
 * Legenda horizontal de séries (ponto colorido + texto), para acompanhar [StackedBarChart] ou
 * [BarChart]. Cores e textos vêm do chamador (tokens do tema).
 */
@Composable
fun ChartLegend(
    items: List<ChartLegendItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(item.color),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarLabel(label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun EmptyChart(message: String, chartHeight: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
