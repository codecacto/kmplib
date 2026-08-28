package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Uma linha "domínio → barra → valor" — o mapa por eixo de um resultado.
 *
 * Par mobile do `ScoreBarRow` da weblib: o app e o portal do cliente mostram o MESMO desenho dos
 * domínios do instrumento, e ter dois layouts para a mesma leitura é como um deles fica para trás.
 *
 * ## O cabeçalho quebra linha, e isso é medido
 *
 * "Flexibilidade Comportamental" com um rótulo qualitativo ao lado ("Em desenvolvimento") não cabe
 * em 360 dp. Sem quebra, o nome do domínio é espremido até virar **uma letra por linha** — foi o
 * defeito real medido na versão web (a página passou de 17.000 px de altura). Aqui o cabeçalho é um
 * `FlowRow`: o rótulo qualitativo desce para a linha de baixo em vez de estrangular o nome.
 *
 * ```kotlin
 * ScoreBarRow(
 *     label = "Regulação Emocional",
 *     value = 3.6, max = 5.0,
 *     valueText = "3,6",
 *     qualitativeLabel = "Consolidado",
 * )
 * ```
 *
 * @param value valor bruto; é clampado em `0..max`.
 * @param valueText o número como a tela deve mostrá-lo (a lib não formata decimal por localidade).
 * @param qualitativeLabel a faixa em palavras — o que faz o número significar algo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoreBarRow(
    label: String,
    value: Double,
    max: Double,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    qualitativeLabel: String? = null,
    barColor: Color? = null,
) {
    val fracao = if (max <= 0.0) 0f else (value / max).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Duas linhas é o teto: nome de domínio longo quebra, mas não empurra a barra para
                // fora da tela nem cria um item de altura imprevisível numa lista de sete.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (qualitativeLabel != null) {
                Text(
                    text = qualitativeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        AppProgressBar(
            progress = fracao,
            color = barColor,
            height = 8.dp,
        )
    }
}
