package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Linha horizontal scrollável de chips de filtro com seleção simples (single choice).
 *
 * Diferente do [SegmentedControl] (curto e fixo), o [FilterChipRow] rola horizontalmente
 * e suporta muitas opções, ideal para filtros por status/categoria.
 *
 * Exemplo (filtro de mensalistas por status):
 * ```kotlin
 * FilterChipRow(
 *     options = listOf("Todos", "Ativo", "A Vencer", "Vencido", "Inativo"),
 *     selectedIndex = filtro,
 *     onOptionSelected = { filtro = it }
 * )
 * ```
 *
 * Usa [FilterChip] do Material 3; cores vêm de [androidx.compose.material3.MaterialTheme]
 * via tema da kmplib. Cada chip expõe [contentDescription] para acessibilidade.
 *
 * @param options Rótulos das opções, na ordem de exibição.
 * @param selectedIndex Índice da opção atualmente selecionada.
 * @param onOptionSelected Callback com o índice da opção escolhida.
 * @param modifier Modificador externo.
 */
@Composable
fun FilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        itemsIndexed(options) { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onOptionSelected(index) },
                label = { Text(text = label) },
                modifier = Modifier.semantics {
                    contentDescription =
                        if (selected) "$label, selecionado" else label
                }
            )
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun FilterChipRowPreview() {
    AppTheme {
        FilterChipRow(
            options = listOf("Todos", "Ativo", "A Vencer", "Vencido", "Inativo"),
            selectedIndex = 1,
            onOptionSelected = {}
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun FilterChipRowAllSelectedPreview() {
    AppTheme {
        FilterChipRow(
            options = listOf("Todos", "Mensalistas", "Rotativos"),
            selectedIndex = 0,
            onOptionSelected = {}
        )
    }
}
