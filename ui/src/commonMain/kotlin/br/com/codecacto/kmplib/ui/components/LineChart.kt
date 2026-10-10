package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.theme.AppColors
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

// ---------------------------------------------------------------------------
// Gráfico de LINHA / ÁREA (peso × tempo) para Compose Multiplatform, commonMain
// puro (Canvas, sem lib de gráfico externa que quebre no iOS). Espelha a filosofia
// e a API do `BarChart` da lib ("data + cor + altura + emptyMessage", tokens do
// tema, responsivo via `LocalIsCompact`) e o `SimpleAreaChart` da weblib
// (`@codecacto/weblib/charts`) — o par mobile faltante (GAP-AC-M-CHART-LINE).
//
// Serve a série temporal contínua de evolução: GMD/evolução do rebanho (Arroba
// Certa), e qualquer app com "evolução ao longo do tempo" (financeiro, saúde,
// estoque). Datas no eixo X em dd/MM/yyyy (o chamador já formata o rótulo).
// ---------------------------------------------------------------------------

/**
 * Um ponto do gráfico de linha: rótulo do eixo X (ex.: data "08/07/2026") + valor.
 *
 * @param label rótulo do eixo X. Para série temporal, formate a data em **dd/MM/yyyy**.
 * @param value valor numérico (peso, saldo, etc.). Pode ser negativo (o eixo Y acomoda).
 */
data class LineChartPoint(
    val label: String,
    val value: Double,
)

/**
 * Uma série do gráfico de linha (linha/área nomeada).
 *
 * @param name nome da série (para a legenda, ex.: "Boi 12", "Lote A").
 * @param points pontos na ordem temporal (esquerda→direita).
 * @param color cor da linha/área; quando `null`, o [LineChart] usa a cor pela posição da série.
 */
data class LineSeries(
    val name: String,
    val points: List<LineChartPoint>,
    val color: Color? = null,
)

/**
 * **Faixa de referência** desenhada ATRÁS das linhas (2.285.0): a banda entre [min] e [max] — ex.: a
 * faixa normal de um analito de exame ("70 a 99 mg/dL"). Um dos lados pode faltar (faixa aberta:
 * "até 200" = só [max]; "a partir de 40" = só [min]); aí a banda vai até a borda do gráfico.
 *
 * A escala Y passa a incluir os limites da banda: um valor sempre dentro da faixa não "some" com a
 * banda fora da tela, e o leitor vê de imediato a distância até o limite.
 *
 * @param min limite inferior (inclusivo), ou `null` (aberta embaixo).
 * @param max limite superior (inclusivo), ou `null` (aberta em cima).
 * @param color cor da banda. `null` = o "sucesso" do tema (`AppColors.success`), a 14% de opacidade.
 */
data class ReferenceBand(
    val min: Double? = null,
    val max: Double? = null,
    val color: Color? = null,
) {
    init {
        require(min != null || max != null) { "ReferenceBand precisa de min, de max ou dos dois" }
        require(min == null || max == null || min <= max) { "ReferenceBand: min ($min) maior que max ($max)" }
    }
}

/**
 * Limites (min, max) da escala Y incluindo os limites da [band] (quando há). Pura, testável.
 */
internal fun lineChartValueBounds(values: List<Double>, band: ReferenceBand?): Pair<Double, Double> =
    lineChartValueBounds(values + listOfNotNull(band?.min, band?.max))

/**
 * Faixa vertical (topo, base) da [band] em frações de 0..1 onde **0 = topo** do gráfico — o que o
 * `Canvas` desenha. Lado aberto vai até a borda. Pura, testável.
 */
internal fun referenceBandFractions(band: ReferenceBand, min: Double, max: Double): Pair<Float, Float> {
    val top = band.max?.let { 1f - normalizeToFraction(it, min, max) } ?: 0f
    val bottom = band.min?.let { 1f - normalizeToFraction(it, min, max) } ?: 1f
    return top to bottom
}

/**
 * Limites (min, max) para escalar o eixo Y de um conjunto de valores. Se todos forem iguais
 * (ou vazio), expande em ±1 para não colapsar a linha numa borda. Pura, testável.
 */
internal fun lineChartValueBounds(values: List<Double>): Pair<Double, Double> {
    if (values.isEmpty()) return -1.0 to 1.0
    val min = values.min()
    val max = values.max()
    if (min == max) return (min - 1.0) to (max + 1.0)
    return min to max
}

/**
 * Fração vertical (0..1) de [value] entre [min] e [max], onde **1 = topo** (maior valor) e
 * **0 = base**. Fora de faixa é clampado. Pura, testável.
 */
