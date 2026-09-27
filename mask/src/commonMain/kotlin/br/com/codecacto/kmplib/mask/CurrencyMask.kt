package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.abs
import kotlin.math.roundToLong

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
    /**
     * Casas decimais. `0` = **reais inteiros** ("R$ 35.000"), o padrão de anúncio de veículo e de
     * imóvel — até a 2.207.0 `0` desenhava "R$ 35.000," com a vírgula pendurada no fim.
     */
    private val decimalPlaces: Int = 2,
    /**
     * `true` (default, comportamento de sempre): campo vazio desenha "R$ 0,00". `false`: vazio
     * fica VAZIO (2.208.0) — um zero desenhado num campo em branco é lido como valor já informado,
     * e o placeholder nunca aparece (Mirassol Conectado, 16/set/2026: *"aparece o 00"*).
     */
    private val showZeroWhenEmpty: Boolean = true,
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
        if (digits.isEmpty()) {
            return if (showZeroWhenEmpty) "${prefix}0" + (if (decimalPlaces > 0) "," + "0".repeat(decimalPlaces) else "") else ""
        }
        if (decimalPlaces <= 0) {
            return "$prefix${groupThousands(digits)}"
        }

        // Preenche com zeros à esquerda se necessário
        val paddedDigits = digits.padStart(decimalPlaces + 1, '0')

        // Separa parte inteira e decimal. A parte inteira é agrupada como TEXTO: convertê-la para
        // Long (até a 2.218.1) desenhava "R$ 0,xx" quando o campo passava de 19 dígitos.
        val integerPart = paddedDigits.dropLast(decimalPlaces)
        val decimalPart = paddedDigits.takeLast(decimalPlaces)

        return "$prefix${groupThousands(integerPart)},$decimalPart"
    }

    private class CurrencyOffsetMapping(
        private val original: String,
        private val formatted: String,
        private val prefixLength: Int
    ) : OffsetMapping {

        override fun originalToTransformed(offset: Int): Int {
            // Para moeda, o cursor sempre vai para o final
            // pois os dígitos são inseridos da direita para esquerda
            // Vazio desenhado como "" não tem prefixo: devolver `prefixLength` aqui estouraria o
            // limite do texto transformado e derrubaria o campo no primeiro quadro.
            if (formatted.isEmpty()) return 0
            if (offset == 0) return prefixLength.coerceAtMost(formatted.length)
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
 * Ex: 123.45 -> "R$ 123,45" · -1000.0 -> "-R$ 1.000,00" · 1234567.89 -> "R$ 1.234.567,89"
 *
 * - **Arredonda** para o centavo mais próximo, meio centavo para longe do zero (HALF_UP sobre o
 *   módulo, a mesma regra de `formatCurrencyBRL` do `kmplib-core`). Até a 2.218.1 a conta
 *   **truncava** (`(this * 100).toLong()`), e `1234567.89` — que em ponto flutuante vale
 *   `123456788.99999…` centavos — saía "R$ 1.234.567,88".
 * - O **sinal vem antes do prefixo** ("-R$ 1.000,00", como o pt-BR escreve). Até a 2.218.1 saía
 *   "R$ -1.000,00", e valor negativo menor que 1 real quebrava o texto ("R$ 0,-50").
 * - Valor que arredonda para zero não leva sinal (`-0.001` → "R$ 0,00").
 * - `NaN`/infinito não derrubam a tela: desenham zero, como antes.
 *
 * Dinheiro que já existe em centavos (`Long`) não deve passar por `Double` — use `Money` do core.
 */
fun Double.formatAsCurrency(prefix: String = "R$ "): String {
    val totalCents = if (isFinite()) (abs(this) * 100).roundToLong() else 0L
    val sign = if (this < 0 && totalCents != 0L) "-" else ""
    val integerPart = totalCents / 100
    val decimalPart = (totalCents % 100).toString().padStart(2, '0')
    return "$sign$prefix${groupThousands(integerPart.toString())},$decimalPart"
}

/** Agrupa uma string de dígitos de 3 em 3 com ".", sem zeros à esquerda ("0012345" -> "12.345"). */
private fun groupThousands(digits: String): String {
    val str = digits.trimStart('0').ifEmpty { "0" }
    return buildString {
        for (i in str.indices) {
            if (i > 0 && (str.length - i) % 3 == 0) append('.')
            append(str[i])
        }
    }
}
