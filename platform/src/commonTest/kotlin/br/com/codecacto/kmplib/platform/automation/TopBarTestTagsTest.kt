package br.com.codecacto.kmplib.platform.automation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `topbar-titulo` é CONTRATO com os flows Maestro de todos os apps: é o alvo que baixa o teclado no
 * iOS (`tapOn: { id: "topbar-titulo" }`). Mudar o texto quebra em silêncio todo flow com campo de
 * texto no iPhone, sem erro de compilação em lugar nenhum — por isso o literal é travado aqui.
 */
class TopBarTestTagsTest {

    @Test
    fun `id literal do titulo e estavel`() {
        assertEquals("topbar-titulo", TopBarTestTags.TITULO)
    }
}
