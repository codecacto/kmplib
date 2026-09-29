package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Um pedaço da [ProportionalBar].
 *
 * @param label rótulo da legenda (já traduzido).
 * @param value quantidade (≥ 0; negativo conta como 0).
 * @param color cor do pedaço — token do tema ou [StatusLevel.color], nunca hex na tela.
 */
@Immutable
data class ProportionalSegment(
    val label: String,
    val value: Double,
    val color: Color,
)

/**
 * Frações (0..1) de cada valor sobre o total, na mesma ordem. Negativos contam como 0; total zero
 * devolve tudo 0 (a barra fica só com a trilha). A soma das frações é 1 quando há total.
 */
fun proportionalFractions(values: List<Double>): List<Float> {
    val clean = values.map { if (it.isNaN() || it < 0.0) 0.0 else it }
    val total = clean.sum()
    if (total <= 0.0) return List(values.size) { 0f }
    return clean.map { (it / total).toFloat() }
}

/**
 * Percentuais inteiros (0..100) que **somam exatamente 100** quando há total — maior resto
 * (método de Hamilton). Arredondar cada um sozinho dá legenda com 33 + 33 + 33 = 99 %.
 */
fun proportionalPercents(values: List<Double>): List<Int> {
    val fractions = proportionalFractions(values)
    if (fractions.all { it == 0f }) return List(values.size) { 0 }
    val raw = fractions.map { it * 100.0 }
    val floors = raw.map { it.toInt() }
    var missing = 100 - floors.sum()
    val result = floors.toMutableList()
    raw.withIndex()
        .sortedByDescending { it.value - it.value.toInt() }
        .forEach { (index, _) ->
            if (missing > 0) {
                result[index] += 1
                missing--
            }
        }
    return result
}

/**
 * **Barra proporcional** (2.220.0 — GAP-ER-08): uma barra horizontal só, dividida na proporção de
 * cada [ProportionalSegment] — "como a carteira se distribui por status". Não é o [StackedBarChart]
 * (várias barras lado a lado, comparando períodos) nem o [ScoreBarRow] (um valor contra um máximo).
 *
 * A legenda (opcional) mostra rótulo + percentual; os percentuais somam 100. Para acessibilidade a
 * barra inteira é UM nó, lido como a lista "rótulo: valor (pct %)".
 *
 * @param valueFormatter texto do valor na legenda e na leitura de tela (default: inteiro).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProportionalBar(
    segments: List<ProportionalSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    gap: Dp = 2.dp,
    showLegend: Boolean = true,
    valueFormatter: (Double) -> String = { it.roundToInt().toString() },
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val values = remember(segments) { segments.map { it.value } }
    val fractions = remember(values) { proportionalFractions(values) }
    val percents = remember(values) { proportionalPercents(values) }
    val description = remember(segments, percents) {
        segments.indices.joinToString("; ") { i ->
            "${segments[i].label}: ${valueFormatter(segments[i].value)} (${percents[i]}%)"
        }
    }

    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(trackColor),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            segments.forEachIndexed { i, segment ->
                val fraction = fractions[i]
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .background(segment.color),
                    )
                }
            }
        }
        if (showLegend && segments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                segments.forEachIndexed { i, segment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(segment.color),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${segment.label} · ${valueFormatter(segment.value)} (${percents[i]}%)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
