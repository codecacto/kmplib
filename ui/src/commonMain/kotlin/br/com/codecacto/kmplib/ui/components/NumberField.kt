package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Campo de entrada numérico
 *
 * @param value Valor atual (String para permitir edição)
 * @param onValueChange Callback quando o valor muda
 * @param modifier Modificador customizado
 * @param label Texto do label
 * @param placeholder Texto do placeholder
 * @param leadingIcon Ícone à esquerda
 * O texto mora no campo e o [value] do ViewModel só o reescreve quando muda por outro motivo que
 * não a digitação (2.262.1) — digitação rápida no iOS não perde mais dígito. A API não mudou.
 *
 * @param allowDecimals Se permite valores decimais (usa vírgula como separador; "." digitado vira ",")
 * @param minValue Valor mínimo permitido (null = sem limite)
 * @param maxValue Valor máximo permitido (null = sem limite)
 * @param errorMessage Mensagem de erro (null = sem erro)
 * @param enabled Se o campo está habilitado
 * @param imeAction Ação do IME
 * @param keyboardActions Ações do teclado
 * @param primaryColor Cor primária
 * @param borderColor Cor da borda
 * @param labelColor Cor do label
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    allowDecimals: Boolean = false,
    minValue: Double? = null,
    maxValue: Double? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    // O texto MORA NO CAMPO (2.262.1). Com `value` vindo do StateFlow do ViewModel, cada dígito
    // fazia a ida e volta antes de aparecer, e no iOS a digitação rápida engolia dígitos ("120" →
    // "10", Meu Controle, 07/out/2026, docs/42). Agora o teclado escreve no `TextFieldState` no mesmo
    // quadro; o ViewModel só recebe, e um `value` que muda por outro motivo (reset do formulário)
    // continua reescrevendo o campo — ver [rememberSyncedTextFieldState].
    val state = rememberSyncedTextFieldState(value, onValueChange)
    // O filtro é do próprio campo (InputTransformation, a forma oficial do TextField com estado):
    // recusar a tecla é desfazê-la ali, não depender de o ViewModel "não mudar".
    val inputTransformation = remember(allowDecimals, minValue, maxValue) {
        NumberFieldInputTransformation(allowDecimals, minValue, maxValue)
    }

    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        supportingText = errorMessage?.let { { Text(it) } },
        isError = errorMessage != null,
        inputTransformation = inputTransformation,
        // Número nunca capitaliza nem autocorrige (ver `appKeyboardOptions`).
        keyboardOptions = appKeyboardOptions(
            keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        onKeyboardAction = keyboardActions.toKeyboardActionHandler(imeAction),
        lineLimits = TextFieldLineLimits.SingleLine,
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
 * O que o [NumberField] aceita do que foi digitado/colado — `null` = recusar a edição inteira
 * (fora de [minValue]..[maxValue]). Pura, testada em `NumberFieldInputTest`.
 *
 * - Inteiro: só algarismos.
 * - Decimal: algarismos e UMA vírgula. O ponto vira vírgula quando não há vírgula no texto — o
 *   teclado decimal do iOS mostra "." em aparelho com região que usa ponto, e o ponto era descartado
 *   em silêncio (a pessoa não conseguia digitar a casa decimal). Havendo vírgula, o ponto é
 *   separador de milhar colado ("1.234,56") e sai.
 * - Texto que não é número (só ",") passa: é o meio da digitação.
 */
internal fun filterNumberFieldInput(
    proposed: String,
    allowDecimals: Boolean,
    minValue: Double?,
    maxValue: Double?,
): String? {
    val filtered = if (allowDecimals) {
        val normalized = if (proposed.contains(',')) proposed else proposed.replace('.', ',')
        val kept = normalized.filter { it.isDigit() || it == ',' }
        val firstComma = kept.indexOf(',')
        if (firstComma >= 0) {
            kept.substring(0, firstComma + 1) + kept.substring(firstComma + 1).replace(",", "")
        } else {
            kept
        }
    } else {
        proposed.filter { it.isDigit() }
    }
    if (filtered.isEmpty()) return filtered
    val number = filtered.replace(",", ".").toDoubleOrNull() ?: return filtered
    val inRange = (minValue == null || number >= minValue) && (maxValue == null || number <= maxValue)
    return if (inRange) filtered else null
}

/** O filtro do [NumberField] aplicado no buffer do campo, antes de o texto ser aceito. */
internal data class NumberFieldInputTransformation(
    val allowDecimals: Boolean,
    val minValue: Double?,
    val maxValue: Double?,
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val accepted = filterNumberFieldInput(proposed, allowDecimals, minValue, maxValue)
        when {
            accepted == null -> revertAllChanges()
            accepted != proposed -> {
                replace(0, length, accepted)
                placeCursorAtEnd()
            }
        }
    }
}

/**
 * Converte String de NumberField para Double
 * Trata vírgula como separador decimal
 */
fun String.toDoubleFromNumberField(): Double? {
    return this.replace(",", ".").toDoubleOrNull()
}

/**
 * Converte String de NumberField para Int
 */
fun String.toIntFromNumberField(): Int? {
    return this.toIntOrNull()
}
