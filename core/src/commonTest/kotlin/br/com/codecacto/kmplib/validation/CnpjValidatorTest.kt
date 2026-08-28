package br.com.codecacto.kmplib.validation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CnpjValidatorTest {

    @Test
    fun testValidNumericCnpjs() {
        // CNPJs numéricos válidos
        assertTrue(CnpjValidator.isValid("11222333000181"))
        assertTrue(CnpjValidator.isValid("11.222.333/0001-81"))
    }

    @Test
    fun testInvalidCnpjs() {
        // CNPJ com todos dígitos iguais
        assertFalse(CnpjValidator.isValid("11111111111111"))
        assertFalse(CnpjValidator.isValid("00000000000000"))

        // CNPJ com dígito verificador errado
        assertFalse(CnpjValidator.isValid("11222333000182"))

        // CNPJ com tamanho errado
        assertFalse(CnpjValidator.isValid("1122233300018"))
        assertFalse(CnpjValidator.isValid("112223330001811"))
        assertFalse(CnpjValidator.isValid(""))
    }

    @Test
    fun testUnmask() {
        assertEquals("11222333000181", CnpjValidator.unmask("11.222.333/0001-81"))
        assertEquals("ABCDEFGH000100", CnpjValidator.unmask("AB.CDE.FGH/0001-00"))
    }

    @Test
    fun testFormat() {
        assertEquals("11.222.333/0001-81", CnpjValidator.format("11222333000181"))
        assertEquals("AB.CDE.FGH/0001-00", CnpjValidator.format("ABCDEFGH000100"))
        // Retorna original se tamanho inválido
        assertEquals("123", CnpjValidator.format("123"))
    }

    @Test
    fun testValidateReturnsNull() {
        assertNull(CnpjValidator.validate("11222333000181"))
        assertNull(CnpjValidator.validate("11.222.333/0001-81"))
    }

    @Test
    fun testValidateReturnsError() {
        assertEquals("CNPJ é obrigatório", CnpjValidator.validate(""))
        assertEquals("CNPJ deve ter 14 caracteres", CnpjValidator.validate("123"))
        assertEquals("CNPJ inválido", CnpjValidator.validate("11222333000182"))
    }
}
