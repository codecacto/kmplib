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

    // ====== 2.218.2: arredondamento em centavos e sinal ======

    @Test
    fun `formatAsCurrency arredonda para o centavo mais proximo em vez de truncar`() {
        // 1234567.89 * 100 = 123456788.99999… em ponto flutuante: truncar dava ",88".
        assertEquals("R$ 1.234.567,89", 1234567.89.formatAsCurrency())
        // 19.99 * 100 = 1998.9999999999998
        assertEquals("R$ 19,99", 19.99.formatAsCurrency())
        // 0.1 + 0.2 = 0.30000000000000004
        assertEquals("R$ 0,30", (0.1 + 0.2).formatAsCurrency())
        // meio centavo arredonda para longe do zero
        assertEquals("R$ 0,01", 0.005.formatAsCurrency())
        assertEquals("R$ 1,00", 0.999.formatAsCurrency())
        // 5.555 * 100 dá exatamente 555.5 em Double: meio centavo, sobe (truncar dava ",55")
        assertEquals("R$ 5,56", 5.555.formatAsCurrency())
    }

    @Test
    fun `formatAsCurrency poe o sinal de menos antes do prefixo`() {
        assertEquals("-R$ 1.000,00", (-1000.0).formatAsCurrency())
        assertEquals("-R$ 0,50", (-0.5).formatAsCurrency())
        assertEquals("-R$ 0,01", (-0.01).formatAsCurrency())
        assertEquals("-R$ 1.234.567,89", (-1234567.89).formatAsCurrency())
        assertEquals("-12,34", (-12.34).formatAsCurrency(prefix = ""))
    }

    @Test
    fun `formatAsCurrency nao desenha menos zero`() {
        assertEquals("R$ 0,00", (-0.0).formatAsCurrency())
        assertEquals("R$ 0,00", (-0.001).formatAsCurrency())
    }

    @Test
    fun `formatAsCurrency desenha zero para valor nao finito`() {
        assertEquals("R$ 0,00", Double.NaN.formatAsCurrency())
        assertEquals("R$ 0,00", Double.POSITIVE_INFINITY.formatAsCurrency())
    }

    @Test
    fun `formatAsCurrency agrupa milhares em varias ordens`() {
        assertEquals("R$ 999,99", 999.99.formatAsCurrency())
        assertEquals("R$ 1.000,00", 1000.0.formatAsCurrency())
        assertEquals("R$ 12.345,67", 12345.67.formatAsCurrency())
        assertEquals("R$ 123.456,78", 123456.78.formatAsCurrency())
        assertEquals("1.234,56", 1234.56.formatAsCurrency(prefix = ""))
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

    // ====== CurrencyVisualTransformation (2.208.0) ======

    private fun desenho(t: CurrencyVisualTransformation, digitos: String): String =
        t.filter(androidx.compose.ui.text.AnnotatedString(digitos)).text.text

    @Test
    fun `reais inteiros nao desenham virgula nem centavos`() {
        val t = CurrencyVisualTransformation(decimalPlaces = 0)
        assertEquals("R$ 35.000", desenho(t, "35000"))
        assertEquals("R$ 1.250.000", desenho(t, "1250000"))
        assertEquals("R$ 7", desenho(t, "007"))
    }

    @Test
    fun `vazio fica vazio quando showZeroWhenEmpty e false`() {
        val t = CurrencyVisualTransformation(decimalPlaces = 0, showZeroWhenEmpty = false)
        val resultado = t.filter(androidx.compose.ui.text.AnnotatedString(""))
        assertEquals("", resultado.text.text)
        // O cursor não pode apontar para além do texto desenhado.
        assertEquals(0, resultado.offsetMapping.originalToTransformed(0))
        assertEquals(0, resultado.offsetMapping.transformedToOriginal(0))
    }

    @Test
    fun `comportamento padrao continua desenhando zero com centavos`() {
        val t = CurrencyVisualTransformation()
        assertEquals("R$ 0,00", desenho(t, ""))
        assertEquals("R$ 1.234,56", desenho(t, "123456"))
    }

    @Test
    fun `mascara nao zera a parte inteira quando o campo passa de 19 digitos`() {
        val t = CurrencyVisualTransformation()
        assertEquals("R$ 12.345.678.901.234.567.890,12", desenho(t, "1234567890123456789012"))
        assertEquals("R$ 0,05", desenho(t, "0005"))
        val inteiros = CurrencyVisualTransformation(decimalPlaces = 0)
        assertEquals("R$ 12.345.678.901.234.567.890", desenho(inteiros, "12345678901234567890"))
    }
}
