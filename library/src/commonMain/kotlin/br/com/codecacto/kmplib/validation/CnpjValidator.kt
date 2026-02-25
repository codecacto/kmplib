package br.com.codecacto.kmplib.validation

/**
 * Validador de CNPJ brasileiro.
 *
 * Suporta tanto o formato numérico tradicional quanto o novo formato alfanumérico
 * que será adotado a partir de julho de 2026.
 *
 * Uso:
 * ```kotlin
 * CnpjValidator.isValid("12.345.678/0001-95") // numérico
 * CnpjValidator.isValid("AB.CDE.FGH/0001-XX") // alfanumérico (2026+)
 * ```
 */
object CnpjValidator {

    private val NUMERIC_WEIGHTS_FIRST = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
    private val NUMERIC_WEIGHTS_SECOND = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)

    /**
     * Valida um CNPJ e retorna mensagem de erro se inválido.
     * @param cnpj O CNPJ a ser validado (com ou sem formatação)
     * @return null se válido, mensagem de erro se inválido
     */
    fun validate(cnpj: String): String? {
        val cleaned = unmask(cnpj)

        if (cleaned.isEmpty()) return "CNPJ é obrigatório"
        if (cleaned.length != 14) return "CNPJ deve ter 14 caracteres"

        return if (isValid(cnpj)) null else "CNPJ inválido"
    }

    /**
     * Valida um CNPJ (numérico ou alfanumérico).
     * @param cnpj O CNPJ a ser validado (com ou sem formatação)
     * @return true se o CNPJ é válido, false caso contrário
     */
    fun isValid(cnpj: String): Boolean {
        val cleaned = unmask(cnpj).uppercase()

        // CNPJ deve ter 14 caracteres
        if (cleaned.length != 14) return false

        // Verificar se é numérico ou alfanumérico
        val isNumeric = cleaned.all { it.isDigit() }

        return if (isNumeric) {
            validateNumericCnpj(cleaned)
        } else {
            validateAlphanumericCnpj(cleaned)
        }
    }

    /**
     * Valida CNPJ numérico tradicional.
     */
    private fun validateNumericCnpj(cnpj: String): Boolean {
        // CNPJ não pode ter todos os dígitos iguais
        if (cnpj.all { it == cnpj[0] }) return false

        // Validar primeiro dígito verificador
        val firstDigit = calculateNumericVerifierDigit(cnpj.substring(0, 12), NUMERIC_WEIGHTS_FIRST)
        if (firstDigit != cnpj[12].digitToInt()) return false

        // Validar segundo dígito verificador
        val secondDigit = calculateNumericVerifierDigit(cnpj.substring(0, 13), NUMERIC_WEIGHTS_SECOND)
        if (secondDigit != cnpj[13].digitToInt()) return false

        return true
    }

    /**
     * Valida CNPJ alfanumérico (novo formato 2026+).
     * Usa valores ASCII para letras (A=17, B=18, ..., Z=42).
     */
    private fun validateAlphanumericCnpj(cnpj: String): Boolean {
        // Converter para valores numéricos (0-9 = 0-9, A-Z = 17-42)
        val values = cnpj.map { charToValue(it) }

        // Validar primeiro dígito verificador
        val firstDigit = calculateAlphanumericVerifierDigit(values.subList(0, 12), NUMERIC_WEIGHTS_FIRST)
        if (firstDigit != values[12]) return false

        // Validar segundo dígito verificador
        val secondDigit = calculateAlphanumericVerifierDigit(values.subList(0, 13), NUMERIC_WEIGHTS_SECOND)
        if (secondDigit != values[13]) return false

        return true
    }

    /**
     * Converte caractere para valor numérico do CNPJ alfanumérico.
     */
    private fun charToValue(char: Char): Int {
        return when {
            char.isDigit() -> char.digitToInt()
            char.isLetter() -> char.uppercaseChar().code - 'A'.code + 17
            else -> 0
        }
    }

    /**
     * Calcula dígito verificador para CNPJ numérico.
     */
    private fun calculateNumericVerifierDigit(partial: String, weights: IntArray): Int {
        var sum = 0
        for (i in partial.indices) {
            sum += partial[i].digitToInt() * weights[i]
        }
        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    /**
     * Calcula dígito verificador para CNPJ alfanumérico.
     */
    private fun calculateAlphanumericVerifierDigit(values: List<Int>, weights: IntArray): Int {
        var sum = 0
        for (i in values.indices) {
            sum += values[i] * weights[i]
        }
        val remainder = sum % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    /**
     * Remove formatação de um CNPJ.
     * @param cnpj O CNPJ formatado
     * @return CNPJ sem pontuação
     */
    fun unmask(cnpj: String): String = cnpj.filter { it.isLetterOrDigit() }

    /**
     * Formata um CNPJ numérico no padrão XX.XXX.XXX/XXXX-XX.
     * @param cnpj O CNPJ (apenas dígitos)
     * @return CNPJ formatado ou string original se inválido
     */
    fun format(cnpj: String): String {
        val cleaned = cnpj.filter { it.isLetterOrDigit() }.uppercase()
        if (cleaned.length != 14) return cnpj

        return buildString {
            append(cleaned.substring(0, 2))
            append('.')
            append(cleaned.substring(2, 5))
            append('.')
            append(cleaned.substring(5, 8))
            append('/')
            append(cleaned.substring(8, 12))
            append('-')
            append(cleaned.substring(12, 14))
        }
    }
}
