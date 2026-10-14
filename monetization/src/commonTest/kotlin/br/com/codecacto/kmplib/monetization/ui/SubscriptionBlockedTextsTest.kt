package br.com.codecacto.kmplib.monetization.ui

import br.com.codecacto.kmplib.monetization.access.SubscriptionBlockReason
import br.com.codecacto.kmplib.ui.screens.subscription.SubscriptionBlockDefaults
import br.com.codecacto.kmplib.ui.screens.subscription.SubscriptionBlockedTestTags
import br.com.codecacto.kmplib.ui.screens.subscription.SubscriptionBlockedTexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 2.300.0 — título por motivo, ids estáveis para o Maestro e o WhatsApp da empresa. */
class SubscriptionBlockedTextsTest {

    private val texts = SubscriptionBlockedTexts(
        trialExpiredTitle = "Seu teste grátis terminou",
        subscriptionRequiredTitle = "Sua assinatura não está ativa",
        message = "Seus dados estão guardados. Assine para continuar usando.",
        subscribe = "Assinar",
        whatsApp = "Falar no WhatsApp",
        signOut = "Sair",
        whatsAppMessage = { "msg-${it.code}" },
    )

    @Test
    fun tituloPorMotivo() {
        assertEquals("Seu teste grátis terminou", texts.titleFor(SubscriptionBlockReason.TRIAL_EXPIRED))
        assertEquals("Sua assinatura não está ativa", texts.titleFor(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED))
        assertEquals("msg-TRIAL_EXPIRED", texts.whatsAppMessage(SubscriptionBlockReason.TRIAL_EXPIRED))
    }

    @Test
    fun idsUnicosComPrefixo() {
        val ids = listOf(
            SubscriptionBlockedTestTags.TELA, SubscriptionBlockedTestTags.TITULO,
            SubscriptionBlockedTestTags.MENSAGEM, SubscriptionBlockedTestTags.BTN_ASSINAR,
            SubscriptionBlockedTestTags.BTN_WHATSAPP, SubscriptionBlockedTestTags.BTN_SAIR,
            SubscriptionBlockedTestTags.PAYWALL,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("assinatura-bloqueio") })
    }

    @Test
    fun whatsAppDaEmpresa() {
        assertEquals("5565998030240", SubscriptionBlockDefaults.WHATSAPP_PHONE)
    }
}
