package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de CPF para Compose.
 *
 * Formata automaticamente como: XXX.XXX.XXX-XX
 *
 * Uso:
 * ```kotlin
 * TextField(
 *     value = cpf,
 *     onValueChange = { cpf = filterCpfInput(it) },
 *     visualTransformation = CpfVisualTransformation()
 * )
 * ```
 */
class CpfVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val formatted = formatCpf(digits)

        return TransformedText(
            AnnotatedString(formatted),
            CpfOffsetMapping(digits, formatted)
        )
    }

    private fun formatCpf(digits: String): String {
        if (digits.isEmpty()) return ""

        return buildString {
            // XXX
            append(digits.take(3))

            if (digits.length > 3) {
                append('.')
                // XXX
                append(digits.substring(3).take(3))
            }

            if (digits.length > 6) {
                append('.')
                // XXX
                append(digits.substring(6).take(3))
            }

            if (digits.length > 9) {
                append('-')
                // XX
                append(digits.substring(9).take(2))
            }
        }
    }

    private class CpfOffsetMapping(
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
 * Filtra entrada para aceitar apenas dígitos de CPF (máximo 11).
 */
fun filterCpfInput(input: String): String = input.filter { it.isDigit() }.take(11)
