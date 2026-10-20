package br.com.codecacto.kmplib.monetization.ui

import br.com.codecacto.kmplib.monetization.access.SubscriptionBlockReason
import br.com.codecacto.kmplib.ui.screens.subscription.SubscriptionBlockDefaults
import br.com.codecacto.kmplib.ui.screens.subscription.SubscriptionBlockedTestTags
import br.com.codecacto.kmplib.ui.screens.subscription.SubscriptionBlockedTexts
import br.com.codecacto.kmplib.ui.screens.subscription.subscriptionBlockOffersPurchase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // ---- 2.308.0: paywallEnabled = false (sistema B2B, GAP-M-01) ----

    @Test
    fun semCompraOTituloESempreAcessoSuspenso() {
        for (reason in SubscriptionBlockReason.entries) {
            assertEquals("Acesso suspenso", texts.titleFor(reason, paywallEnabled = false))
        }
        assertEquals("Seu teste grátis terminou", texts.titleFor(SubscriptionBlockReason.TRIAL_EXPIRED, paywallEnabled = true))
    }

    @Test
    fun semCompraAMensagemMandaFalarComOAdministradorSemPreco() {
        val msg = texts.messageFor(paywallEnabled = false)
        assertTrue(msg.contains("administrador"))
        assertFalse(msg.contains("Assine"))
        assertEquals(texts.message, texts.messageFor(paywallEnabled = true))
    }

    @Test
    fun whatsAppAcompanhaOModo() {
        assertEquals("msg-SUBSCRIPTION_REQUIRED", texts.whatsAppMessageFor(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED, true))
        assertEquals(texts.suspendedWhatsAppMessage, texts.whatsAppMessageFor(SubscriptionBlockReason.SUBSCRIPTION_REQUIRED, false))
    }

    @Test
    fun compraSoComPaywallLigadoEPresente() {
        assertTrue(subscriptionBlockOffersPurchase(paywallEnabled = true, hasPaywall = true))
        assertFalse(subscriptionBlockOffersPurchase(paywallEnabled = false, hasPaywall = true))
        // Paywall nulo: "Assinar" sem destino seria controle mudo — cai no modo sem compra.
        assertFalse(subscriptionBlockOffersPurchase(paywallEnabled = true, hasPaywall = false))
        assertFalse(subscriptionBlockOffersPurchase(paywallEnabled = false, hasPaywall = false))
    }
}
