package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlan
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlanLabels
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTexts
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTrial
import br.com.codecacto.kmplib.ui.screens.paywall.toPaywallPlansFromStore
import com.revenuecat.purchases.kmp.models.IntroEligibilityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Teste grátis pela loja (2.231.0, docs/43): quando a tela PODE dizer "7 dias grátis".
 *
 * A regra que isto trava: só a confirmação da loja vira promessa. `UNKNOWN` nunca vira — botão que
 * diz grátis e cobra na hora é recusa 3.1.2 da Apple.
 */
class TrialEligibilityTest {

    @Test
    fun `iOS - o status do StoreKit manda`() {
        assertEquals(TrialEligibility.ELIGIBLE, resolveTrialEligibility(IntroEligibilityStatus.ELIGIBLE, filteredByStore = false))
        assertEquals(TrialEligibility.INELIGIBLE, resolveTrialEligibility(IntroEligibilityStatus.INELIGIBLE, filteredByStore = false))
        assertEquals(TrialEligibility.NO_OFFER, resolveTrialEligibility(IntroEligibilityStatus.NO_INTRO_OFFER_EXISTS, filteredByStore = false))
        assertEquals(TrialEligibility.UNKNOWN, resolveTrialEligibility(IntroEligibilityStatus.UNKNOWN, filteredByStore = false))
        assertEquals(TrialEligibility.UNKNOWN, resolveTrialEligibility(null, filteredByStore = false))
    }

    @Test
    fun `Android - a oferta com fase gratis ja veio filtrada pelo Play`() {
        assertEquals(TrialEligibility.ELIGIBLE, resolveTrialEligibility(IntroEligibilityStatus.UNKNOWN, filteredByStore = true))
        assertEquals(TrialEligibility.ELIGIBLE, resolveTrialEligibility(null, filteredByStore = true))
    }

    @Test
    fun `so trial confirmado e ofertavel`() {
        val base = pacote()
        assertNull(base.offerableFreeTrial)
        assertNull(base.copy(freeTrial = SETE_DIAS, trialEligibility = TrialEligibility.UNKNOWN).offerableFreeTrial)
        assertEquals(SETE_DIAS, base.copy(freeTrial = SETE_DIAS, trialEligibility = TrialEligibility.ELIGIBLE).offerableFreeTrial)
    }

    @Test
    fun `semana vira dias no rotulo - e como a fabrica promete`() {
        val rotulos = PaywallPlanLabels()
        assertEquals("7 dias", rotulos.trialLabel(FreeTrialPeriod(1, FreeTrialUnit.WEEK)))
        assertEquals("7 dias", rotulos.trialLabel(SETE_DIAS))
        assertEquals("1 dia", rotulos.trialLabel(FreeTrialPeriod(1, FreeTrialUnit.DAY)))
        assertEquals("1 mês", rotulos.trialLabel(FreeTrialPeriod(1, FreeTrialUnit.MONTH)))
        assertEquals("1 ano", rotulos.trialLabel(FreeTrialPeriod(1, FreeTrialUnit.YEAR)))
    }

    @Test
    fun `mapeador da loja leva o trial confirmado ao card`() {
        val planos = listOf(
            pacote().copy(freeTrial = SETE_DIAS, trialEligibility = TrialEligibility.ELIGIBLE),
            pacote(id = "\$rc_annual", meses = 12, micros = 249_900_000).copy(
                freeTrial = SETE_DIAS,
                trialEligibility = TrialEligibility.INELIGIBLE,
            ),
        ).toPaywallPlansFromStore()
        assertEquals("7 dias", planos.first { it.durationMonths == 1 }.trial?.periodLabel)
        assertNull(planos.first { it.durationMonths == 12 }.trial)
    }

    @Test
    fun `preco por periodo do termo de cobranca`() {
        val textos = PaywallTexts()
        fun plano(meses: Int?) = PaywallPlan(id = "x", name = "x", priceLabel = "R$ 29,90", durationMonths = meses)
        assertEquals("R$ 29,90/mês", textos.pricePerPeriod(plano(1)))
        assertEquals("R$ 29,90/semestre", textos.pricePerPeriod(plano(6)))
        assertEquals("R$ 29,90/ano", textos.pricePerPeriod(plano(12)))
        assertEquals("R$ 29,90 a cada 3 meses", textos.pricePerPeriod(plano(3)))
        val comTrial = plano(12).copy(trial = PaywallTrial(SETE_DIAS, "7 dias"))
        assertEquals("Começar 7 dias grátis", textos.ctaLabel(comTrial))
        assertEquals(
            "Grátis por 7 dias, depois R$ 29,90/ano. Renova automaticamente; cancele quando quiser.",
            textos.trialTerms(comTrial),
        )
    }

    @Test
    fun `plano sem duracao nao promete trial - o termo exige o periodo`() {
        val semDuracao = PaywallPlan(id = "x", name = "x", priceLabel = "R$ 9,90", durationMonths = null)
            .copy(trial = PaywallTrial(SETE_DIAS, "7 dias"))
        assertEquals("Assinar", PaywallTexts().ctaLabel(semDuracao))
        assertNull(PaywallTexts().trialTerms(semDuracao))
    }

    private fun pacote(id: String = "\$rc_monthly", meses: Int = 1, micros: Long = 29_900_000) = PurchasePackage(
        packageId = id,
        packageType = if (meses == 12) PurchasePackageType.ANNUAL else PurchasePackageType.MONTHLY,
        storeProductId = "premium_teste",
        priceLabel = "R$ ${micros / 1_000_000}",
        priceAmountMicros = micros,
        currencyCode = "BRL",
        durationMonths = meses,
    )

    private companion object {
        val SETE_DIAS = FreeTrialPeriod(7, FreeTrialUnit.DAY)
    }
}
