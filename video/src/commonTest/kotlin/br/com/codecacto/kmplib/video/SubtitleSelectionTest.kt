package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleSelectionTest {

    private fun embutida(id: String, label: String, lang: String) =
        VideoSubtitleOption(id = id, label = label, language = lang, embedded = true)

    private fun externa(id: String, label: String, lang: String) =
        VideoSubtitleOption(id = id, label = label, language = lang, embedded = false)

    @Test
    fun `embutidas na frente - externas depois`() {
        val juntas = mergeSubtitleOptions(
            embedded = listOf(embutida("text-0", "Português", "pt-BR")),
            external = listOf(externa("u1", "English", "en")),
        )
        assertEquals(listOf("Português", "English"), juntas.map { it.label })
    }

    @Test
    fun `mesma lingua nas duas origens deixa so a embutida`() {
        // Duas opções com o mesmo rótulo fariam o aluno escolher no escuro entre a sincronizada
        // pela plataforma e a que depende do nosso relógio.
        val juntas = mergeSubtitleOptions(
            embedded = listOf(embutida("text-0", "Português", "pt-BR")),
            external = listOf(externa("u1", "Português", "pt")),
        )
        assertEquals(1, juntas.size)
        assertEquals(true, juntas.single().embedded)
    }

    @Test
    fun `sem faixa embutida as externas passam todas`() {
        val juntas = mergeSubtitleOptions(
            embedded = emptyList(),
            external = listOf(externa("u1", "Português", "pt-BR"), externa("u2", "English", "en")),
        )
        assertEquals(2, juntas.size)
    }

    @Test
    fun `preferencia casa a regiao exata antes da lingua`() {
        val opcoes = listOf(
            embutida("text-0", "Português (Portugal)", "pt-PT"),
            embutida("text-1", "Português (Brasil)", "pt-BR"),
        )
        assertEquals("text-1", preferredSubtitleOf(opcoes, "pt-BR")?.id)
    }

    @Test
    fun `preferencia so por lingua aceita qualquer regiao`() {
        val opcoes = listOf(embutida("text-0", "Português (Portugal)", "pt-PT"))
        assertEquals("text-0", preferredSubtitleOf(opcoes, "pt")?.id)
        assertEquals("text-0", preferredSubtitleOf(opcoes, "pt-BR")?.id)
    }

    @Test
    fun `sem preferencia o video comeca sem legenda`() {
        val opcoes = listOf(embutida("text-0", "Português", "pt-BR"))
        assertNull(preferredSubtitleOf(opcoes, null))
        assertNull(preferredSubtitleOf(opcoes, ""))
    }

    @Test
    fun `preferencia sem faixa correspondente nao liga nada`() {
        val opcoes = listOf(embutida("text-0", "English", "en"))
        assertNull(preferredSubtitleOf(opcoes, "pt-BR"))
    }

    @Test
    fun `caixa da etiqueta de idioma nao importa`() {
        val opcoes = listOf(embutida("text-0", "Português", "PT-br"))
        assertEquals("text-0", preferredSubtitleOf(opcoes, "pt-BR")?.id)
    }
}
