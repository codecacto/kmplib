package br.com.codecacto.kmplib.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTextTest {

    @Test
    fun `dobra caixa e acento do portugues`() {
        assertEquals("jose", foldForSearch("José"))
        assertEquals("joao conceicao", foldForSearch("JOÃO Conceição"))
        assertEquals("aeiou aeiou aaoo", foldForSearch("áéíóú âêîôû ãàõö"))
        assertEquals("acucar", foldForSearch("Açúcar"))
    }

    @Test
    fun `dobra acento que tabela a mao esquece`() {
        // ő (húngaro), ů (tcheco), ș (romeno), ñ, ç — tudo pela NFD do sistema.
        assertEquals("o u s n c", foldForSearch("ő ů ș ñ ç"))
    }

    @Test
    fun `texto ja decomposto tambem dobra`() {
        // "é" escrito como e + U+0301 (combinante): é o que alguns teclados e colagens entregam.
        assertEquals("jose", foldForSearch("José"))
    }

    @Test
    fun `letras sem decomposicao seguem o unaccent do Postgres`() {
        assertEquals("strasse aeoe o l d th", foldForSearch("Straße ÆŒ Ø Ł Đ Þ"))
        assertEquals("i", foldForSearch("ı"))
        assertEquals("i", foldForSearch("İ"), "İ minúsculo vira i + ponto combinante, e o ponto sai")
    }

    @Test
    fun `colapsa e apara espacos sem mexer em pontuacao e digito`() {
        assertEquals("maria da silva", foldForSearch("  Maria \t da\n  Silva  "))
        assertEquals("(65) 99999-0000", foldForSearch("(65) 99999-0000"))
        assertEquals("", foldForSearch(""))
        assertEquals("", foldForSearch("   "))
    }

    @Test
    fun `searchTerms separa os termos ja dobrados`() {
        assertEquals(listOf("mar", "silva"), searchTerms("  Már   SILVA "))
        assertEquals(emptyList(), searchTerms("   "))
    }

    @Test
    fun `matchesSearch casa por pedaco e soma termos`() {
        assertTrue(matchesSearch("mar", "Maria"))
        assertTrue(matchesSearch("mar", "Marcos"))
        assertTrue(matchesSearch("jose", "José Antônio"))
        assertTrue(matchesSearch("ANTON jos", "José Antônio"))
        assertTrue(matchesSearch("9999", "Ana", "(65) 99999-0000"), "pedaço do telefone, em outro campo")
        assertFalse(matchesSearch("mar silva", "Maria Souza"), "o segundo termo refina")
        assertTrue(matchesSearch("", "qualquer"), "consulta em branco casa com tudo")
        assertFalse(matchesSearch("x", null), "campo nulo não casa")
    }
}
