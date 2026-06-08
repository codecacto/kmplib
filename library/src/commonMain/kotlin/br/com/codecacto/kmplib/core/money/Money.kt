package br.com.codecacto.kmplib.core.money

/**
 * Utilitário de dinheiro **string decimal ponta a ponta** — aritmética 100% em centavos ([Long]).
 *
 * Dinheiro NUNCA é representado/calculado como [Double] (perda de precisão / arredondamento). Toda
 * conta acontece em **centavos** ([Long]) e os valores entram/saem como string decimal canônica
 * `"123.45"` (ponto como separador, exatamente 2 casas decimais) — o shape ideal para persistir em
 * Firestore/JSON e para paridade com um backend ou web que use a mesma convenção.
 *
 * Genérico, sem regra de negócio: cobre parse/format BRL, ida-e-volta centavos↔decimal, subtotal
 * (preço × quantidade), soma de subtotais e total com desconto (piso 0), além de helpers para
 * máscaras de moeda (dígitos crus). Para EXIBIR em pt-BR ("R$ 1.234,56") use [Money.formatBRL]
 * (ponto de apresentação) — nunca para cálculo.
 *
 * Distinto de [br.com.codecacto.kmplib.core.format.formatCurrencyBRL] (que opera sobre [Double] no
 * ponto de exibição): este utilitário é a primitiva de **cálculo** exato em centavos.
 *
 * Promovido para a kmplib na 2.19.0 (origem: MinhaOS `core/util/Money.kt`, conceito duplicado no web).
 */
object Money {

    /** Valor canônico zero (`"0.00"`). */
    const val ZERO: String = "0.00"

    /**
     * Converte uma string decimal canônica/parcial em centavos.
     * Aceita ponto OU vírgula como separador; ignora separadores de milhar.
     * Trunca (não arredonda) a partir da 3ª casa decimal. Entrada inválida/vazia -> 0.
     */
    fun toCents(decimal: String?): Long {
        if (decimal.isNullOrBlank()) return 0L
        val negative = decimal.trim().startsWith("-")
        // Mantém apenas dígitos e o último separador decimal (',' ou '.').
        val cleaned = decimal.replace(",", ".").filter { it.isDigit() || it == '.' }
        if (cleaned.isEmpty()) return 0L
        val parts = cleaned.split(".")
        val intPart = parts.first().ifEmpty { "0" }
        val fracPart = if (parts.size > 1) parts.last() else ""
        val frac2 = (fracPart + "00").take(2)
        val cents = (intPart.toLongOrNull() ?: 0L) * 100 + (frac2.toLongOrNull() ?: 0L)
        return if (negative) -cents else cents
    }

    /** Converte centavos em string decimal canônica `"123.45"`. */
    fun fromCents(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val intPart = abs / 100
        val fracPart = (abs % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + "$intPart.$fracPart"
    }

    /** Normaliza qualquer entrada para a forma canônica `"123.45"`. */
    fun normalize(decimal: String?): String = fromCents(toCents(decimal))

    /** Subtotal = unitPrice × quantity (string decimal). Quantidade negativa = piso em 0. */
    fun subtotal(unitPrice: String, quantity: Int): String =
        fromCents(toCents(unitPrice) * quantity.coerceAtLeast(0))

    /** Soma de subtotais (string decimal). */
    fun sum(values: List<String>): String =
        fromCents(values.sumOf { toCents(it) })

    /**
     * Total = Σ(subtotais) − desconto, **nunca negativo** (piso em 0). Cálculo 100% em centavos.
     */
    fun total(subtotals: List<String>, discount: String): String {
        val result = subtotals.sumOf { toCents(it) } - toCents(discount)
        return fromCents(result.coerceAtLeast(0L))
    }

    /** Saldo = base − abatimento, **nunca negativo** (piso em 0). */
    fun balance(base: String, deduction: String): String =
        fromCents((toCents(base) - toCents(deduction)).coerceAtLeast(0L))

    /**
     * Converte dígitos crus de centavos (saída de um `CurrencyVisualTransformation`/máscara)
     * para a forma canônica. Ex.: dígitos `"12345"` -> `"123.45"`.
     */
    fun fromDigits(digits: String): String =
        fromCents(digits.filter { it.isDigit() }.toLongOrNull() ?: 0L)

    /** Dígitos crus (centavos) a partir de uma string decimal canônica — para alimentar a máscara. */
    fun toDigits(decimal: String?): String = toCents(decimal).coerceAtLeast(0L).toString()

    /** Exibição pt-BR "R$ 1.234,56" — ponto de apresentação. */
    fun formatBRL(decimal: String?): String {
        val cents = toCents(decimal)
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val intPart = abs / 100
        val fracPart = (abs % 100).toString().padStart(2, '0')
        val intFormatted = intPart.toString()
            .reversed().chunked(3).joinToString(".").reversed()
        return (if (negative) "-" else "") + "R$ $intFormatted,$fracPart"
    }
}