internal fun normalizeToFraction(value: Double, min: Double, max: Double): Float {
    if (max <= min) return 0.5f
    return ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Índices dos rótulos do eixo X a exibir, dado [count] pontos e o máximo de [maxLabels] rótulos
 * (evita poluição com muitas datas). Sempre inclui o primeiro e o último; distribui o restante
 * uniformemente. Pura, testável.
 */
internal fun xAxisLabelIndices(count: Int, maxLabels: Int): List<Int> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(0)
    val cap = maxLabels.coerceAtLeast(2)
    if (count <= cap) return (0 until count).toList()
    val result = LinkedHashSet<Int>()
    for (i in 0 until cap) {
        val idx = (i * (count - 1)) / (cap - 1)
        result.add(idx)
    }
    return result.toList()
}

/**
 * Gráfico de **linha/área** de uma única série (Compose MP, commonMain puro).
 *
 * ```kotlin
 * LineChart(
 *     points = pesagens.map { LineChartPoint(it.dataBr, it.pesoKg) },
 *     filled = true,
 *     valueFormatter = { "${it.toInt()} kg" },
 * )
 * ```
 *
 * @param points pontos na ordem temporal.
 * @param modifier modificador externo.
 * @param lineColor cor da linha/área. Default `colorScheme.primary`.
 * @param filled quando `true`, preenche a área sob a linha com um degradê sutil (área).
 * @param chartHeight altura da área do gráfico (expandido). No compacto reduz ~20%.
 * @param valueFormatter formata os rótulos do eixo Y (min/max). A lib não conhece a unidade.
 * @param showDots desenha um ponto em cada vértice.
 * @param emptyMessage texto quando não há dados (0 ou 1 ponto — linha precisa de ≥2).
 * @param referenceBand faixa de referência atrás da linha (2.285.0). Para ponto colorido por status
 *   com legenda em texto (exame), use o [ReferenceBandChart].
 */
@Composable
fun LineChart(
    points: List<LineChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false,
    chartHeight: Dp = 160.dp,
    valueFormatter: ((Double) -> String)? = null,
    showDots: Boolean = true,
    emptyMessage: String = "Sem dados para exibir.",
    referenceBand: ReferenceBand? = null,
) {
    LineChart(
        series = listOf(LineSeries(name = "", points = points, color = lineColor)),
        modifier = modifier,
        filled = filled,
        chartHeight = chartHeight,
        valueFormatter = valueFormatter,
        showDots = showDots,
        emptyMessage = emptyMessage,
        referenceBand = referenceBand,
    )
}

