package br.com.codecacto.kmplib.ads.custom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdLanguageTest {

    private val global = setOf("pt-BR", "pt-PT", "en", "es")

    @Test
    fun `aparelho casa com o idioma declarado`() {
        assertEquals("en", resolveAdLanguage("en-US", global))
        assertEquals("en", resolveAdLanguage("en-GB", global))
        assertEquals("es", resolveAdLanguage("es-MX", global))
        assertEquals("es", resolveAdLanguage("es-419", global))
        assertEquals("pt-PT", resolveAdLanguage("pt-PT", global))
        assertEquals("pt-PT", resolveAdLanguage("pt_PT", global))
        assertEquals("pt-BR", resolveAdLanguage("pt-BR", global))
        assertEquals("pt-BR", resolveAdLanguage("pt", global))
    }

    @Test
    fun `app so em portugues num aparelho em ingles fica em pt-BR`() {
        assertEquals("pt-BR", resolveAdLanguage("en-US", CustomAdConfig.DEFAULT_SUPPORTED_LANGUAGES))
        assertEquals("pt-BR", resolveAdLanguage("es-MX", setOf("pt-BR", "en")))
        assertEquals("pt-BR", resolveAdLanguage("pt-PT", setOf("pt-BR", "en")))
    }

    @Test
    fun `idioma fora da fabrica e tag invalida ficam em pt-BR`() {
        assertEquals("pt-BR", resolveAdLanguage("fr-FR", global))
        assertEquals("pt-BR", resolveAdLanguage("pt-AO", global))
        assertEquals("pt-BR", resolveAdLanguage("", global))
        assertEquals("pt-BR", resolveAdLanguage(null, global))
    }

    @Test
    fun `lista declarada com regiao vale pelo idioma`() {
        assertEquals("en", resolveAdLanguage("en-GB", setOf("pt-BR", "en-US")))
        assertEquals("es", resolveAdLanguage("es-AR", setOf("es-ES")))
    }

    @Test
    fun `lista vazia nao quebra e fica em pt-BR`() {
        assertEquals("pt-BR", resolveAdLanguage("en-US", emptySet()))
    }

    @Test
    fun `pt-BR nao vai na query e os demais vao como estao`() {
        assertNull(adLanguageQueryValue("pt-BR"))
        assertEquals("en", adLanguageQueryValue("en"))
        assertEquals("es", adLanguageQueryValue("es"))
        assertEquals("pt-PT", adLanguageQueryValue("pt-PT"))
    }
}
