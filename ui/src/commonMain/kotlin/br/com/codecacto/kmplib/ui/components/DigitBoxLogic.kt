package br.com.codecacto.kmplib.ui.components

/**
 * Regras puras do [DigitBoxField] (2.225.0) — o que entra, o que é recusado e o que se anuncia.
 * Separadas do Compose para serem provadas em `commonTest`.
 */

/** Resultado de uma edição no [DigitBoxField]. */
sealed interface DigitBoxInput {
    /** Novo valor aceito (só algarismos, até o tamanho do campo). */
    data class Accepted(val value: String) : DigitBoxInput

    /**
     * Chegaram MAIS algarismos do que o campo comporta de uma vez (um número colado maior). O
     * valor anterior fica — cortar em silêncio trocaria o número por outro que a pessoa não
     * escreveu — e o campo avisa no próprio campo.
     */
    data class Overflow(val attemptedDigits: String) : DigitBoxInput

    /** Tecla a mais num campo já cheio: ignorada, como em qualquer campo de código. */
    data object Ignored : DigitBoxInput
}

/**
 * Decide o que uma edição faz. [incoming] é o texto inteiro que o campo de texto propôs (digitação,
 * apagar, colar). Não-algarismos são descartados (colar "12-34" vira "1234").
 *
 * - Até [length] algarismos: aceito.
 * - Um algarismo a mais num campo cheio: ignorado (a pessoa continuou digitando).
 * - Colou um número COMPLETO por cima do que havia: o colado substitui o anterior.
 * - Colou mais do que cabe: [DigitBoxInput.Overflow].
 */
fun applyDigitBoxInput(current: String, incoming: String, length: Int): DigitBoxInput {
    require(length > 0) { "o campo precisa de pelo menos 1 caixa" }
    val digits = incoming.onlyAsciiDigits()
    if (digits.length <= length) return DigitBoxInput.Accepted(digits)
    if (digits.length == current.length + 1 && current.length >= length && digits.startsWith(current)) {
        return DigitBoxInput.Ignored
    }
    if (incoming.startsWith(current)) {
        val inserted = incoming.substring(current.length).onlyAsciiDigits()
        if (inserted.length == length) return DigitBoxInput.Accepted(inserted)
        if (inserted.length > length) return DigitBoxInput.Overflow(inserted)
    }
    return DigitBoxInput.Overflow(digits)
}

/** Quantos algarismos faltam para completar o campo (0 quando completo). */
fun digitBoxMissingCount(value: String, length: Int): Int = (length - value.onlyAsciiDigits().length).coerceAtLeast(0)

/** Índice da caixa "da vez" (onde o próximo algarismo entra); a última quando o campo está cheio. */
internal fun digitBoxActiveIndex(value: String, length: Int): Int = value.length.coerceAtMost(length - 1)

private fun String.onlyAsciiDigits(): String = filter { it in '0'..'9' }

/** Substitui `%1$d`/`%2$d` num modelo de texto dos recursos. */
internal fun formatDigitBoxTemplate(template: String, first: Int, second: Int = 0): String =
    template.replace("%1\$d", first.toString()).replace("%2\$d", second.toString())
