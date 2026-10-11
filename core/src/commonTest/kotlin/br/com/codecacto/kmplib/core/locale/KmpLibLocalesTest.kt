package br.com.codecacto.kmplib.core.locale

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpLibLocalesTest {

    private val soBr = listOf(FactoryLocales.PT_BR)

    @AfterTest
    fun limpar() = KmpLibLocales.reset()

    @Test
    fun `default sao os quatro da fabrica e app GLOBAL nao muda nada`() {
        assertEquals(FactoryLocales.ALL, KmpLibLocales.supported)
        listOf("en-US", "es-MX", "pt-PT", "pt-BR", "fr-FR", null).forEach { tag ->
            assertFalse(KmpLibLocales.shouldUseBaseTexts(tag), "GLOBAL não usa a base para $tag")
        }
    }

    @Test
    fun `app so pt-BR em aparelho em ingles espanhol ou pt-PT usa a base pt-BR`() {
        assertTrue(KmpLibLocales.shouldUseBaseTexts("en-US", soBr))
        assertTrue(KmpLibLocales.shouldUseBaseTexts("en-GB", soBr))
        assertTrue(KmpLibLocales.shouldUseBaseTexts("es-419", soBr))
        assertTrue(KmpLibLocales.shouldUseBaseTexts("pt-PT", soBr))
    }

    @Test
    fun `aparelho em idioma que a lib nao traduz ja esta na base e nao precisa`() {
        assertFalse(KmpLibLocales.shouldUseBaseTexts("fr-FR", soBr))
        assertFalse(KmpLibLocales.shouldUseBaseTexts("pt-AO", soBr))
        assertFalse(KmpLibLocales.shouldUseBaseTexts("pt-BR", soBr))
        assertFalse(KmpLibLocales.shouldUseBaseTexts(null, soBr))
    }

    @Test
    fun `app com pt-BR e en acompanha o ingles e cai na base so no espanhol`() {
        val brEn = listOf(FactoryLocales.PT_BR, FactoryLocales.EN)
        assertFalse(KmpLibLocales.shouldUseBaseTexts("en-US", brEn))
        assertTrue(KmpLibLocales.shouldUseBaseTexts("es-ES", brEn))
        assertTrue(KmpLibLocales.shouldUseBaseTexts("pt-PT", brEn))
    }

    @Test
    fun `idioma do app que a lib nao traduz nao liga a base`() {
        // App em pt-BR + fr num aparelho francês: o app mostra fr, a lib já cai em pt-BR — nada a fazer.
        assertFalse(KmpLibLocales.shouldUseBaseTexts("fr-FR", listOf(FactoryLocales.PT_BR, "fr")))
    }

    @Test
    fun `configure grava normaliza e o appLanguageTag passa a seguir a declaracao`() {
        KmpLibLocales.configure(soBr)
        assertEquals(soBr, KmpLibLocales.supported)
        // Seja qual for o idioma da JVM de teste, um app só pt-BR mostra pt-BR — e é isso que vai ao servidor.
        assertEquals(FactoryLocales.PT_BR, appLanguageTag())
        KmpLibLocales.reset()
        assertEquals(FactoryLocales.ALL, KmpLibLocales.supported)
    }

    @Test
    fun `normalize poe a base pt-BR na frente tira repeticao e aceita sublinhado`() {
        assertEquals(listOf("pt-BR", "en"), KmpLibLocales.normalize(listOf("en")))
        assertEquals(listOf("en", "pt-BR"), KmpLibLocales.normalize(listOf(" en ", "pt_BR", "en")))
        assertEquals(listOf("pt-BR"), KmpLibLocales.normalize(emptyList()))
    }

    @Test
    fun `tag invalida e recusada na hora da declaracao`() {
        assertFailsWith<IllegalArgumentException> { KmpLibLocales.configure(listOf("")) }
        assertFailsWith<IllegalArgumentException> { KmpLibLocales.configure(listOf("1234")) }
        assertEquals(FactoryLocales.ALL, KmpLibLocales.supported)
    }

    @Test
    fun `idioma da lib e sempre um dos quatro`() {
        assertEquals("en", KmpLibLocales.libraryLanguageTag("en-AU"))
        assertEquals("pt-BR", KmpLibLocales.libraryLanguageTag("de-DE"))
        assertTrue(KmpLibLocales.libraryLanguageTag() in FactoryLocales.ALL)
    }
}
