package br.com.codecacto.kmplib.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyFormattersTest {

    @Test
    fun formatCurrencyBRL_basic() {
        assertEquals("R$ 1.234,56", formatCurrencyBRL(1234.56))
    }

    @Test
    fun formatCurrencyBRL_zero() {
        assertEquals("R$ 0,00", formatCurrencyBRL(0.0))
    }

    @Test
    fun formatCurrencyBRL_negative() {
        assertEquals("-R$ 1.234,56", formatCurrencyBRL(-1234.56))
    }

    @Test
    fun formatCurrencyBRL_large_value() {
        assertEquals("R$ 1.000.000,00", formatCurrencyBRL(1_000_000.0))
    }

    @Test
    fun formatCurrencyUSD_basic() {
        assertEquals("$1,234.56", formatCurrencyUSD(1234.56))
    }

    @Test
    fun formatCurrencyUSD_zero() {
        assertEquals("$0.00", formatCurrencyUSD(0.0))
    }

    @Test
    fun formatCurrencyUSD_negative() {
        assertEquals("-$1,234.56", formatCurrencyUSD(-1234.56))
    }

    @Test
    fun formatCurrencyUSD_large_value() {
        assertEquals("$1,000,000.00", formatCurrencyUSD(1_000_000.0))
    }

    @Test
    fun formatCurrencyEUR_basic() {
        assertEquals("1.234,56 €", formatCurrencyEUR(1234.56))
    }

    @Test
    fun formatCurrencyEUR_zero() {
        assertEquals("0,00 €", formatCurrencyEUR(0.0))
    }

    @Test
    fun formatCurrencyEUR_negative() {
        assertEquals("-1.234,56 €", formatCurrencyEUR(-1234.56))
    }

    @Test
    fun formatCurrency_custom_locale() {
        assertEquals(
            "CHF 1'234.56",
            formatCurrency(1234.56, thousandsSeparator = "'", decimalSeparator = ".", prefix = "CHF "),
        )
    }
}
