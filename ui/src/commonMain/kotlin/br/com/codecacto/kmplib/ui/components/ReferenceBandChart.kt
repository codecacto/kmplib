package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_band_from
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_band_range
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_band_up_to
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_next
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_point
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_point_no_status
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_previous
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_status_above
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_status_below
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_status_within
import br.com.codecacto.kmplib.generated.resources.kmplib_chart_summary
import br.com.codecacto.kmplib.ui.compare.formatCompareTemplate
import br.com.codecacto.kmplib.ui.theme.AppColors
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

// ---------------------------------------------------------------------------------------------
// Gráfico de EVOLUÇÃO com FAIXA DE REFERÊNCIA (2.285.0) — o par mobile do `ReferenceBandChart` da
// weblib (`/charts`, LW-REC). Nasceu no Vitalis (evolução de um analito de exame: glicemia, TSH…),
// e serve a qualquer medida com faixa "boa": peso-alvo, pressão, consumo.
// ---------------------------------------------------------------------------------------------

/**
 * Status de um ponto, decidido por QUEM SABE (o servidor/o médico — a faixa do laboratório e a do
 * médico podem divergir): o rótulo é o TEXTO que aparece na legenda e na leitura, e o tom é a cor.
 * A cor nunca aparece sozinha.
 */
@Immutable
data class ReferenceStatus(val label: String, val tone: StatusTone)

/**
 * Um ponto da evolução.
 *
 * @param label o rótulo do eixo X e da leitura (a data já formatada: "12/03/2026").
 * @param value o valor, na unidade da faixa.
 * @param date a data do ponto. Quando TODOS os pontos têm, o eixo X é proporcional ao tempo
 *   (exames de março, abril e dezembro não ficam igualmente espaçados); senão, por posição.
 * @param status o status vindo do servidor. `null` = derivado da [ReferenceBand] (abaixo, na faixa,
 *   acima) — e sem faixa, ponto sem status.
 */
@Immutable
data class ReferencePoint(
    val label: String,
    val value: Double,
    val date: LocalDate? = null,
    val status: ReferenceStatus? = null,
)

/** Onde um valor cai em relação à faixa. */
enum class BandPosition { BELOW, WITHIN, ABOVE }

/**
 * Posição de [value] em relação à [band] (limites inclusivos). `null` sem faixa. Pura, testável.
 */
fun classifyAgainstBand(value: Double, band: ReferenceBand?): BandPosition? {
    if (band == null) return null
    if (band.min != null && value < band.min) return BandPosition.BELOW
    if (band.max != null && value > band.max) return BandPosition.ABOVE
    return BandPosition.WITHIN
}

/**
 * Posição horizontal (0..1) de cada ponto: proporcional ao tempo quando todos têm `date` e há mais de
 * um dia distinto; senão, igualmente espaçados. Um ponto só fica no meio. Pura, testável.
 */
fun referenceChartXFractions(points: List<ReferencePoint>): List<Float> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) return listOf(0.5f)
    val days = points.map { it.date?.toEpochDays() }
    if (days.all { it != null }) {
        val d = days.map { it!! }
        val min = d.min()
        val max = d.max()
        if (max > min) return d.map { ((it - min).toDouble() / (max - min).toDouble()).toFloat() }
    }
    return points.indices.map { it.toFloat() / (points.size - 1) }
}

/** Índice do ponto mais próximo de [fraction] (0..1) no eixo X. `null` sem pontos. Pura, testável. */
fun nearestReferencePoint(xFractions: List<Float>, fraction: Float): Int? =
    xFractions.indices.minByOrNull { abs(xFractions[it] - fraction) }

/** Textos do [ReferenceBandChart] (4 idiomas). Default [rememberReferenceBandChartTexts]. */
@Immutable
data class ReferenceBandChartTexts(
    /** "Faixa de referência: 70 a 99 mg/dL" (valores já formatados). */
    val bandRange: (min: String, max: String) -> String,
    /** "Faixa de referência: a partir de 40". */
    val bandFrom: (min: String) -> String,
    /** "Faixa de referência: até 200". */
    val bandUpTo: (max: String) -> String,
    val below: String,
    val within: String,
    val above: String,
    /** "5 medições. Última: 12/03/2026: 105 mg/dL, Acima da faixa." */
    val summary: (count: Int, last: String) -> String,
    /** "12/03/2026: 105 mg/dL, Acima da faixa". */
    val point: (label: String, value: String, status: String?) -> String,
    val next: String,
    val previous: String,
    val empty: String,
)

