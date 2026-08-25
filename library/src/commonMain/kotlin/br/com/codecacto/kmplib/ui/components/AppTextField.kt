package br.com.codecacto.kmplib.ui.components

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
import androidx.compose.ui.text.input.ImeAction
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
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Supporting text: erro > dica > contador. Só um por vez — três linhas sob o campo é ruído.
    val supportingText: (@Composable () -> Unit)? = when {
        errorMessage != null -> {{ Text(errorMessage) }}
        // Depois do erro, e antes do contador: dica é informação do campo, contador é acessório.
        helperText != null -> {{ Text(helperText) }}
        showCharCounter && maxLength != null -> {{
            Text(
                text = "${value.length}/$maxLength",
                style = MaterialTheme.typography.bodySmall
            )
        }}
        else -> null
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
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                    )
                }
            }
        } else null,
        visualTransformation = when {
            isPassword && !passwordVisible -> PasswordVisualTransformation()
            else -> visualTransformation
        },
        // Capitalização/autocorreção derivadas do tipo (ver `appKeyboardOptions`): sem isso, o teclado
        // do iOS capitaliza e AUTOCORRIGE e-mail/senha/telefone — o campo envia uma palavra que a
        // pessoa não digitou. Campo de senha entra como identificador, nunca como texto corrido.
        keyboardOptions = appKeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            capitalization = capitalization ?: defaultCapitalizationFor(
                if (isPassword) KeyboardType.Password else keyboardType,
            ),
            autoCorrect = autoCorrect ?: defaultAutoCorrectFor(
                if (isPassword) KeyboardType.Password else keyboardType,
            ),
        ),
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        isError = errorMessage != null,
        supportingText = supportingText,
        enabled = enabled,
        readOnly = readOnly,
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
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    // Supporting text: erro > dica > contador. Só um por vez — três linhas sob o campo é ruído.
    val supportingText: (@Composable () -> Unit)? = when {
        errorMessage != null -> {{ Text(errorMessage) }}
        // Depois do erro, e antes do contador: dica é informação do campo, contador é acessório.
        helperText != null -> {{ Text(helperText) }}
        showCharCounter && maxLength != null -> {{
            Text(
                text = "${value.length}/$maxLength",
                style = MaterialTheme.typography.bodySmall
            )
        }}
        else -> null
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
