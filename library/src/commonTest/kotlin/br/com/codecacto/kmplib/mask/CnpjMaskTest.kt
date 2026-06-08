package br.com.codecacto.kmplib.mask

import kotlin.test.Test
import kotlin.test.assertEquals

class CnpjMaskTest {

    @Test
    fun filterMantemAlfanumericoEmMaiusculas() {
        assertEquals("11222333000181", filterCnpjInput("11.222.333/0001-81"))
        assertEquals("ABCDEFGH000100", filterCnpjInput("ab.cde.fgh/0001-00"))
    }

    @Test
    fun filterLimitaA14Caracteres() {
        assertEquals("11222333000181", filterCnpjInput("11222333000181999"))
    }

    @Test
    fun filterRemovePontuacao() {
        assertEquals("11222333000181", filterCnpjInput("11/222.333-0001 81"))
    }

    @Test
    fun filterStringVaziaRetornaVazio() {
        assertEquals("", filterCnpjInput(""))
        assertEquals("", filterCnpjInput("./- "))
    }

    @Test
    fun filterEntradaParcial() {
        assertEquals("11", filterCnpjInput("11"))
        assertEquals("11222", filterCnpjInput("11.222"))
    }
}
