package br.com.codecacto.kmplib.ui.screens.paywall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Os textos padrão do paywall saem em pt-BR acentuado — é tela de venda, e a loja revisa. */
class PaywallTextsTest {

    private val semAcento = Regex(
        "\\b(Voce|voce|disponivel|periodo|informacoes|Informacoes|duvidas|configuracoes|" +
            "confirmacao|sera|ate|Politica|mes)\\b",
    )

    @Test
    fun nenhumTextoPadraoSemAcento() {
        val t = PaywallTexts()
        listOf(
            t.screenTitle, t.headerTitle, t.headerSubtitle, t.choosePlanLabel, t.ctaSubscribe,
            t.recommendedBadge, t.restore, t.restoring, t.usageLabel, t.emptyPlans, t.activeTitle,
            t.activeDescription, t.renewsAtLabel, t.expiresAtLabel, t.manageSubscription,
            t.legalInfoTitle, t.autoRenewalNotice, t.subscriptionDisclosure, t.privacyPolicy,
            t.termsOfUse, t.needHelpTitle, t.needHelpDescription, t.needHelpButton,
            t.backContentDescription, t.errorDismiss,
        ).forEach { texto ->
            assertFalse(semAcento.containsMatchIn(texto), "texto sem acento: $texto")
        }
    }

    @Test
    fun acentuacaoDosTextosCorrigidos() {
        val t = PaywallTexts()
        assertEquals("Nenhum plano disponível no momento.", t.emptyPlans)
        assertEquals("Você tem acesso a todos os recursos premium.", t.activeDescription)
        assertEquals("Informações legais", t.legalInfoTitle)
        assertEquals("Política de Privacidade", t.privacyPolicy)
        assertEquals("1 mês", defaultDurationLabel(1))
    }
}
