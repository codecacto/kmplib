package br.com.codecacto.kmplib.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2.262.3 — `on*Container` = a marca escurecida (claro) / clareada (escuro) até ≥ 4,5:1 sobre o container
 * COMO APARECE (marca translúcida composta sobre o fundo E sobre a superfície). Caso de origem: Sinaleiro
 * (08/out), secundária amarela `#EAB308` sobre o próprio container a 10% (~1,7:1) no cartão "Quick".
 */
class OnBrandContainerColorTest {

    private val brands = listOf(
        Color(0xFFEAB308), // amarelo — o caso Sinaleiro
        Color(0xFFFACC15), // amarelo claro
        Color(0xFFF59E0B), // âmbar
        Color(0xFF84CC16), // verde-limão
        Color(0xFF22D3EE), // ciano claro
        Color(0xFFFFFFFF), // branco (marca extrema)
        Color(0xFF111827), // azul-marinho
        Color(0xFF000000), // preto (marca extrema)
        Color(0xFF7C2D12), // marrom escuro
        Color(0xFFDC2626), // vermelho
        Color(0xFF6C63FF), // roxo default
    )

    private val lightBackdrops = listOf(Color(0xFFFAFAFA), Color.White)
    private val darkBackdrops = listOf(Color(0xFF121212), Color(0xFF1E1E1E))

    private fun pairs(s: ColorScheme) = listOf(
        "primary" to (s.primaryContainer to s.onPrimaryContainer),
        "secondary" to (s.secondaryContainer to s.onSecondaryContainer),
        "tertiary" to (s.tertiaryContainer to s.onTertiaryContainer),
        "error" to (s.errorContainer to s.onErrorContainer),
    )

    private fun assertAllReadable(s: ColorScheme, backdrops: List<Color>, label: String) {
        for ((name, pair) in pairs(s)) {
            val (container, on) = pair
            assertEquals(1f, on.alpha, "$label $name: on*Container deve ser opaco")
            for (bd in backdrops) {
                val shown = ColorContrast.compositeOver(container, 1f, bd)
                val ratio = ColorContrast.contrastRatio(on, shown)
                assertTrue(ratio >= ColorContrast.AA_TEXT, "$label $name sobre $bd: $ratio < 4.5")
            }
        }
    }

    private fun palette(brand: Color, surfaces: Boolean = false) = AppColorPalette(
        primary = brand, secondary = brand, tertiary = brand, error = brand,
        darkSurfaces = if (surfaces) AppSurfaceColors(background = Color(0xFF0A0A0A), surface = Color(0xFF141414)) else null,
        lightSurfaces = if (surfaces) AppSurfaceColors(background = Color(0xFFFFF8E1), surface = Color(0xFFFFFDF5)) else null,
    )

    @Test
    fun `caso Sinaleiro — amarelo no container a 10 por cento passa de 1,7 para 4,5`() {
        val yellow = Color(0xFFEAB308)
        val s = createLightColorScheme(AppColorPalette(primary = Color(0xFF1D4ED8), secondary = yellow))
        val shown = ColorContrast.compositeOver(s.secondaryContainer, 1f, Color.White)
        assertTrue(ColorContrast.contrastRatio(yellow, shown) < 2.0, "pré-condição: a marca crua era ilegível")
        assertTrue(ColorContrast.contrastRatio(s.onSecondaryContainer, shown) >= ColorContrast.AA_TEXT)
    }

    @Test
    fun `marcas claras e escuras ficam legiveis nos dois temas`() {
        for (brand in brands) {
            assertAllReadable(createLightColorScheme(palette(brand)), lightBackdrops, "light $brand")
            assertAllReadable(createDarkColorScheme(palette(brand)), darkBackdrops, "dark $brand")
        }
    }

    @Test
    fun `com superficies proprias o contraste se mede nelas`() {
        for (brand in brands) {
            assertAllReadable(createLightColorScheme(palette(brand, true)),
                listOf(Color(0xFFFFF8E1), Color(0xFFFFFDF5)), "light-surf $brand")
            assertAllReadable(createDarkColorScheme(palette(brand, true)),
                listOf(Color(0xFF0A0A0A), Color(0xFF141414)), "dark-surf $brand")
        }
    }

    @Test
    fun `paletas prontas ficam legiveis nos dois temas`() {
        val ready = with(AppColorPalettes) { listOf(Default, Orange, Green, Blue, Pink, Red, Teal) }
        for (p in ready) {
            assertAllReadable(createLightColorScheme(p), lightBackdrops, "light ${p.primary}")
            assertAllReadable(createDarkColorScheme(p), darkBackdrops, "dark ${p.primary}")
        }
    }

    @Test
    fun `marca que ja contrasta volta intacta`() {
        val navy = Color(0xFF1E3A8A)
        assertEquals(navy, createLightColorScheme(AppColorPalette(primary = navy)).onPrimaryContainer)
        val sky = Color(0xFF7DD3FC)
        assertEquals(sky, createDarkColorScheme(AppColorPalette(primary = sky)).onPrimaryContainer)
    }

    @Test
    fun `claro escurece e escuro clareia, preservando o matiz`() {
        val yellow = Color(0xFFEAB308)
        val light = createLightColorScheme(AppColorPalette(primary = yellow)).onPrimaryContainer
        val dark = createDarkColorScheme(AppColorPalette(primary = Color(0xFF7C2D12))).onPrimaryContainer
        assertTrue(ColorContrast.relativeLuminance(light) < ColorContrast.relativeLuminance(yellow))
        assertTrue(ColorContrast.relativeLuminance(dark) > ColorContrast.relativeLuminance(Color(0xFF7C2D12)))
        // Matiz preservado: o amarelo escurecido continua com vermelho ≥ verde > azul.
        assertTrue(light.red >= light.green && light.green > light.blue, "matiz amarelo perdido: $light")
        assertTrue(abs(light.red - light.blue) > 0.1f, "virou cinza: $light")
    }
}
