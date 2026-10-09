package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_password_hide
import br.com.codecacto.kmplib.generated.resources.kmplib_password_show
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TextField customizado com estilo padronizado
 *
 * @param value Valor atual do campo
 * @param onValueChange Callback quando o valor muda
 * @param modifier Modificador customizado
 * @param label Texto do label
 * @param placeholder Texto do placeholder
 * @param leadingIcon Ícone à esquerda
 * @param isPassword Se é campo de senha (com toggle de visibilidade)
 * @param keyboardType Tipo de teclado
 * @param imeAction Ação do IME
 * @param capitalization Capitalização do teclado; `null` = derivada do [keyboardType] (ver [appKeyboardOptions])
 * @param autoCorrect Autocorreção do teclado; `null` = derivada do [keyboardType] (ver [appKeyboardOptions])
 * @param keyboardActions Ações do teclado
 * @param visualTransformation Transformação visual do texto
 * @param errorMessage Mensagem de erro (null = sem erro)
 * @param enabled Se o campo está habilitado
 * @param singleLine Se é single-line
 * @param maxLength Comprimento máximo (null = ilimitado)
 * @param showCharCounter Se deve mostrar contador de caracteres
 * @param primaryColor Cor primária (borda focada, label focado)
 * @param borderColor Cor da borda não focada
 * @param labelColor Cor do label não focado
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization? = null,
    autoCorrect: Boolean? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    errorMessage: String? = null,
    /**
     * Dica **neutra** embaixo do campo — o que ele não teria como adivinhar, e nunca o óbvio.
     *
     * Existe porque só havia [errorMessage]: quem precisava mostrar "Buscando endereço…" ou
     * "Válido até o fim do mês" usava o campo de erro e **pintava o controle de vermelho** durante
     * uma operação normal. [errorMessage] vence quando os dois vêm — erro é mais urgente que dica.
     */
    helperText: String? = null,
    enabled: Boolean = true,
    /**
     * Campo **só de leitura**, mas com aparência de HABILITADO (2.145.0).
     *
     * Diferente de `enabled = false`, que pinta tudo com as cores de desabilitado (o texto e a
     * borda cinza que fazem o campo parecer desligado). `readOnly` mantém as cores normais e só
     * impede a edição e o teclado — é o que um campo-vitrine precisa: o valor está lá, ativo à
     * vista, e a escrita acontece por outro caminho (um seletor, um mapa, um dropdown que embrulha
     * este campo).
     */
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    showCharCounter: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * O texto **mora no campo** e o [value] de fora só o reescreve quando muda por outro motivo que
     * não a digitação (limpar, restaurar, preencher) — 2.258.0.
     *
     * Ligue em campo de BUSCA/FILTRO cujo [value] vem do `StateFlow` do ViewModel: sem isto, cada
     * letra faz a ida e volta pelo ViewModel, o campo é recomposto um quadro atrasado com o valor
     * velho e o iOS **perde letras** na digitação rápida ("teste qa" → "tete a", MinhaOS, docs/42).
     * O eco atrasado é reconhecido e ignorado ([TextInputReconciler]).
     *
     * **Não ligue** em campo que RECUSA a tecla (`if (it.all(Char::isDigit)) set(it)`): recusar é não
     * mudar o [value], e com o texto local a letra recusada ficaria na tela. Transformar (filtrar,
     * cortar) funciona — o valor transformado chega diferente do digitado e vence.
     *
     * Para busca nova, prefira o [AppSearchField] (já traz lupa, "x" e o teclado de busca).
     */
    keepTextLocally: Boolean = false,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Com o texto local, [value] vira só o sinal de "a tela mudou o texto"; o que se desenha e o
    // que o contador conta é o texto do campo.
    val reconciler = if (keepTextLocally) remember { TextInputReconciler(value) } else null
    var localValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (reconciler != null) {
        SideEffect {
            reconciler.onExternal(value, localValue.text)?.let {
                localValue = TextFieldValue(it, TextRange(it.length))
            }
        }
    }
    val shownText = if (keepTextLocally) localValue.text else value

    // Supporting text: erro > dica > contador. Só um por vez — três linhas sob o campo é ruído.
    val supportingText: (@Composable () -> Unit)? = when {
        errorMessage != null -> {{ Text(errorMessage) }}
        // Depois do erro, e antes do contador: dica é informação do campo, contador é acessório.
        helperText != null -> {{ Text(helperText) }}
        showCharCounter && maxLength != null -> {{
            Text(
                text = "${shownText.length}/$maxLength",
                style = MaterialTheme.typography.bodySmall
            )
        }}
        else -> null
    }

    val fieldModifier = modifier.fillMaxWidth()
    val labelSlot: (@Composable () -> Unit)? = label?.let { { Text(it) } }
    val placeholderSlot: (@Composable () -> Unit)? = placeholder?.let { { Text(it) } }
    val leadingSlot: (@Composable () -> Unit)? = leadingIcon?.let {
        { Icon(imageVector = it, contentDescription = null) }
    }
    val trailingSlot: (@Composable () -> Unit)? = if (isPassword) {
        {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = stringResource(
                        if (passwordVisible) Res.string.kmplib_password_hide else Res.string.kmplib_password_show,
                    )
                )
            }
        }
    } else null
    val effectiveTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }
    // Capitalização/autocorreção derivadas do tipo (ver `appKeyboardOptions`): sem isso, o teclado
    // do iOS capitaliza e AUTOCORRIGE e-mail/senha/telefone — o campo envia uma palavra que a
    // pessoa não digitou. Campo de senha entra como identificador, nunca como texto corrido.
    val effectiveKeyboardType = if (isPassword) KeyboardType.Password else keyboardType
    val keyboardOptions = appKeyboardOptions(
        keyboardType = effectiveKeyboardType,
        imeAction = imeAction,
        capitalization = capitalization ?: defaultCapitalizationFor(effectiveKeyboardType),
        autoCorrect = autoCorrect ?: defaultAutoCorrectFor(effectiveKeyboardType),
    )
    val shape = RoundedCornerShape(12.dp)
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = primaryColor,
        unfocusedBorderColor = borderColor,
        focusedLabelColor = primaryColor,
        unfocusedLabelColor = labelColor,
        errorBorderColor = MaterialTheme.colorScheme.error,
        errorLabelColor = MaterialTheme.colorScheme.error
    )

    if (reconciler != null) {
        OutlinedTextField(
            value = localValue,
            onValueChange = { novo ->
                val cortado = if (maxLength != null && novo.text.length > maxLength) {
                    TextFieldValue(novo.text.take(maxLength), TextRange(maxLength))
                } else {
                    novo
                }
                val mudouTexto = cortado.text != localValue.text
                // Mesmo quadro: o campo nunca espera o ViewModel para mostrar a letra.
                localValue = cortado
                if (mudouTexto && reconciler.onLocalText(cortado.text)) onValueChange(cortado.text)
            },
            modifier = fieldModifier,
            label = labelSlot,
            placeholder = placeholderSlot,
            leadingIcon = leadingSlot,
            trailingIcon = trailingSlot,
            visualTransformation = effectiveTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            isError = errorMessage != null,
            supportingText = supportingText,
            enabled = enabled,
            readOnly = readOnly,
            shape = shape,
            colors = colors,
        )
        return
    }

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Aplicar maxLength se definido
            if (maxLength != null && newValue.length > maxLength) {
                onValueChange(newValue.take(maxLength))
            } else {
                onValueChange(newValue)
            }
        },
        modifier = fieldModifier,
        label = labelSlot,
        placeholder = placeholderSlot,
        leadingIcon = leadingSlot,
        trailingIcon = trailingSlot,
        visualTransformation = effectiveTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        isError = errorMessage != null,
        supportingText = supportingText,
        enabled = enabled,
        readOnly = readOnly,
        shape = shape,
        colors = colors,
    )
}

