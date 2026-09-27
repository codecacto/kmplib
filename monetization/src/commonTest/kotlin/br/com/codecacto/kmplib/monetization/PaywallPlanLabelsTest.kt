package br.com.codecacto.kmplib.monetization

import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorTexts
import br.com.codecacto.kmplib.monetization.purchase.loadPurchaseErrorTexts
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlanLabels
import br.com.codecacto.kmplib.ui.screens.paywall.defaultDurationLabel
import br.com.codecacto.kmplib.ui.screens.paywall.defaultPlanName
import br.com.codecacto.kmplib.ui.screens.paywall.loadPaywallPlanLabels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaywallPlanLabelsTest {

    @Test
    fun `rotulos default sao os de defaultPlanName e defaultDurationLabel`() {
        val r = PaywallPlanLabels()
        listOf(1, 3, 6, 12, 24).forEach { m ->
            assertEquals(defaultPlanName(m), r.planName(m))
            assertEquals(defaultDurationLabel(m), r.durationLabel(m))
        }
    }

    @Test
    fun `rotulos traduzidos usam o modelo de meses`() {
        val en = PaywallPlanLabels("Monthly", "Semiannual", "Yearly", "1 month", "1 year", "%1\$d months")
        assertEquals("Semiannual", en.planName(6))
        assertEquals("3 months", en.planName(3))
        assertEquals("1 year", en.durationLabel(12))
        assertEquals("6 months", en.durationLabel(6))
    }

    @Test
    fun `leitores nunca lancam e devolvem textos utilizaveis`() = runTest {
        assertTrue(loadPaywallPlanLabels().planName(1).isNotBlank())
        val erros = loadPurchaseErrorTexts()
        assertTrue(erros.unknown.isNotBlank())
        // JVM sem recursos → default; aparelho pt-BR → o mesmo texto (paridade do recurso).
        if (erros == PurchaseErrorTexts()) assertEquals(PurchaseErrorTexts().networkError, erros.networkError)
    }
}
