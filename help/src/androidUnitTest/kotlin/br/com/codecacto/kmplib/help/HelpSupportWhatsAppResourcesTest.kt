package br.com.codecacto.kmplib.help

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Os textos do suporte pelo WhatsApp nos 4 idiomas da lib (2.375.0), lidos dos MESMOS recursos que o
 * `rememberHelpTexts()` lê, e montados pelo [HelpSupportWhatsApp.messageFor].
 */
class HelpSupportWhatsAppResourcesTest {

    private fun ler(pasta: String): Map<String, String> {
        val arquivo = File("../ui/src/commonMain/composeResources/$pasta/strings.xml")
        assertTrue(arquivo.exists(), "não achei ${arquivo.absolutePath}")
        return Regex("""<string name="(kmplib_help_support_whatsapp[a-z_]*)">(.*?)</string>""")
            .findAll(arquivo.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun textos(pasta: String): HelpTexts {
        val r = ler(pasta)
        return HelpTexts(
            supportWhatsApp = r.getValue("kmplib_help_support_whatsapp"),
            supportWhatsAppMessageWithProductTemplate = r.getValue("kmplib_help_support_whatsapp_message_product"),
            supportWhatsAppMessage = r.getValue("kmplib_help_support_whatsapp_message"),
        )
    }

    @Test
    fun `pt-BR e o default da data class`() {
        val pt = textos("values")
        val padrao = HelpTexts()
        assertEquals(padrao.supportWhatsApp, pt.supportWhatsApp)
        assertEquals(padrao.supportWhatsAppMessageWithProductTemplate, pt.supportWhatsAppMessageWithProductTemplate)
        assertEquals(padrao.supportWhatsAppMessage, pt.supportWhatsAppMessage)
        assertEquals("Chamar o suporte no WhatsApp", pt.supportWhatsApp)
    }

    @Test
    fun `mensagem default em cada idioma`() {
        val esperado = mapOf(
            "values" to "Olá! Uso o LocAki e gostaria de falar com o suporte.",
            "values-en" to "Hi! I use LocAki and would like to talk to support.",
            "values-es" to "¡Hola! Uso LocAki y me gustaría hablar con el soporte.",
            "values-pt-rPT" to "Olá! Utilizo o LocAki e gostaria de falar com o suporte.",
        )
        esperado.forEach { (pasta, frase) ->
            assertEquals(frase, HelpSupportWhatsApp(productName = "LocAki").messageFor(textos(pasta)), pasta)
            val generica = HelpSupportWhatsApp().messageFor(textos(pasta))
            assertTrue(generica.isNotBlank() && !generica.contains("%"), "$pasta: $generica")
        }
    }
}
