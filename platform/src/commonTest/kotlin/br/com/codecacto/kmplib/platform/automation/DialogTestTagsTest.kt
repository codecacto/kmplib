package br.com.codecacto.kmplib.platform.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Os ids são CONTRATO com os flows Maestro dos apps (`<app>/mobile/.maestro/`): mudar o texto de um
 * quebra em silêncio o flow de exclusão de conta, de confirmação de saída etc. de todos os apps de
 * uma vez, sem erro de compilação em lugar nenhum. Por isso o valor literal é travado aqui.
 */
class DialogTestTagsTest {

    @Test
    fun `ids literais sao estaveis`() {
        assertEquals("dialogo", DialogTestTags.CONTAINER)
        assertEquals("dialogo-titulo", DialogTestTags.TITULO)
        assertEquals("dialogo-mensagem", DialogTestTags.MENSAGEM)
        assertEquals("dialogo-input", DialogTestTags.INPUT)
        assertEquals("dialogo-btn-confirmar", DialogTestTags.BTN_CONFIRMAR)
        assertEquals("dialogo-btn-cancelar", DialogTestTags.BTN_CANCELAR)
        assertEquals("dialogo-btn-fechar", DialogTestTags.BTN_FECHAR)
        assertEquals("dialogo-folha", DialogTestTags.FOLHA)
        assertEquals("dialogo-menu", DialogTestTags.MENU)
    }

    @Test
    fun `todos os ids estao listados e sao unicos`() {
        assertEquals(9, DialogTestTags.all.size)
        assertEquals(DialogTestTags.all.size, DialogTestTags.all.toSet().size)
    }

    @Test
    fun `ids seguem o vocabulario da fabrica - minusculas com hifen e prefixo dialogo`() {
        val vocabulario = Regex("^dialogo(-[a-z0-9]+)*$")
        DialogTestTags.all.forEach { id ->
            assertTrue(vocabulario.matches(id), "id fora do vocabulário: $id")
        }
    }

    @Test
    fun `botoes seguem o padrao prefixo-btn-acao dos demais TestTags da lib`() {
        // Mesmo desenho de `login-btn-entrar`, `paywall-btn-assinar`, `ads-btn-fechar-interstitial`.
        listOf(DialogTestTags.BTN_CONFIRMAR, DialogTestTags.BTN_CANCELAR, DialogTestTags.BTN_FECHAR)
            .forEach { assertTrue(it.startsWith("dialogo-btn-"), it) }
    }
}
