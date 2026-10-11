package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.locale.DateSkeletons
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_clear
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_lowest
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_next
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_previous
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_selected
import br.com.codecacto.kmplib.generated.resources.kmplib_price_history_summary
import br.com.codecacto.kmplib.ui.theme.AppColors
import kotlinx.datetime.TimeZone
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos do [PriceHistoryChart]. O default ([rememberPriceHistoryChartTexts]) sai dos recursos da
 * lib, no idioma da tela (pt-BR, en, es, pt-PT). Para trocar uma frase: `.copy(…)`.
 *
 * @property empty sem nenhuma captura na janela.
 * @property lowestLegend legenda do marcador: (preço, data) → "Menor preço: R$ 89,90 em 12/09".
 * @property summary leitura do leitor de tela: (atual, menor, data do menor, maior, data do maior).
 * @property selected ponto escolhido: (preço, data e hora) → "R$ 99,90 em 29/09 14:32".
 * @property nextPoint / [previousPoint] / [clearSelection] ações de acessibilidade.
 */
@Immutable
data class PriceHistoryChartTexts(
    val empty: String,
    val lowestLegend: (price: String, date: String) -> String,
    val summary: (current: String, lowest: String, lowestDate: String, highest: String, highestDate: String) -> String,
    val selected: (price: String, dateTime: String) -> String,
    val nextPoint: String,
    val previousPoint: String,
    val clearSelection: String,
)

/** Textos padrão do [PriceHistoryChart], no idioma da tela. */
@Composable
fun rememberPriceHistoryChartTexts(): PriceHistoryChartTexts {
    val empty = kmpStringResource(Res.string.kmplib_price_history_empty)
    val lowest = kmpStringResource(Res.string.kmplib_price_history_lowest)
    val summary = kmpStringResource(Res.string.kmplib_price_history_summary)
    val selected = kmpStringResource(Res.string.kmplib_price_history_selected)
    val next = kmpStringResource(Res.string.kmplib_price_history_next)
    val previous = kmpStringResource(Res.string.kmplib_price_history_previous)
    val clear = kmpStringResource(Res.string.kmplib_price_history_clear)
    return remember(empty, lowest, summary, selected, next, previous, clear) {
        PriceHistoryChartTexts(
            empty = empty,
            lowestLegend = { p, d -> fillPriceTemplate(lowest, p, d) },
            summary = { c, l, ld, h, hd -> fillPriceTemplate(summary, c, l, ld, h, hd) },
            selected = { p, dt -> fillPriceTemplate(selected, p, dt) },
            nextPoint = next,
            previousPoint = previous,
            clearSelection = clear,
        )
    }
}

/** Medidas padrão do [PriceHistoryChart]. */
object PriceHistoryChartDefaults {
    /** Altura da área do gráfico (sem a legenda). */
    val Height: Dp = 180.dp

    /** Espessura da linha em degrau. */
    val LineWidth: Dp = 2.dp

    /** Raio do ponto de captura. */
    val CaptureDotRadius: Dp = 3.dp

    /** Raio do marcador do menor preço. */
    val LowestMarkerRadius: Dp = 5.dp

    /** Teto de marcas do eixo Y. */
    const val MAX_Y_TICKS: Int = 4

    /** Esqueleto CLDR da data/hora do ponto tocado: `29/09 14:32` · `9/29, 2:32 PM`. */
    const val SELECTED_SKELETON: String = "ddMMjjmm"
}

