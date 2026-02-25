package br.com.codecacto.kmplib.validation

/**
 * Validador de telefone brasileiro.
 *
 * Suporta números fixos (10 dígitos) e celulares (11 dígitos).
 *
 * Uso:
 * ```kotlin
 * PhoneValidator.isValid("(11) 98765-4321") // celular - true
 * PhoneValidator.isValid("(11) 3456-7890")  // fixo - true
 * PhoneValidator.isValid("11987654321")     // sem formatação - true
 * ```
 */
object PhoneValidator {

    // DDDs válidos no Brasil
    private val VALID_DDD = setOf(
        11, 12, 13, 14, 15, 16, 17, 18, 19, // SP
        21, 22, 24, // RJ
        27, 28, // ES
        31, 32, 33, 34, 35, 37, 38, // MG
        41, 42, 43, 44, 45, 46, // PR
        47, 48, 49, // SC
        51, 53, 54, 55, // RS
        61, // DF
        62, 64, // GO
        63, // TO
        65, 66, // MT
        67, // MS
        68, // AC
        69, // RO
        71, 73, 74, 75, 77, // BA
        79, // SE
        81, 82, 83, 84, 85, 86, 87, 88, 89, // PE, AL, PB, RN, CE, PI
        91, 92, 93, 94, 95, 96, 97, 98, 99 // PA, AM, RR, AP, MA
    )

    /**
     * Valida um telefone e retorna mensagem de erro se inválido.
     * @param phone O telefone a ser validado (com ou sem formatação)
     * @return null se válido, mensagem de erro se inválido
     */
    fun validate(phone: String): String? {
        val digits = unmask(phone)

        if (digits.isEmpty()) return "Telefone é obrigatório"
        if (digits.length !in 10..11) return "Telefone deve ter 10 ou 11 dígitos"

        val ddd = digits.substring(0, 2).toIntOrNull()
        if (ddd == null || ddd !in VALID_DDD) return "DDD inválido"

        if (digits.length == 11 && digits[2] != '9') return "Celular deve começar com 9"
        if (digits.length == 10 && digits[2] == '9') return "Telefone fixo não pode começar com 9"

        return null
    }

    /**
     * Valida um número de telefone brasileiro.
     * @param phone O telefone a ser validado (com ou sem formatação)
     * @return true se o telefone é válido, false caso contrário
     */
    fun isValid(phone: String): Boolean {
        val digits = unmask(phone)

        // Telefone deve ter 10 (fixo) ou 11 (celular) dígitos
        if (digits.length !in 10..11) return false

        // Validar DDD
        val ddd = digits.substring(0, 2).toIntOrNull() ?: return false
        if (ddd !in VALID_DDD) return false

        // Se tiver 11 dígitos, o terceiro dígito deve ser 9 (celular)
        if (digits.length == 11 && digits[2] != '9') return false

        // Se tiver 10 dígitos, o terceiro dígito não pode ser 9
        if (digits.length == 10 && digits[2] == '9') return false

        return true
    }

    /**
     * Verifica se é um número de celular (11 dígitos começando com 9).
     */
    fun isMobile(phone: String): Boolean {
        val digits = unmask(phone)
        return digits.length == 11 && digits[2] == '9'
    }

    /**
     * Verifica se é um número fixo (10 dígitos).
     */
    fun isLandline(phone: String): Boolean {
        val digits = unmask(phone)
        return digits.length == 10
    }

    /**
     * Remove formatação de um telefone.
     * @param phone O telefone formatado
     * @return Apenas os dígitos do telefone
     */
    fun unmask(phone: String): String = phone.filter { it.isDigit() }

    /**
     * Formata um telefone no padrão brasileiro.
     * @param phone O telefone (apenas dígitos)
     * @return Telefone formatado (XX) XXXX-XXXX ou (XX) XXXXX-XXXX
     */
    fun format(phone: String): String {
        val digits = unmask(phone)

        return when (digits.length) {
            10 -> { // Fixo: (XX) XXXX-XXXX
                "(${digits.substring(0, 2)}) ${digits.substring(2, 6)}-${digits.substring(6, 10)}"
            }
            11 -> { // Celular: (XX) XXXXX-XXXX
                "(${digits.substring(0, 2)}) ${digits.substring(2, 7)}-${digits.substring(7, 11)}"
            }
            else -> phone
        }
    }

    /**
     * Extrai o DDD de um telefone.
     * @param phone O telefone (com ou sem formatação)
     * @return O DDD ou null se inválido
     */
    fun extractDdd(phone: String): Int? {
        val digits = unmask(phone)
        if (digits.length < 2) return null
        return digits.substring(0, 2).toIntOrNull()
    }
}
