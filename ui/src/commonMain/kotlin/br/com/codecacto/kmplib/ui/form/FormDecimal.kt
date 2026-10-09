package br.com.codecacto.kmplib.ui.form

import androidx.compose.runtime.Immutable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * **Número decimal EXATO do FormSchema v1** — o par, no app, do `BigDecimal` do `backlib-forms` e do
 * decimal sobre `bigint` da weblib (`/forms/decimal.ts`).
 *
 * O servidor confere casas decimais, passo e pontos da régua sobre o LITERAL do JSON. Em `Double` a
 * conta ingênua erra justamente aí: `0.3 % 0.1` dá `0.09999999999999998` (passo recusado que o
 * servidor aceita) e `3 × 0.1` dá `0.30000000000000004` (ponto da régua que não casa com a resposta
 * `0.3`). Cliente e servidor discordando sobre "este valor é válido" é o defeito que as fixtures
 * compartilhadas existem para impedir — por isso nada aqui passa por ponto flutuante.
 *
 * Forma canônica: `±dígitos × 10^expoente`, sem zero à esquerda nem à direita nos dígitos (`72.50`
 * e `72.5` são o mesmo número, e `7.0` é `7`). [equals] e [compareTo] são por VALOR.
 *
 * No fio é número JSON (nunca texto): lido do literal, escrito sem expoente e sem zero à direita
 * (`72.5`, `1000`) — a forma que a documentação do kotlinx.serialization indica para decimal exato
 * (`JsonUnquotedLiteral`).
 */
