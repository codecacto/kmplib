package br.com.codecacto.kmplib.familia

import br.com.codecacto.kmplib.familia.screens.FamilySettingsSection
import br.com.codecacto.kmplib.familia.screens.FamilySettingsSlot
import br.com.codecacto.kmplib.familia.screens.FamilyShareDestination
import br.com.codecacto.kmplib.familia.screens.FamilyShareOutcome
import br.com.codecacto.kmplib.familia.screens.familyAboutLinks
import br.com.codecacto.kmplib.familia.screens.familyPremiumCta
import br.com.codecacto.kmplib.familia.screens.familySettingsSectionsAt
import br.com.codecacto.kmplib.familia.screens.familyShareOutcome
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialPeriod
import br.com.codecacto.kmplib.monetization.purchase.FreeTrialUnit
import br.com.codecacto.kmplib.ui.screens.about.AboutExtraLink
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlan
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTexts
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTrial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** As lacunas que os 12 apps acharam no kit (2.357.0). */
class FamilyKitLacunasTest {

    private val mensal = PaywallPlan(id = "m", name = "Mensal", priceLabel = "R$ 9,90", durationMonths = 1)

    @Test
    fun `sem trial o botao e Assinar plano e nao ha termo`() {
        val cta = familyPremiumCta(mensal, "Assinar Mensal", PaywallTexts())
        assertEquals("Assinar Mensal", cta.label)
        assertNull(cta.terms)
    }

    @Test
    fun `com trial que a loja confirmou o botao diz a verdade e o termo vai junto`() {
        val comTrial = mensal.copy(trial = PaywallTrial(FreeTrialPeriod(1, FreeTrialUnit.WEEK), "7 dias"))
        val cta = familyPremiumCta(comTrial, "Assinar Mensal", PaywallTexts())
        assertEquals("Começar 7 dias grátis", cta.label)
        assertEquals("Grátis por 7 dias, depois R$ 9,90/mês. Renova automaticamente; cancele quando quiser.", cta.terms)
    }

    @Test
    fun `secoes extras de Configuracoes entram no slot pedido e na ordem do app`() {
        val a = FamilySettingsSection("privacidade", "Privacidade", FamilySettingsSlot.AFTER_REMINDERS) {}
        val b = FamilySettingsSection("b", "B") {}
        val c = FamilySettingsSection("c", "C", FamilySettingsSlot.AFTER_REMINDERS) {}
        assertEquals(listOf(a, c), familySettingsSectionsAt(listOf(a, b, c), FamilySettingsSlot.AFTER_REMINDERS))
        assertEquals(listOf(b), familySettingsSectionsAt(listOf(a, b, c), FamilySettingsSlot.AFTER_BACKUP))
        assertEquals(FamilySettingsSlot.AFTER_BACKUP, b.slot)
    }

    @Test
    fun `links do Sobre - traducao primeiro, depois os do app, sem id repetido`() {
        val traducao = AboutExtraLink("Sobre a tradução", "sobre-btn-traducao") {}
        val fontes = AboutExtraLink("Fontes dos mapas", "sobre-btn-fontes") {}
        assertEquals(listOf(traducao, fontes), familyAboutLinks(traducao, listOf(fontes, fontes)))
        assertEquals(listOf(fontes), familyAboutLinks(null, listOf(fontes)))
    }

    @Test
    fun `compartilhar - folha do sistema que abriu e SHARED, a que lancou e FAILED`() {
        assertEquals(FamilyShareOutcome.SHARED, familyShareOutcome(Result.success(Unit)))
        assertEquals(FamilyShareOutcome.FAILED, familyShareOutcome(Result.failure(IllegalStateException())))
        assertEquals("mais", FamilyShareDestination.MORE.id)
        assertEquals(listOf("whatsapp", "status", "instagram", "copiar", "mais"), FamilyShareDestination.entries.map { it.id })
    }
}
