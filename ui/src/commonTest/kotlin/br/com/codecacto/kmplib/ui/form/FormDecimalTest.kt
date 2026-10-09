package br.com.codecacto.kmplib.ui.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O decimal EXATO — onde `Double` erra e o servidor (`BigDecimal`) acerta. */
class FormDecimalTest {

    private fun d(text: String) = FormDecimal.parse(text)!!

    @Test
    fun `le o literal do JSON na forma canonica`() {
        assertEquals("72.5", d("72.50").toPlainString())
        assertEquals("7", d("7.0").toPlainString())
        assertEquals("1000", d("1e3").toPlainString())
        assertEquals("0.0000001", d("1e-7").toPlainString())
        assertEquals("1500000000000000000000", d("1.5E+21").toPlainString())
        assertEquals("-0.001", d("-0.001").toPlainString())
        assertEquals("0", d("-0").toPlainString())
        assertEquals("0", d("000").toPlainString())
    }

    @Test
    fun `recusa o que nao e numero`() {
        listOf("", "abc", "72,5", "1.", ".5", "NaN", "Infinity", "1e", "--1", "1e100000").forEach {
            assertNull(FormDecimal.parse(it), it)
        }
    }

    @Test
    fun `igualdade e ordem por valor`() {
        assertEquals(d("7"), d("7.0"))
        assertEquals(d("7").hashCode(), d("7.00").hashCode())
        assertTrue(d("4.9") < d("5"))
        assertTrue(d("-1") < d("-0.5"))
        assertTrue(d("300.55") > d("300"))
        assertTrue(d("0.1") > d("0.09999"))
        assertEquals(0, d("1e2").compareTo(d("100")))
        assertTrue(d("-100") < d("0"))
    }

    @Test
    fun `casas decimais significativas`() {
        assertEquals(1, d("72.50").decimalPlaces)
        assertEquals(0, d("100").decimalPlaces)
        assertEquals(7, d("1e-7").decimalPlaces)
        assertEquals(2, d("72.55").decimalPlaces)
    }

    @Test
    fun `passo exato - onde o resto do Double erra`() {
        assertTrue(isOnFormStep(d("0.3"), FormDecimal.ZERO, d("0.1")))
        assertTrue(isOnFormStep(d("7.5"), FormDecimal.ZERO, d("2.5")))
        assertFalse(isOnFormStep(d("6"), FormDecimal.ZERO, d("2.5")))
        assertTrue(isOnFormStep(d("21.5"), d("20"), d("0.5")))
        assertFalse(isOnFormStep(d("21.4"), d("20"), d("0.5")))
        assertTrue(isOnFormStep(d("-3"), d("-5"), d("2")))
        assertFalse(isOnFormStep(d("1"), FormDecimal.ZERO, FormDecimal.ZERO))
        assertTrue(isOnFormStep(d("999999999999999.5"), FormDecimal.ZERO, d("0.5")), "sem estouro de Long")
    }

    @Test
    fun `pontos da regua pela conta exata do servidor`() {
        fun scale(min: String, max: String, step: String? = null) = FormLikertScale(d(min), d(max), step?.let(::d))
        assertEquals(listOf("0", "1", "2", "3"), scale("0", "3").points().map { it.toPlainString() })
        assertEquals(listOf("0", "0.1", "0.2", "0.3"), scale("0", "0.3", "0.1").points().map { it.toPlainString() })
        assertEquals(listOf("-2", "-1", "0", "1", "2"), scale("-2", "2").points().map { it.toPlainString() })
        assertEquals(6, scale("0", "10", "2").points().size)
        assertTrue(scale("0", "1", "0.3").points().isEmpty(), "passo que não fecha em max")
        assertTrue(scale("3", "3").points().isEmpty(), "um ponto só não é escolha")
        assertTrue(scale("3", "1").points().isEmpty(), "limites invertidos")
        assertTrue(scale("0", "1", "0").points().isEmpty(), "passo zero")
        assertEquals(101, scale("0", "100").points().size, "o teto é 101 pontos")
        assertTrue(scale("0", "101").points().isEmpty(), "102 pontos passa do teto")
    }

    @Test
    fun `de Long e de Double pelo texto decimal`() {
        assertEquals("-9223372036854775808", FormDecimal.of(Long.MIN_VALUE).toPlainString())
        assertEquals("72.5", FormDecimal.of(72.5)!!.toPlainString())
        assertNull(FormDecimal.of(Double.NaN))
        assertEquals(72.5, d("72.50").toDouble())
    }
}
