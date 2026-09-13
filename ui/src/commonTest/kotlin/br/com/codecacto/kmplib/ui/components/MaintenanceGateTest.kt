package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contrato do modo manutenção (defaults i18n pt-BR + resolução da mensagem). A UI em si é validada
 * visualmente — testes de UI automatizados estão desativados na lib (decisão fundador, jun/2026).
 */
class MaintenanceGateTest {

    @Test
    fun `textos default sao pt-BR`() {
        val texts = MaintenanceTexts()
        assertEquals("Estamos em manutenção", texts.title)
        assertEquals("Estamos fazendo melhorias no aplicativo. Volte daqui a pouco.", texts.message)
        assertEquals("Tentar novamente", texts.retryButton)
        assertEquals("Verificando…", texts.checkingButton)
    }

    @Test
    fun `construtor posicional segue a ordem titulo, mensagem, botao, verificando`() {
        val en = MaintenanceTexts("Under maintenance", "Back soon.", "Try again", "Checking…")
        assertEquals("Under maintenance", en.title)
        assertEquals("Back soon.", en.message)
        assertEquals("Try again", en.retryButton)
        assertEquals("Checking…", en.checkingButton)
    }

    @Test
    fun `mensagem do servidor vence e sai aparada`() {
        assertEquals(
            "Voltamos às 14h.",
            resolveMaintenanceMessage("  Voltamos às 14h.\n", MaintenanceTexts()),
        )
    }

    @Test
    fun `mensagem nula usa o texto padrao`() {
        assertEquals(MaintenanceTexts().message, resolveMaintenanceMessage(null, MaintenanceTexts()))
    }

    @Test
    fun `mensagem vazia ou branca usa o texto padrao`() {
        val texts = MaintenanceTexts(message = "Padrão")
        assertEquals("Padrão", resolveMaintenanceMessage("", texts))
        assertEquals("Padrão", resolveMaintenanceMessage("   \n\t", texts))
    }
}
