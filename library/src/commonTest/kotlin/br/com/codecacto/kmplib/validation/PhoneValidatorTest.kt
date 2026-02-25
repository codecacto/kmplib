package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneValidatorTest {

    @Test
    fun testValidMobilePhones() {
        assertTrue(PhoneValidator.isValid("11987654321"))
        assertTrue(PhoneValidator.isValid("(11) 98765-4321"))
        assertTrue(PhoneValidator.isValid("21999999999"))
    }

    @Test
    fun testValidLandlinePhones() {
        assertTrue(PhoneValidator.isValid("1134567890"))
        assertTrue(PhoneValidator.isValid("(11) 3456-7890"))
        assertTrue(PhoneValidator.isValid("2125551234"))
    }

    @Test
    fun testInvalidPhones() {
        // Tamanho inválido
        assertFalse(PhoneValidator.isValid("119876543"))
        assertFalse(PhoneValidator.isValid("119876543210"))

        // DDD inválido
        assertFalse(PhoneValidator.isValid("00987654321"))
        assertFalse(PhoneValidator.isValid("01987654321"))

        // 11 dígitos mas terceiro não é 9
        assertFalse(PhoneValidator.isValid("11887654321"))

        // 10 dígitos mas terceiro é 9
        assertFalse(PhoneValidator.isValid("1198765432"))
    }

    @Test
    fun testIsMobile() {
        assertTrue(PhoneValidator.isMobile("11987654321"))
        assertFalse(PhoneValidator.isMobile("1134567890"))
    }

    @Test
    fun testIsLandline() {
        assertTrue(PhoneValidator.isLandline("1134567890"))
        assertFalse(PhoneValidator.isLandline("11987654321"))
    }

    @Test
    fun testUnmask() {
        assertEquals("11987654321", PhoneValidator.unmask("(11) 98765-4321"))
        assertEquals("1134567890", PhoneValidator.unmask("(11) 3456-7890"))
    }

    @Test
    fun testFormat() {
        assertEquals("(11) 98765-4321", PhoneValidator.format("11987654321"))
        assertEquals("(11) 3456-7890", PhoneValidator.format("1134567890"))
    }

    @Test
    fun testExtractDdd() {
        assertEquals(11, PhoneValidator.extractDdd("11987654321"))
        assertEquals(21, PhoneValidator.extractDdd("(21) 98765-4321"))
    }

    @Test
    fun testValidateReturnsNull() {
        assertNull(PhoneValidator.validate("11987654321"))
        assertNull(PhoneValidator.validate("(11) 98765-4321"))
        assertNull(PhoneValidator.validate("1134567890"))
    }

    @Test
    fun testValidateReturnsError() {
        assertEquals("Telefone é obrigatório", PhoneValidator.validate(""))
        assertEquals("Telefone deve ter 10 ou 11 dígitos", PhoneValidator.validate("123"))
        assertEquals("DDD inválido", PhoneValidator.validate("00987654321"))
        assertEquals("Celular deve começar com 9", PhoneValidator.validate("11887654321"))
    }
}
