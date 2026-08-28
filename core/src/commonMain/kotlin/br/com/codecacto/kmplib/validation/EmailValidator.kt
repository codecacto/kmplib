package br.com.codecacto.kmplib.validation

/**
 * Validador de email.
 *
 * Uso:
 * ```kotlin
 * EmailValidator.isValid("email@exemplo.com") // true
 * EmailValidator.isValid("invalido")          // false
 * ```
 */
object EmailValidator {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    /**
     * Valida um endereço de email.
     * @param email O email a ser validado
     * @return true se o email é válido, false caso contrário
     */
    fun isValid(email: String): Boolean {
        if (email.isBlank()) return false
        return EMAIL_REGEX.matches(email.trim())
    }

    /**
     * Valida um email e retorna uma mensagem de erro se inválido.
     * @param email O email a ser validado
     * @return null se válido, mensagem de erro se inválido
     */
    fun validate(email: String): String? {
        return when {
            email.isBlank() -> "Email é obrigatório"
            !EMAIL_REGEX.matches(email.trim()) -> "Email inválido"
            else -> null
        }
    }

    /**
     * Normaliza um email (lowercase e trim).
     * @param email O email a ser normalizado
     * @return Email normalizado
     */
    fun normalize(email: String): String = email.trim().lowercase()

    /**
     * Alias para normalize() - mantém consistência com outros validadores.
     * Para email, "unmask" significa normalizar (trim + lowercase).
     * @param email O email a ser processado
     * @return Email normalizado
     */
    fun unmask(email: String): String = normalize(email)

    /**
     * Alias para normalize() - mantém consistência com outros validadores.
     * @param email O email a ser formatado
     * @return Email normalizado
     */
    fun format(email: String): String = normalize(email)
}
