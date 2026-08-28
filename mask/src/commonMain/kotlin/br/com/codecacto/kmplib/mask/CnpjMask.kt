package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de CNPJ para Compose.
 *
 * Formata automaticamente como: XX.XXX.XXX/XXXX-XX
 * Suporta tanto numérico quanto alfanumérico (novo formato 2026+).
 *
 * Uso:
 * ```kotlin
 * TextField(
 *     value = cnpj,
 *     onValueChange = { cnpj = filterCnpjInput(it) },
 *     visualTransformation = CnpjVisualTransformation()
 * )
 * ```
 */
class CnpjVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val chars = text.text.filter { it.isLetterOrDigit() }.take(14).uppercase()
        val formatted = formatCnpj(chars)

        return TransformedText(
            AnnotatedString(formatted),
            CnpjOffsetMapping(chars, formatted)
        )
    }

    private fun formatCnpj(chars: String): String {
        if (chars.isEmpty()) return ""

        return buildString {
            // XX
            append(chars.take(2))

            if (chars.length > 2) {
                append('.')
                // XXX
                append(chars.substring(2).take(3))
            }

            if (chars.length > 5) {
                append('.')
                // XXX
                append(chars.substring(5).take(3))
            }

            if (chars.length > 8) {
                append('/')
                // XXXX
                append(chars.substring(8).take(4))
            }

            if (chars.length > 12) {
                append('-')
                // XX
                append(chars.substring(12).take(2))
            }
        }
    }

    private class CnpjOffsetMapping(
        private val original: String,
        private val formatted: String
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            var transformedOffset = 0
            var originalCount = 0

            for (char in formatted) {
                if (originalCount >= offset) break
                transformedOffset++
                if (char.isLetterOrDigit()) originalCount++
            }

            return transformedOffset
        }

        override fun transformedToOriginal(offset: Int): Int {
            var originalOffset = 0

            for (i in 0 until minOf(offset, formatted.length)) {
                if (formatted[i].isLetterOrDigit()) originalOffset++
            }

            return minOf(originalOffset, original.length)
        }
    }
}

/**
 * Filtra entrada para aceitar apenas caracteres alfanuméricos de CNPJ (máximo 14).
 */
fun filterCnpjInput(input: String): String = input.filter { it.isLetterOrDigit() }.take(14).uppercase()
