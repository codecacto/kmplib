package br.com.codecacto.kmplib.mask

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyMaskTest {

    // ====== String.currencyToDouble ======

    @Test
    fun `currencyToDouble converte digitos em centavos`() {
        assertEquals(0.01, "1".currencyToDouble())
        assertEquals(1.23, "123".currencyToDouble())
        assertEquals(123.45, "12345".currencyToDouble())
        assertEquals(1234.56, "123456".currencyToDouble())
    }

    @Test
    fun `currencyToDouble remove caracteres nao numericos antes de converter`() {
        assertEquals(123.45, "R$ 123,45".currencyToDouble())
        assertEquals(1234.56, "1.234,56".currencyToDouble())
    }

    @Test
    fun `currencyToDouble retorna zero quando string vazia`() {
        assertEquals(0.0, "".currencyToDouble())
        assertEquals(0.0, "abc".currencyToDouble())
    }

    @Test
    fun `currencyToDouble aceita zero`() {
        assertEquals(0.0, "0".currencyToDouble())
        assertEquals(0.0, "00".currencyToDouble())
    }

    // ====== Double.formatAsCurrency ======

    @Test
    fun `formatAsCurrency formata valores simples`() {
        assertEquals("R$ 0,00", 0.0.formatAsCurrency())
        assertEquals("R$ 1,23", 1.23.formatAsCurrency())
        assertEquals("R$ 123,45", 123.45.formatAsCurrency())
    }

    @Test
    fun `formatAsCurrency adiciona separador de milhar`() {
        assertEquals("R$ 1.234,56", 1234.56.formatAsCurrency())
        assertEquals("R$ 1.000.000,00", 1_000_000.0.formatAsCurrency())
    }

    @Test
    fun `formatAsCurrency aceita prefixo customizado`() {
        assertEquals("$ 12,34", 12.34.formatAsCurrency(prefix = "$ "))
        assertEquals("12,34", 12.34.formatAsCurrency(prefix = ""))
    }

    @Test
    fun `formatAsCurrency preserva duas casas decimais`() {
        assertEquals("R$ 5,00", 5.0.formatAsCurrency())
        assertEquals("R$ 5,50", 5.5.formatAsCurrency())
        assertEquals("R$ 5,05", 5.05.formatAsCurrency())
    }

    @Test
    fun `formatAsCurrency arredonda valores excedentes via truncamento`() {
        // 5.555 → (5.555 * 100).toLong() = 555 → "R$ 5,55"
        // Implementação trunca via toLong()
        assertEquals("R$ 5,55", 5.555.formatAsCurrency())
    }

    @Test
    fun `roundtrip currencyToDouble e formatAsCurrency`() {
        val originals = listOf(0.0, 0.01, 1.0, 1.23, 1234.56, 1_000_000.0)
        for (value in originals) {
            val formatted = value.formatAsCurrency()
            val parsed = formatted.currencyToDouble()
            assertEquals(value, parsed, "roundtrip falhou para $value (formatted=$formatted)")
        }
    }
}
