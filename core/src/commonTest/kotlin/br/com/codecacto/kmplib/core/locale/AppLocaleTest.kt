package br.com.codecacto.kmplib.core.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLocaleTest {

    @Test
    fun `os quatro idiomas da fabrica casam consigo mesmos`() {
        FactoryLocales.ALL.forEach { assertEquals(it, FactoryLocales.match(it)) }
    }

    @Test
    fun `idioma com regiao cai na pasta do idioma sem regiao`() {
        assertEquals("en", FactoryLocales.match("en-US"))
        assertEquals("en", FactoryLocales.match("en-GB"))
        assertEquals("es", FactoryLocales.match("es-MX"))
        assertEquals("es", FactoryLocales.match("es-419"))
    }

    @Test
    fun `portugues de Portugal so com a regiao PT, o resto do portugues ve pt-BR`() {
        assertEquals("pt-PT", FactoryLocales.match("pt-PT"))
        assertEquals("pt-PT", FactoryLocales.match("pt_PT"))
        assertEquals("pt-BR", FactoryLocales.match("pt-BR"))
        // Como o compose-resources: values-pt-rPT não serve pt-AO, e não há values-pt → values.
        assertEquals("pt-BR", FactoryLocales.match("pt-AO"))
        assertEquals("pt-BR", FactoryLocales.match("pt"))
    }

    @Test
    fun `idioma sem traducao ve o fallback, que e o que a tela mostra`() {
        assertEquals("pt-BR", FactoryLocales.match("fr-FR"))
        assertEquals("pt-BR", FactoryLocales.match("de"))
        assertEquals("pt-BR", FactoryLocales.match(null))
        assertEquals("pt-BR", FactoryLocales.match(""))
        assertEquals("en", FactoryLocales.match("fr-FR", fallback = "en"))
    }

    @Test
    fun `lista propria do app vale no lugar dos quatro`() {
        val suportados = listOf("pt-BR", "en", "fr")
        assertEquals("fr", FactoryLocales.match("fr-CA", suportados))
        assertEquals("pt-BR", FactoryLocales.match("es-ES", suportados))
    }

    @Test
    fun `split aceita sublinhado, pula script e reconhece regiao numerica`() {
        assertEquals("pt" to "BR", splitLanguageTag("pt_BR"))
        assertEquals("zh" to "TW", splitLanguageTag("zh-Hant-TW"))
        assertEquals("es" to "419", splitLanguageTag("es-419"))
        assertEquals("en" to null, splitLanguageTag("EN"))
        assertNull(splitLanguageTag("  "))
        assertNull(splitLanguageTag("1234"))
    }

    @Test
    fun `idioma do aparelho e tag valida e o da tela esta entre os quatro`() {
        assertTrue(splitLanguageTag(deviceLanguageTag()) != null)
        assertTrue(deviceLanguage().length in 2..3)
        assertTrue(appLanguageTag() in FactoryLocales.ALL)
    }
}