/**
 * Gráfico de **linha/área** com **1..N séries** (Compose MP, commonMain puro).
 *
 * Todas as séries compartilham a mesma escala Y (min/max entre todos os pontos) e o mesmo eixo X
 * (índice do ponto). Para comparar animais/lotes na tela de GMD, passe uma [LineSeries] por item e
 * acompanhe com [ChartLegend]. Cores por [LineSeries.color] ou por [seriesColors] pela posição.
 *
 * @param series séries a plotar (cada uma com seus pontos).
 * @param modifier modificador externo.
 * @param seriesColors cores por índice de série (quando a série não traz cor própria).
 * @param filled preenche a área sob cada linha (recomendado só para 1 série).
 * @param chartHeight altura da área do gráfico.
 * @param valueFormatter formata os rótulos do eixo Y (min/max).
 * @param showDots desenha um ponto em cada vértice.
 * @param maxXLabels máximo de rótulos de data no eixo X (default 4; evita poluir).
 * @param emptyMessage texto quando não há dados suficientes.
 * @param referenceBand faixa de referência atrás das linhas (2.285.0) — a escala Y passa a incluí-la.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    seriesColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    ),
    filled: Boolean = false,
    chartHeight: Dp = 160.dp,
    valueFormatter: ((Double) -> String)? = null,
    showDots: Boolean = true,
    maxXLabels: Int = 4,
    emptyMessage: String = "Sem dados para exibir.",
    referenceBand: ReferenceBand? = null,
) {
    val allValues = series.flatMap { s -> s.points.map { it.value } }
    val maxPoints = series.maxOfOrNull { it.points.size } ?: 0
    // Linha exige ao menos 2 pontos em alguma série.
    if (allValues.isEmpty() || maxPoints < 2) {
        EmptyLineChart(emptyMessage, chartHeight, modifier)
        return
    }

    val compact = LocalIsCompact.current
    val areaHeight = if (compact) chartHeight * 0.8f else chartHeight
    val (minValue, maxValue) = lineChartValueBounds(allValues, referenceBand)
    val bandColor = referenceBand?.let { it.color ?: AppColors.current.success }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fallbackColors = seriesColors.ifEmpty { listOf(MaterialTheme.colorScheme.primary) }

    // Rótulos do eixo X: usa os labels da série mais longa.
    val labelSource = series.maxByOrNull { it.points.size }?.points ?: emptyList()
    val labelIndices = xAxisLabelIndices(labelSource.size, maxXLabels)

    val yAxisWidth: Dp = if (valueFormatter != null) 44.dp else 0.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(areaHeight)) {
            if (valueFormatter != null) {
                Column(
                    modifier = Modifier.width(yAxisWidth).fillMaxHeight().padding(end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    AxisText(valueFormatter(maxValue), axisTextColor)
                    AxisText(valueFormatter((maxValue + minValue) / 2.0), axisTextColor)
                    AxisText(valueFormatter(minValue), axisTextColor)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Linhas de grade horizontais (topo/meio/base), tracejadas discretas.
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = h * frac
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = dash,
                        )
                    }

                    // Faixa de referência ATRÁS das linhas: banda translúcida + bordas tracejadas.
                    if (referenceBand != null && bandColor != null) {
                        val (topF, bottomF) = referenceBandFractions(referenceBand, minValue, maxValue)
                        val top = h * topF
                        val bottom = h * bottomF
                        drawRect(
                            color = bandColor.copy(alpha = 0.14f),
                            topLeft = Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(w, (bottom - top).coerceAtLeast(1f)),
                        )
                        listOfNotNull(
                            referenceBand.max?.let { top },
                            referenceBand.min?.let { bottom },
                        ).forEach { y ->
                            drawLine(
                                color = bandColor.copy(alpha = 0.7f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.5f,
                                pathEffect = dash,
                            )
                        }
                    }

                    series.forEachIndexed { index, s ->
                        if (s.points.size < 2) return@forEachIndexed
                        val color = s.color ?: fallbackColors[index % fallbackColors.size]
                        val stepX = if (s.points.size == 1) 0f else w / (s.points.size - 1)
                        val offsets = s.points.mapIndexed { i, p ->
                            val x = stepX * i
                            val frac = normalizeToFraction(p.value, minValue, maxValue)
                            val y = h - (frac * h)
                            Offset(x, y)
                        }

                        // Área preenchida (degradê sutil sob a linha).
                        if (filled) {
                            val area = Path().apply {
                                moveTo(offsets.first().x, h)
                                offsets.forEach { lineTo(it.x, it.y) }
                                lineTo(offsets.last().x, h)
                                close()
                            }
                            drawPath(
                                path = area,
                                brush = Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                                    startY = 0f,
                                    endY = h,
                                ),
                            )
                        }

                        // Linha.
                        val line = Path().apply {
                            moveTo(offsets.first().x, offsets.first().y)
                            offsets.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = line,
                            color = color,
                            style = Stroke(width = if (compact) 2.5f else 3f),
                        )

                        // Pontos.
                        if (showDots) {
                            offsets.forEach { drawCircle(color = color, radius = if (compact) 3f else 4f, center = it) }
                        }
                    }
                }
            }
        }

        // Eixo X (datas) alinhado à área do gráfico.
        if (labelIndices.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (valueFormatter != null) Spacer(Modifier.width(yAxisWidth))
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        labelIndices.forEach { idx ->
                            Text(
                                text = labelSource.getOrNull(idx)?.label.orEmpty(),
                                fontSize = 9.sp,
                                color = axisTextColor,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gráfico de **área** — atalho para `LineChart(..., filled = true)`. Recomendado para série única
 * (peso × tempo do animal), com a área preenchida sob a linha.
 */
@Composable
fun AreaChart(
    points: List<LineChartPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 160.dp,
    valueFormatter: ((Double) -> String)? = null,
    showDots: Boolean = true,
    emptyMessage: String = "Sem dados para exibir.",
) {
    LineChart(
        points = points,
        modifier = modifier,
        lineColor = lineColor,
        filled = true,
        chartHeight = chartHeight,
        valueFormatter = valueFormatter,
        showDots = showDots,
        emptyMessage = emptyMessage,
    )
}

@Composable
private fun AxisText(text: String, color: Color) {
    Text(text = text, fontSize = 9.sp, color = color, maxLines = 1)
}

@Composable
private fun EmptyLineChart(message: String, chartHeight: Dp, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(chartHeight),
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
