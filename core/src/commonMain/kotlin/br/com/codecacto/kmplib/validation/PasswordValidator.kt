package br.com.codecacto.kmplib.validation

/**
 * Validador de senha com regras configuráveis.
 *
 * **Padrão da fábrica = só comprimento mínimo ([DEFAULT_MIN_LENGTH] caracteres).** Exigir maiúscula,
 * dígito e caractere especial é opt-in ([PasswordRules.strong]), nunca o default: a composição
 * obrigatória cria atrito no cadastro sem ganho real de segurança (NIST SP 800-63B desaconselha), e
 * o produto sofre na conversão. Quem quiser cobrar força pede explicitamente.
 *
 * ```kotlin
 * PasswordValidator.isValid("123456")                        // true — 6 caracteres bastam
 * PasswordValidator.errorMessage("123")                      // "A senha deve ter no mínimo 6 caracteres"
 * PasswordValidator.isValid("abc", PasswordRules(minLength = 8))   // regra própria do app
 * PasswordValidator.validate("senha", PasswordRules.strong())      // aí sim cobra composição
 * ```
 *
 * A mensagem **diz o que falta** ([errorMessage]) — "senha fraca" não informa nada a quem está
 * tentando se cadastrar. Rótulo de força ([getStrengthLabel]) é outra coisa: serve de medidor
 * opcional na UI, não de barreira.
 */
object PasswordValidator {

    /**
     * Regras de validação de senha.
     */
    data class PasswordRules(
        val minLength: Int = DEFAULT_MIN_LENGTH,
        val maxLength: Int = 128,
        val requireUppercase: Boolean = false,
        val requireLowercase: Boolean = false,
        val requireDigit: Boolean = false,
        val requireSpecialChar: Boolean = false,
        val specialChars: String = "!@#\$%^&*()_+-=[]{}|;':\",./<>?"
    ) {
        companion object {
            /** Composição obrigatória (maiúscula + minúscula + dígito + especial, mínimo 8). Opt-in. */
            fun strong(minLength: Int = 8): PasswordRules = PasswordRules(
                minLength = minLength,
                requireUppercase = true,
                requireLowercase = true,
                requireDigit = true,
                requireSpecialChar = true,
            )
        }
    }

    /**
     * Resultado da validação de senha.
     */
    sealed class ValidationError(val message: String) {
        /** Mensagem genérica; prefira [errorMessage], que informa o mínimo exigido de fato. */
        data object TooShort : ValidationError("Senha muito curta")
        data object TooLong : ValidationError("Senha muito longa")
        data object MissingUppercase : ValidationError("Senha deve conter letra maiúscula")
        data object MissingLowercase : ValidationError("Senha deve conter letra minúscula")
        data object MissingDigit : ValidationError("Senha deve conter número")
        data object MissingSpecialChar : ValidationError("Senha deve conter caractere especial")
    }

    /** Mínimo padrão do ecossistema — o mesmo do backend (`AuthLocalConfig.minPasswordLength`). */
    const val DEFAULT_MIN_LENGTH: Int = 6

    private val DEFAULT_RULES = PasswordRules()

    /**
     * Motivo da recusa, pronto para exibir no campo — ou `null` se a senha passa. Diz **o que
     * corrigir** ("A senha deve ter no mínimo 6 caracteres"), em vez do inútil "senha fraca".
     */
    fun errorMessage(password: String, rules: PasswordRules = DEFAULT_RULES): String? = when {
        password.length < rules.minLength -> "A senha deve ter no mínimo ${rules.minLength} caracteres"
        password.length > rules.maxLength -> "A senha deve ter no máximo ${rules.maxLength} caracteres"
        rules.requireUppercase && password.none { it.isUpperCase() } -> "A senha deve conter letra maiúscula"
        rules.requireLowercase && password.none { it.isLowerCase() } -> "A senha deve conter letra minúscula"
        rules.requireDigit && password.none { it.isDigit() } -> "A senha deve conter número"
        rules.requireSpecialChar && password.none { it in rules.specialChars } -> "A senha deve conter caractere especial"
        else -> null
    }

    /**
     * Valida uma senha com as regras padrão (só o mínimo de [DEFAULT_MIN_LENGTH] caracteres).
     * @param password A senha a ser validada
     * @return true se a senha é válida, false caso contrário
     */
    fun isValid(password: String, rules: PasswordRules = DEFAULT_RULES): Boolean =
        validate(password, rules).isEmpty()

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

