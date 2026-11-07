package br.com.codecacto.kmplib.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackFormTest {

    private val br = FeedbackContactPolicy.WHATSAPP_REQUIRED
    private val global = FeedbackContactPolicy.EMAIL_REQUIRED

    @Test
    fun formularioCompletoPodeEnviar() {
        val recusados = validateFeedbackForm(FeedbackMotivo.BUG, "O app fecha ao abrir", "", "65999998888", true, br)
        assertTrue(recusados.isEmpty())
    }

    @Test
    fun formularioVazioRecusaTudoNaOrdemDaTela() {
        assertEquals(
            listOf(FeedbackField.MOTIVO, FeedbackField.MENSAGEM, FeedbackField.WHATSAPP),
            validateFeedbackForm(null, "", "", "", false, br),
        )
        assertEquals(
            listOf(FeedbackField.MOTIVO, FeedbackField.MENSAGEM, FeedbackField.EMAIL),
            validateFeedbackForm(null, "", "", "", false, global),
        )
    }

    @Test
    fun mensagemSoComEspacoNaoConta() {
        val recusados = validateFeedbackForm(FeedbackMotivo.OUTRO, "  \n\t ", "", "65999998888", true, br)
        assertEquals(listOf(FeedbackField.MENSAGEM), recusados)
    }

    @Test
    fun brEmailOpcionalSoRecusaQuandoPreenchidoErrado() {
        assertTrue(validateFeedbackForm(FeedbackMotivo.ELOGIO, "Muito bom", "   ", "65999998888", true, br).isEmpty())
        assertTrue(validateFeedbackForm(FeedbackMotivo.ELOGIO, "Muito bom", " nome@dominio.com.br ", "65999998888", true, br).isEmpty())
        assertEquals(
            listOf(FeedbackField.EMAIL),
            validateFeedbackForm(FeedbackMotivo.ELOGIO, "Muito bom", "nome@", "65999998888", true, br),
        )
    }

    @Test
    fun globalEmailObrigatorioEWhatsappOpcional() {
        assertTrue(validateFeedbackForm(FeedbackMotivo.ELOGIO, "Great", "ana@example.com", "", false, global).isEmpty())
        assertEquals(
            listOf(FeedbackField.EMAIL),
            validateFeedbackForm(FeedbackMotivo.ELOGIO, "Great", "", "", false, global),
        )
        // WhatsApp opcional, mas incompleto quando preenchido é recusado.
        assertEquals(
            listOf(FeedbackField.WHATSAPP),
            validateFeedbackForm(FeedbackMotivo.ELOGIO, "Great", "ana@example.com", "+3519", false, global),
        )
    }

    @Test
    fun primeiroRecusadoSegueAOrdemDaPolitica() {
        assertEquals(
            listOf(FeedbackField.WHATSAPP, FeedbackField.EMAIL),
            validateFeedbackForm(FeedbackMotivo.DUVIDA, "Como exporto?", "x", "659", false, br),
        )
        assertEquals(
            listOf(FeedbackField.EMAIL, FeedbackField.WHATSAPP),
            validateFeedbackForm(FeedbackMotivo.DUVIDA, "How?", "x", "+3519", false, global),
        )
    }

    @Test
    fun politicaPeloAlcanceDaLoja() {
        assertEquals(global, FeedbackContactPolicy.forStoreReach("GLOBAL"))
        assertEquals(global, FeedbackContactPolicy.forStoreReach(" global "))
        assertEquals(br, FeedbackContactPolicy.forStoreReach("BR"))
        assertEquals(br, FeedbackContactPolicy.forStoreReach(""))
        assertEquals(br, FeedbackContactPolicy.forStoreReach(null))
    }
}
