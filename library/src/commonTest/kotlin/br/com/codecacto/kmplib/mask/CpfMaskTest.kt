package br.com.codecacto.kmplib.mask

import kotlin.test.Test
import kotlin.test.assertEquals

class CpfMaskTest {

    @Test
    fun filterMantemApenasDigitos() {
        assertEquals("12345678901", filterCpfInput("123.456.789-01"))
        assertEquals("12345678901", filterCpfInput("123abc456def789-01"))
    }

    @Test
    fun filterLimitaA11Digitos() {
        assertEquals("12345678901", filterCpfInput("123456789012345"))
    }

    @Test
    fun filterStringVaziaRetornaVazio() {
        assertEquals("", filterCpfInput(""))
        assertEquals("", filterCpfInput("abc.-/"))
    }

    @Test
    fun filterEntradaParcial() {
        assertEquals("123", filterCpfInput("123"))
        assertEquals("123456", filterCpfInput("123.456"))
    }
}
