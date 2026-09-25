package br.com.codecacto.kmplib.ui.screens

import br.com.codecacto.kmplib.ui.components.FormPlaceholders
import br.com.codecacto.kmplib.ui.screens.developer.ContactTexts
import br.com.codecacto.kmplib.ui.screens.feedback.FeedbackTexts
import kotlin.test.Test
import kotlin.test.assertEquals

/** Contato e feedback usam os placeholders da lib — nada de `voce@email.com` ou número inventado. */
class CentralPlaceholdersTest {

    @Test
    fun contatoUsaInstrucaoEFormato() {
        val t = ContactTexts()
        assertEquals(FormPlaceholders.EMAIL, t.emailPlaceholder)
        assertEquals(FormPlaceholders.PHONE, t.whatsappPlaceholder)
    }

    @Test
    fun feedbackUsaInstrucaoEFormato() {
        val t = FeedbackTexts()
        assertEquals(FormPlaceholders.EMAIL, t.emailPlaceholder)
        assertEquals(FormPlaceholders.PHONE, t.whatsappPlaceholder)
    }
}
