package br.com.codecacto.kmplib.mask

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneMaskTest {

    @Test
    fun filterMantemApenasDigitos() {
        assertEquals("11987654321", filterPhoneInput("(11) 98765-4321"))
        assertEquals("1134567890", filterPhoneInput("(11) 3456-7890"))
    }

    @Test
    fun filterLimitaA11Digitos() {
        assertEquals("11987654321", filterPhoneInput("119876543219999"))
    }

    @Test
    fun filterStringVaziaRetornaVazio() {
        assertEquals("", filterPhoneInput(""))
        assertEquals("", filterPhoneInput("() -"))
    }

    @Test
    fun filterEntradaParcial() {
        assertEquals("11", filterPhoneInput("(11"))
        assertEquals("119", filterPhoneInput("(11) 9"))
    }
}
