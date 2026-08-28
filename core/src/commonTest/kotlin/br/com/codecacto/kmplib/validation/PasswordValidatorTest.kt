package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordValidatorTest {

    // ========================
    // isValid() Tests
    // ========================

    @Test
    fun `isValid returns true for valid password with default rules`() {
        assertTrue(PasswordValidator.isValid("Senha@123"))
        assertTrue(PasswordValidator.isValid("MyP@ssw0rd"))
        assertTrue(PasswordValidator.isValid("Test#2025"))
    }

    // O default da fábrica cobra SÓ comprimento (>= 6). Composição obrigatória é opt-in via
    // PasswordRules.strong() — regra de negócio, não default (ver KDoc do validador).
    @Test
    fun `isValid com o default aceita senha simples de 6 caracteres`() {
        assertTrue(PasswordValidator.isValid("123456"))
        assertTrue(PasswordValidator.isValid("senha1"))
        assertTrue(PasswordValidator.isValid("abcdef"))
    }

    @Test
    fun `isValid returns false for too short password`() {
        assertFalse(PasswordValidator.isValid("Ab1!"))
        assertFalse(PasswordValidator.isValid("12345"))
    }

    @Test
    fun `isValid com strong() returns false for missing uppercase`() {
        assertFalse(PasswordValidator.isValid("senha@123", PasswordRules.strong()))
    }

    @Test
    fun `isValid com strong() returns false for missing lowercase`() {
        assertFalse(PasswordValidator.isValid("SENHA@123", PasswordRules.strong()))
    }

    @Test
    fun `isValid com strong() returns false for missing digit`() {
        assertFalse(PasswordValidator.isValid("Senha@Test", PasswordRules.strong()))
    }

    @Test
    fun `isValid com strong() returns false for missing special char`() {
        assertFalse(PasswordValidator.isValid("Senha123", PasswordRules.strong()))
    }

    // ========================
    // errorMessage() — o texto que a UI mostra
    // ========================

    @Test
    fun `errorMessage diz o minimo exigido em vez de 'senha fraca'`() {
        assertEquals("A senha deve ter no mínimo 6 caracteres", PasswordValidator.errorMessage("12345"))
        assertEquals("A senha deve ter no mínimo 8 caracteres",
            PasswordValidator.errorMessage("1234567", PasswordRules(minLength = 8)))
    }

    @Test
    fun `errorMessage devolve null quando a senha passa`() {
        assertEquals(null, PasswordValidator.errorMessage("123456"))
    }

    @Test
    fun `errorMessage aponta a composicao que falta quando o app pede strong()`() {
        assertEquals("A senha deve conter letra maiúscula",
            PasswordValidator.errorMessage("senha@123", PasswordRules.strong()))
        assertEquals("A senha deve conter número",
            PasswordValidator.errorMessage("Senha@abc", PasswordRules.strong()))
    }

    // ========================
    // validate() Tests
    // ========================

    @Test
    fun `validate returns empty list for valid password`() {
        val result = PasswordValidator.validate("Senha@123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `validate returns TooShort for short password`() {
        val result = PasswordValidator.validate("Ab1!")   // 4 chars — abaixo do mínimo padrão (6)
        assertEquals(1, result.size)
        assertTrue(result[0] is PasswordValidator.ValidationError.TooShort)
    }

    @Test
    fun `validate returns TooLong for very long password`() {
        val longPassword = "A".repeat(130) + "a1!"
        val result = PasswordValidator.validate(longPassword)
        assertTrue(result.any { it is PasswordValidator.ValidationError.TooLong })
    }

    @Test
    fun `validate returns MissingUppercase when no uppercase`() {
        val result = PasswordValidator.validate("senha@123", PasswordRules.strong())
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingUppercase })
    }

    @Test
    fun `validate returns MissingLowercase when no lowercase`() {
        val result = PasswordValidator.validate("SENHA@123", PasswordRules.strong())
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingLowercase })
    }

    @Test
    fun `validate returns MissingDigit when no digit`() {
        val result = PasswordValidator.validate("Senha@Test", PasswordRules.strong())
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingDigit })
    }

    @Test
    fun `validate returns MissingSpecialChar when no special char`() {
        val result = PasswordValidator.validate("Senha123", PasswordRules.strong())
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingSpecialChar })
    }

    @Test
    fun `validate returns multiple errors for invalid password`() {
        val result = PasswordValidator.validate("abc", PasswordRules.strong())
        assertTrue(result.size >= 4) // TooShort, MissingUppercase, MissingDigit, MissingSpecialChar
    }

    @Test
    fun `validate with custom rules - no special char required`() {
        val rules = PasswordRules.strong().copy(requireSpecialChar = false)
        val result = PasswordValidator.validate("Senha123", rules)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `validate with custom rules - different min length`() {
        val rules = PasswordRules(minLength = 12)
        val result = PasswordValidator.validate("Short@1", rules)
        assertTrue(result.any { it is PasswordValidator.ValidationError.TooShort })
    }

    @Test
    fun `validate with custom rules - all optional`() {
        val rules = PasswordRules(
            minLength = 4,
            requireUppercase = false,
            requireLowercase = false,
            requireDigit = false,
            requireSpecialChar = false
        )
        val result = PasswordValidator.validate("test", rules)
        assertTrue(result.isEmpty())
    }

    // ========================
    // calculateStrength() Tests
    // ========================

    @Test
    fun `calculateStrength returns low score for weak password`() {
        val score = PasswordValidator.calculateStrength("abc")
        assertTrue(score < 30)
    }

    @Test
    fun `calculateStrength returns high score for strong password`() {
        val score = PasswordValidator.calculateStrength("MyV3ry\$tr0ng&C0mpl3xP@ssw0rd!")
        assertTrue(score >= 80)
    }

    @Test
    fun `calculateStrength returns medium score for medium password`() {
        // "Test123": 28 (len) + 10 (maiúscula) + 10 (minúscula) + 10 (dígito)
        // + 14 (variedade) = 72. Sem caractere especial → faixa intermediária-alta.
        val score = PasswordValidator.calculateStrength("Test123")
        assertTrue(score in 60..80, "Esperava 60..80, veio $score")
    }

    @Test
    fun `calculateStrength never exceeds 100`() {
        val veryLongPassword = "A".repeat(100) + "a1!@#"
        val score = PasswordValidator.calculateStrength(veryLongPassword)
        assertTrue(score <= 100)
    }

    @Test
    fun `calculateStrength rewards character variety`() {
        val simplePassword = "aaaaaaa1A!"
        val variedPassword = "abcde12A!Z"

        val simpleScore = PasswordValidator.calculateStrength(simplePassword)
        val variedScore = PasswordValidator.calculateStrength(variedPassword)

        assertTrue(variedScore > simpleScore)
    }

    // ========================
    // getStrength() Tests
    // ========================

    @Test
    fun `getStrength returns WEAK for weak password`() {
        // "ab": 8 (len) + 10 (minúscula) + 4 (variedade) = 22 → 0..25 WEAK.
        assertEquals(PasswordStrength.WEAK, PasswordValidator.getStrength("ab"))
    }

    @Test
    fun `getStrength returns FAIR for fair password`() {
        // "abc": 12 + 10 + 6 = 28 → 26..50 FAIR.
        assertEquals(PasswordStrength.FAIR, PasswordValidator.getStrength("abc"))
    }

    @Test
    fun `getStrength returns GOOD for good password`() {
        // "test123": 60 → 51..75 GOOD.
        assertEquals(PasswordStrength.GOOD, PasswordValidator.getStrength("test123"))
    }

    @Test
    fun `getStrength returns STRONG for strong password 8 chars all types`() {
        // "Test@123": 32 + 10 + 10 + 10 + 15 + 15 = 92 → STRONG.
        assertEquals(PasswordStrength.STRONG, PasswordValidator.getStrength("Test@123"))
    }

    @Test
    fun `getStrength returns STRONG for strong password`() {
        assertEquals(PasswordStrength.STRONG, PasswordValidator.getStrength("MyV3ry\$tr0ng&P@ssw0rd!"))
    }

    // ========================
    // getStrengthLabel() Tests
    // ========================

    @Test
    fun `getStrengthLabel returns correct labels`() {
        assertEquals("Muito fraca", PasswordValidator.getStrengthLabel("ab"))   // 22
        assertEquals("Fraca", PasswordValidator.getStrengthLabel("abc"))         // 28
        assertEquals("Média", PasswordValidator.getStrengthLabel("test123"))     // 60
        assertEquals("Muito forte", PasswordValidator.getStrengthLabel("Test@123")) // 92
        assertTrue(
            PasswordValidator.getStrengthLabel("MyV3ry\$tr0ng&C0mpl3xP@ssw0rd!") in
            listOf("Forte", "Muito forte")
        )
    }

    // ========================
    // generatePassword() Tests
    // ========================

    @Test
    fun `generatePassword creates password with default length`() {
        val password = PasswordValidator.generatePassword()
        assertEquals(12, password.length)
    }

    @Test
    fun `generatePassword creates password with custom length`() {
        val password = PasswordValidator.generatePassword(length = 20)
        assertEquals(20, password.length)
    }

    @Test
    fun `generatePassword includes uppercase when requested`() {
        val password = PasswordValidator.generatePassword(
            length = 20,
            includeUppercase = true,
            includeLowercase = false,
            includeDigits = false,
            includeSpecial = false
        )
        assertTrue(password.all { it.isUpperCase() })
    }

    @Test
    fun `generatePassword includes lowercase when requested`() {
        val password = PasswordValidator.generatePassword(
            length = 20,
            includeUppercase = false,
            includeLowercase = true,
            includeDigits = false,
            includeSpecial = false
        )
        assertTrue(password.all { it.isLowerCase() })
    }

    @Test
    fun `generatePassword includes digits when requested`() {
        val password = PasswordValidator.generatePassword(
            length = 20,
            includeUppercase = false,
            includeLowercase = false,
            includeDigits = true,
            includeSpecial = false
        )
        assertTrue(password.all { it.isDigit() })
    }

    @Test
    fun `generatePassword includes all character types`() {
        val password = PasswordValidator.generatePassword(length = 20)
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `generatePassword meets validation requirements`() {
        val password = PasswordValidator.generatePassword()
        assertTrue(PasswordValidator.isValid(password))
    }

    @Test
    fun `generatePassword with custom special chars`() {
        val customSpecialChars = "@#$"
        val password = PasswordValidator.generatePassword(
            length = 10,
            includeUppercase = false,
            includeLowercase = false,
            includeDigits = false,
            includeSpecial = true,
            specialChars = customSpecialChars
        )
        assertTrue(password.all { it in customSpecialChars })
    }

    // ========================
    // Edge Cases
    // ========================

    @Test
    fun `validate empty password`() {
        val result = PasswordValidator.validate("")
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it is PasswordValidator.ValidationError.TooShort })
    }

    @Test
    fun `validate password with only spaces`() {
        // Com o default (só comprimento) 8 espaços "passam" — é o preço de não cobrar composição;
        // o campo em si é que não deixa enviar em branco. Com strong(), reprova.
        assertTrue(PasswordValidator.validate("        ").isEmpty())
        assertFalse(PasswordValidator.validate("        ", PasswordRules.strong()).isEmpty())
    }

    @Test
    fun `validate password at exact min length`() {
        val result = PasswordValidator.validate("Test@123") // exactly 8 chars
        assertTrue(result.isEmpty())
    }

    @Test
    fun `validate password at exact max length`() {
        // maxLength = 128 → 125 letras + "a1!" = exatamente 128, ainda válida.
        val password = "A".repeat(125) + "a1!"
        assertEquals(128, password.length)
        val result = PasswordValidator.validate(password)
        assertFalse(result.any { it is PasswordValidator.ValidationError.TooLong })
    }

    // ========================
    // Extension Properties Tests
    // ========================

    @Test
    fun `isValid extension returns true for empty errors list`() {
        val errors = emptyList<PasswordValidator.ValidationError>()
        assertTrue(errors.isValid)
    }

    @Test
    fun `isValid extension returns false for non-empty errors list`() {
        val errors = listOf(PasswordValidator.ValidationError.TooShort)
        assertFalse(errors.isValid)
    }

    @Test
    fun `errors extension returns the list itself`() {
        val errorsList = listOf(
            PasswordValidator.ValidationError.TooShort,
            PasswordValidator.ValidationError.MissingUppercase
        )
        assertEquals(errorsList, errorsList.errors)
    }
}
