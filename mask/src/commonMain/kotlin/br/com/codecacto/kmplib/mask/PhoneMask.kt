package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de telefone brasileiro para Compose.
 *
 * Formata automaticamente como:
 * - Fixo (10 dígitos): (XX) XXXX-XXXX
 * - Celular (11 dígitos): (XX) XXXXX-XXXX
 *
 * Uso:
 * ```kotlin
 * TextField(
 *     value = phone,
 *     onValueChange = { phone = filterPhoneInput(it) },
 *     visualTransformation = PhoneVisualTransformation()
 * )
 * ```
 */
class PhoneVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val formatted = formatPhone(digits)

        return TransformedText(
            AnnotatedString(formatted),
            PhoneOffsetMapping(digits, formatted)
        )
    }

    private fun formatPhone(digits: String): String {
        if (digits.isEmpty()) return ""

        return buildString {
            // (
            append('(')

            // DDD
            append(digits.take(2))

            if (digits.length > 2) {
                append(") ")

                // Parte principal
                val mainPart = digits.substring(2)
                val isCellphone = digits.length == 11

                if (isCellphone) {
                    // Celular: XXXXX-XXXX
                    append(mainPart.take(5))
                    if (mainPart.length > 5) {
                        append('-')
                        append(mainPart.substring(5))
                    }
                } else {
                    // Fixo: XXXX-XXXX
                    append(mainPart.take(4))
                    if (mainPart.length > 4) {
                        append('-')
                        append(mainPart.substring(4))
                    }
                }
            }
        }
    }

    private class PhoneOffsetMapping(
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
 * Filtra entrada para aceitar apenas dígitos de telefone (máximo 11).
 */
fun filterPhoneInput(input: String): String = input.filter { it.isDigit() }.take(11)
