package br.com.codecacto.kmplib.familia

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import br.com.codecacto.kmplib.familia.screens.FamilyReminderTimes
import br.com.codecacto.kmplib.familia.screens.familyBestPlanId
import br.com.codecacto.kmplib.familia.screens.formatReminderTime
import br.com.codecacto.kmplib.familia.theme.FamilyApp
import br.com.codecacto.kmplib.familia.theme.FamilyFonts
import br.com.codecacto.kmplib.familia.theme.FamilyPalette
import br.com.codecacto.kmplib.familia.theme.FamilyPalettes
import br.com.codecacto.kmplib.familia.theme.FamilyTypography
import br.com.codecacto.kmplib.familia.theme.toAppColorPalette
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlan
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import br.com.codecacto.kmplib.ui.theme.TextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilyKitTest {

    private fun FamilyPalette.all(): List<Color> = listOf(bg, sf, sf2, fg, mu, ln, ac, aci, acs, acd, ac2)

    @Test
    fun `os 12 apps tem paleta clara e escura completas e diferentes`() {
        assertEquals(12, FamilyApp.entries.size)
        FamilyApp.entries.forEach { app ->
            val s = FamilyPalettes.of(app)
            assertEquals(11, s.light.all().size)
            s.light.all().forEach { assertEquals(1f, it.alpha, "$app claro") }
            s.dark.all().forEach { assertEquals(1f, it.alpha, "$app escuro") }
            assertNotEquals(s.light.bg, s.dark.bg, "$app: fundo igual nos dois temas")
        }
        // Valores do protótipo (amostra).
        assertEquals(Color(0xFFE8960F), FamilyPalettes.of(FamilyApp.VersiculoHoje).light.ac)
        assertEquals(Color(0xFFD4B06A), FamilyPalettes.of(FamilyApp.RosarioHoje).dark.ac)
        assertEquals(Color(0xFF6FB6E3), FamilyPalettes.of(FamilyApp.AtlasBiblico).dark.ac)
    }

    @Test
    fun `texto da paleta tem contraste AA com o fundo nos 24 temas`() {
        FamilyApp.entries.forEach { app ->
            listOf(FamilyPalettes.of(app).light, FamilyPalettes.of(app).dark).forEach { p ->
                assertTrue(ColorContrast.contrastRatio(p.fg, p.bg) >= 4.5, "$app fg/bg")
                assertTrue(ColorContrast.contrastRatio(p.fg, p.sf) >= 4.5, "$app fg/sf")
                assertTrue(ColorContrast.contrastRatio(p.mu, p.bg) >= 4.5, "$app mu/bg")
            }
        }
    }

    @Test
    fun `ponte para o AppTheme troca a cor de acao no escuro`() {
        val s = FamilyPalettes.of(FamilyApp.RosarioHoje)
        val p = s.toAppColorPalette()
        assertEquals(s.light.ac, p.lightBrand?.primary)
        assertEquals(s.dark.ac, p.darkBrand?.primary)
        assertEquals(s.dark.aci, p.darkBrand?.onPrimary)
        assertEquals(s.light.bg, p.lightSurfaces?.background)
        assertEquals(s.dark.sf2, p.darkSurfaces?.surfaceVariant)
        assertEquals(s.light.mu, p.lightSurfaces?.onSurfaceVariant)
    }

    @Test
    fun `escala nomeada segue o A menos A mais`() {
        val f = FontFamily.Default
        val fonts = FamilyFonts(f, f, f, f)
        val base = FamilyTypography.create(fonts, TextScale.STANDARD.factor)
        assertEquals(17f, base.body.fontSize.value, 0.01f)
        assertEquals(17f * 1.75f, base.h1.fontSize.value, 0.01f)
        assertEquals(17f * 1.6f, base.vs.fontSize.value, 0.01f)
        assertEquals(17f * 0.72f, base.tab.fontSize.value, 0.01f)
        val big = FamilyTypography.create(fonts, TextScale.LARGEST.factor)
        assertEquals(21f, big.body.fontSize.value, 0.01f)
        assertEquals(21f * 1.75f, big.h1.fontSize.value, 0.01f)
        assertEquals(21f * 2.6f, big.big.fontSize.value, 0.01f)
        assertEquals(19f, FamilyTypography.create(fonts, TextScale.LARGE.factor).body.fontSize.value, 0.01f)
    }

    @Test
    fun `selo melhor valor vai no de maior duracao disponivel`() {
        fun plan(id: String, months: Int?) = PaywallPlan(id = id, name = id, priceLabel = "x", durationMonths = months)
        assertEquals("anual", familyBestPlanId(listOf(plan("mensal", 1), plan("semestral", 6), plan("anual", 12))))
        assertEquals("semestral", familyBestPlanId(listOf(plan("mensal", 1), plan("semestral", 6))))
        assertEquals("mensal", familyBestPlanId(listOf(plan("mensal", 1))))
        assertNull(familyBestPlanId(emptyList()))
    }

    @Test
    fun `horarios do lembrete`() {
        assertEquals(listOf("05:30", "06:00", "06:30", "07:00", "08:00", "12:00", "18:00", "20:00", "21:00"), FamilyReminderTimes.map(::formatReminderTime))
        assertEquals("00:05", formatReminderTime(1445))
    }

    @Test
    fun `apps pelo slug e pelo nome do Nexus`() {
        assertEquals(FamilyApp.DiarioDeOracao, FamilyApp.fromSlug("diario-de-oracao"))
        assertEquals(FamilyApp.BibliaAberta, FamilyApp.matchName("biblia aberta"))
        assertNull(FamilyApp.matchName("Super 8"))
        assertEquals(12, FamilyApp.entries.map { it.slug }.toSet().size)
    }

    @Test
    fun `base pt-BR dos textos esta completa`() {
        assertEquals("Melhor valor", familiaBaseString("familia_best_value"))
        assertTrue(FAMILIA_BASE_STRING_COUNT >= 80)
    }
}