/**
 * **Histórico de preço em DEGRAU** — o gráfico de "quanto custou ao longo do tempo".
 *
 * Por que degrau e não linha: o preço de loja fica parado e salta; ligar duas capturas com uma
 * reta (ou curva suavizada, como o `AreaChart`) inventa valores que o produto nunca teve. Aqui a
 * linha fica no valor até a próxima captura e muda na vertical.
 *
 * - **Eixo Y real** na moeda ([priceFormatter]; default BRL), em marcas redondas, sem forçar o zero.
 * - **Pontos de captura** (onde o preço foi visto), quando cabem na largura.
 * - **Marca do menor preço** (cor de sucesso do tema) + legenda com valor e data.
 * - **Toque ou arrasto** mostra a data/hora da captura e o preço daquele trecho.
 * - **Acessível**: o leitor de tela lê o resumo (atual, menor e maior com data) e navega pelas
 *   mudanças de preço com as ações "próxima"/"anterior".
 *
 * Canvas do Compose, `commonMain` puro — o mesmo desenho no Android e no iOS, sem WebView e sem lib
 * de gráfico de terceiro. Par do `PriceHistoryChart` da weblib (`/charts`).
 *
 * ```kotlin
 * var dias by remember { mutableStateOf(30) }
 * SegmentedControl(…)  // 30 | 90
 * PriceHistoryChart(
 *     points = state.historico.map { PricePoint(it.capturadoEm, it.precoCentavos) },
 *     startMillis = agora - dias * 86_400_000L,
 *     endMillis = agora,          // o último preço segue valendo até hoje
 * )
 * ```
 *
 * Sem nenhuma captura na janela mostra [PriceHistoryChartTexts.empty] no lugar da área. Para um
 * estado vazio de tela inteira, a tela usa `EmptyState` e nem chama o gráfico.
 *
 * @param points capturas, em qualquer ordem (o componente ordena).
 * @param startMillis início da janela (30/90 dias); `null` = primeira captura. A captura anterior
 *   ao início vira o preço de abertura — o preço que já valia.
 * @param endMillis fim da janela; `null` = última captura. Passe o "agora".
 * @param chartHeight altura da área do gráfico.
 * @param lineColor cor da linha. Default `colorScheme.primary`.
 * @param lowestColor cor do marcador do menor preço. Default a cor de sucesso do tema.
 * @param priceFormatter preço completo (tooltip, legenda, leitor de tela).
 * @param axisPriceFormatter marcas do eixo Y (sem centavos quando redondas).
 * @param timeZone fuso das datas exibidas. Default o do aparelho.
 * @param showLowestLegend mostra a linha "Menor preço: … em …" abaixo do gráfico.
 */
