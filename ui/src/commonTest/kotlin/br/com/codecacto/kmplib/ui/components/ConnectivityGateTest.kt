package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato do aviso de conectividade (defaults i18n pt-BR + estilos). A UI em si
 * (modal/banner reativos) é validada visualmente — testes de UI automatizados estão
 * desativados na lib (decisão fundador, jun/2026), como em [OfflineBannerTest].
 */
class ConnectivityGateTest {

    @Test
    fun `textos default sao pt-BR e nao vazios`() {
        val texts = ConnectivityTexts()
        assertEquals("Sem conexão com a internet", texts.modalTitle)
        assertEquals("Tentar novamente", texts.retryButton)
        assertEquals("Sem conexão com a internet", texts.bannerText)
        assertTrue(texts.modalMessage.isNotBlank())
    }

    @Test
    fun `textos sao customizaveis para i18n`() {
        val en = ConnectivityTexts(
            modalTitle = "No internet connection",
            modalMessage = "You appear to be offline. Check your connection and try again.",
            retryButton = "Try again",
            bannerText = "No internet connection",
        )
        assertEquals("Try again", en.retryButton)
        assertEquals("No internet connection", en.modalTitle)
    }

    @Test
    fun `estilos disponiveis sao Modal, Banner e FullScreen`() {
        assertEquals(
            listOf(ConnectivityStyle.Modal, ConnectivityStyle.Banner, ConnectivityStyle.FullScreen),
            ConnectivityStyle.entries.toList(),
        )
    }

    @Test
    fun `textos da tela cheia tem default pt-BR e nao vazios`() {
        val texts = ConnectivityTexts()
        assertEquals("Sem conexão com a internet", texts.screenTitle)
        assertEquals("Verificando conexão…", texts.checkingButton)
        assertTrue(texts.screenMessage.isNotBlank())
    }

    @Test
    fun `construtor posicional antigo continua compilando`() {
        // Os campos da tela cheia entraram NO FIM e com default: quem montava os 4 textos
        // posicionalmente antes da 2.200.0 não quebra.
        val antigo = ConnectivityTexts("a", "b", "c", "d")
        assertEquals("d", antigo.bannerText)
        assertEquals(ConnectivityTexts().screenTitle, antigo.screenTitle)
    }

    @Test
    fun `o retorno de verificacao dura o bastante para ser visto`() {
        assertTrue(NO_INTERNET_CHECK_FEEDBACK_MS in 800L..2_000L)
    }
}
