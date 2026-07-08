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
    fun `estilos disponiveis sao Modal e Banner`() {
        assertEquals(
            listOf(ConnectivityStyle.Modal, ConnectivityStyle.Banner),
            ConnectivityStyle.entries.toList(),
        )
    }
}
