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

    @Test
    fun `isValid returns false for too short password`() {
        assertFalse(PasswordValidator.isValid("Ab1!"))
        assertFalse(PasswordValidator.isValid("Test@1"))
    }

    @Test
    fun `isValid returns false for missing uppercase`() {
        assertFalse(PasswordValidator.isValid("senha@123"))
    }

    @Test
    fun `isValid returns false for missing lowercase`() {
        assertFalse(PasswordValidator.isValid("SENHA@123"))
    }

    @Test
    fun `isValid returns false for missing digit`() {
        assertFalse(PasswordValidator.isValid("Senha@Test"))
    }

    @Test
    fun `isValid returns false for missing special char`() {
        assertFalse(PasswordValidator.isValid("Senha123"))
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
        val result = PasswordValidator.validate("Ab1!")
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
        val result = PasswordValidator.validate("senha@123")
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingUppercase })
    }

    @Test
    fun `validate returns MissingLowercase when no lowercase`() {
        val result = PasswordValidator.validate("SENHA@123")
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingLowercase })
    }

    @Test
    fun `validate returns MissingDigit when no digit`() {
        val result = PasswordValidator.validate("Senha@Test")
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingDigit })
    }

    @Test
    fun `validate returns MissingSpecialChar when no special char`() {
        val result = PasswordValidator.validate("Senha123")
        assertTrue(result.any { it is PasswordValidator.ValidationError.MissingSpecialChar })
    }

    @Test
    fun `validate returns multiple errors for invalid password`() {
        val result = PasswordValidator.validate("abc")
        assertTrue(result.size >= 4) // TooShort, MissingUppercase, MissingDigit, MissingSpecialChar
    }

    @Test
    fun `validate with custom rules - no special char required`() {
        val rules = PasswordRules(requireSpecialChar = false)
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
        val score = PasswordValidator.calculateStrength("Test123")
        assertTrue(score in 30..70)
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
        assertEquals(PasswordStrength.WEAK, PasswordValidator.getStrength("abc"))
    }

    @Test
    fun `getStrength returns FAIR for fair password`() {
        assertEquals(PasswordStrength.FAIR, PasswordValidator.getStrength("test123"))
    }

    @Test
    fun `getStrength returns GOOD for good password`() {
        assertEquals(PasswordStrength.GOOD, PasswordValidator.getStrength("Test@123"))
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
        assertEquals("Muito fraca", PasswordValidator.getStrengthLabel("abc"))
        assertEquals("Fraca", PasswordValidator.getStrengthLabel("test123"))
        assertEquals("Média", PasswordValidator.getStrengthLabel("Test@123"))
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
        val result = PasswordValidator.validate("        ")
        assertFalse(result.isEmpty()) // Should fail some requirements
    }

    @Test
    fun `validate password at exact min length`() {
        val result = PasswordValidator.validate("Test@123") // exactly 8 chars
        assertTrue(result.isEmpty())
    }

    @Test
    fun `validate password at exact max length`() {
        val password = "A".repeat(127) + "a1!"
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
