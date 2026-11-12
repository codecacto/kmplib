package br.com.codecacto.kmplib.familia

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.familia.components.familyPhoneFieldValue
import br.com.codecacto.kmplib.familia.components.familyShowsBanner
import br.com.codecacto.kmplib.familia.screens.FamilyMoreEntry
import br.com.codecacto.kmplib.familia.screens.FamilyMoreItem
import br.com.codecacto.kmplib.familia.screens.FamilyMoreSlot
import br.com.codecacto.kmplib.familia.screens.familyMoreEntries
import br.com.codecacto.kmplib.familia.screens.familyPremiumBenefits
import br.com.codecacto.kmplib.familia.theme.FamilyApp
import br.com.codecacto.kmplib.familia.theme.FamilyAppIdentity
import br.com.codecacto.kmplib.familia.theme.FamilyColorScheme
import br.com.codecacto.kmplib.familia.theme.FamilyPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** App SEM anúncio no kit, itens do app no "Mais" e campo de WhatsApp (2.359.0). */
class FamilyNoAdsTest {

    private fun palette() = FamilyPalette(
        bg = Color.White, sf = Color.White, sf2 = Color.White, fg = Color.Black, mu = Color.Gray, ln = Color.Gray,
        ac = Color.Blue, aci = Color.White, acs = Color.Cyan, acd = Color.Blue, ac2 = Color.Green,
    )
    private val icon = ImageVector.Builder(name = "t", defaultWidth = 100.dp, defaultHeight = 100.dp, viewportWidth = 100f, viewportHeight = 100f).build()
    private val scheme = FamilyColorScheme(light = palette(), dark = palette())

    @Test
    fun `identidade - os 12 cristaos e a de fora mostram anuncio por default, showsAds false desliga`() {
        assertTrue(FamilyApp.entries.all { it.showsAds })
        assertTrue(FamilyAppIdentity("x", "X", icon, scheme).showsAds)
        val semAnuncio = FamilyAppIdentity("chegou-aqui", "Chegou Aqui", icon, scheme, showsAds = false)
        assertFalse(semAnuncio.showsAds)
        assertNotEquals(FamilyAppIdentity("chegou-aqui", "Chegou Aqui", icon, scheme), semAnuncio)
    }

    @Test
    fun `banner - so com anuncio ligado no app e sem Premium`() {
        assertTrue(familyShowsBanner(appShowsAds = true, isPremium = false))
        assertFalse(familyShowsBanner(appShowsAds = true, isPremium = true))
        assertFalse(familyShowsBanner(appShowsAds = false, isPremium = false))
        assertFalse(familyShowsBanner(appShowsAds = false, isPremium = true))
    }

    @Test
    fun `beneficios - Sem anuncios primeiro so em app com anuncio`() {
        val extras = listOf("Entregas sem limite", "Relatório em PDF")
        assertEquals(listOf("Sem anúncios") + extras, familyPremiumBenefits("Sem anúncios", extras, adsRemovedBenefit = true))
        assertEquals(extras, familyPremiumBenefits("Sem anúncios", extras, adsRemovedBenefit = false))
        assertEquals(listOf("A"), familyPremiumBenefits("Sem anúncios", listOf("A", " ", "A"), adsRemovedBenefit = false))
        assertEquals(emptyList(), familyPremiumBenefits("Sem anúncios", emptyList(), adsRemovedBenefit = false))
    }

    private fun item(id: String, slot: FamilyMoreSlot = FamilyMoreSlot.BEFORE_PREMIUM) = FamilyMoreItem(id, id, icon, {}, slot = slot)

    private fun List<FamilyMoreEntry>.ids() = map {
        when (it) {
            FamilyMoreEntry.Premium -> "premium"
            FamilyMoreEntry.Settings -> "configuracoes"
            FamilyMoreEntry.OtherApps -> "outros-apps"
            FamilyMoreEntry.Feedback -> "feedback"
            FamilyMoreEntry.About -> "sobre"
            is FamilyMoreEntry.Extra -> "app:" + it.item.id
        }
    }

    @Test
    fun `Mais - sem itens do app a ordem da casa nao muda`() {
        assertEquals(listOf("premium", "configuracoes", "outros-apps", "feedback", "sobre"), familyMoreEntries(true, true, emptyList()).ids())
        assertEquals(listOf("configuracoes", "feedback", "sobre"), familyMoreEntries(false, false, emptyList()).ids())
    }

    @Test
    fun `Mais - itens do app entram no slot pedido, na ordem do app, sem repetir id, nunca depois de feedback`() {
        val extras = listOf(
            item("dados"),
            item("exportar", FamilyMoreSlot.AFTER_SETTINGS),
            item("ajuda", FamilyMoreSlot.BEFORE_FEEDBACK),
            item("contatos"),
            item("dados"),
            item("plano", FamilyMoreSlot.AFTER_PREMIUM),
        )
        assertEquals(
            listOf("app:dados", "app:contatos", "premium", "app:plano", "configuracoes", "app:exportar", "outros-apps", "app:ajuda", "feedback", "sobre"),
            familyMoreEntries(showPremium = true, showOtherApps = true, extraItems = extras).ids(),
        )
        // Sem Premium e sem Outros apps: os slots seguem no mesmo ponto da ordem.
        assertEquals(
            listOf("app:dados", "app:contatos", "app:plano", "configuracoes", "app:exportar", "app:ajuda", "feedback", "sobre"),
            familyMoreEntries(showPremium = false, showOtherApps = false, extraItems = extras).ids(),
        )
    }

    @Test
    fun `WhatsApp - valor de fora normalizado como o campo guarda`() {
        assertEquals("65999998888", familyPhoneFieldValue("+55 (65) 99999-8888", brazilian = true))
        assertEquals("65999998888", familyPhoneFieldValue("65999998888", brazilian = true))
        assertEquals("", familyPhoneFieldValue("", brazilian = true))
        assertEquals("+351912345678", familyPhoneFieldValue("+351 912 345 678", brazilian = false))
        assertEquals("4155550100", familyPhoneFieldValue("(415) 555-0100", brazilian = false))
    }
}
