package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.core.format.formatCurrencyBRL

/**
 * Helpers internos de formatação para o render do PDF da OS.
 * commonMain puro e testável (não depende de plataforma).
 */
internal object OsPdfFormat {

    /**
     * Converte uma string decimal de dinheiro (ex.: "320.00", "1234.5", "1.234,56",
     * "R$ 320,00") em [Double]. Tolerante: aceita ponto OU vírgula como separador
     * decimal e ignora separadores de milhar e símbolo de moeda. Retorna 0.0 se
     * não houver dígitos.
     */
    fun parseMoney(raw: String): Double {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return 0.0

        val sign = if (cleaned.startsWith("-")) -1.0 else 1.0
        // Mantém apenas dígitos, ponto e vírgula
        val digitsAndSeps = cleaned.filter { it.isDigit() || it == '.' || it == ',' }
        if (digitsAndSeps.isEmpty()) return 0.0

        val lastDot = digitsAndSeps.lastIndexOf('.')
        val lastComma = digitsAndSeps.lastIndexOf(',')
        // O separador decimal é o ÚLTIMO ponto/vírgula que aparece.
        val decimalSepIndex = maxOf(lastDot, lastComma)

        val normalized = if (decimalSepIndex < 0) {
            digitsAndSeps
        } else {
            val intPart = digitsAndSeps.substring(0, decimalSepIndex).filter { it.isDigit() }
            val decPart = digitsAndSeps.substring(decimalSepIndex + 1).filter { it.isDigit() }
            if (decPart.isEmpty()) intPart else "$intPart.$decPart"
        }

        val value = normalized.toDoubleOrNull() ?: return 0.0
        return sign * value
    }

    /** Formata uma string decimal de dinheiro como BRL ("R$ 1.234,56"). */
    fun money(raw: String): String = formatCurrencyBRL(parseMoney(raw))

    /** `true` se [raw] representa um valor diferente de zero (com tolerância de centavo). */
    fun isNonZero(raw: String): Boolean = kotlin.math.abs(parseMoney(raw)) >= 0.005
}
