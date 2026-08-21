package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Uma opção do [AppPickerField]. [value] é o que volta ao servidor; [label] é o que se lê. */
data class PickerOption(val value: String, val label: String)

/**
 * Campo de escolha ÚNICA a partir de uma lista **longa** — estado, categoria, país.
 *
 * ## Por que ele existe (⟦gap fechado em 2.129.0⟧)
 *
 * A lib tinha três formas de escolher e nenhuma servia a uma lista de 27+ itens num formulário:
 * `FilterChipRow` é escolha única mas em `LazyRow`, que **rola de lado** e esconde o que não coube —
 * a pessoa escolhe entre as três opções que enxerga sem saber que havia outras; `AppMultiSelect` e
 * `MultiSelectList` são múltipla escolha. O resultado é que cada app resolvia com um campo de texto
 * de duas letras e uma validação própria — foi o que o NeuroCoreX teve de fazer com a UF.
 *
 * ## Sheet, não menu suspenso
 *
 * Num celular, menu suspenso cobre o campo que está sendo preenchido e some com um toque fora. O
 * sheet ocupa a metade de baixo, é rolável e tem alvo de 48 dp por linha — e o item escolhido vem
 * marcado, que é como a pessoa confere sem fechar e reabrir.
 *
 * O campo em si é **somente leitura**: quem edita é o sheet. Deixar digitar aqui traria de volta o
 * problema que este componente resolve — valor livre que o servidor recusa.
 */
@Composable
fun AppPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<PickerOption>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    /** Título do sheet. Em branco, repete o [label]. */
    sheetTitle: String? = null,
) {
    var aberto by remember { mutableStateOf(false) }
    val selecionada = options.firstOrNull { it.value == value }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // O clique é do BOX, não do campo: `OutlinedTextField` desabilitado não recebe clique, e
            // habilitado abriria o teclado. Este é o arranjo que dá campo somente-leitura clicável.
            .clickable(enabled = enabled, role = Role.Button) { aberto = true }
            .semantics {
                contentDescription = listOfNotNull(label, selecionada?.label).joinToString(": ")
            },
    ) {
        AppTextField(
            value = selecionada?.label.orEmpty(),
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            helperText = helperText,
            errorMessage = errorMessage,
            // Desabilitado de propósito: é o que impede o teclado de abrir e a digitação livre.
            enabled = false,
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        )
    }

    AppBottomSheet(isVisible = aberto, onDismiss = { aberto = false }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = sheetTitle ?: label.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(options, key = { it.value }) { opcao ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                onValueChange(opcao.value)
                                aberto = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(text = opcao.label, style = MaterialTheme.typography.bodyLarge)
                        if (opcao.value == value) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.CenterEnd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
