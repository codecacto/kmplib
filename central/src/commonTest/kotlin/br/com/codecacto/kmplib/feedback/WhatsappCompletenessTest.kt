package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.ui.screens.feedback.isCompleteWhatsapp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O WhatsApp dos formulários da lib (feedback, contato) — 2.219.0. No Brasil a regra de sempre
 * (celular com DDD, 11 dígitos); fora dele, telefone internacional plausível. Antes, quem estava fora
 * do Brasil não conseguia enviar o feedback, porque "11 dígitos" não existe no número dele.
 */
class WhatsappCompletenessTest {

    @Test
    fun `Brasil exige os 11 digitos de sempre`() {
        val br = PhoneInputFormat.Brazil
        assertTrue(br.isCompleteWhatsapp("65999998888"))
        assertFalse(br.isCompleteWhatsapp("6533221100"))
        assertFalse(br.isCompleteWhatsapp(""))
    }

    @Test
    fun `fora do Brasil vale o numero do pais, com ou sem DDI`() {
        val pt = PhoneInputFormat.forRegion("PT")
        assertTrue(pt.isCompleteWhatsapp("912345678"))
        assertTrue(pt.isCompleteWhatsapp("+14155550100"))
        assertFalse(pt.isCompleteWhatsapp("9123"))
    }
}
