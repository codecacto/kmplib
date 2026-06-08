package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Seletor de múltiplos itens exibidos como chips ([FilterChip] em [FlowRow]).
 *
 * Alternar um chip adiciona ou remove o item da seleção e propaga a nova lista
 * via [onSelectionChange].
 *
 * @param T Tipo dos itens
 * @param options Opções disponíveis
 * @param selectedItems Itens atualmente selecionados
 * @param onSelectionChange Callback com a nova lista de selecionados
 * @param label Rótulo exibido acima dos chips
 * @param itemLabel Mapeia um item para seu texto exibível
 * @param modifier Modificador do container
 * @param isEnabled Se a seleção está habilitada
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> AppMultiSelect(
    options: List<T>,
    selectedItems: List<T>,
    onSelectionChange: (List<T>) -> Unit,
    label: String,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option in selectedItems
                FilterChip(
                    selected = isSelected,
                    enabled = isEnabled,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedItems - option
                        } else {
                            selectedItems + option
                        }
                        onSelectionChange(updated)
                    },
                    label = { Text(itemLabel(option)) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(0.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
