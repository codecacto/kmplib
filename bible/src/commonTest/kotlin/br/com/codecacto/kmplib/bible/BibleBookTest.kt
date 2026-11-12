package br.com.codecacto.kmplib.bible

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleBookTest {

    @Test
    fun `66 livros com usfm unico ordem continua e 1189 capitulos`() {
        assertEquals(66, BibleBook.entries.size)
        assertEquals(66, BibleBook.entries.map { it.usfm }.toSet().size)
        assertEquals((1..66).toList(), BibleBook.entries.map { it.order })
        assertEquals(1189, BibleBook.entries.sumOf { it.chapters })
        assertEquals(39, BibleBook.entries.count { it.testament == Testament.OLD })
        assertEquals("GEN", BibleBook.entries.first().usfm)
        assertEquals("REV", BibleBook.entries.last().usfm)
    }

    @Test
    fun `nomes e abreviacoes completos nos quatro idiomas`() {
        BibleLanguage.entries.forEach { lang ->
            val names = BibleBookNames.of(lang)
            assertEquals(66, names.size, "faltou livro em $lang")
            names.values.forEach { assertTrue(it.name.isNotBlank() && it.abbreviation.isNotBlank()) }
        }
        assertEquals("João", BibleBook.JOHN.name(BibleLanguage.PT_BR))
        assertEquals("Juan", BibleBook.JOHN.name(BibleLanguage.ES))
        assertEquals("Génesis", BibleBook.GENESIS.name(BibleLanguage.PT_PT))
        assertEquals("1Co", BibleBook.CORINTHIANS_1.abbreviation(BibleLanguage.PT_BR))
    }

    @Test
    fun `fromUsfm ignora caixa`() {
        assertEquals(BibleBook.JOHN_1, BibleBook.fromUsfm("1jn"))
        assertNull(BibleBook.fromUsfm("XYZ"))
    }

    @Test
    fun `idioma pela etiqueta`() {
        assertEquals(BibleLanguage.PT_PT, BibleLanguage.fromTag("pt_PT"))
        assertEquals(BibleLanguage.PT_BR, BibleLanguage.fromTag("pt-AO"))
        assertEquals(BibleLanguage.EN, BibleLanguage.fromTag("en-US"))
        assertEquals(BibleLanguage.ES, BibleLanguage.fromTag("es-419"))
        assertEquals(BibleLanguage.EN, BibleLanguage.fromTag("fr-FR", default = BibleLanguage.EN))
    }
}