@Immutable
@Serializable(with = FormDecimalSerializer::class)
class FormDecimal private constructor(
    private val negative: Boolean,
    /** Dígitos sem zero à esquerda nem à direita; `"0"` só para o zero. */
    private val digits: String,
    private val exponent: Int,
) : Comparable<FormDecimal> {

    /** Casas decimais significativas: `72.5` → 1, `72.50` → 1, `100` → 0, `1e-7` → 7. */
    val decimalPlaces: Int get() = if (exponent < 0) -exponent else 0

    /** `-1`, `0` ou `1`. */
    val signum: Int get() = when {
        isZero -> 0
        negative -> -1
        else -> 1
    }

    val isZero: Boolean get() = digits == "0"

    /** Sem parte fracionária. */
    val isInteger: Boolean get() = exponent >= 0

    /** Texto sem expoente e sem zero à direita (`72.5`, `-0.001`, `1000`) — o literal do fio. */
    fun toPlainString(): String {
        val body = when {
            exponent >= 0 -> digits + "0".repeat(exponent)
            else -> {
                val point = digits.length + exponent
                if (point > 0) {
                    digits.substring(0, point) + "." + digits.substring(point)
                } else {
                    "0." + "0".repeat(-point) + digits
                }
            }
        }
        return if (negative) "-$body" else body
    }

    /** Para EXIBIR ou para API que só fala `Double` — nunca para validar. */
    fun toDouble(): Double = toPlainString().toDouble()

    /** O valor absoluto. */
    fun abs(): FormDecimal = if (negative) FormDecimal(false, digits, exponent) else this

    override fun compareTo(other: FormDecimal): Int {
        if (signum != other.signum) return signum.compareTo(other.signum)
        if (signum == 0) return 0
        val magnitude = compareMagnitude(this, other)
        return if (negative) -magnitude else magnitude
    }

    override fun equals(other: Any?): Boolean =
        other is FormDecimal && negative == other.negative && digits == other.digits && exponent == other.exponent

    override fun hashCode(): Int = (digits.hashCode() * 31 + exponent) * 31 + negative.hashCode()

    override fun toString(): String = toPlainString()

    // -----------------------------------------------------------------------------------------
    // Conta exata (interna): alinhar na mesma escala e operar em inteiro
    // -----------------------------------------------------------------------------------------

    /** Este número × 10^[scale], como inteiro; `null` se não é inteiro nessa escala ou passaria do teto. */
    internal fun scaledInteger(scale: Int): SignedNatural? {
        val zeros = exponent.toLong() + scale
        if (zeros < 0) return null
        if (isZero) return SignedNatural.ZERO
        if (digits.length + zeros > MAX_ALIGNED_DIGITS) return null
        return SignedNatural(negative, Natural.fromDigits(digits, zeros.toInt()))
    }

    companion object {
        val ZERO: FormDecimal = FormDecimal(false, "0", 0)
        val ONE: FormDecimal = FormDecimal(false, "1", 0)

        /**
         * Teto de dígitos de uma conta alinhada (passo, régua). Número que passaria dele é absurdo de
         * cadastro — o lint do servidor recusa antes —, e a conta devolve "não cabe" em vez de alocar.
         */
        internal const val MAX_ALIGNED_DIGITS: Int = 4_096

        private val NUMBER = Regex("""^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$""")

        /**
         * Teto do expoente escrito (`1e400`). Além dele o literal não cabe nem no `number` do
         * JavaScript — na weblib vira `Infinity` e é recusado como forma errada; aqui também.
         */
        private const val MAX_WRITTEN_EXPONENT: Long = 10_000

        /**
         * Lê o literal de um número JSON (`72.5`, `-3`, `1e-7`, `1.5E+21`). `null` para o que não é
         * número (texto, `"72,5"`, vazio, `NaN`) e para expoente absurdo (ver [MAX_WRITTEN_EXPONENT]).
         */
        fun parse(text: String): FormDecimal? {
            val match = NUMBER.matchEntire(text.trim()) ?: return null
            val (sign, integer, fraction, exponentText) = match.destructured
            val explicitExponent = if (exponentText.isEmpty()) 0L else exponentText.toLongOrNull() ?: return null
            if (explicitExponent > MAX_WRITTEN_EXPONENT || explicitExponent < -MAX_WRITTEN_EXPONENT) return null
            return canonical(sign == "-", integer + fraction, (explicitExponent - fraction.length).toInt())
        }

        fun of(value: Long): FormDecimal = parse(value.toString())!!

        fun of(value: Int): FormDecimal = of(value.toLong())

        /**
         * Pelo texto decimal de [value] (`72.5`), nunca pela representação binária. `null` para
         * `NaN`/infinito. Prefira [parse] com o texto digitado: é ele o que a pessoa quis dizer.
         */
        fun of(value: Double): FormDecimal? = if (value.isFinite()) parse(value.toString()) else null

        internal fun fromScaled(value: SignedNatural, scale: Int): FormDecimal =
            canonical(value.negative, value.magnitude.toDigitString(), -scale)

        private fun canonical(negative: Boolean, rawDigits: String, rawExponent: Int): FormDecimal {
            val trimmed = rawDigits.trimStart('0')
            if (trimmed.isEmpty()) return ZERO
            var end = trimmed.length
            while (end > 1 && trimmed[end - 1] == '0') end--
            val stripped = trimmed.length - end
            return FormDecimal(negative, trimmed.substring(0, end), rawExponent + stripped)
        }

        private fun compareMagnitude(a: FormDecimal, b: FormDecimal): Int {
            // A ordem de grandeza é a posição do dígito mais significativo.
            val orderA = a.digits.length.toLong() + a.exponent
            val orderB = b.digits.length.toLong() + b.exponent
            if (orderA != orderB) return orderA.compareTo(orderB)
            val length = maxOf(a.digits.length, b.digits.length)
            for (i in 0 until length) {
                val x = a.digits.getOrElse(i) { '0' }
                val y = b.digits.getOrElse(i) { '0' }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}

/**
 * `value − base` é múltiplo EXATO de `step` (`step > 0`) — o passo do `<input type="number">`,
 * contado a partir de `base` (o `min`, ou 0). Passo não positivo nunca casa.
 */
fun isOnFormStep(value: FormDecimal, base: FormDecimal, step: FormDecimal): Boolean {
    if (step.signum <= 0) return false
    val scale = maxOf(value.decimalPlaces, base.decimalPlaces, step.decimalPlaces)
    val v = value.scaledInteger(scale) ?: return false
    val b = base.scaledInteger(scale) ?: return false
    val s = step.scaledInteger(scale) ?: return false
    return (v - b).magnitude.isMultipleOf(s.magnitude)
}

// ---------------------------------------------------------------------------------------------
// Inteiro de precisão arbitrária — só o que a conta exata precisa (somar, subtrair, comparar,
// resto). Base 10, dígito menos significativo primeiro: os números aqui têm dezenas de dígitos.
// ---------------------------------------------------------------------------------------------

/** Natural (≥ 0). Sem zeros à esquerda: o zero é a lista vazia. */
internal class Natural private constructor(private val digits: IntArray) : Comparable<Natural> {

    val isZero: Boolean get() = digits.isEmpty()

    operator fun plus(other: Natural): Natural {
        val out = IntArray(maxOf(digits.size, other.digits.size) + 1)
        var carry = 0
        for (i in out.indices) {
            val sum = digits.getOrElse(i) { 0 } + other.digits.getOrElse(i) { 0 } + carry
            out[i] = sum % 10
            carry = sum / 10
        }
        return normalized(out)
    }

    /** `this − other`, com `this ≥ other`. */
    operator fun minus(other: Natural): Natural {
        val out = IntArray(digits.size)
        var borrow = 0
        for (i in digits.indices) {
            var diff = digits[i] - other.digits.getOrElse(i) { 0 } - borrow
            borrow = if (diff < 0) 1 else 0
            if (diff < 0) diff += 10
            out[i] = diff
        }
        check(borrow == 0) { "subtração de natural maior" }
        return normalized(out)
    }

    override fun compareTo(other: Natural): Int {
        if (digits.size != other.digits.size) return digits.size.compareTo(other.digits.size)
        for (i in digits.indices.reversed()) {
            if (digits[i] != other.digits[i]) return digits[i].compareTo(other.digits[i])
        }
        return 0
    }

    /** `this mod divisor == 0` — divisão longa, dígito a dígito (o divisor é pequeno em dígitos). */
    fun isMultipleOf(divisor: Natural): Boolean {
        require(!divisor.isZero) { "divisor zero" }
        var remainder = ZERO
        for (i in digits.indices.reversed()) {
            remainder = remainder.timesTenPlus(digits[i])
            while (remainder >= divisor) remainder -= divisor
        }
        return remainder.isZero
    }

    private fun timesTenPlus(digit: Int): Natural {
        if (isZero && digit == 0) return this
        val out = IntArray(digits.size + 1)
        out[0] = digit
        digits.copyInto(out, destinationOffset = 1)
        return normalized(out)
    }

    fun toDigitString(): String =
        if (isZero) "0" else buildString(digits.size) { for (i in digits.indices.reversed()) append('0' + digits[i]) }

    override fun equals(other: Any?): Boolean = other is Natural && digits.contentEquals(other.digits)

    override fun hashCode(): Int = digits.contentHashCode()

    companion object {
        val ZERO: Natural = Natural(IntArray(0))

        /** O natural escrito em [text] (só dígitos) seguido de [zeros] zeros. */
        fun fromDigits(text: String, zeros: Int): Natural {
            val out = IntArray(text.length + zeros)
            for (i in text.indices) out[zeros + text.length - 1 - i] = text[i] - '0'
            return normalized(out)
        }

        private fun normalized(raw: IntArray): Natural {
            var size = raw.size
            while (size > 0 && raw[size - 1] == 0) size--
            return Natural(if (size == raw.size) raw else raw.copyOf(size))
        }
    }
}

/** Inteiro com sinal sobre [Natural]. O zero nunca é negativo. */
internal class SignedNatural(negative: Boolean, val magnitude: Natural) : Comparable<SignedNatural> {
    val negative: Boolean = negative && !magnitude.isZero

    operator fun plus(other: SignedNatural): SignedNatural {
        if (negative == other.negative) return SignedNatural(negative, magnitude + other.magnitude)
        return if (magnitude >= other.magnitude) {
            SignedNatural(negative, magnitude - other.magnitude)
        } else {
            SignedNatural(other.negative, other.magnitude - magnitude)
        }
    }

    operator fun unaryMinus(): SignedNatural = SignedNatural(!negative, magnitude)

    operator fun minus(other: SignedNatural): SignedNatural = this + (-other)

    override fun compareTo(other: SignedNatural): Int = when {
        negative != other.negative -> if (negative) -1 else 1
        negative -> other.magnitude.compareTo(magnitude)
        else -> magnitude.compareTo(other.magnitude)
    }

    companion object {
        val ZERO: SignedNatural = SignedNatural(false, Natural.ZERO)
    }
}

/**
 * Lê/grava [FormDecimal] como NÚMERO JSON. Texto (`"72.5"`) é recusado: o contrato manda número, e
 * aceitar os dois esconderia um servidor errado.
 */
internal object FormDecimalSerializer : KSerializer<FormDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.form.FormDecimal", PrimitiveKind.DOUBLE)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: FormDecimal) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
        } else {
            encoder.encodeDouble(value.toDouble())
        }
    }

    override fun deserialize(decoder: Decoder): FormDecimal {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return element.toFormDecimalOrNull()
                ?: throw SerializationException("Esperava um número JSON no formulário")
        }
        return FormDecimal.of(decoder.decodeDouble())
            ?: throw SerializationException("Número não finito no formulário")
    }
}

/** O número de um primitivo JSON NÃO-texto; `null` para texto, booleano, `null` e não-primitivo. */
internal fun JsonElement.toFormDecimalOrNull(): FormDecimal? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    val content = primitive.content
    if (content == "true" || content == "false") return null
    return FormDecimal.parse(content)
}
