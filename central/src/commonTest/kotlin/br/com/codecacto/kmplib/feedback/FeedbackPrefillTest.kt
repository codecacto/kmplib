package br.com.codecacto.kmplib.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackPrefillTest {

    @Test
    fun semMensagemInicialNasceVazia() {
        assertEquals("", feedbackInitialMessage(null))
        assertEquals("", feedbackInitialMessage(""))
        assertEquals("", feedbackInitialMessage("  \n "))
    }

    @Test
    fun mensagemInicialNaoEhAparada() {
        val texto = "Quero o contrato personalizado (R$ 30).\n\n"
        assertEquals(texto, feedbackInitialMessage(texto))
    }

    @Test
    fun semeiaUmaVezQuandoOValorChegaEOCampoEstaVazio() {
        assertTrue(shouldSeedFeedbackField(alreadySeeded = false, hasIncoming = true, fieldIsEmpty = true))
    }

    @Test
    fun naoSemeiaDuasVezes() {
        assertFalse(shouldSeedFeedbackField(alreadySeeded = true, hasIncoming = true, fieldIsEmpty = true))
    }

    @Test
    fun naoSemeiaSemValor() {
        assertFalse(shouldSeedFeedbackField(alreadySeeded = false, hasIncoming = false, fieldIsEmpty = true))
    }

    @Test
    fun naoSobrescreveOQueAPessoaPreencheu() {
        assertFalse(shouldSeedFeedbackField(alreadySeeded = false, hasIncoming = true, fieldIsEmpty = false))
    }

    @Test
    fun valorDaTelaVenceODoServico() {
        assertEquals("Ana", feedbackPrefillValue("Ana", "Mariana"))
        assertEquals("Mariana", feedbackPrefillValue(null, "Mariana"))
        assertEquals("Mariana", feedbackPrefillValue("  ", "Mariana"))
        assertEquals(null, feedbackPrefillValue("", " "))
        assertEquals(null, feedbackPrefillValue(null, null))
    }
}
