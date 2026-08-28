package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de CEP brasileiro para Compose.
 *
 * Formata automaticamente como: XXXXX-XXX
 *
 * Uso:
 * ```kotlin
 * TextField(
 *     value = cep,
 *     onValueChange = { cep = filterCepInput(it) },
 *     visualTransformation = CepVisualTransformation()
 * )
 * ```
 */
class CepVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(8)
        val formatted = formatCep(digits)

        return TransformedText(
            AnnotatedString(formatted),
            CepOffsetMapping(digits, formatted)
        )
    }

    private fun formatCep(digits: String): String {
        if (digits.isEmpty()) return ""

        return buildString {
            // XXXXX
            append(digits.take(5))

            if (digits.length > 5) {
                append('-')
                // XXX
                append(digits.substring(5).take(3))
            }
        }
    }

    private class CepOffsetMapping(
        private val original: String,
        private val formatted: String
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            var transformedOffset = 0
            var originalCount = 0

            for (char in formatted) {
                if (originalCount >= offset) break
                transformedOffset++
                if (char.isDigit()) originalCount++
            }

            return transformedOffset
        }

        override fun transformedToOriginal(offset: Int): Int {
            var originalOffset = 0

            for (i in 0 until minOf(offset, formatted.length)) {
                if (formatted[i].isDigit()) originalOffset++
            }

            return minOf(originalOffset, original.length)
        }
    }
}

/**
 * Filtra entrada para aceitar apenas dígitos de CEP (máximo 8).
 */
fun filterCepInput(input: String): String = input.filter { it.isDigit() }.take(8)

/**
 * Formata um CEP no padrão XXXXX-XXX.
 */
fun formatCep(cep: String): String {
    val digits = cep.filter { it.isDigit() }
    if (digits.length != 8) return cep

    return "${digits.substring(0, 5)}-${digits.substring(5, 8)}"
}
