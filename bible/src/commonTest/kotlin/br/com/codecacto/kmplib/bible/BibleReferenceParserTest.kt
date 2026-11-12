package br.com.codecacto.kmplib.bible

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BibleReferenceParserTest {

    private val pt = BibleReferenceParser(listOf(BibleLanguage.PT_BR))

    private fun key(p: BibleReferenceParser, s: String) = p.parse(s)?.key

    @Test
    fun `formas comuns em portugues`() {
        assertEquals("JHN.3.16", key(pt, "Jo 3:16"))
        assertEquals("JHN.3.16", key(pt, "João 3,16"))
        assertEquals("JHN.3.16", key(pt, "joao 3.16"))
        assertEquals("JHN.3.16", key(pt, "JOÃO 3 16"))
        assertEquals("1CO.13.4-7", key(pt, "1 Co 13:4-7"))
        assertEquals("1CO.13.4-7", key(pt, "1co 13:4–7"))
        assertEquals("1CO.13", key(pt, "1 Coríntios 13"))
        assertEquals("1CO.13", key(pt, "I Coríntios 13"))
        assertEquals("PSA.23", key(pt, "Sl 23"))
        assertEquals("PSA.23.1", key(pt, "salmo 23:1"))
        assertEquals("LAM.3.22-23", key(pt, "Lamentações 3:22-23"))
        assertEquals("ISA.41.10", key(pt, "Isaías 41:10"))
        assertEquals("ISA.41.10", key(pt, "isaias 41:10"))
        assertEquals("GEN.1.1", key(pt, "gen 1:1"))
        assertEquals("REV.21.4", key(pt, "apoc 21:4"))
        assertEquals("SNG.2.1", key(pt, "Cantares 2:1"))
        assertEquals("2TI.3.16", key(pt, "2 Tm 3.16"))
    }

    @Test
    fun `acento separa Jo de Jo`() {
        assertEquals("JOB.1.21", key(pt, "Jó 1:21"))
        assertEquals("JHN.1.21", key(pt, "Jo 1:21"))
    }

    @Test
    fun `ingles e espanhol`() {
        val en = BibleReferenceParser(listOf(BibleLanguage.EN))
        assertEquals("JHN.3.16", key(en, "John 3:16"))
        assertEquals("1JN.4.8", key(en, "1 John 4:8"))
        assertEquals("PSA.23.1", key(en, "Psalm 23:1"))
        assertEquals("SNG.2.1", key(en, "Song of Solomon 2:1"))
        assertEquals("PHP.4.13", key(en, "Phil 4:13"))
        val es = BibleReferenceParser(listOf(BibleLanguage.ES))
        assertEquals("JHN.3.16", key(es, "Juan 3:16"))
        assertEquals("ACT.2.38", key(es, "Hechos 2:38"))
        assertEquals("JAS.1.5", key(es, "Santiago 1:5"))
        // Idioma do app em inglês, referência digitada em português: atende igual.
        assertEquals("JHN.3.16", key(en, "João 3:16"))
    }

    @Test
    fun `entrada invalida vira null`() {
        listOf("", "3:16", "Xyz 3:16", "João", "João 0:1", "João 3:0", "João 3:5-2", "a 1").forEach {
            assertNull(pt.parse(it), it)
        }
    }

    @Test
    fun `normalizacao de busca igual a do gerador`() {
        assertEquals("porque deus amou ao mundo", normalizeBibleSearchText("Porque Deus amou ao mundo;"))
        assertEquals("unigenito nao pereca", normalizeBibleSearchText("unigênito, não pereça!"))
        assertEquals("d agua", normalizeBibleSearchText("d'água"))
        assertEquals("nino", normalizeBibleSearchText("Niño"))
        assertEquals(listOf("amor", "deus"), bibleSearchTerms("  Amor  DEUS amor "))
        assertEquals(emptyList(), bibleSearchTerms(" %_ "))
    }
}