/**
 * TextArea (multi-line) customizado com contador de caracteres
 *
 * @param value Valor atual do campo
 * @param onValueChange Callback quando o valor muda
 * @param modifier Modificador customizado
 * @param label Texto do label
 * @param placeholder Texto do placeholder
 * @param maxLength Comprimento máximo (null = ilimitado)
 * @param minLines Número mínimo de linhas visíveis
 * @param maxLines Número máximo de linhas visíveis (null = ilimitado)
 * @param showCharCounter Se deve mostrar contador de caracteres
 * @param errorMessage Mensagem de erro (null = sem erro)
 * @param enabled Se o campo está habilitado
 * @param height Altura do componente (opcional, usa minLines se null)
 * @param primaryColor Cor primária (borda focada, label focado)
 * @param borderColor Cor da borda não focada
 * @param labelColor Cor do label não focado
 */
@Composable
fun AppTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    maxLength: Int? = null,
    minLines: Int = 3,
    maxLines: Int? = null,
    showCharCounter: Boolean = true,
    errorMessage: String? = null,
    /**
     * Dica **neutra** embaixo do campo — o que ele não teria como adivinhar, e nunca o óbvio.
     *
     * Existe porque só havia [errorMessage]: quem precisava mostrar "Buscando endereço…" ou
     * "Válido até o fim do mês" usava o campo de erro e **pintava o controle de vermelho** durante
     * uma operação normal. [errorMessage] vence quando os dois vêm — erro é mais urgente que dica.
     */
    helperText: String? = null,
    enabled: Boolean = true,
    height: Dp? = null,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * O texto **mora no campo** (2.268.0) — o mesmo `keepTextLocally` do [AppTextField]: o [value] de
     * fora só reescreve o campo quando muda por outro motivo que não a digitação, e o eco atrasado do
     * ViewModel não come letra no iOS. Ligue quando o [value] vem do `StateFlow` do ViewModel.
     */
    keepTextLocally: Boolean = false,
) {
    val reconciler = if (keepTextLocally) remember { TextInputReconciler(value) } else null
    var localValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (reconciler != null) {
        SideEffect {
            reconciler.onExternal(value, localValue.text)?.let {
                localValue = TextFieldValue(it, TextRange(it.length))
            }
        }
    }
    val shownText = if (keepTextLocally) localValue.text else value

    // Supporting text: erro > dica > contador. Só um por vez — três linhas sob o campo é ruído.
    val supportingText: (@Composable () -> Unit)? = when {
        errorMessage != null -> {{ Text(errorMessage) }}
        // Depois do erro, e antes do contador: dica é informação do campo, contador é acessório.
        helperText != null -> {{ Text(helperText) }}
        showCharCounter && maxLength != null -> {{
            Text(
                text = "${shownText.length}/$maxLength",
                style = MaterialTheme.typography.bodySmall
            )
        }}
        else -> null
    }

    if (reconciler != null) {
        OutlinedTextField(
            value = localValue,
            onValueChange = { novo ->
                val cortado = if (maxLength != null && novo.text.length > maxLength) {
                    TextFieldValue(novo.text.take(maxLength), TextRange(maxLength))
                } else {
                    novo
                }
                val mudouTexto = cortado.text != localValue.text
                localValue = cortado
                if (mudouTexto && reconciler.onLocalText(cortado.text)) onValueChange(cortado.text)
            },
            modifier = if (height != null) modifier.fillMaxWidth().height(height) else modifier.fillMaxWidth(),
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            keyboardOptions = appKeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
            singleLine = false,
            minLines = minLines,
            maxLines = maxLines ?: Int.MAX_VALUE,
            isError = errorMessage != null,
            supportingText = supportingText,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = borderColor,
                focusedLabelColor = primaryColor,
                unfocusedLabelColor = labelColor,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            )
        )
        return
    }

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Aplicar maxLength se definido
            if (maxLength != null && newValue.length > maxLength) {
                onValueChange(newValue.take(maxLength))
            } else {
                onValueChange(newValue)
            }
        },
        modifier = if (height != null) {
            modifier.fillMaxWidth().height(height)
        } else {
            modifier.fillMaxWidth()
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        // TextArea é texto corrido de verdade (comentário, observação): capitalização de frase e
        // autocorreção LIGADAS são o comportamento certo aqui — o oposto dos campos de identificador.
        keyboardOptions = appKeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default,
        ),
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines ?: Int.MAX_VALUE,
        isError = errorMessage != null,
        supportingText = supportingText,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            unfocusedBorderColor = borderColor,
            focusedLabelColor = primaryColor,
            unfocusedLabelColor = labelColor,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error
        )
    )
}
