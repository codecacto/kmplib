package br.com.codecacto.kmplib.help

import br.com.codecacto.kmplib.core.config.CodeCactoContact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HelpSupportWhatsAppTest {

    private val texts = HelpTexts()

    @Test
    fun `numero default e o da CodeCacto`() {
        val support = HelpSupportWhatsApp()
        assertEquals("5565998030240", CodeCactoContact.WHATSAPP_PHONE)
        assertEquals(CodeCactoContact.WHATSAPP_PHONE, support.phone)
        assertEquals("5565998030240", support.normalizedPhone)
    }

    @Test
    fun `numero passa pela regua do WhatsApp`() {
        assertEquals("5565999998888", HelpSupportWhatsApp(phone = "(65) 99999-8888").normalizedPhone)
        assertEquals("5565999998888", HelpSupportWhatsApp(phone = "+55 65 99999-8888").normalizedPhone)
        assertNull(HelpSupportWhatsApp(phone = "").normalizedPhone)
        assertNull(HelpSupportWhatsApp(phone = "123").normalizedPhone)
    }

    @Test
    fun `mensagem default diz o produto`() {
        assertEquals(
            "Olá! Uso o LocAki e gostaria de falar com o suporte.",
            HelpSupportWhatsApp(productName = "  LocAki ").messageFor(texts),
        )
    }

    @Test
    fun `sem produto a mensagem e a generica`() {
        assertEquals("Olá! Gostaria de falar com o suporte.", HelpSupportWhatsApp().messageFor(texts))
        assertEquals("Olá! Gostaria de falar com o suporte.", HelpSupportWhatsApp(productName = " ").messageFor(texts))
    }

    @Test
    fun `mensagem propria vence a default e em branco cai na default`() {
        assertEquals(
            "Preciso de ajuda com a locação",
            HelpSupportWhatsApp(message = " Preciso de ajuda com a locação ", productName = "LocAki").messageFor(texts),
        )
        assertEquals(
            "Olá! Uso o LocAki e gostaria de falar com o suporte.",
            HelpSupportWhatsApp(message = "   ", productName = "LocAki").messageFor(texts),
        )
    }

    @Test
    fun `mensagem segue os textos do idioma`() {
        val en = HelpTexts(
            supportWhatsAppMessageWithProductTemplate = "Hi! I use %1\$s and would like to talk to support.",
            supportWhatsAppMessage = "Hi! I would like to talk to support.",
        )
        assertEquals("Hi! I use LocAki and would like to talk to support.", HelpSupportWhatsApp(productName = "LocAki").messageFor(en))
        assertEquals("Hi! I would like to talk to support.", HelpSupportWhatsApp().messageFor(en))
    }

    @Test
    fun `ids de automacao do botao`() {
        assertEquals("ajuda-btn-suporte-whatsapp", HelpTestTags.SUPPORT_WHATSAPP_BUTTON)
        assertEquals("ajuda-btn-suporte-whatsapp-resposta", HelpTestTags.SUPPORT_WHATSAPP_IN_ANSWER_BUTTON)
    }
}