@Composable
fun rememberReferenceBandChartTexts(): ReferenceBandChartTexts {
    val range = stringResource(Res.string.kmplib_chart_band_range)
    val from = stringResource(Res.string.kmplib_chart_band_from)
    val upTo = stringResource(Res.string.kmplib_chart_band_up_to)
    val below = stringResource(Res.string.kmplib_chart_status_below)
    val within = stringResource(Res.string.kmplib_chart_status_within)
    val above = stringResource(Res.string.kmplib_chart_status_above)
    val summary = stringResource(Res.string.kmplib_chart_summary)
    val point = stringResource(Res.string.kmplib_chart_point)
    val pointNoStatus = stringResource(Res.string.kmplib_chart_point_no_status)
    val next = stringResource(Res.string.kmplib_chart_next)
    val previous = stringResource(Res.string.kmplib_chart_previous)
    val empty = stringResource(Res.string.kmplib_chart_empty)
    return remember(range, from, upTo, below, within, above, summary, point, pointNoStatus, next, previous, empty) {
        ReferenceBandChartTexts(
            bandRange = { a, b -> formatCompareTemplate(range, a, b) },
            bandFrom = { a -> formatCompareTemplate(from, a) },
            bandUpTo = { b -> formatCompareTemplate(upTo, b) },
            below = below,
            within = within,
            above = above,
            summary = { n, last -> formatCompareTemplate(summary, n, last) },
            point = { l, v, st ->
                if (st == null) formatCompareTemplate(pointNoStatus, l, v) else formatCompareTemplate(point, l, v, st)
            },
            next = next,
            previous = previous,
            empty = empty,
        )
    }
}

/** Ids de automação do [ReferenceBandChart]. */
object ReferenceBandChartTestTags {
    const val CHART: String = "grafico-faixa"
    const val READOUT: String = "grafico-faixa-leitura"
    const val LEGEND: String = "grafico-faixa-legenda"
}

