package br.com.codecacto.kmplib.help

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpRichTextTest {

    private fun AnnotatedString.boldParts(): List<String> =
        spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.substring(it.start, it.end) }

    @Test
    fun `trecho entre dois asteriscos vira negrito e perde a marca`() {
        val result = helpBoldText("Toque em **Cancelar**.")
        assertEquals("Toque em Cancelar.", result.text)
        assertEquals(listOf("Cancelar"), result.boldParts())
    }

    @Test
    fun `varios trechos na mesma frase`() {
        val result = helpBoldText("**Abra** a agenda e toque em **Salvar**")
        assertEquals("Abra a agenda e toque em Salvar", result.text)
        assertEquals(listOf("Abra", "Salvar"), result.boldParts())
    }

    @Test
    fun `texto sem marca fica igual`() {
        val result = helpBoldText("Toque em Cancelar.")
        assertEquals("Toque em Cancelar.", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `marca sem par fica como veio`() {
        val result = helpBoldText("Toque em **Cancelar.")
        assertEquals("Toque em **Cancelar.", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `outro markdown nao e interpretado`() {
        val source = "*itálico* _sublinhado_ # título [link](x) `código`"
        val result = helpBoldText(source)
        assertEquals(source, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `negrito nao atravessa quebra de linha`() {
        val source = "**um\ndois**"
        assertEquals(source, helpBoldText(source).text)
    }

    private fun flow(steps: List<String> = emptyList(), text: String? = null) =
        HelpFlow(id = "a", slug = "a", title = "T", summary = null, steps = steps, text = text, video = null)

    @Test
    fun `busca ignora os asteriscos do conteudo`() {
        val flows = listOf(flow(steps = listOf("Toque em Can**celar**")))
        assertEquals(1, filterHelpFlows(flows, "cancelar").size)
        val withText = listOf(flow(text = "Use o **menu**lateral"))
        assertEquals(1, filterHelpFlows(withText, "menulateral").size)
    }

    @Test
    fun `busca ignora os asteriscos da consulta`() {
        val flows = listOf(flow(steps = listOf("Toque em **Cancelar**")))
        assertEquals(1, filterHelpFlows(flows, "**cancelar**").size)
    }
}
