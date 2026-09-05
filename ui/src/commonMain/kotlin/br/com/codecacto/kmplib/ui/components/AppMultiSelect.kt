package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
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
 * @param errorMessage Mensagem de erro (null = sem erro). Ver a nota abaixo.
 *
 * ## Erro de campo fica NO campo (2.181.0)
 *
 * "Marque ao menos um dia da semana" é erro **deste** campo, e a regra da casa (constituição,
 * 19/ago/2026) manda que ele apareça nele. Sem o parâmetro, cada tela punha um `Text` vermelho
 * solto embaixo dos chips: nada ligava a frase ao grupo, o leitor de tela não anunciava campo
 * inválido e o foco não tinha onde parar depois de um envio recusado.
 *
 * Com [errorMessage] o componente faz as três coisas que os campos de texto da lib fazem:
 * **rótulo em `colorScheme.error`**, **frase embaixo do grupo** (mesma tipografia do
 * `supportingText` do Material) e **`error()` na semântica** do container.
 *
 * A "borda vermelha" aqui é uma **caixa em volta dos chips**, e só existe no estado de erro. O
 * Material não define estado de erro para `FilterChip` (chip não tem borda de campo), então a caixa
 * é o equivalente honesto da borda do `OutlinedTextField` — e mantê-la fora do estado normal é o que
 * impede que todo formulário que já usa o componente ganhe um recuo que ninguém pediu.
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
    isEnabled: Boolean = true,
    errorMessage: String? = null
) {
    val temErro = errorMessage != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (errorMessage != null) Modifier.semantics { error(errorMessage) } else Modifier
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (temErro) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = if (temErro) {
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            } else {
                Modifier
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option in selectedItems
                FilterChip(
                    selected = isSelected,
                    enabled = isEnabled,
                    onClick = { onSelectionChange(alternarSelecao(selectedItems, option)) },
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

        errorMessage?.let { frase ->
            Text(
                text = frase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * O que a seleção vira ao tocar um chip: quem estava marcado sai, quem não estava entra.
 *
 * Vive fora do composable para poder ser testado — a única regra deste componente que não depende
 * de tela, e a que decide se o formulário recebe a lista certa.
 */
internal fun <T> alternarSelecao(selecionados: List<T>, item: T): List<T> =
    if (item in selecionados) selecionados - item else selecionados + item
