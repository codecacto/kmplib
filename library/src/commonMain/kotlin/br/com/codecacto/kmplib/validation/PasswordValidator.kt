package br.com.codecacto.kmplib.validation

/**
 * Validador de senha com regras configuráveis.
 *
 * Uso:
 * ```kotlin
 * // Validação simples (mínimo 8 caracteres)
 * PasswordValidator.isValid("Senha123!") // true
 *
 * // Validação com regras personalizadas
 * val rules = PasswordRules(
 *     minLength = 8,
 *     requireUppercase = true,
 *     requireLowercase = true,
 *     requireDigit = true,
 *     requireSpecialChar = true
 * )
 * PasswordValidator.validate("Senha@123", rules) // lista vazia = válida
 * ```
 */
object PasswordValidator {

    /**
     * Regras de validação de senha.
     */
    data class PasswordRules(
        val minLength: Int = 8,
        val maxLength: Int = 128,
        val requireUppercase: Boolean = true,
        val requireLowercase: Boolean = true,
        val requireDigit: Boolean = true,
        val requireSpecialChar: Boolean = true,
        val specialChars: String = "!@#\$%^&*()_+-=[]{}|;':\",./<>?"
    )

    /**
     * Resultado da validação de senha.
     */
    sealed class ValidationError(val message: String) {
        data object TooShort : ValidationError("Senha muito curta")
        data object TooLong : ValidationError("Senha muito longa")
        data object MissingUppercase : ValidationError("Senha deve conter letra maiúscula")
        data object MissingLowercase : ValidationError("Senha deve conter letra minúscula")
        data object MissingDigit : ValidationError("Senha deve conter número")
        data object MissingSpecialChar : ValidationError("Senha deve conter caractere especial")
    }

    private val DEFAULT_RULES = PasswordRules()

    /**
     * Valida uma senha com regras padrão (mínimo 8 caracteres).
     * @param password A senha a ser validada
     * @return true se a senha é válida, false caso contrário
     */
    fun isValid(password: String): Boolean = validate(password, DEFAULT_RULES).isEmpty()

    /**
     * Valida uma senha com regras personalizadas.
     * @param password A senha a ser validada
     * @param rules As regras de validação
     * @return Lista de erros de validação (vazia se válida)
     */
    fun validate(password: String, rules: PasswordRules = DEFAULT_RULES): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (password.length < rules.minLength) {
            errors.add(ValidationError.TooShort)
        }

        if (password.length > rules.maxLength) {
            errors.add(ValidationError.TooLong)
        }

        if (rules.requireUppercase && !password.any { it.isUpperCase() }) {
            errors.add(ValidationError.MissingUppercase)
        }

        if (rules.requireLowercase && !password.any { it.isLowerCase() }) {
            errors.add(ValidationError.MissingLowercase)
        }

        if (rules.requireDigit && !password.any { it.isDigit() }) {
            errors.add(ValidationError.MissingDigit)
        }

        if (rules.requireSpecialChar && !password.any { it in rules.specialChars }) {
            errors.add(ValidationError.MissingSpecialChar)
        }

        return errors
    }

    /**
     * Calcula a força de uma senha (0-100).
     * @param password A senha a ser avaliada
     * @return Pontuação de 0 a 100
     */
    fun calculateStrength(password: String): Int {
        var score = 0

        // Tamanho
        score += minOf(password.length * 4, 40)

        // Letras maiúsculas
        if (password.any { it.isUpperCase() }) score += 10

        // Letras minúsculas
        if (password.any { it.isLowerCase() }) score += 10

        // Números
        if (password.any { it.isDigit() }) score += 10

        // Caracteres especiais
        if (password.any { !it.isLetterOrDigit() }) score += 15

        // Variedade de caracteres
        val uniqueChars = password.toSet().size
        score += minOf(uniqueChars * 2, 15)

        return minOf(score, 100)
    }

    /**
     * Retorna a forca da senha baseada em calculateStrength().
     */
    fun getStrength(password: String): PasswordStrength {
        return when (calculateStrength(password)) {
            in 0..25 -> PasswordStrength.WEAK
            in 26..50 -> PasswordStrength.FAIR
            in 51..75 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }
    }

    /**
     * Retorna uma descrição textual da força da senha.
     */
    fun getStrengthLabel(password: String): String {
        return when (calculateStrength(password)) {
            in 0..25 -> "Muito fraca"
            in 26..50 -> "Fraca"
            in 51..75 -> "Média"
            in 76..90 -> "Forte"
            else -> "Muito forte"
        }
    }
    /**
     * Gera uma senha aleatoria com os requisitos informados.
     */
    fun generatePassword(
        length: Int = 12,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSpecial: Boolean = true,
        specialChars: String = DEFAULT_RULES.specialChars
    ): String {
        val pools = mutableListOf<String>()
        if (includeUppercase) pools.add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (includeLowercase) pools.add("abcdefghijklmnopqrstuvwxyz")
        if (includeDigits) pools.add("0123456789")
        if (includeSpecial) pools.add(specialChars)

        require(pools.isNotEmpty()) { "Selecione ao menos um conjunto de caracteres" }
        require(length >= pools.size) { "Tamanho minimo deve ser >= quantidade de conjuntos" }

        val random = kotlin.random.Random.Default
        val result = mutableListOf<Char>()

        pools.forEach { pool ->
            result.add(pool[random.nextInt(pool.length)])
        }

        val allChars = pools.joinToString("")
        repeat(length - result.size) {
            result.add(allChars[random.nextInt(allChars.length)])
        }

        return result.shuffled(random).joinToString("")
    }
}

/**
 * Enum de forca de senha.
 */
enum class PasswordStrength {
    WEAK,
    FAIR,
    GOOD,
    STRONG
}

/**
 * Propriedades auxiliares para manter compatibilidade com exemplos de uso.
 */
val List<PasswordValidator.ValidationError>.isValid: Boolean
    get() = this.isEmpty()

val List<PasswordValidator.ValidationError>.errors: List<PasswordValidator.ValidationError>
    get() = this

/**
 * Alias para facilitar imports (ex: PasswordRules).
 */
typealias PasswordRules = PasswordValidator.PasswordRules

