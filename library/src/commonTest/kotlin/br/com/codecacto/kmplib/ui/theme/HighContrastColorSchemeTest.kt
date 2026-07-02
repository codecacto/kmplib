package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes dos valores-chave dos esquemas de **alto contraste** e da paleta [AppColorPalettes.Teal].
 *
 * O ponto do alto contraste (feedback do fundador) é ser **dramático e legítimo**: a `primary` NÃO é
 * mais a cor da marca (que era imperceptível no tema claro quase-branco), e sim quase-preto — assim
 * um tile `Normal` (que usa `primary`) muda de "teal" para "quase-preto + texto branco".
 */
class HighContrastColorSchemeTest {

    private val ink = Color(0xFF000000)
    private val paper = Color(0xFFFFFFFF)

    // --- Light alto contraste ---

    @Test
    fun `HC light primary e quase-preto (nao a cor da marca)`() {
        val hc = createHighContrastLightColorScheme(AppColorPalettes.Teal)
        assertEquals(Color(0xFF0A0A0A), hc.primary)
        assertEquals(paper, hc.onPrimary)
        // A primária do HC deve DIFERIR da primária da marca (era imperceptível).
        assertNotEquals(AppColorPalettes.Teal.primary, hc.primary)
    }

    @Test
    fun `HC light tem outline preto e superficies branco-preto`() {
        val hc = createHighContrastLightColorScheme(AppColorPalettes.Teal)
        assertEquals(ink, hc.outline)
        assertEquals(paper, hc.surface)
        assertEquals(ink, hc.onSurface)
        assertEquals(paper, hc.background)
        assertEquals(ink, hc.onBackground)
    }

    @Test
    fun `HC light tom Quick nao some (container claro com texto ink)`() {
        val hc = createHighContrastLightColorScheme(AppColorPalettes.Teal)
        assertEquals(ink, hc.onSecondaryContainer)
        assertNotEquals(paper, hc.secondaryContainer) // container distinto do fundo branco
        assertNotEquals(hc.secondaryContainer, hc.onSecondaryContainer)
    }

    @Test
    fun `HC light error e vermelho escuro forte com texto branco`() {
        val hc = createHighContrastLightColorScheme(AppColorPalettes.Teal)
        assertEquals(Color(0xFF8A0000), hc.error)
        assertEquals(paper, hc.onError)
    }

    // --- Dark alto contraste (coerência) ---

    @Test
    fun `HC dark primary e branco com texto preto`() {
        val hc = createHighContrastDarkColorScheme(AppColorPalettes.Teal)
        assertEquals(paper, hc.primary)
        assertEquals(ink, hc.onPrimary)
        assertEquals(paper, hc.outline)
        assertEquals(ink, hc.surface)
        assertEquals(paper, hc.onSurface)
    }

    // --- Paleta Teal ---

    @Test
    fun `paleta Teal tem teal escuro na primaria e secundaria harmonica`() {
        assertEquals(Color(0xFF0F766E), AppColorPalettes.Teal.primary)
        assertEquals(Color(0xFF0D9488), AppColorPalettes.Teal.secondary)
        assertEquals(Color(0xFFDC2626), AppColorPalettes.Teal.error)
        // primária e secundária são tons distintos (harmônicos, não iguais).
        assertNotEquals(AppColorPalettes.Teal.primary, AppColorPalettes.Teal.secondary)
    }

    @Test
    fun `tema claro normal da Teal preenche Normal com teal (nao branco)`() {
        val normal = createLightColorScheme(AppColorPalettes.Teal)
        // No tema NORMAL, o tile Normal (primary) é colorido, não a "parede branca".
        assertEquals(AppColorPalettes.Teal.primary, normal.primary)
        assertNotEquals(Color.White, normal.primary)
        assertEquals(Color.White, normal.onPrimary)
        assertTrue(normal.primary != normal.surface)
    }
}
