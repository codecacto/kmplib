package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorTexts
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlanLabels
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTexts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O recurso pt-BR diz exatamente o que o default da classe dizia (2.219.0 trocou a fonte, não o
 * texto) — em especial o disclosure legal do paywall, que a Apple e o Google conferem.
 */
class MonetizationPtResourceParityTest {

    private val pt: Map<String, String> by lazy {
        val xml = File("../ui/src/commonMain/composeResources/values/strings.xml").readText()
        Regex("""<string name="([^"]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].replace("&amp;", "&") }
    }

    @Test
    fun `erros de compra em pt-BR sao os de antes`() {
        val d = PurchaseErrorTexts()
        assertEquals(d.networkError, pt["kmplib_purchase_network"])
        assertEquals(d.storeError, pt["kmplib_purchase_store"])
        assertEquals(d.productNotFound, pt["kmplib_purchase_product_not_found"])
        assertEquals(d.paymentPending, pt["kmplib_purchase_pending"])
        assertEquals(d.paymentDeclined, pt["kmplib_purchase_declined"])
        assertEquals(d.alreadyOwned, pt["kmplib_purchase_already_owned"])
        assertEquals(d.configurationError, pt["kmplib_purchase_configuration"])
        assertEquals(d.purchaseNotAllowed, pt["kmplib_purchase_not_allowed"])
        assertEquals(d.alreadyOwnedByOtherUser, pt["kmplib_purchase_other_user"])
        assertEquals(d.purchaseInProgress, pt["kmplib_purchase_in_progress"])
        assertEquals(d.ineligible, pt["kmplib_purchase_ineligible"])
        assertEquals(d.userCancelled, pt["kmplib_purchase_cancelled"])
        assertEquals(d.unknown, pt["kmplib_purchase_unknown"])
    }

    @Test
    fun `paywall em pt-BR e o de antes, disclosure legal inclusive`() {
        val d = PaywallTexts()
        assertEquals(d.screenTitle, pt["kmplib_paywall_screen_title"])
        assertEquals(d.headerTitle, pt["kmplib_paywall_header_title"])
        assertEquals(d.headerSubtitle, pt["kmplib_paywall_header_subtitle"])
        assertEquals(d.activeDescription, pt["kmplib_paywall_active_description"])
        assertEquals(d.autoRenewalNotice, pt["kmplib_paywall_auto_renewal"])
        assertEquals(d.subscriptionDisclosure, pt["kmplib_paywall_disclosure"])
        assertEquals(d.needHelpDescription, pt["kmplib_paywall_help_description"])
        assertEquals(d.privacyPolicy, pt["kmplib_privacy_policy"])
        assertEquals(d.termsOfUse, pt["kmplib_terms_of_use"])
    }

    @Test
    fun `nomes de plano em pt-BR sao os de antes`() {
        val d = PaywallPlanLabels()
        assertEquals(d.monthly, pt["kmplib_plan_monthly"])
        assertEquals(d.semiAnnual, pt["kmplib_plan_semiannual"])
        assertEquals(d.yearly, pt["kmplib_plan_yearly"])
        assertEquals(d.monthsTemplate, pt["kmplib_duration_months"])
    }
}
