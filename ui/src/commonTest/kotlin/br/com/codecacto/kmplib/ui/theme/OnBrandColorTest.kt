package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2.262.2 — `onPrimary`/`onSecondary`/`onTertiary`/`onError` escolhidos por contraste WCAG sobre a cor
 * de marca EXIBIDA (no escuro, a marca a 90% composta sobre o fundo), nos temas claro e escuro.
 * Caso de origem: Minha Ficha (08/out) — vermelho `#DC2626` com texto `#1C1C1C` (~3,6:1) e
 * secundária `#111827` com texto `#1C1C1C` (invisível) no tema escuro.
 */
class OnBrandColorTest {

    private val white = Color.White
    private val nearBlack = Color(0xFF1C1C1C)

    private val red = Color(0xFFDC2626)
    private val amber = Color(0xFFF59E0B)
    private val navy = Color(0xFF111827)
    private val green = Color(0xFF10B981)
    private val yellow = Color(0xFFFACC15)

    private fun darkFill(scheme: ColorScheme, brand: Color, backdrop: Color = Color(0xFF121212)) =
        ColorContrast.compositeOver(brand, 0.9f, backdrop)

    private fun assertReadable(fg: Color, bg: Color, label: String) {
        val ratio = ColorContrast.contrastRatio(fg, bg)
        assertTrue(ratio >= ColorContrast.AA_TEXT, "$label: contraste $ratio < 4.5")
    }

    // --- casos esperados, nos dois temas ---

    @Test
    fun `vermelho leva texto branco nos dois temas`() {
        val p = AppColorPalette(primary = red, error = red)
        val dark = createDarkColorScheme(p)
        val light = createLightColorScheme(p)
        assertEquals(white, dark.onPrimary)
        assertEquals(white, dark.onError)
        assertEquals(white, light.onPrimary)
        assertReadable(dark.onPrimary, darkFill(dark, red), "dark vermelho")
        assertReadable(light.onPrimary, red, "light vermelho")
    }

    @Test
    fun `azul-escuro leva texto branco — o caso que sumia no tema escuro`() {
        val p = AppColorPalette(primary = red, secondary = navy)
        val dark = createDarkColorScheme(p)
        val light = createLightColorScheme(p)
        assertEquals(white, dark.onSecondary)
        assertEquals(white, light.onSecondary)
        assertReadable(dark.onSecondary, darkFill(dark, navy), "dark navy")
    }

    @Test
    fun `ambar verde e amarelo levam quase-preto nos dois temas`() {
        for (brand in listOf(amber, green, yellow)) {
            val p = AppColorPalette(primary = brand, tertiary = brand)
            val dark = createDarkColorScheme(p)
            val light = createLightColorScheme(p)
            assertEquals(nearBlack, dark.onPrimary, "dark $brand")
            assertEquals(nearBlack, light.onPrimary, "light $brand")
            assertEquals(nearBlack, light.onTertiary, "light tertiary $brand")
            assertReadable(dark.onPrimary, darkFill(dark, brand), "dark $brand")
            assertReadable(light.onPrimary, brand, "light $brand")
        }
    }

    @Test
    fun `escuro mede sobre o fundo das superficies proprias do app`() {
        val backdrop = Color(0xFF0A0A0A)
        val p = AppColorPalette(primary = navy, darkSurfaces = AppSurfaceColors(background = backdrop))
        val dark = createDarkColorScheme(p)
        assertEquals(white, dark.onPrimary)
        assertReadable(dark.onPrimary, darkFill(dark, navy, backdrop), "dark navy preto")
    }

    @Test
    fun `paletas prontas da lib ficam legiveis nos dois temas`() {
        val palettes = listOf(
            AppColorPalettes.Default, AppColorPalettes.Orange, AppColorPalettes.Green,
            AppColorPalettes.Blue, AppColorPalettes.Pink, AppColorPalettes.Red, AppColorPalettes.Teal,
        )
        for (p in palettes) {
            val light = createLightColorScheme(p)
            assertReadable(light.onPrimary, p.primary, "light ${p.primary}")
            assertReadable(light.onSecondary, p.secondary, "light sec ${p.secondary}")
            assertReadable(light.onError, p.error, "light err ${p.error}")
            val dark = createDarkColorScheme(p)
            assertReadable(dark.onPrimary, darkFill(dark, p.primary), "dark ${p.primary}")
            assertReadable(dark.onError, darkFill(dark, p.error), "dark err ${p.error}")
        }
    }

    // --- garantia geral: nenhuma cor fica abaixo de 4,5:1 ---

    @Test
    fun `qualquer cinza da escala inteira atinge 4_5 — inclusive a faixa media`() {
        for (v in 0..255) {
            val gray = Color(red = v / 255f, green = v / 255f, blue = v / 255f)
            val on = onBrandColorFor(gray)
            assertReadable(on, gray, "cinza $v")
        }
    }

    @Test
    fun `varredura de matizes atinge 4_5 nos dois temas`() {
        val steps = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        for (r in steps) for (g in steps) for (b in steps) {
            val brand = Color(r, g, b)
            assertReadable(onBrandColorFor(brand), brand, "light $brand")
            val p = AppColorPalette(primary = brand)
            val dark = createDarkColorScheme(p)
            assertReadable(dark.onPrimary, darkFill(dark, brand), "dark $brand")
        }
    }

    @Test
    fun `faixa media cai para preto puro quando o quase-preto nao alcanca`() {
        // luminância ~0,18: branco ~4,6:1, #1C1C1C ~4,1:1 — nenhum dos dois de antes basta.
        val mid = Color(0xFF777777)
        val on = onBrandColorFor(mid)
        assertReadable(on, mid, "cinza medio")
    }
}
