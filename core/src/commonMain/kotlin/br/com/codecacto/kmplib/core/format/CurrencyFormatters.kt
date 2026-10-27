package br.com.codecacto.kmplib.core.format


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
 *
 * **App global / moeda por conta (2.303.0):** NÃO use estes — use `RegionalFormat.formatMoney`
 * (`core.locale`) e o tipo `Money` (`core.currency`): formatador oficial da plataforma, qualquer
 * moeda ISO 4217, na região do aparelho, valor exato em unidades menores. USD/EUR/genérico daqui
 * ficaram `@Deprecated`; os de BRL seguem para app só-Brasil.
 */

private fun formatNumberParts(value: Double, thousandsSep: String, decimalSep: String): Pair<String, String> {
    val cents = doubleToAbsCents(value)
    val integer = cents / 100
    val centsPart = (cents % 100).toString().padStart(2, '0')
    val integerFormatted = integer.toString().reversed().chunked(3).joinToString(thousandsSep).reversed()
    return integerFormatted to centsPart
}

/** Sinal só quando o valor não arredonda para zero (`-0.001` não vira "-$0.00"). */
private fun signOf(value: Double): String = if (value < 0 && doubleToAbsCents(value) != 0L) "-" else ""

/**
 * "R$ 1.234,56" (pt-BR). Alias de [formatCurrencyBRL] — mesma correção de `NaN`/sinal na 2.329.0.
 */
@Deprecated(
    "Dinheiro em Double perde centavo. Guarde em unidades menores (Long) e use Money(centavos, \"BRL\").format() " +
        "(core.currency, 2.303.0) — ou MoneyField no campo. Este continua funcionando; migre quando a tela for tocada.",
    ReplaceWith(
        "Money((value * 100).roundToLong(), \"BRL\").format()",
        "br.com.codecacto.kmplib.core.currency.Money",
        "kotlin.math.roundToLong",
    ),
)
@Suppress("DEPRECATION")
fun formatCurrencyBR(value: Double): String = formatCurrencyBRL(value)

/**
 * "$1,234.56" (en-US).
 */
@Deprecated(
    "Separadores escritos à mão e Double. Use RegionalFormat.formatMoney(minorUnits, \"USD\") " +
        "(core.locale, 2.303.0): formatador oficial da plataforma, na região do aparelho, valor exato em unidades menores.",
)
fun formatCurrencyUSD(value: Double): String {
    val (integerPart, centsPart) = formatNumberParts(value, ",", ".")
    val sign = signOf(value)
    return "$sign\$$integerPart.$centsPart"
}

/**
 * "1.234,56 €" (es-ES, Eurozone).
 */
@Deprecated(
    "Separadores escritos à mão e Double. Use RegionalFormat.formatMoney(minorUnits, \"EUR\") " +
        "(core.locale, 2.303.0): formatador oficial da plataforma, na região do aparelho, valor exato em unidades menores.",
)
fun formatCurrencyEUR(value: Double): String {
    val (integerPart, centsPart) = formatNumberParts(value, ".", ",")
    val sign = signOf(value)
    return "$sign$integerPart,$centsPart €"
}

/**
 * Suporte generico: gera "<sign><prefix><int><dec><cents><suffix>" usando os
 * separadores informados. Util para locales nao listados aqui.
 */
@Deprecated(
    "Separadores escritos à mão e Double. Use RegionalFormat.formatMoney(minorUnits, codigoIso) " +
        "(core.locale, 2.303.0): formatador oficial da plataforma, qualquer moeda ISO 4217.",
)
fun formatCurrency(
    value: Double,
    thousandsSeparator: String,
    decimalSeparator: String,
    prefix: String = "",
    suffix: String = "",
): String {
    val (integerPart, centsPart) = formatNumberParts(value, thousandsSeparator, decimalSeparator)
    val sign = signOf(value)
    return "$sign$prefix$integerPart$decimalSeparator$centsPart$suffix"
}
