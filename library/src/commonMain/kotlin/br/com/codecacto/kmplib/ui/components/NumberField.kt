package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
 * @param allowDecimals Se permite valores decimais (usa vírgula como separador)
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
    primaryColor: Color = Color(0xFF6C63FF),
    borderColor: Color = Color(0xFFE0E0E0),
    labelColor: Color = Color(0xFF9E9E9E)
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Filtrar entrada para aceitar apenas números
            val filtered = if (allowDecimals) {
                // Permite números e vírgula como separador decimal
                newValue.filter { it.isDigit() || it == ',' }
                    .let {
                        // Garantir apenas uma vírgula
                        val commaCount = it.count { char -> char == ',' }
                        if (commaCount > 1) {
                            val firstCommaIndex = it.indexOf(',')
                            it.substring(0, firstCommaIndex + 1) +
                                it.substring(firstCommaIndex + 1).replace(",", "")
                        } else {
                            it
                        }
                    }
            } else {
                // Permite apenas números inteiros
                newValue.filter { it.isDigit() }
            }

            // Validar min/max se o valor não estiver vazio
            if (filtered.isNotEmpty()) {
                val numValue = filtered.replace(",", ".").toDoubleOrNull()
                if (numValue != null) {
                    val isValid = (minValue == null || numValue >= minValue) &&
                                  (maxValue == null || numValue <= maxValue)
                    if (isValid) {
                        onValueChange(filtered)
                    }
                } else {
                    onValueChange(filtered)
                }
            } else {
                onValueChange(filtered)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        singleLine = true,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
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
