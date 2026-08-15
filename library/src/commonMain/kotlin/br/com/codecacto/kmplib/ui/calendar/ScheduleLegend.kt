package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Legenda da camada de **destinação** — o par "amostra + rótulo" de cada `kind` do vocabulário.
 * Par mobile do `ScheduleLegend` da weblib.
 *
 * **Não é opcional na prática:** um fundo colorido e texturizado sem legenda é enfeite ilegível, e é a
 * legenda que dá nome ao que a cor e a textura só insinuam. Por isso a ordem é a do vocabulário
 * declarado (estável entre dias, em vez de dançar conforme o que está agendado hoje) e o `kind` que
 * aparecer sem declaração entra no fim, em vez de virar retângulo anônimo — ver [layerLegendEntries].
 *
 * Cada item é **um nó** para o leitor de tela ("Clubinho"), porque a amostra sozinha não tem o que
 * anunciar. Cores por token do tema; zero cor hardcoded.
 *
 * ```kotlin
 * ScheduleLegend(layers = destinacoes, legend = legenda)
 * AppTimeGridScheduler(events = ocupacao, layers = destinacoes, layerLegend = legenda, ...)
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleLegend(
    entries: List<ResolvedLayerStyle>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = entry.label },
            ) {
                LayerSwatch(color = layerToneColor(entry.tone), pattern = entry.pattern)
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Sobrecarga que **deriva** as entradas — o uso normal: passe as mesmas `layers` e a mesma `legend` que
 * foram para o [AppTimeGridScheduler] e a legenda acompanha a grade sozinha.
 */
@Composable
fun ScheduleLegend(
    layers: List<ScheduleLayer>,
    legend: ScheduleLayerLegend? = null,
    modifier: Modifier = Modifier,
) {
    ScheduleLegend(entries = layerLegendEntries(layers, legend), modifier = modifier)
}
