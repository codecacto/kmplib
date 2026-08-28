package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de moeda brasileira (BRL) para Compose.
 *
 * Formata automaticamente como: R$ 1.234,56
 *
 * Uso:
 * ```kotlin
 * TextField(
 *     value = value,
 *     onValueChange = { value = filterCurrencyInput(it) },
 *     visualTransformation = CurrencyVisualTransformation()
 * )
 * ```
 */
class CurrencyVisualTransformation(
    private val prefix: String = "R$ ",
    private val decimalPlaces: Int = 2
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = formatCurrency(digits)

        return TransformedText(
            AnnotatedString(formatted),
            CurrencyOffsetMapping(digits, formatted, prefix.length)
        )
    }

    private fun formatCurrency(digits: String): String {
        if (digits.isEmpty()) return "${prefix}0,00"

        // Preenche com zeros à esquerda se necessário
        val paddedDigits = digits.padStart(decimalPlaces + 1, '0')

        // Separa parte inteira e decimal
        val integerPart = paddedDigits.dropLast(decimalPlaces).toLongOrNull() ?: 0L
        val decimalPart = paddedDigits.takeLast(decimalPlaces)

        // Formata parte inteira com separador de milhar
        val formattedInteger = formatWithThousandSeparator(integerPart)

        return "$prefix$formattedInteger,$decimalPart"
    }

    private fun formatWithThousandSeparator(value: Long): String {
        val str = value.toString()
        val result = StringBuilder()

        for (i in str.indices) {
            if (i > 0 && (str.length - i) % 3 == 0) {
                result.append('.')
            }
            result.append(str[i])
        }

        return result.toString()
    }

    private class CurrencyOffsetMapping(
        private val original: String,
        private val formatted: String,
        private val prefixLength: Int
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            // Para moeda, o cursor sempre vai para o final
            // pois os dígitos são inseridos da direita para esquerda
            if (offset == 0) return prefixLength
            return formatted.length
        }

        override fun transformedToOriginal(offset: Int): Int {
            // Mapeia posição no texto formatado para posição nos dígitos originais
            if (offset <= prefixLength) return 0
            return original.length
        }
    }
}

/**
 * Filtra entrada para aceitar apenas dígitos de valor monetário.
 */
fun filterCurrencyInput(input: String): String = input.filter { it.isDigit() }

/**
 * Converte uma string de dígitos para Double (centavos).
 * Ex: "12345" -> 123.45
 */
fun String.currencyToDouble(): Double {
    val digits = this.filter { it.isDigit() }
    if (digits.isEmpty()) return 0.0
    return digits.toLongOrNull()?.div(100.0) ?: 0.0
}

/**
 * Converte um Double para string formatada como moeda brasileira.
 * Ex: 123.45 -> "R$ 123,45"
 */
fun Double.formatAsCurrency(prefix: String = "R$ "): String {
    val totalCents = (this * 100).toLong()
    val integerPart = totalCents / 100
    val decimalPart = (totalCents % 100).toString().padStart(2, '0')

    val formattedInteger = buildString {
        val str = integerPart.toString()
        for (i in str.indices) {
            if (i > 0 && (str.length - i) % 3 == 0) {
                append('.')
            }
            append(str[i])
        }
    }

    return "$prefix$formattedInteger,$decimalPart"
}
