package br.com.codecacto.kmplib.ui.screens.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AboutScreenTest {

    private val texts = AboutTexts()

    @Test
    fun `versao sem build`() {
        assertEquals("Versão 1.4.2", aboutVersionLabel("1.4.2", "37", showBuildNumber = false, texts = texts))
    }

    @Test
    fun `versao com build quando pedido`() {
        assertEquals("Versão 1.4.2 (37)", aboutVersionLabel(" 1.4.2 ", "37", showBuildNumber = true, texts = texts))
    }

    @Test
    fun `build vazio cai na versao simples`() {
        assertEquals("Versão 1.4.2", aboutVersionLabel("1.4.2", " ", showBuildNumber = true, texts = texts))
        assertEquals("Versão 1.4.2", aboutVersionLabel("1.4.2", null, showBuildNumber = true, texts = texts))
    }

    @Test
    fun `sem versao legivel a linha nao aparece`() {
        assertNull(aboutVersionLabel(null, "37", showBuildNumber = true, texts = texts))
        assertNull(aboutVersionLabel("  ", "37", showBuildNumber = false, texts = texts))
    }

    @Test
    fun `textos traduzidos entram pelas lambdas`() {
        val en = AboutTexts(version = { "Version $it" }, versionWithBuild = { v, b -> "Version $v ($b)" })
        assertEquals("Version 2.0 (5)", aboutVersionLabel("2.0", "5", showBuildNumber = true, texts = en))
    }

    @Test
    fun `ids estaveis e unicos`() {
        assertEquals("sobre-tela", AboutTestTags.TELA)
        assertEquals("sobre-versao", AboutTestTags.VERSAO)
        assertEquals("sobre-btn-termos", AboutTestTags.BTN_TERMOS)
        assertEquals("sobre-btn-privacidade", AboutTestTags.BTN_PRIVACIDADE)
        assertEquals("sobre-btn-desenvolvido-por", AboutTestTags.BTN_DESENVOLVIDO_POR)
        assertEquals(AboutTestTags.all.size, AboutTestTags.all.toSet().size)
    }
}
