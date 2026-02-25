package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailValidatorTest {

    @Test
    fun testValidEmails() {
        assertTrue(EmailValidator.isValid("email@exemplo.com"))
        assertTrue(EmailValidator.isValid("usuario.nome@empresa.com.br"))
        assertTrue(EmailValidator.isValid("test+tag@gmail.com"))
        assertTrue(EmailValidator.isValid("email_com_underscore@dominio.org"))
    }

    @Test
    fun testInvalidEmails() {
        assertFalse(EmailValidator.isValid(""))
        assertFalse(EmailValidator.isValid("   "))
        assertFalse(EmailValidator.isValid("semArroba"))
        assertFalse(EmailValidator.isValid("sem@dominio"))
        assertFalse(EmailValidator.isValid("@dominio.com"))
        assertFalse(EmailValidator.isValid("email@"))
    }

    @Test
    fun testValidateReturnsNull() {
        assertNull(EmailValidator.validate("email@valido.com"))
    }

    @Test
    fun testValidateReturnsError() {
        assertEquals("Email é obrigatório", EmailValidator.validate(""))
        assertEquals("Email inválido", EmailValidator.validate("invalido"))
    }

    @Test
    fun testNormalize() {
        assertEquals("email@exemplo.com", EmailValidator.normalize("  EMAIL@Exemplo.COM  "))
    }
}
