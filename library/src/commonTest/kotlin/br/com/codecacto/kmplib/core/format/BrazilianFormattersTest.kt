package br.com.codecacto.kmplib.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrazilianFormattersTest {

    @Test
    fun formatCurrencyBRLFormatsPositiveZeroAndNegativeValues() {
        assertEquals("R$ 1.234,56", formatCurrencyBRL(1234.56))
        assertEquals("R$ 0,00", formatCurrencyBRL(0.0))
        assertEquals("-R$ 987,65", formatCurrencyBRL(-987.65))
    }

    @Test
    fun formatCurrencyBRLRoundsCents() {
        assertEquals("R$ 10,01", formatCurrencyBRL(10.005))
        assertEquals("-R$ 10,01", formatCurrencyBRL(-10.005))
    }

    @Test
    fun formatCpfDelegatesToCpfValidator() {
        assertEquals("529.982.247-25", formatCpf("52998224725"))
        assertEquals("123", formatCpf("123"))
    }

    @Test
    fun formatCnpjDelegatesToCnpjValidator() {
        assertEquals("11.222.333/0001-81", formatCnpj("11222333000181"))
        assertEquals("AB.CDE.FGH/0001-00", formatCnpj("ABCDEFGH000100"))
        assertEquals("123", formatCnpj("123"))
    }

    @Test
    fun validatorsDelegateToExistingCpfAndCnpjValidators() {
        assertTrue(isValidCpf("529.982.247-25"))
        assertFalse(isValidCpf("111.111.111-11"))
        assertTrue(isValidCnpj("11.222.333/0001-81"))
        assertFalse(isValidCnpj("11.222.333/0001-82"))
    }

    @Test
    fun initialsOfHandlesEmptySingleAndMultipleNames() {
        assertEquals("?", initialsOf(""))
        assertEquals("M", initialsOf("Maria"))
        assertEquals("MS", initialsOf("Maria Silva"))
        assertEquals("MS", initialsOf("  Maria   da Silva  "))
    }

    @Test
    fun formatMonthYearCoercesInvalidMonths() {
        assertEquals("Janeiro 2026", formatMonthYear(1, 2026))
        assertEquals("Janeiro 2026", formatMonthYear(0, 2026))
        assertEquals("Dezembro 2026", formatMonthYear(13, 2026))
    }
}
