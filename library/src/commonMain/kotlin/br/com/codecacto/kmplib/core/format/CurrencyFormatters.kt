package br.com.codecacto.kmplib.core.format

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formatadores de moeda multi-locale.
 *
 * Cada formatador segue a convencao do locale:
 *   - BRL (pt-BR): "R$ 1.234,56"
 *   - USD (en-US): "$1,234.56"
 *   - EUR (es-ES): "1.234,56 €"
 *
 * Sem dependencia de ICU/java.text para funcionar em todos os targets KMP
 * (Android, iOS, JVM, wasmJs).
 */

private fun formatNumberParts(value: Double, thousandsSep: String, decimalSep: String): Pair<String, String> {
    val cents = (abs(value) * 100).roundToLong()
    val integer = cents / 100
    val centsPart = (cents % 100).toString().padStart(2, '0')
    val integerFormatted = integer.toString().reversed().chunked(3).joinToString(thousandsSep).reversed()
    return integerFormatted to centsPart
}

/**
 * "R$ 1.234,56" (pt-BR). Alias mais explicito sobre o ja-existente nesta lib.
 */
fun formatCurrencyBR(value: Double): String = formatCurrencyBRL(value)

/**
 * "$1,234.56" (en-US).
 */
fun formatCurrencyUSD(value: Double): String {
    val (integerPart, centsPart) = formatNumberParts(value, ",", ".")
    val sign = if (value < 0) "-" else ""
    return "$sign\$$integerPart.$centsPart"
}

/**
 * "1.234,56 €" (es-ES, Eurozone).
 */
fun formatCurrencyEUR(value: Double): String {
    val (integerPart, centsPart) = formatNumberParts(value, ".", ",")
    val sign = if (value < 0) "-" else ""
    return "$sign$integerPart,$centsPart €"
}

/**
 * Suporte generico: gera "<sign><prefix><int><dec><cents><suffix>" usando os
 * separadores informados. Util para locales nao listados aqui.
 */
fun formatCurrency(
    value: Double,
    thousandsSeparator: String,
    decimalSeparator: String,
    prefix: String = "",
    suffix: String = "",
): String {
    val (integerPart, centsPart) = formatNumberParts(value, thousandsSeparator, decimalSeparator)
    val sign = if (value < 0) "-" else ""
    return "$sign$prefix$integerPart$decimalSeparator$centsPart$suffix"
}
