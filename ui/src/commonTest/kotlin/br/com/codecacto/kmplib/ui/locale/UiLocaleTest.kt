package br.com.codecacto.kmplib.ui.locale

import br.com.codecacto.kmplib.core.locale.FactoryLocales
import br.com.codecacto.kmplib.sync.rest.DomainApiTexts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiLocaleTest {

    @Test
    fun `modelos com numero sao preenchidos`() {
        assertEquals("Erro do servidor (503).", formatStatusTemplate("Erro do servidor (%1\$d).", 503))
        assertEquals("3 of 12", formatTwoNumberTemplate("%1\$d of %2\$d", 3, 12))
        assertEquals("sem argumento", formatStatusTemplate("sem argumento", 1))
    }

    @Test
    fun `idioma da tela e um dos quatro`() = runTest {
        assertTrue(uiLanguageTag() in FactoryLocales.ALL)
    }

    @Test
    fun `textos do cliente de dominio em pt-BR batem com os defaults da classe`() = runTest {
        // Recurso pt-BR (quando carrega) e default literal têm de dizer a mesma coisa: quem monta
        // o objeto fora da composição e quem lê o recurso não podem ver frases diferentes.
        val lido = loadDomainApiTexts()
        val padrao = DomainApiTexts()
        if (uiLanguageTag() == FactoryLocales.PT_BR) {
            assertEquals(padrao.offline, lido.offline)
            assertEquals(padrao.rateLimited, lido.rateLimited)
            assertEquals(padrao.sessionExpired, lido.sessionExpired)
            assertEquals(padrao.quotaReached, lido.quotaReached)
            assertEquals(padrao.serverError(500), lido.serverError(500))
        } else {
            assertTrue(lido.serverError(500).contains("500"))
        }
    }
}
