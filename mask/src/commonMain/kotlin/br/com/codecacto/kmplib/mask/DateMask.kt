package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de data no formato DD/MM/YYYY para uso com TextField.
 *
 * Exemplo:
 * ```kotlin
 * OutlinedTextField(
 *     value = dateText,
 *     onValueChange = { dateText = filterDateInput(it) },
 *     visualTransformation = DateVisualTransformation()
 * )
 * ```
 */
class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(8)
        val formatted = buildString {
            for (i in digits.indices) {
                append(digits[i])
                if (i == 1 || i == 3) {
                    if (i < digits.lastIndex) append('/')
                }
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, digits.length)
                return when {
                    clamped <= 2 -> clamped
                    clamped <= 4 -> clamped + 1
                    else -> clamped + 2
                }.coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                return when {
                    clamped <= 2 -> clamped
                    clamped <= 5 -> clamped - 1
                    else -> clamped - 2
                }.coerceAtMost(digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Filtra input para aceitar apenas dígitos de data (máximo 8 dígitos).
 */
fun filterDateInput(text: String): String {
    return text.filter { it.isDigit() }.take(8)
}
