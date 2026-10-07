package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_search
import br.com.codecacto.kmplib.generated.resources.kmplib_search_clear
import org.jetbrains.compose.resources.stringResource

/** Ids estáveis do [AppSearchField] para o Maestro (2.258.0). */
object SearchFieldTestTags {
    /** O "x" que limpa a busca. */
    const val LIMPAR = "busca-btn-limpar"
}

/**
 * [TextFieldState] cujo texto **mora na UI** e é sincronizado com um valor do ViewModel (2.258.0).
 *
 * É a forma oficial do Compose para campo de texto (Google, *state-based TextField*): o teclado
 * escreve direto no estado, no mesmo quadro, e nada depende da ida e volta pelo `StateFlow`. Sem
 * isso, com `value = state.query` vindo do ViewModel, o iOS **perde letras** na digitação rápida
 * ("teste qa" → "tete a", MinhaOS, docs/42).
 *
 * - Cada mudança de texto chama [onTextChange] (o ViewModel recebe; aplique debounce lá).
 * - [text] é o valor do ViewModel. Quando ele muda **por outro motivo** que não a digitação — a tela
 *   limpou a busca, restaurou um filtro, preencheu com o item escolhido — o campo assume o valor
 *   novo. O eco atrasado do que a pessoa acabou de digitar é reconhecido e ignorado
 *   ([TextInputReconciler]).
 * - Sobrevive a rotação e morte de processo (`rememberTextFieldState` é *saveable*); se o ViewModel
 *   voltar vazio, o texto restaurado é reenviado a ele.
 *
 * Use direto quando o projeto desenha o próprio campo (protótipo) com `BasicTextField(state = …)`;
 * para o campo padrão de busca, [AppSearchField] já faz tudo.
 */
@Composable
fun rememberSyncedTextFieldState(
    text: String,
    onTextChange: (String) -> Unit,
): TextFieldState {
    val state = rememberTextFieldState(text)
    val reconciler = remember { TextInputReconciler(text) }
    val emit by rememberUpdatedState(onTextChange)

    // Valor de fora: só reescreve o campo se não for eco do que foi digitado.
    SideEffect {
        reconciler.onExternal(text, state.text.toString())?.let { state.setTextAndPlaceCursorAtEnd(it) }
    }

    // Texto de dentro: tecla, colar, o "x" — vai para o ViewModel.
    LaunchedEffect(state, reconciler) {
        snapshotFlow { state.text.toString() }.collect { atual ->
            if (reconciler.onLocalText(atual)) emit(atual)
        }
    }
    return state
}

/**
 * **Campo de busca** que filtra enquanto digita — o texto mora no campo, não no ViewModel (2.258.0).
 *
 * Troque por este todo `AppTextField(value = state.query, onValueChange = { onAction(…) },
 * leadingIcon = Icons.Default.Search)`: a assinatura é a mesma ideia ([query] + [onQueryChange]),
 * mas a digitação não passa mais pela ida e volta do `StateFlow` — que no iOS engolia letras em
 * digitação rápida (MinhaOS, 06/out/2026). Ver [rememberSyncedTextFieldState].
 *
 * Já vem com o que toda busca da fábrica precisa: lupa, "x" para limpar (id
 * [SearchFieldTestTags.LIMPAR]), teclado com ação **Buscar**, **sem autocorreção** (trocar "Hygor" por "Higor" no meio da digitação some com o resultado), uma linha.
 *
 * ```kotlin
 * AppSearchField(
 *     query = state.query,                       // do ViewModel — limpar/restaurar continua valendo
 *     onQueryChange = { onAction(Action.QueryChanged(it)) },
 *     placeholder = stringResource(Res.string.clientes_buscar),
 *     modifier = Modifier.testTag("clientes-busca"),
 * )
 * ```
 *
 * @param query valor de busca do ViewModel. Mudança vinda de fora (limpar, restaurar) é aplicada.
 * @param onQueryChange cada mudança do texto; debounce é do ViewModel.
 * @param onSearch ação "Buscar" do teclado (o filtro já roda a cada tecla; útil para busca remota).
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = stringResource(Res.string.kmplib_search),
    label: String? = null,
    enabled: Boolean = true,
    helperText: String? = null,
    onSearch: (String) -> Unit = {},
    clearDescription: String = stringResource(Res.string.kmplib_search_clear),
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val state = rememberSyncedTextFieldState(query, onQueryChange)

    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                IconButton(
                    onClick = { state.clearText() },
                    enabled = enabled,
                    modifier = Modifier.testTag(SearchFieldTestTags.LIMPAR),
                ) {
                    Icon(Icons.Default.Clear, contentDescription = clearDescription)
                }
            }
        } else null,
        supportingText = helperText?.let { { Text(it) } },
        keyboardOptions = appKeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
        onKeyboardAction = { onSearch(state.text.toString()) },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            unfocusedBorderColor = borderColor,
            focusedLabelColor = primaryColor,
            unfocusedLabelColor = labelColor,
        ),
    )
}
