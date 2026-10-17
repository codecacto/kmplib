package br.com.codecacto.kmplib.core.currency

import br.com.codecacto.kmplib.core.locale.RegionalFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * # Dinheiro em QUALQUER moeda — valor exato em unidades menores + código ISO 4217
 *
 * O contrato de API da fábrica (o mesmo `Money` da weblib e da backlib) é:
 *
 * ```json
 * {"amount": "2880.00", "currency": "BRL"}
 * {"amount": "2880",    "currency": "JPY"}
 * {"amount": "1.500",   "currency": "KWD"}
 * ```
 *
 * `amount` é **string decimal** com ponto e **exatamente as casas da moeda** ([fractionDigitsOf]) —
 * nunca `Double`, nunca número JSON (`2880.1` em ponto flutuante não existe exatamente).
 *
 * Em memória, o valor é [minorUnits]: um `Long` na **unidade menor DA PRÓPRIA MOEDA** — centavos para
 * BRL/USD/EUR (2 casas), o próprio iene para JPY (0 casas), o fils para KWD (3 casas). Ou seja:
 * `Money(288000, "BRL")` = R$ 2.880,00 e `Money(2880, "JPY")` = ¥2.880. Toda conta é inteira e exata.
 *
 * As casas vêm da **tabela ISO 4217** ([fractionDigitsOf]), não do CLDR do aparelho: o CLDR diverge
 * da ISO em ~12 moedas (IQD, IRR, LAK, LBP…), e a escala do valor TEM de ser idêntica no app, no web
 * (Intl) e no backend (JDK `Currency`, que segue a ISO) — senão um IQD vira mil vezes outro.
 *
 * Não confundir com `br.com.codecacto.kmplib.core.money.Money` (o utilitário antigo, só BRL, sobre
 * strings `"123.45"`), que continua existindo para quem já usa.
 *
 * Para EXIBIR: [format] (formatador oficial da plataforma, na região do aparelho).
 */
@Serializable(with = MoneySerializer::class)
class Money(
    /** Valor na unidade menor da moeda ([fractionDigits] casas). Negativo é permitido (estorno, saldo). */
    val minorUnits: Long,
    /** Código ISO 4217, três letras maiúsculas (`BRL`, `USD`, `EUR`, `JPY`). */
    val currency: String,
) : Comparable<Money> {

    init {
        require(isIsoCurrencyCodeFormat(currency)) {
            "código de moeda inválido: \"$currency\" (esperado ISO 4217, 3 letras maiúsculas)"
        }
    }

    /** Casas decimais da moeda pela ISO 4217 (BRL 2, JPY 0, KWD 3). */
    val fractionDigits: Int get() = fractionDigitsOf(currency)

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L

    /** `true` quando não há fração (R$ 2.880,00 → sim; R$ 2.880,50 → não). JPY é sempre inteiro. */
    val isWhole: Boolean get() = minorUnits % pow10(fractionDigits) == 0L

    /**
     * O `amount` do contrato: string decimal com ponto e exatamente [fractionDigits] casas —
     * `"2880.00"` (BRL), `"2880"` (JPY), `"-0.50"`, `"1.500"` (KWD).
     */
    fun toAmountString(): String {
        val texto = minorUnits.toString()
        val negativo = texto.startsWith('-')
        val algarismos = texto.removePrefix("-")
        val casas = fractionDigits
        val corpo = if (casas == 0) {
            algarismos
        } else {
            val cheio = algarismos.padStart(casas + 1, '0')
            cheio.dropLast(casas) + "." + cheio.takeLast(casas)
        }
        return if (negativo) "-$corpo" else corpo
    }

    /**
     * Texto para a TELA, no formato da região do aparelho, pelo formatador oficial da plataforma —
     * ver [RegionalFormat.formatMoney]. `showCentsWhenWhole = false` → "R$ 2.880" (cartão);
     * `true` → "R$ 2.880,00" (campo, recibo).
     */
    fun format(showCentsWhenWhole: Boolean = true): String =
        RegionalFormat.formatMoney(minorUnits, currency, showCentsWhenWhole)

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(addExact(minorUnits, other.minorUnits), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(addExact(minorUnits, negateExact(other.minorUnits)), currency)
    }

    operator fun unaryMinus(): Money = Money(negateExact(minorUnits), currency)

    /** Preço × quantidade, exato. Estoura com [ArithmeticException], nunca dá a volta em silêncio. */
    operator fun times(quantity: Long): Money = Money(multiplyExact(minorUnits, quantity), currency)

    operator fun times(quantity: Int): Money = times(quantity.toLong())

    /** Compara valores da MESMA moeda; moedas diferentes lançam [IllegalArgumentException]. */
    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    override fun equals(other: Any?): Boolean =
        other is Money && other.minorUnits == minorUnits && other.currency == currency

    override fun hashCode(): Int = 31 * minorUnits.hashCode() + currency.hashCode()

    /** `"2880.00 BRL"` — o contrato, legível em log. */
    override fun toString(): String = "${toAmountString()} $currency"

    private fun requireSameCurrency(other: Money) {
        require(other.currency == currency) {
            "moedas diferentes: $currency e ${other.currency} — converter é decisão do produto, não da conta"
        }
    }

    companion object {

        /** Zero na moeda [currency]. */
        fun zero(currency: String): Money = Money(0L, currency)

        /** A partir de unidades INTEIRAS da moeda: `ofMajor(2880, "BRL")` = R$ 2.880,00. */
        fun ofMajor(units: Long, currency: String): Money {
            require(isIsoCurrencyCodeFormat(currency)) { "código de moeda inválido: \"$currency\"" }
            return Money(multiplyExact(units, pow10(fractionDigitsOf(currency))), currency)
        }

        /**
         * Lê o `amount` do contrato (`"2880.00"`, `"-0.5"`, `"2880"`).
         *
         * - Aceita **menos** casas que a moeda (`"2880"` em BRL = 2880,00) e **mais** casas só se as
         *   excedentes forem zero (`"2880.000"` em BRL vale; `"2880.005"` é recusado — não se arredonda
         *   dinheiro em silêncio).
         * - Recusa vírgula, espaço, separador de milhar, expoente, `+`, vazio, e valor que não cabe no `Long`.
         *
         * @throws IllegalArgumentException entrada fora do contrato.
         */
        fun parse(amount: String, currency: String): Money =
            parseOrNull(amount, currency)
                ?: throw IllegalArgumentException("amount fora do contrato para $currency: \"$amount\"")

        /** Igual a [parse], mas devolve `null` em vez de lançar. */
        fun parseOrNull(amount: String, currency: String): Money? {
            if (!isIsoCurrencyCodeFormat(currency)) return null
            val m = AMOUNT_REGEX.matchEntire(amount) ?: return null
            val negativo = m.groupValues[1] == "-"
            val inteiro = m.groupValues[2]
            val fracao = m.groupValues[3]
            val casas = fractionDigitsOf(currency)
            if (fracao.length > casas && fracao.drop(casas).any { it != '0' }) return null
            val fracaoAjustada = fracao.take(casas).padEnd(casas, '0')
            val algarismos = (inteiro + fracaoAjustada).trimStart('0').ifEmpty { "0" }
            val valor = (if (negativo) "-$algarismos" else algarismos).toLongOrNull() ?: return null
            return Money(valor, currency)
        }

        /**
         * Casas decimais da moeda pela **ISO 4217** (lista oficial, SIX Group): 2 para quase todas;
         * 0 para JPY, KRW, CLP, VND, ISK, PYG, os francos CFA…; 3 para BHD, IQD, JOD, KWD, LYD, OMR,
         * TND; 4 para CLF e UYW. Códigos sem unidade menor (ouro `XAU`, `XDR`, `XXX`…) contam como 0.
         * É a mesma resposta do `java.util.Currency.getDefaultFractionDigits` do JDK (backlib).
         */
        fun fractionDigitsOf(currency: String): Int = when (currency) {
            in ZERO_DIGITS, in NO_MINOR_UNIT -> 0
            in THREE_DIGITS -> 3
            in FOUR_DIGITS -> 4
            else -> 2
        }

        private val AMOUNT_REGEX = Regex("^(-?)(\\d+)(?:\\.(\\d+))?$")

        private val ZERO_DIGITS = setOf(
            "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "UYI", "VND",
            "VUV", "XAF", "XOF", "XPF",
        )
        private val THREE_DIGITS = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")
        private val FOUR_DIGITS = setOf("CLF", "UYW")
        private val NO_MINOR_UNIT = setOf(
            "XAG", "XAU", "XBA", "XBB", "XBC", "XBD", "XDR", "XPD", "XPT", "XSU", "XTS", "XUA", "XXX",
        )
    }
}

