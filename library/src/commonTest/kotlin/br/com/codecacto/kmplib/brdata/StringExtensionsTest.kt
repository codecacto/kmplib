package br.com.codecacto.kmplib.brdata

import kotlin.test.Test
import kotlin.test.assertEquals

class StringExtensionsTest {

    // ========================
    // removeAccents Tests
    // ========================

    @Test
    fun `removeAccents removes common Portuguese accents`() {
        assertEquals("aeiou", "áéíóú".removeAccents())
        assertEquals("AEIOU", "ÁÉÍÓÚ".removeAccents())
    }

    @Test
    fun `removeAccents handles circumflex accents`() {
        assertEquals("aeo", "âêô".removeAccents())
        assertEquals("AEO", "ÂÊÔ".removeAccents())
    }

    @Test
    fun `removeAccents handles tilde`() {
        assertEquals("ao", "ãõ".removeAccents())
        assertEquals("AO", "ÃÕ".removeAccents())
    }

    @Test
    fun `removeAccents handles cedilla`() {
        assertEquals("c", "ç".removeAccents())
        assertEquals("C", "Ç".removeAccents())
    }

    @Test
    fun `removeAccents handles grave accent`() {
        assertEquals("a", "à".removeAccents())
        assertEquals("A", "À".removeAccents())
    }

    @Test
    fun `removeAccents on empty string returns empty`() {
        assertEquals("", "".removeAccents())
    }

    @Test
    fun `removeAccents on string without accents returns same string`() {
        assertEquals("test", "test".removeAccents())
        assertEquals("Hello World", "Hello World".removeAccents())
        assertEquals("123456", "123456".removeAccents())
    }

    @Test
    fun `removeAccents handles mixed accented and non-accented text`() {
        assertEquals("Sao Paulo", "São Paulo".removeAccents())
        assertEquals("Goias", "Goiás".removeAccents())
        assertEquals("Brasil", "Brasil".removeAccents())
    }

    @Test
    fun `removeAccents handles full Brazilian state names`() {
        assertEquals("Parana", "Paraná".removeAccents())
        assertEquals("Ceara", "Ceará".removeAccents())
        assertEquals("Maranhao", "Maranhão".removeAccents())
        assertEquals("Espirito Santo", "Espírito Santo".removeAccents())
        assertEquals("Piaui", "Piauí".removeAccents())
        assertEquals("Rondonia", "Rondônia".removeAccents())
        assertEquals("Amapa", "Amapá".removeAccents())
    }

    @Test
    fun `removeAccents handles sentences with multiple accented words`() {
        val input = "A ação é necessária para começar"
        val expected = "A acao e necessaria para comecar"
        assertEquals(expected, input.removeAccents())
    }

    @Test
    fun `removeAccents preserves numbers and special characters`() {
        assertEquals("email@test.com", "email@test.com".removeAccents())
        assertEquals("R$ 100,00", "R$ 100,00".removeAccents())
        assertEquals("123-456-789-00", "123-456-789-00".removeAccents())
    }

    @Test
    fun `removeAccents handles spaces and punctuation`() {
        assertEquals("Ola, tudo bem?", "Olá, tudo bem?".removeAccents())
        assertEquals("Nao sei!", "Não sei!".removeAccents())
    }

    @Test
    fun `removeAccents is idempotent`() {
        val text = "São Paulo"
        val once = text.removeAccents()
        val twice = once.removeAccents()
        assertEquals(once, twice)
    }

    @Test
    fun `removeAccents handles all Portuguese vowel variations`() {
        // a variations
        assertEquals("a", "á".removeAccents())
        assertEquals("a", "à".removeAccents())
        assertEquals("a", "â".removeAccents())
        assertEquals("a", "ã".removeAccents())

        // e variations
        assertEquals("e", "é".removeAccents())
        assertEquals("e", "ê".removeAccents())

        // i variations
        assertEquals("i", "í".removeAccents())

        // o variations
        assertEquals("o", "ó".removeAccents())
        assertEquals("o", "ô".removeAccents())
        assertEquals("o", "õ".removeAccents())

        // u variations
        assertEquals("u", "ú".removeAccents())
        assertEquals("u", "ü".removeAccents())
    }

    @Test
    fun `removeAccents handles uppercase and lowercase consistently`() {
        assertEquals("Acao", "Ação".removeAccents())
        assertEquals("ACAO", "AÇÃO".removeAccents())
        assertEquals("acao", "ação".removeAccents())
    }

    @Test
    fun `removeAccents handles compound words`() {
        assertEquals("bem-vindo", "bem-vindo".removeAccents())
        assertEquals("Guarda-chuva", "Guarda-chuva".removeAccents())
        assertEquals("Parana-mirim", "Paraná-mirim".removeAccents())
    }

    @Test
    fun `removeAccents handles multiple spaces`() {
        assertEquals("Sao  Paulo", "São  Paulo".removeAccents())
        assertEquals("   test   ", "   test   ".removeAccents())
    }

    @Test
    fun `removeAccents handles common Brazilian words`() {
        assertEquals("acao", "ação".removeAccents())
        assertEquals("comeco", "começo".removeAccents())
        assertEquals("situacao", "situação".removeAccents())
        assertEquals("resolucao", "resolução".removeAccents())
        assertEquals("nao", "não".removeAccents())
        assertEquals("tambem", "também".removeAccents())
        assertEquals("voce", "você".removeAccents())
        assertEquals("ate", "até".removeAccents())
        assertEquals("cafe", "café".removeAccents())
        assertEquals("Joao", "João".removeAccents())
        assertEquals("Jose", "José".removeAccents())
        assertEquals("Maria", "Maria".removeAccents())
    }
}
