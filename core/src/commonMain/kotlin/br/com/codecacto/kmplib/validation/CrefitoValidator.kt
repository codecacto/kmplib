package br.com.codecacto.kmplib.validation

/**
 * Valida numero CREFITO: 6 digitos seguidos de F (fisioterapeuta) ou T (terapeuta ocupacional).
 *
 * Exemplo valido: "123456F"
 */
object CrefitoValidator {
    private val PATTERN = Regex("^\\d{6}[FT]$")

    fun isValid(value: String): Boolean = PATTERN.matches(value.uppercase())
}
