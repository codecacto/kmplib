package br.com.codecacto.kmplib.core.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountriesTest {

    @Test
    fun `codigo ISO so com duas letras maiusculas`() {
        assertTrue(isIsoCountryCode("BR"))
        assertFalse(isIsoCountryCode("br"))
        assertFalse(isIsoCountryCode("BRA"))
        assertFalse(isIsoCountryCode("41"))
    }

    @Test
    fun `bandeira sai dos indicadores regionais`() {
        assertEquals("🇧🇷", countryFlagEmoji("BR"))
        assertEquals("🇵🇹", countryFlagEmoji("pt"))
        assertEquals("", countryFlagEmoji("419"))
        assertEquals(countryFlagEmoji("US"), Country("US", "x").flag)
    }

    @Test
    fun `busca por pedaco ignora acento e caixa e soma os termos`() {
        val lista = listOf(Country("AT", "Áustria"), Country("AU", "Austrália"), Country("BR", "Brasil"))
        assertEquals(listOf("AT", "AU"), Countries.search("aus", lista).map { it.code })
        assertEquals(listOf("AT"), Countries.search("AUSTRIA", lista).map { it.code })
        assertEquals(listOf("BR"), Countries.search("br", lista).map { it.code })
        assertEquals(lista, Countries.search("  ", lista))
        assertEquals(emptyList(), Countries.search("aus bra", lista))
    }

    @Test
    fun `lista da plataforma traz os paises com nome e sem codigo invalido`() {
        val todos = Countries.all("pt-BR")
        assertTrue(todos.size > 200, "só ${todos.size} países")
        assertTrue(todos.all { isIsoCountryCode(it.code) && it.name.isNotBlank() })
        assertTrue(todos.any { it.code == "BR" })
        assertEquals(todos.size, todos.map { it.code }.toSet().size)
    }

    @Test
    fun `nome de pais conhecido, null para codigo que nao e pais`() {
        assertTrue(!Countries.name("PT", "pt-BR").isNullOrBlank())
        assertEquals(Countries.name("PT", "en"), Countries.name("pt", "en"))
        assertNull(Countries.name("ZZZ"))
        assertNull(Countries.name("1A"))
    }
}