@Composable
fun PriceHistoryChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    startMillis: Long? = null,
    endMillis: Long? = null,
    chartHeight: Dp = PriceHistoryChartDefaults.Height,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    lowestColor: Color = AppColors.current.success,
    priceFormatter: (Long) -> String = ::defaultPriceFormatter,
    axisPriceFormatter: (Long) -> String = ::defaultPriceAxisFormatter,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    showLowestLegend: Boolean = true,
    texts: PriceHistoryChartTexts = rememberPriceHistoryChartTexts(),
) {
    val series = remember(points, startMillis, endMillis) { buildPriceHistory(points, startMillis, endMillis) }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    if (series.isEmpty) {
        Box(
            modifier = modifier.fillMaxWidth().height(chartHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(texts.empty, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
        }
        return
    }

    val low = series.lowest!!
    val high = series.highest!!
    val ticks = remember(low.priceCents, high.priceCents) {
        priceAxisTicks(low.priceCents, high.priceCents, PriceHistoryChartDefaults.MAX_Y_TICKS)
    }
    fun dateOf(millis: Long) = RegionalFormat.formatDateTime(millis, DateSkeletons.DAY_MONTH, timeZone)
    fun dateTimeOf(millis: Long) = RegionalFormat.formatDateTime(millis, PriceHistoryChartDefaults.SELECTED_SKELETON, timeZone)

    var selected by remember { mutableStateOf<Int?>(null) }
    // Janela nova (30 → 90) invalida o índice escolhido.
    LaunchedEffect(series) { selected = null }
    val stepCount = series.steps.size

    val summary = texts.summary(
        priceFormatter(series.current!!.priceCents),
        priceFormatter(low.priceCents), dateOf(low.capturedAtMillis),
        priceFormatter(high.priceCents), dateOf(high.capturedAtMillis),
    )
    val selectedText = selected?.let { i ->
        val s = series.steps[i]
        texts.selected(priceFormatter(s.priceCents), dateTimeOf(s.capturedAtMillis))
    }

    val scheme = MaterialTheme.colorScheme
    val gridColor = scheme.outlineVariant
    val tooltipBg = scheme.inverseSurface
    val tooltipFg = scheme.inverseOnSurface
    val dotHole = scheme.surface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = tooltipFg)
    val measurer = rememberTextMeasurer()
    val axisLabels = remember(ticks, axisPriceFormatter) { ticks.map(axisPriceFormatter) }
    val xStartLabel = dateOf(series.startMillis)
    val xEndLabel = dateOf(series.endMillis)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics {
                    contentDescription = summary
                    if (selectedText != null) stateDescription = selectedText
                    customActions = buildList {
                        add(CustomAccessibilityAction(texts.nextPoint) {
                            selected = ((selected ?: -1) + 1).coerceAtMost(stepCount - 1); true
                        })
                        add(CustomAccessibilityAction(texts.previousPoint) {
                            selected = ((selected ?: stepCount) - 1).coerceAtLeast(0); true
                        })
                        if (selected != null) {
                            add(CustomAccessibilityAction(texts.clearSelection) { selected = null; true })
                        }
                    }
                }
                .pointerInput(series, axisLabels) {
                    val left = plotLeftOf(this, measurer, axisLabels, labelStyle)
                    val right = size.width - PriceHistoryChartDefaults.LowestMarkerRadius.toPx()
                    detectTapGestures { offset -> selected = stepAtX(series, offset.x, left, right) }
                }
                .pointerInput(series, axisLabels) {
                    val left = plotLeftOf(this, measurer, axisLabels, labelStyle)
                    val right = size.width - PriceHistoryChartDefaults.LowestMarkerRadius.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> selected = stepAtX(series, offset.x, left, right) },
                    ) { change, _ ->
                        change.consume()
                        selected = stepAtX(series, change.position.x, left, right)
                    }
                },
        ) {
            val gap = 8.dp.toPx()
            val labelLayouts = axisLabels.map { measurer.measure(it, labelStyle) }
            val left = (labelLayouts.maxOfOrNull { it.size.width } ?: 0) + gap
            val xStart = measurer.measure(xStartLabel, labelStyle)
            val xEnd = measurer.measure(xEndLabel, labelStyle)
            val tooltipH = measurer.measure("0", tooltipStyle).size.height + 8.dp.toPx()
            val top = tooltipH + 4.dp.toPx()
            val bottom = size.height - xStart.size.height - gap
            val right = size.width - PriceHistoryChartDefaults.LowestMarkerRadius.toPx()
            val plotW = (right - left).coerceAtLeast(1f)
            val plotH = (bottom - top).coerceAtLeast(1f)
            val loTick = ticks.first()
            val hiTick = ticks.last()

            fun xOf(t: Long) = left + priceTimeFraction(t, series.startMillis, series.endMillis) * plotW
            fun yOf(c: Long) = bottom - priceValueFraction(c, loTick, hiTick) * plotH

            // Grade e eixo Y
            ticks.forEachIndexed { i, tick ->
                val y = yOf(tick)
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                val l = labelLayouts[i]
                drawText(l, topLeft = Offset(left - gap - l.size.width, y - l.size.height / 2f))
            }
            // Eixo X: início e fim da janela
            drawText(xStart, topLeft = Offset(left, bottom + gap / 2))
            drawText(xEnd, topLeft = Offset((right - xEnd.size.width).coerceAtLeast(left), bottom + gap / 2))

            // Menor preço: guia tracejada
            val lowY = yOf(low.priceCents)
            drawLine(
                lowestColor.copy(alpha = 0.6f), Offset(left, lowY), Offset(right, lowY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )

            // Linha em degrau
            val steps = series.steps
            val path = Path()
            steps.forEachIndexed { i, s ->
                val y = yOf(s.priceCents)
                if (i == 0) path.moveTo(xOf(s.startMillis), y) else path.lineTo(xOf(s.startMillis), y)
                path.lineTo(xOf(s.endMillis), y)
            }
            if (steps.size == 1 && steps[0].startMillis == steps[0].endMillis) {
                // Uma captura só e janela sem largura: a linha vira um traço de ponta a ponta.
                path.reset()
                path.moveTo(left, yOf(steps[0].priceCents)); path.lineTo(right, yOf(steps[0].priceCents))
            }
            drawPath(
                path, lineColor,
                style = Stroke(PriceHistoryChartDefaults.LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Miter),
            )

            // Pontos de captura, só quando cabem (senão viram uma faixa grossa)
            val captures = steps.filter { !it.carriedOver }
            if (captures.isNotEmpty() && plotW / captures.size >= 8.dp.toPx()) {
                captures.forEach { s ->
                    drawCircle(lineColor, PriceHistoryChartDefaults.CaptureDotRadius.toPx(), Offset(xOf(s.startMillis), yOf(s.priceCents)))
                }
            }

            // Marcador do menor preço
            val lowStep = steps[low.stepIndex]
            val lowCenter = Offset(xOf(lowStep.startMillis), lowY)
            drawCircle(lowestColor, PriceHistoryChartDefaults.LowestMarkerRadius.toPx(), lowCenter)
            drawCircle(dotHole, PriceHistoryChartDefaults.LowestMarkerRadius.toPx() / 2.2f, lowCenter)

            // Seleção: guia vertical + ponto + balão
            selected?.let { i ->
                val s = steps[i]
                val cx = ((xOf(s.startMillis) + xOf(s.endMillis)) / 2f).coerceIn(left, right)
                val cy = yOf(s.priceCents)
                drawLine(onSurfaceVariant, Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx())
                drawCircle(lineColor, PriceHistoryChartDefaults.LowestMarkerRadius.toPx(), Offset(cx, cy))
                drawTooltip(measurer.measure(selectedText.orEmpty(), tooltipStyle), cx, tooltipH, left, size.width, tooltipBg)
            }
        }

        if (showLowestLegend) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(lowestColor, CircleShape))
                Text(
                    texts.lowestLegend(priceFormatter(low.priceCents), dateOf(low.capturedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.drawTooltip(
    layout: androidx.compose.ui.text.TextLayoutResult,
    centerX: Float,
    height: Float,
    minX: Float,
    maxX: Float,
    background: Color,
) {
    val padH = 8.dp.toPx()
    val w = layout.size.width + padH * 2
    val x = (centerX - w / 2).coerceIn(minX, (maxX - w).coerceAtLeast(minX))
    drawRoundRect(background, Offset(x, 0f), Size(w, height), CornerRadius(6.dp.toPx()))
    drawText(layout, topLeft = Offset(x + padH, (height - layout.size.height) / 2f))
}

/** Largura da coluna de rótulos do eixo Y — a mesma conta do desenho, para o toque acertar. */
private fun plotLeftOf(
    density: androidx.compose.ui.unit.Density,
    measurer: androidx.compose.ui.text.TextMeasurer,
    labels: List<String>,
    style: TextStyle,
): Float = with(density) {
    (labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0) + 8.dp.toPx()
}

private fun stepAtX(series: PriceHistorySeries, x: Float, left: Float, right: Float): Int? {
    if (series.isEmpty) return null
    val fraction = if (right <= left) 0f else ((x - left) / (right - left)).coerceIn(0f, 1f)
    val t = series.startMillis + ((series.endMillis - series.startMillis) * fraction.toDouble()).toLong()
    return priceStepIndexAt(series.steps, t).takeIf { it >= 0 }
}
