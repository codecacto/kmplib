package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CpfValidatorTest {

    @Test
    fun testValidCpfs() {
        // CPFs válidos conhecidos
        assertTrue(CpfValidator.isValid("52998224725"))
        assertTrue(CpfValidator.isValid("529.982.247-25"))
        assertTrue(CpfValidator.isValid("11144477735"))
        assertTrue(CpfValidator.isValid("111.444.777-35"))
    }

    @Test
    fun testInvalidCpfs() {
        // CPF com todos dígitos iguais
        assertFalse(CpfValidator.isValid("11111111111"))
        assertFalse(CpfValidator.isValid("00000000000"))
        assertFalse(CpfValidator.isValid("99999999999"))

        // CPF com dígito verificador errado
        assertFalse(CpfValidator.isValid("52998224726"))
        assertFalse(CpfValidator.isValid("11144477736"))

        // CPF com tamanho errado
        assertFalse(CpfValidator.isValid("123456789"))
        assertFalse(CpfValidator.isValid("123456789012"))
        assertFalse(CpfValidator.isValid(""))
    }

    @Test
    fun testUnmask() {
        assertEquals("52998224725", CpfValidator.unmask("529.982.247-25"))
        assertEquals("11144477735", CpfValidator.unmask("111.444.777-35"))
        assertEquals("12345678901", CpfValidator.unmask("12345678901"))
    }

    @Test
    fun testFormat() {
        assertEquals("529.982.247-25", CpfValidator.format("52998224725"))
        assertEquals("111.444.777-35", CpfValidator.format("11144477735"))
        // Retorna original se tamanho inválido
        assertEquals("123", CpfValidator.format("123"))
    }

    @Test
    fun testValidateReturnsNull() {
        assertNull(CpfValidator.validate("52998224725"))
        assertNull(CpfValidator.validate("529.982.247-25"))
    }

    @Test
    fun testValidateReturnsError() {
        assertEquals("CPF é obrigatório", CpfValidator.validate(""))
        assertEquals("CPF deve ter 11 dígitos", CpfValidator.validate("123"))
        assertEquals("CPF inválido", CpfValidator.validate("11111111111"))
        assertEquals("CPF inválido", CpfValidator.validate("12345678900"))
    }
}
