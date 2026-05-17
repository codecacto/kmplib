package br.com.codecacto.kmplib.core.format

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneFormattersTest {

    @Test
    fun formats_celular_11_digits() {
        assertEquals("(11) 99999-9999", formatPhone("11999999999"))
    }

    @Test
    fun formats_fixo_10_digits() {
        assertEquals("(11) 3333-3333", formatPhone("1133333333"))
    }

    @Test
    fun strips_non_digits_before_formatting() {
        assertEquals("(11) 99999-9999", formatPhone("(11) 99999-9999"))
        assertEquals("(11) 99999-9999", formatPhone("11-99999-9999"))
    }

    @Test
    fun returns_input_when_not_10_or_11_digits() {
        assertEquals("12345", formatPhone("12345"))
        assertEquals("", formatPhone(""))
    }
}
