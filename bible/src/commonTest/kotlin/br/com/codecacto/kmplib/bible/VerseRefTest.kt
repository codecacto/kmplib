package br.com.codecacto.kmplib.bible

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerseRefTest {

    @Test
    fun `chave canonica ida e volta`() {
        val ref = VerseRef(BibleBook.JOHN, 3, 16)
        assertEquals("JHN.3.16", ref.key)
        assertEquals(ref, VerseRef.fromKey("JHN.3.16"))
        assertEquals("LAM.3.22-23", VerseRef(BibleBook.LAMENTATIONS, 3, 22, 23).key)
        assertEquals(VerseRef(BibleBook.LAMENTATIONS, 3, 22, 23), VerseRef.fromKey("LAM.3.22-23"))
        assertEquals("PSA.23", VerseRef(BibleBook.PSALMS, 23).key)
        assertEquals(VerseRef(BibleBook.PSALMS, 23), VerseRef.fromKey("psa.23"))
        assertEquals(VerseRef(BibleBook.CORINTHIANS_1, 13, 4, 7), VerseRef.fromKey("1CO.13.4-7"))
        // Intervalo de um só = versículo único.
        assertEquals("JHN.3.16", VerseRef.fromKey("JHN.3.16-16")!!.key)
    }

    @Test
    fun `chave malformada vira null`() {
        listOf("", "JHN", "JHN.0.1", "JHN.3.0", "JHN.3.5-2", "XXX.1.1", "JHN.3.16.2", "JHN 3:16").forEach {
            assertNull(VerseRef.fromKey(it), it)
        }
    }

    @Test
    fun `construtor recusa intervalo invertido`() {
        assertFailsWith<IllegalArgumentException> { VerseRef(BibleBook.JOHN, 3, 5, 2) }
        assertFailsWith<IllegalArgumentException> { VerseRef(BibleBook.JOHN, 0) }
    }

    @Test
    fun `formata por idioma`() {
        val r = VerseRef(BibleBook.JOHN, 3, 16)
        assertEquals("João 3:16", r.format(BibleLanguage.PT_BR))
        assertEquals("John 3:16", r.format(BibleLanguage.EN))
        assertEquals("Juan 3:16", r.format(BibleLanguage.ES))
        assertEquals("Jo 3:16", r.format(BibleLanguage.PT_BR, abbreviated = true))
        assertEquals("Lamentações 3:22-23", VerseRef(BibleBook.LAMENTATIONS, 3, 22, 23).format(BibleLanguage.PT_BR))
        assertEquals("Salmos 23", VerseRef(BibleBook.PSALMS, 23).format(BibleLanguage.PT_BR))
    }

    @Test
    fun `contem e ordena`() {
        val range = VerseRef(BibleBook.CORINTHIANS_1, 13, 4, 7)
        assertTrue(VerseRef(BibleBook.CORINTHIANS_1, 13, 5) in range)
        assertFalse(VerseRef(BibleBook.CORINTHIANS_1, 13, 8) in range)
        assertTrue(VerseRef(BibleBook.CORINTHIANS_1, 13, 99) in range.wholeChapter())
        assertEquals(4, range.verses().size)
        val sorted = listOf(VerseRef(BibleBook.JOHN, 3, 16), VerseRef(BibleBook.GENESIS, 1, 1), VerseRef(BibleBook.JOHN, 1, 1)).sorted()
        assertEquals(listOf("GEN.1.1", "JHN.1.1", "JHN.3.16"), sorted.map { it.key })
    }
}