/**
 * Gráfico de **evolução com faixa de referência**: a linha por data, a banda min–max atrás e cada
 * ponto pintado pelo seu status — com a **leitura em texto** do ponto escolhido e a **legenda em
 * texto** dos status presentes (a cor nunca é a única pista: WCAG 1.4.1).
 *
 * ```kotlin
 * ReferenceBandChart(
 *     points = resultados.map {
 *         ReferencePoint(
 *             label = formatDateBr(it.coletadoEm), value = it.valor, date = it.coletadoEm,
 *             status = ReferenceStatus(it.statusRotulo, it.severidade.toTone()),   // do servidor
 *         )
 *     },
 *     band = ReferenceBand(min = 70.0, max = 99.0),
 *     valueFormatter = { "${RegionalFormat.formatNumber(it)} mg/dL" },
 * )
 * ```
 *
 * - **Toque** num ponto (ou perto dele) mostra a leitura "12/03/2026: 105 mg/dL, Acima da faixa".
 *   Começa no ÚLTIMO ponto — o resultado mais recente é o que se olha primeiro.
 * - **Leitor de tela**: o gráfico é um nó só, com o resumo ("5 medições. Última: …"), a faixa e a
 *   leitura do ponto escolhido; ações "Próxima medição"/"Medição anterior" andam entre os pontos.
 * - Sem faixa vira uma linha comum com pontos. 0 ponto mostra [ReferenceBandChartTexts.empty]; 1
 *   ponto aparece sozinho (exame único também tem faixa a mostrar).
 *
 * @param valueFormatter formata o valor COM a unidade, para eixo, legenda e leitura.
 * @param lineColor cor da linha. Default `colorScheme.primary`.
 * @param chartHeight altura do gráfico (no compacto, ~85%).
 * @param maxXLabels máximo de rótulos de data no eixo X.
 * @param showLegend mostra a legenda em texto (faixa + status presentes).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReferenceBandChart(
    points: List<ReferencePoint>,
    band: ReferenceBand?,
    modifier: Modifier = Modifier,
    valueFormatter: (Double) -> String = { RegionalFormat.formatNumber(it) },
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 180.dp,
    maxXLabels: Int = 4,
    showLegend: Boolean = true,
    texts: ReferenceBandChartTexts = rememberReferenceBandChartTexts(),
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(chartHeight), contentAlignment = Alignment.Center) {
            Text(texts.empty, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }

    // Status efetivo de cada ponto: o do servidor, ou o derivado da faixa.
    val derived: List<ReferenceStatus?> = points.map { p ->
        p.status ?: when (classifyAgainstBand(p.value, band)) {
            BandPosition.BELOW -> ReferenceStatus(texts.below, StatusTone.WARNING)
            BandPosition.WITHIN -> ReferenceStatus(texts.within, StatusTone.SUCCESS)
            BandPosition.ABOVE -> ReferenceStatus(texts.above, StatusTone.WARNING)
            null -> null
        }
    }
    val toneColors = StatusTone.entries.associateWith { statusToneColor(it) }
    val pointColors = derived.map { st -> st?.let { toneColors.getValue(it.tone) } ?: lineColor }

    val xFractions = remember(points) { referenceChartXFractions(points) }
    val (minValue, maxValue) = lineChartValueBounds(points.map { it.value }, band)
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val sel = selected.coerceIn(0, points.lastIndex)

    val readout: (Int) -> String = { i ->
        texts.point(points[i].label, valueFormatter(points[i].value), derived[i]?.label)
    }
    val bandText = band?.let {
        when {
            it.min != null && it.max != null -> texts.bandRange(valueFormatter(it.min), valueFormatter(it.max))
            it.min != null -> texts.bandFrom(valueFormatter(it.min))
            else -> texts.bandUpTo(valueFormatter(it.max!!))
        }
    }

    val compact = LocalIsCompact.current
    val areaHeight = if (compact) chartHeight * 0.85f else chartHeight
    val bandColor = band?.color ?: AppColors.current.success
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val labelIndices = xAxisLabelIndices(points.size, maxXLabels)
    val yAxisWidth = 52.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(areaHeight)) {
            Column(
                modifier = Modifier.width(yAxisWidth).fillMaxHeight().padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf(maxValue, (maxValue + minValue) / 2.0, minValue).forEach {
                    Text(valueFormatter(it), fontSize = 9.sp, color = axisTextColor, maxLines = 1)
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(ReferenceBandChartTestTags.CHART)
                    .semantics {
                        contentDescription = listOfNotNull(texts.summary(points.size, readout(points.lastIndex)), bandText)
                            .joinToString(" ")
                        stateDescription = readout(sel)
                        customActions = listOf(
                            CustomAccessibilityAction(texts.next) {
                                if (sel < points.lastIndex) { selected = sel + 1; true } else false
                            },
                            CustomAccessibilityAction(texts.previous) {
                                if (sel > 0) { selected = sel - 1; true } else false
                            },
                        )
                    }
                    .pointerInput(xFractions) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat()
                            if (w > 0f) nearestReferencePoint(xFractions, offset.x / w)?.let { selected = it }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                // Margem horizontal para o ponto da ponta não sair cortado pela metade.
                val inset = 8.dp.toPx()
                val usable = (w - inset * 2).coerceAtLeast(1f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                listOf(0f, 0.5f, 1f).forEach { f ->
                    drawLine(gridColor, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1f, pathEffect = dash)
                }
                if (band != null) {
                    val (topF, bottomF) = referenceBandFractions(band, minValue, maxValue)
                    val top = h * topF
                    val bottom = h * bottomF
                    drawRect(bandColor.copy(alpha = 0.14f), Offset(0f, top), Size(w, (bottom - top).coerceAtLeast(1f)))
                    listOfNotNull(band.max?.let { top }, band.min?.let { bottom }).forEach { y ->
                        drawLine(bandColor.copy(alpha = 0.7f), Offset(0f, y), Offset(w, y), strokeWidth = 1.5f, pathEffect = dash)
                    }
                }
                val offsets = points.mapIndexed { i, p ->
                    Offset(inset + xFractions[i] * usable, h - normalizeToFraction(p.value, minValue, maxValue) * h)
                }
                if (offsets.size >= 2) {
                    val line = Path().apply {
                        moveTo(offsets.first().x, offsets.first().y)
                        offsets.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(line, lineColor, style = Stroke(width = if (compact) 2.5f else 3f))
                }
                // Marca do ponto escolhido: guia vertical discreta.
                val sx = offsets[sel].x
                drawLine(gridColor, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1f)
                offsets.forEachIndexed { i, o ->
                    val r = if (i == sel) 7f else 5f
                    drawCircle(surface, radius = r + 2f, center = o)
                    drawCircle(pointColors[i], radius = r, center = o)
                }
            }
        }

        if (labelIndices.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(yAxisWidth))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    labelIndices.forEach { idx ->
                        Text(points[idx].label, fontSize = 9.sp, color = axisTextColor, maxLines = 1)
                    }
                }
            }
        }

        // Leitura do ponto escolhido — a mesma frase que o leitor de tela ouve.
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(ReferenceBandChartTestTags.READOUT)) {
            Box(Modifier.size(10.dp).background(pointColors[sel], CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(readout(sel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }

        if (showLegend) {
            val statuses = derived.filterNotNull().distinct()
            if (bandText != null || statuses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag(ReferenceBandChartTestTags.LEGEND),
                ) {
                    if (bandText != null) {
                        LegendEntry(bandText) {
                            Box(Modifier.size(width = 14.dp, height = 10.dp).background(bandColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                        }
                    }
                    statuses.forEach { st ->
                        LegendEntry(st.label) {
                            Box(Modifier.size(10.dp).background(toneColors.getValue(st.tone), CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendEntry(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