/** `true` para exatamente três letras maiúsculas A–Z (só o FORMATO; se a moeda existe é outra pergunta). */
fun isIsoCurrencyCodeFormat(code: String?): Boolean =
    code != null && code.length == 3 && code.all { it in 'A'..'Z' }

/**
 * Serializa [Money] no contrato `{"amount": "2880.00", "currency": "BRL"}`. `amount` como número
 * JSON, moeda malformada ou casas a mais (não-zero) são recusados com [SerializationException].
 */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = MoneySurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Money) {
        encoder.encodeSerializableValue(
            MoneySurrogate.serializer(),
            MoneySurrogate(amount = value.toAmountString(), currency = value.currency),
        )
    }

    override fun deserialize(decoder: Decoder): Money {
        val bruto = decoder.decodeSerializableValue(MoneySurrogate.serializer())
        if (!isIsoCurrencyCodeFormat(bruto.currency)) {
            throw SerializationException("Money.currency inválido: \"${bruto.currency}\"")
        }
        return Money.parseOrNull(bruto.amount, bruto.currency)
            ?: throw SerializationException("Money.amount fora do contrato para ${bruto.currency}: \"${bruto.amount}\"")
    }
}

@Serializable
@SerialName("Money")
internal class MoneySurrogate(val amount: String, val currency: String)

internal fun pow10(n: Int): Long {
    var r = 1L
    repeat(n) { r *= 10 }
    return r
}

private fun addExact(a: Long, b: Long): Long {
    val r = a + b
    if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("estouro em Money: $a + $b")
    return r
}

private fun negateExact(a: Long): Long {
    if (a == Long.MIN_VALUE) throw ArithmeticException("estouro em Money: -$a")
    return -a
}

private fun multiplyExact(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    val r = a * b
    if (r / b != a || (a == -1L && b == Long.MIN_VALUE) || (b == -1L && a == Long.MIN_VALUE)) {
        throw ArithmeticException("estouro em Money: $a × $b")
    }
    return r
}
