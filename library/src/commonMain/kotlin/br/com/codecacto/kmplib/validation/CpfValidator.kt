package br.com.codecacto.kmplib.validation

/**
 * Validador de CPF brasileiro.
 *
 * Valida o formato e os dígitos verificadores usando o algoritmo módulo 11.
 *
 * Uso:
 * ```kotlin
 * CpfValidator.isValid("123.456.789-09") // true ou false
 * CpfValidator.isValid("12345678909")    // true ou false (aceita com ou sem formatação)
 * ```
 */
object CpfValidator {

    /**
     * Valida um CPF e retorna mensagem de erro se inválido.
     * @param cpf O CPF a ser validado (com ou sem formatação)
     * @return null se válido, mensagem de erro se inválido
     */
    fun validate(cpf: String): String? {
        val digits = cpf.filter { it.isDigit() }

        if (digits.isEmpty()) return "CPF é obrigatório"
        if (digits.length != 11) return "CPF deve ter 11 dígitos"
        if (digits.all { it == digits[0] }) return "CPF inválido"

        return if (isValid(cpf)) null else "CPF inválido"
    }

    /**
     * Valida um CPF.
     * @param cpf O CPF a ser validado (com ou sem formatação)
     * @return true se o CPF é válido, false caso contrário
     */
    fun isValid(cpf: String): Boolean {
        val digits = cpf.filter { it.isDigit() }

        // CPF deve ter 11 dígitos
        if (digits.length != 11) return false

        // CPF não pode ter todos os dígitos iguais
        if (digits.all { it == digits[0] }) return false

        // Validar primeiro dígito verificador
        val firstDigit = calculateVerifierDigit(digits.substring(0, 9))
        if (firstDigit != digits[9].digitToInt()) return false

        // Validar segundo dígito verificador
        val secondDigit = calculateVerifierDigit(digits.substring(0, 10))
        if (secondDigit != digits[10].digitToInt()) return false

        return true
    }

    /**
     * Calcula o dígito verificador de um CPF parcial.
     */
    private fun calculateVerifierDigit(partialCpf: String): Int {
        val weights = if (partialCpf.length == 9) {
            intArrayOf(10, 9, 8, 7, 6, 5, 4, 3, 2)
        } else {
            intArrayOf(11, 10, 9, 8, 7, 6, 5, 4, 3, 2)
        }

        var sum = 0
        for (i in partialCpf.indices) {
            sum += partialCpf[i].digitToInt() * weights[i]
        }

        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    /**
     * Remove formatação de um CPF.
     * @param cpf O CPF formatado
     * @return Apenas os dígitos do CPF
     */
    fun unmask(cpf: String): String = cpf.filter { it.isDigit() }

    /**
     * Formata um CPF no padrão XXX.XXX.XXX-XX.
     * @param cpf O CPF (apenas dígitos)
     * @return CPF formatado ou string original se inválido
     */
    fun format(cpf: String): String {
        val digits = cpf.filter { it.isDigit() }
        if (digits.length != 11) return cpf

        return buildString {
            append(digits.substring(0, 3))
            append('.')
            append(digits.substring(3, 6))
            append('.')
            append(digits.substring(6, 9))
            append('-')
            append(digits.substring(9, 11))
        }
    }
}
