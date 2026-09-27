package br.com.codecacto.kmplib.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import br.com.codecacto.kmplib.validation.InternationalPhone
import br.com.codecacto.kmplib.validation.PhoneValidator

/**
 * Máscara de telefone **internacional**, enquanto a pessoa digita: `+351 912 345 678`,
 * `+1 415-555-0100`, `+55 (65) 99999-8888` — e, sem `+`, o número nacional da [defaultRegion] no
 * formato dela.
 *
 * Par do [filterInternationalPhoneInput] (que deixa só `+` no começo e dígitos). Só insere
 * separadores, nunca troca um caractere de lugar — o cursor anda certo.
 */
class InternationalPhoneVisualTransformation(
    private val defaultRegion: String?,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val bruto = InternationalPhone.filterInput(text.text)
        val formatado = InternationalPhone.formatAsYouType(bruto, defaultRegion)
        return TransformedText(AnnotatedString(formatado), SignificantCharOffsetMapping(bruto, formatado))
    }

    override fun equals(other: Any?): Boolean =
        other is InternationalPhoneVisualTransformation && other.defaultRegion == defaultRegion

    override fun hashCode(): Int = defaultRegion.hashCode()
}

/** Filtro de digitação do telefone internacional: `+` só no começo, dígitos, até 15. */
fun filterInternationalPhoneInput(input: String): String = InternationalPhone.filterInput(input)

/**
 * **O campo de telefone de um app que atende o Brasil e o mundo** — decide, por região, entre o
 * caminho brasileiro de sempre e o internacional (E.164).
 *
 * - **Brasil** (região `BR`, ou região desconhecida): `filterPhoneInput` + [PhoneVisualTransformation]
 *   + [PhoneValidator], exatamente como antes — o número sai com DDD e sem DDI (`65999998888`).
 * - **Outra região**: [filterInternationalPhoneInput] + [InternationalPhoneVisualTransformation] +
 *   [InternationalPhone]; o número sai em **E.164** (`+351912345678`).
 *
 * Região desconhecida cai no Brasil **só aqui**, e de propósito: é o comportamento que todo app da
 * fábrica tinha, e quem está fora do Brasil quase sempre tem região configurada. Em qualquer modo, a
 * pessoa pode digitar `+DDI` — no brasileiro o `+` é descartado, então quem espera receber número
 * estrangeiro deve usar [forRegion] com a região do aparelho (`deviceRegion()`, `kmplib-platform`).
 *
 * ```kotlin
 * val formato = remember { PhoneInputFormat.forRegion(deviceRegion()) }
 * AppTextField(
 *     value = telefone,
 *     onValueChange = { telefone = formato.filter(it) },
 *     visualTransformation = formato.visualTransformation,
 *     errorMessage = erro,
 * )
 * // ao enviar:
 * if (!formato.isValid(telefone)) erro = … else api.enviar(formato.toSubmitValue(telefone))
 * ```
 */
class PhoneInputFormat private constructor(
    /** A região ISO 3166-1 de referência (`BR`, `PT`, `US`). */
    val region: String,
) {
    /** `true` no caminho brasileiro (DDD + número, sem DDI). */
    val isBrazilian: Boolean get() = region == BRAZIL

    /** Filtra o que a pessoa digita (chame no `onValueChange`). */
    fun filter(input: String): String =
        if (isBrazilian) filterPhoneInput(input) else filterInternationalPhoneInput(input)

    /** A máscara de exibição do modo. */
    val visualTransformation: VisualTransformation =
        if (isBrazilian) PhoneVisualTransformation() else InternationalPhoneVisualTransformation(region)

    /** Telefone plausível para o modo (BR: [PhoneValidator]; demais: [InternationalPhone.isValid]). */
    fun isValid(value: String): Boolean =
        if (isBrazilian) PhoneValidator.isValid(value) else InternationalPhone.isValid(value, region)

    /**
     * O que mandar ao servidor: no Brasil, os dígitos como sempre (`65999998888`); nos demais, E.164
     * (`+351912345678`). Valor inválido volta aparado, para o servidor recusar com a mensagem dele.
     */
    fun toSubmitValue(value: String): String =
        if (isBrazilian) PhoneValidator.unmask(value)
        else InternationalPhone.toE164(value, region) ?: value.trim()

    /** Traz um valor salvo (E.164 ou dígitos) para o formato de digitação do modo. */
    fun fromStoredValue(value: String?): String {
        val v = value.orEmpty()
        if (!isBrazilian) return filter(v)
        // Número brasileiro salvo em E.164 (+55…) volta sem o DDI, que o modo BR não mostra.
        val digitos = v.filter { it in '0'..'9' }
        return filter(if (v.trimStart().startsWith("+55")) digitos.drop(2) else digitos)
    }

    override fun equals(other: Any?): Boolean = other is PhoneInputFormat && other.region == region
    override fun hashCode(): Int = region.hashCode()
    override fun toString(): String = "PhoneInputFormat($region)"

    companion object {
        private const val BRAZIL: String = "BR"

        /** O caminho brasileiro — o de sempre. */
        val Brazil: PhoneInputFormat = PhoneInputFormat(BRAZIL)

        /**
         * O formato da [region] (ISO 3166-1, normalmente `deviceRegion()`). Região `null`, em branco
         * ou desconhecida do plano de numeração → [Brazil].
         */
        fun forRegion(region: String?): PhoneInputFormat {
            val r = region?.trim()?.uppercase().orEmpty()
            if (r.isEmpty() || r == BRAZIL || InternationalPhone.callingCodeFor(r) == null) return Brazil
            return PhoneInputFormat(r)
        }
    }
}

/**
 * Mapeia o cursor entre o texto cru ([original], só `+` e dígitos) e o formatado, que contém os
 * mesmos caracteres na mesma ordem com separadores entre eles.
 */
internal class SignificantCharOffsetMapping(
    private val original: String,
    private val formatted: String,
) : OffsetMapping {

    private fun isSignificant(c: Char): Boolean = c == '+' || c in '0'..'9'

    override fun originalToTransformed(offset: Int): Int {
        if (offset <= 0) return 0
        var vistos = 0
        for ((i, c) in formatted.withIndex()) {
            if (isSignificant(c)) {
                vistos++
                if (vistos == offset) return i + 1
            }
        }
        return formatted.length
    }

    override fun transformedToOriginal(offset: Int): Int {
        var vistos = 0
        for (i in 0 until minOf(offset, formatted.length)) {
            if (isSignificant(formatted[i])) vistos++
        }
        return minOf(vistos, original.length)
    }
}
