package br.com.codecacto.kmplib.core.format

import br.com.codecacto.kmplib.validation.CnpjValidator
import br.com.codecacto.kmplib.validation.CpfValidator
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * "R$ 1.234,56" a partir de um [Double] — o caminho ANTIGO de dinheiro.
 *
 * 2.329.0 (correção): `NaN`/infinito desenham "R$ 0,00" em vez de **derrubar o app** (`roundToLong`
 * lança em `NaN` — média de lista vazia, `0.0 / 0.0`), e valor que arredonda para zero não leva
 * sinal (`-0.001` saía "-R$ 0,00"). Mesma regra do `formatAsCurrency` do `kmplib-mask` (2.218.2).
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
fun formatCurrencyBRL(value: Double): String {
    val cents = doubleToAbsCents(value)
    val reais = cents / 100
    val centsPart = (cents % 100).toString().padStart(2, '0')
    val integerPart = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    val sign = if (value < 0 && cents != 0L) "-" else ""
    return "${sign}R$ $integerPart,$centsPart"
}

/**
 * Centavos do MÓDULO de [value], arredondando meio centavo para longe do zero; `NaN`/infinito → 0
 * (antes, `NaN` lançava `IllegalArgumentException` no `roundToLong`).
 */
internal fun doubleToAbsCents(value: Double): Long =
    if (value.isFinite()) (abs(value) * 100).roundToLong() else 0L

fun formatCpf(value: String): String = CpfValidator.format(value)

fun formatCnpj(value: String): String = CnpjValidator.format(value)

fun isValidCpf(value: String): Boolean = CpfValidator.isValid(value)

fun isValidCnpj(value: String): Boolean = CnpjValidator.isValid(value)

fun initialsOf(name: String): String {
    val parts = name.trim().split(' ', '\t').filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
    val last = if (parts.size > 1) parts.last().firstOrNull()?.uppercaseChar() else null
    return if (last != null) "$first$last" else first.toString()
}

fun formatMonthYear(month: Int, year: Int): String {
    val months = listOf(
        "Janeiro", "Fevereiro", "Marco", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    val safeMonth = month.coerceIn(1, 12)
    return "${months[safeMonth - 1]} $year"
}
