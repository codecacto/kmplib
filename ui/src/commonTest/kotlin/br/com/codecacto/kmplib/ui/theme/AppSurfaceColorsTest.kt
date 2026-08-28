package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Abertura da paleta de superfícies (2.71.0). Cobre: retrocompatibilidade (sem superfícies = esquema
 * neutro anterior, byte-idêntico), aplicação exata dos tokens do design-system do Meu Barbeiro
 * (`#0A0A0A`/`#141414`), derivação coesa da escala de elevação, contraste garantido das cores `on*`
 * derivadas e a validação de contraste de superfícies passadas à mão.
 */
class AppSurfaceColorsTest {

    // Paleta do Meu Barbeiro (design-system.md §2) — preto predominante + roxo da marca.
    private val meuBarbeiroSurfaces = AppSurfaceColors(
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF141414),
        surfaceVariant = Color(0xFF1F1F1F),
        outline = Color(0xFF2E2E2E),
        outlineVariant = Color(0xFF3D3D3D),
        onSurface = Color(0xFFFAFAFA),
        onSurfaceVariant = Color(0xFFA1A1AA)
    )
    private val meuBarbeiroPalette = AppColorPalette(
        primary = Color(0xFF8B5CF6),
        darkSurfaces = meuBarbeiroSurfaces
    )

    // --- Retrocompatibilidade: nenhum app existente muda de aparência ---

    @Test
    fun `dark sem superficies mantem o esquema neutro anterior byte-identico`() {
        val scheme = createDarkColorScheme(AppColorPalette(primary = Color(0xFF6C63FF)))
        assertEquals(Color(0xFF121212), scheme.background)
        assertEquals(Color(0xFF1E1E1E), scheme.surface)
        assertEquals(Color(0xFF2C2C2C), scheme.surfaceVariant)
        assertEquals(Color(0xFF424242), scheme.outline)
        assertEquals(Color(0xFFE0E0E0), scheme.onSurface)
        assertEquals(Color(0xFFB0B0B0), scheme.onSurfaceVariant)
    }

    @Test
    fun `light sem superficies mantem o esquema neutro anterior byte-identico`() {
        val scheme = createLightColorScheme(AppColorPalette(primary = Color(0xFF6C63FF)))
        assertEquals(Color(0xFFFAFAFA), scheme.background)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color(0xFFF5F5F5), scheme.surfaceVariant)
        assertEquals(Color(0xFF1C1C1C), scheme.onSurface)
    }

    @Test
    fun `superficies nao afetam as cores de marca`() {
        val scheme = createDarkColorScheme(meuBarbeiroPalette)
        // primary continua derivada da marca (roxo @90% no dark), inalterada.
        assertEquals(Color(0xFF8B5CF6).copy(alpha = 0.9f), scheme.primary)
    }

    // --- Preto de verdade: tokens exatos do design-system ---

    @Test
    fun `dark com superficies do Meu Barbeiro aplica preto de verdade`() {
        val scheme = createDarkColorScheme(meuBarbeiroPalette)
        assertEquals(Color(0xFF0A0A0A), scheme.background)
        assertEquals(Color(0xFF141414), scheme.surface)
        assertEquals(Color(0xFF1F1F1F), scheme.surfaceVariant)
        assertEquals(Color(0xFF2E2E2E), scheme.outline)
        assertEquals(Color(0xFF3D3D3D), scheme.outlineVariant)
        assertEquals(Color(0xFFFAFAFA), scheme.onSurface)
        assertEquals(Color(0xFFA1A1AA), scheme.onSurfaceVariant)
    }

    @Test
    fun `escala de elevacao fica na familia preta (containers entre fundo e variante)`() {
        val scheme = createDarkColorScheme(meuBarbeiroPalette)
        // Nunca o cinza-roxo do baseline Material: containers ancorados no preto.
        assertEquals(Color(0xFF0A0A0A), scheme.surfaceContainerLowest)
        assertEquals(Color(0xFF1F1F1F), scheme.surfaceContainerHighest)
        assertEquals(Color(0xFF141414), scheme.surfaceContainer) // = surface
        // Degraus intermediários monotônicos entre fundo (#0A) e variante (#1F).
        assertTrue(scheme.surfaceContainerLow.red in Color(0xFF0A0A0A).red..Color(0xFF1F1F1F).red)
        assertTrue(scheme.surfaceContainerHigh.red in scheme.surfaceContainerLow.red..Color(0xFF1F1F1F).red)
    }

    // --- Contraste garantido nas cores derivadas ---

    @Test
    fun `onBackground derivado por contraste sai claro sobre fundo preto`() {
        val scheme = createDarkColorScheme(meuBarbeiroPalette)
        // onBackground não foi informado → derivado; deve ser claro e legível.
        assertTrue(ColorContrast.meetsTextContrast(scheme.onBackground, scheme.background))
    }

    @Test
    fun `fundo claro com on-color ausente deriva texto escuro (nunca claro sobre claro)`() {
        val palette = AppColorPalette(
            primary = Color(0xFF6C63FF),
            lightSurfaces = AppSurfaceColors(background = Color(0xFFFAFAFA), surface = Color.White)
        )
        val scheme = createLightColorScheme(palette)
        assertTrue(ColorContrast.meetsTextContrast(scheme.onBackground, scheme.background))
        assertTrue(ColorContrast.meetsTextContrast(scheme.onSurface, scheme.surface))
    }

    // --- Validação de contraste (debug) ---

    @Test
    fun `superficies do Meu Barbeiro nao geram nenhum aviso de contraste`() {
        assertTrue(surfaceContrastWarnings(meuBarbeiroSurfaces).isEmpty())
    }

    @Test
    fun `on-color claro passado a mao sobre fundo claro gera aviso`() {
        val bad = AppSurfaceColors(
            background = Color(0xFFFAFAFA),
            surface = Color.White,
            onBackground = Color(0xFFFFFFFF), // branco sobre quase-branco: ilegível
            onSurface = Color(0xFFFFFFFF)
        )
        val warnings = surfaceContrastWarnings(bad)
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.any { it.contains("onBackground") })
    }
}
