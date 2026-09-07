package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Escolha ÚNICA entre poucas opções, em chips que **quebram linha** quando não cabem.
 *
 * ## Por que ele existe, tendo [SegmentedControl] e [FilterChipRow]
 *
 * Os três resolvem "escolha uma", e cada um falha de um jeito diferente quando a largura acaba:
 *
 * | Componente | O que faz quando não cabe |
 * |---|---|
 * | [SegmentedControl] | **Comprime**: uma linha só, `SingleChoiceSegmentedButtonRow`. Os rótulos se espremem e cortam. |
 * | [FilterChipRow] | **Esconde**: é `LazyRow`, o que passou da borda só aparece rolando — e ninguém rola o que não sabe que existe. |
 * | **`ChoiceChipGroup`** | **Desce**: `FlowRow`, as opções continuam todas visíveis, em duas linhas. |
 *
 * Use este quando as opções **precisam ser lidas de uma vez** (é o caso de qualquer coisa opcional:
 * a pessoa só escolhe se enxergar que dá) e o rótulo não é de uma palavra. Quatro opções em que uma
 * é "Qualquer horário" já não cabem numa linha de telefone pequeno — foi esse o caso que originou o
 * componente (formulário de pedido do Crédito na Mão, 07/set/2026): em 320dp o segmentado espremia
 * os quatro rótulos até cortar.
 *
 * Continue no [SegmentedControl] para 2–3 rótulos curtos ("Pix/Cartão/Dinheiro"), onde a linha única
 * é mais legível; e no [AppPickerField] quando a lista passa de umas dezenas, porque aí a tela é de
 * procurar, não de comparar.
 *
 * ## Alvo e acessibilidade
 *
 * Cada chip tem no mínimo **48dp** de altura (o mínimo do Material para toque), e não os 32dp do
 * `FilterChip` puro — o público destes apps inclui dedo apressado e baixa precisão. O estado vai na
 * semântica ("selecionado"), nunca só na cor.
 *
 * ```kotlin
 * ChoiceChipGroup(
 *     options = listOf("Qualquer horário", "Manhã", "Tarde", "Noite"),
 *     selectedIndex = indice,
 *     onOptionSelected = { indice = it },
 * )
 * ```
 *
 * @param options Rótulos, na ordem de exibição. Lista vazia não desenha nada.
 * @param selectedIndex Índice selecionado; fora da faixa = nenhum selecionado.
 * @param onOptionSelected Callback com o índice escolhido.
 * @param modifier Modificador externo.
 * @param enabled `false` desabilita todos os chips (ex.: formulário enviando).
 * @param optionContentDescriptions Descrição por opção para o leitor de tela, quando o rótulo
 *   sozinho não diz a que se refere. `null` (default) usa o próprio rótulo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChipGroup(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionContentDescriptions: List<String>? = null,
) {
    if (options.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                enabled = enabled,
                onClick = { onOptionSelected(index) },
                label = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = enabled,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    // 48dp: o mínimo de alvo do Material. O FilterChip nasce com 32dp.
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        val base = optionContentDescriptions?.getOrNull(index)
                            ?.takeIf { it.isNotBlank() }
                            ?: label
                        contentDescription = if (selected) "$base, selecionado" else base
                    },
            )
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ChoiceChipGroupPreview() {
    AppTheme {
        ChoiceChipGroup(
            options = listOf("Qualquer horário", "Manhã", "Tarde", "Noite"),
            selectedIndex = 0,
            onOptionSelected = {},
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ChoiceChipGroupSemSelecaoPreview() {
    AppTheme {
        ChoiceChipGroup(
            options = listOf("Rua", "Avenida", "Travessa", "Rodovia", "Estrada"),
            selectedIndex = -1,
            onOptionSelected = {},
        )
    }
}
