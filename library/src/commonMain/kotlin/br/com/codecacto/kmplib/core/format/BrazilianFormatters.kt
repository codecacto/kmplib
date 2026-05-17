package br.com.codecacto.kmplib.core.format

import br.com.codecacto.kmplib.validation.CnpjValidator
import br.com.codecacto.kmplib.validation.CpfValidator
import kotlin.math.abs
import kotlin.math.roundToLong

fun formatCurrencyBRL(value: Double): String {
    val cents = (abs(value) * 100).roundToLong()
    val reais = cents / 100
    val centsPart = (cents % 100).toString().padStart(2, '0')
    val integerPart = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    val sign = if (value < 0) "-" else ""
    return "${sign}R$ $integerPart,$centsPart"
}

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
