package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 2.309.0 (GAP-TB-M-04) — cores de marca por tema (`lightBrand`/`darkBrand`). Caso de origem: Todos a
 * Bordo, marca amarela `#FFC83D` ilegível como primária no tema claro (~1,5:1 sobre o branco).
 */
class AppBrandColorsTest {

    private val yellow = Color(0xFFFFC83D)
    private val navy = Color(0xFF1E2A4A)
    private val teal = Color(0xFF0F766E)

    private fun assertReadable(fg: Color, bg: Color, label: String) {
        val ratio = ColorContrast.contrastRatio(fg, bg)
        assertTrue(ratio >= ColorContrast.AA_TEXT, "$label: contraste $ratio < 4.5")
    }

    @Test
    fun `sem sobreposicao os esquemas sao identicos aos de antes`() {
        val p = AppColorPalette(primary = yellow, secondary = teal)
        val withNulls = p.copy(lightBrand = null, darkBrand = null)
        assertEquals(createLightColorScheme(p).toString(), createLightColorScheme(withNulls).toString())
        assertSame(p, p.forTheme(darkTheme = false))
        assertSame(p, p.forTheme(darkTheme = true))
        val light = createLightColorScheme(p)
        assertEquals(yellow, light.primary)
        assertEquals(yellow, light.surfaceTint)
        assertEquals(yellow.copy(alpha = 0.8f), light.inversePrimary)
        val dark = createDarkColorScheme(p)
        assertEquals(yellow.copy(alpha = 0.9f), dark.primary)
        assertEquals(yellow, dark.inversePrimary)
    }

    @Test
    fun `primaria do claro diferente da do escuro`() {
        val p = AppColorPalette(primary = yellow, lightBrand = AppBrandColors(primary = navy))
        val light = createLightColorScheme(p)
        val dark = createDarkColorScheme(p)
        assertEquals(navy, light.primary)
        assertEquals(navy, light.surfaceTint)
        assertEquals(yellow.copy(alpha = 0.9f), dark.primary)
        // A primária como TEXTO (TextButton, foco) agora é legível no claro.
        assertReadable(light.primary, light.background, "primary/background claro")
        assertReadable(light.primary, light.surface, "primary/surface claro")
        // onPrimary derivado sobre a primária efetiva de cada tema.
        assertReadable(light.onPrimary, navy, "onPrimary claro")
        assertReadable(dark.onPrimary, ColorContrast.compositeOver(yellow, 0.9f, Color(0xFF121212)), "onPrimary escuro")
        // Container tonal segue a primária do tema.
        assertEquals(navy.copy(alpha = 0.1f), light.primaryContainer)
        // Par invertido: inversePrimary é a primária do OUTRO tema.
        assertEquals(yellow.copy(alpha = 0.8f), light.inversePrimary)
        assertEquals(navy, dark.inversePrimary)
    }

    @Test
    fun `secundaria no default acompanha a primaria do tema`() {
        val p = AppColorPalette(primary = yellow, lightBrand = AppBrandColors(primary = navy))
        assertEquals(navy, createLightColorScheme(p).secondary)
        assertEquals(navy, createLightColorScheme(p).tertiary)
        assertEquals(yellow.copy(alpha = 0.9f), createDarkColorScheme(p).secondary)
    }

    @Test
    fun `secundaria explicita na paleta nao e arrastada`() {
        val p = AppColorPalette(primary = yellow, secondary = teal, lightBrand = AppBrandColors(primary = navy))
        assertEquals(teal, createLightColorScheme(p).secondary)
        assertEquals(navy, createLightColorScheme(p).tertiary)
    }

    @Test
    fun `secundaria por tema e onPrimary manual valem como vieram`() {
        val p = AppColorPalette(
            primary = navy,
            darkBrand = AppBrandColors(primary = yellow, onPrimary = Color.Black, secondary = teal, onSecondary = Color.White),
        )
        val dark = createDarkColorScheme(p)
        assertEquals(yellow.copy(alpha = 0.9f), dark.primary)
        assertEquals(Color.Black, dark.onPrimary)
        assertEquals(teal.copy(alpha = 0.9f), dark.secondary)
        assertEquals(Color.White, dark.onSecondary)
        // O claro fica com a paleta base.
        assertEquals(navy, createLightColorScheme(p).primary)
        assertEquals(navy, createLightColorScheme(p).secondary)
    }

    @Test
    fun `forTheme entrega a paleta resolvida para AppColors`() {
        val p = AppColorPalette(primary = yellow, lightBrand = AppBrandColors(primary = navy))
        val light = p.forTheme(darkTheme = false)
        assertEquals(navy, light.primary)
        assertEquals(navy, light.secondary)
        assertEquals(p.success, light.success)
        assertSame(p, p.forTheme(darkTheme = true))
    }

    @Test
    fun `aviso so para on manual abaixo de 4,5`() {
        val ok = AppColorPalette(primary = yellow, lightBrand = AppBrandColors(primary = navy))
        assertTrue(brandContrastWarnings(ok, darkTheme = false).isEmpty(), "derivado não avisa")
        val bad = AppColorPalette(
            primary = yellow,
            lightBrand = AppBrandColors(primary = navy, onPrimary = Color(0xFF333333)),
            darkBrand = AppBrandColors(onPrimary = Color.White),
        )
        val lw = brandContrastWarnings(bad, darkTheme = false)
        assertEquals(1, lw.size)
        assertTrue(lw.single().startsWith("onPrimary/primary"))
        // Branco sobre amarelo (a 90% no fundo escuro) também fica abaixo.
        assertEquals(1, brandContrastWarnings(bad, darkTheme = true).size)
        val good = AppColorPalette(primary = yellow, darkBrand = AppBrandColors(onPrimary = Color.Black))
        assertTrue(brandContrastWarnings(good, darkTheme = true).isEmpty())
        assertTrue(brandContrastWarnings(AppColorPalette(primary = yellow), darkTheme = false).isEmpty())
    }
}
