package br.com.codecacto.kmplib.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackFormTest {

    @Test
    fun formularioCompletoPodeEnviar() {
        val recusados = validateFeedbackForm(FeedbackMotivo.BUG, "O app fecha ao abrir", "", whatsappComplete = true)
        assertTrue(recusados.isEmpty())
    }

    @Test
    fun formularioVazioRecusaTudoNaOrdemDaTela() {
        val recusados = validateFeedbackForm(null, "", "", whatsappComplete = false)
        assertEquals(listOf(FeedbackField.MOTIVO, FeedbackField.MENSAGEM, FeedbackField.WHATSAPP), recusados)
    }

    @Test
    fun mensagemSoComEspacoNaoConta() {
        val recusados = validateFeedbackForm(FeedbackMotivo.OUTRO, "  \n\t ", "", whatsappComplete = true)
        assertEquals(listOf(FeedbackField.MENSAGEM), recusados)
    }

    @Test
    fun emailOpcionalSoRecusaQuandoPreenchidoErrado() {
        assertTrue(validateFeedbackForm(FeedbackMotivo.ELOGIO, "Muito bom", "   ", true).isEmpty())
        assertTrue(validateFeedbackForm(FeedbackMotivo.ELOGIO, "Muito bom", " nome@dominio.com.br ", true).isEmpty())
        assertEquals(
            listOf(FeedbackField.EMAIL),
            validateFeedbackForm(FeedbackMotivo.ELOGIO, "Muito bom", "nome@", true),
        )
    }

    @Test
    fun primeiroRecusadoEhOQueATelaLevaAVista() {
        val recusados = validateFeedbackForm(FeedbackMotivo.DUVIDA, "Como exporto?", "x", whatsappComplete = false)
        assertEquals(FeedbackField.WHATSAPP, recusados.first())
        assertEquals(listOf(FeedbackField.WHATSAPP, FeedbackField.EMAIL), recusados)
    }
}
