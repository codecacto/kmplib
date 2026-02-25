package br.com.codecacto.kmplib.ui

import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.screens.LoginColors
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes para a classe LoginColors
 */
class LoginColorsTest {

    @Test
    fun `test default colors`() {
        val colors = LoginColors()

        assertEquals(Color(0xFF6C63FF), colors.primary)
        assertEquals(null, colors.secondary)
        assertEquals(Color.White, colors.onPrimary)
        assertEquals(Color(0xFFF5F5F5), colors.background)
        assertEquals(Color.White, colors.surface)
        assertEquals(Color(0xFFD32F2F), colors.error)
        assertEquals(Color(0xFF1A1A1A), colors.textPrimary)
        assertEquals(Color(0xFF757575), colors.textSecondary)
        assertEquals(Color(0xFFE0E0E0), colors.border)
    }

    @Test
    fun `test custom primary color`() {
        val customPrimary = Color(0xFF10B981)
        val colors = LoginColors(primary = customPrimary)

        assertEquals(customPrimary, colors.primary)
    }

    @Test
    fun `test custom secondary color`() {
        val customSecondary = Color(0xFFF59E0B)
        val colors = LoginColors(secondary = customSecondary)

        assertEquals(customSecondary, colors.secondary)
    }

    @Test
    fun `test all custom colors`() {
        val colors = LoginColors(
            primary = Color(0xFF10B981),
            secondary = Color(0xFFF59E0B),
            onPrimary = Color.Black,
            background = Color(0xFF1A1A2E),
            surface = Color(0xFF2E2E3E),
            error = Color(0xFFFF2D2D),
            textPrimary = Color.White,
            textSecondary = Color(0xFFCCCCCC),
            border = Color(0xFF444444)
        )

        assertEquals(Color(0xFF10B981), colors.primary)
        assertEquals(Color(0xFFF59E0B), colors.secondary)
        assertEquals(Color.Black, colors.onPrimary)
        assertEquals(Color(0xFF1A1A2E), colors.background)
        assertEquals(Color(0xFF2E2E3E), colors.surface)
        assertEquals(Color(0xFFFF2D2D), colors.error)
        assertEquals(Color.White, colors.textPrimary)
        assertEquals(Color(0xFFCCCCCC), colors.textSecondary)
        assertEquals(Color(0xFF444444), colors.border)
    }

    @Test
    fun `test copy with new primary color`() {
        val original = LoginColors()
        val newPrimary = Color(0xFFFF5722)
        val updated = original.copy(primary = newPrimary)

        assertEquals(newPrimary, updated.primary)
        assertEquals(original.secondary, updated.secondary)
        assertEquals(original.onPrimary, updated.onPrimary)
    }

    @Test
    fun `test multiple color themes`() {
        // Tema Locadora (Laranja)
        val locadoraColors = LoginColors(
            primary = Color(0xFFF97316),
            secondary = Color(0xFF8B5CF6)
        )
        assertEquals(Color(0xFFF97316), locadoraColors.primary)
        assertEquals(Color(0xFF8B5CF6), locadoraColors.secondary)

        // Tema Meu Advogado (Verde Esmeralda)
        val advogadoColors = LoginColors(
            primary = Color(0xFF10B981),
            secondary = Color(0xFFF59E0B)
        )
        assertEquals(Color(0xFF10B981), advogadoColors.primary)
        assertEquals(Color(0xFFF59E0B), advogadoColors.secondary)

        // Tema TechPromos (Roxo)
        val techPromosColors = LoginColors(
            primary = Color(0xFF6C63FF),
            secondary = Color(0xFF002C4A)
        )
        assertEquals(Color(0xFF6C63FF), techPromosColors.primary)
        assertEquals(Color(0xFF002C4A), techPromosColors.secondary)
    }

    @Test
    fun `test dark theme colors`() {
        val darkColors = LoginColors(
            background = Color(0xFF1A1A2E),
            surface = Color(0xFF1A1A2E),
            textPrimary = Color.White,
            textSecondary = Color(0xFFB0B0B0),
            border = Color(0xFF444444)
        )

        assertEquals(Color(0xFF1A1A2E), darkColors.background)
        assertEquals(Color(0xFF1A1A2E), darkColors.surface)
        assertEquals(Color.White, darkColors.textPrimary)
        assertEquals(Color(0xFFB0B0B0), darkColors.textSecondary)
        assertEquals(Color(0xFF444444), darkColors.border)
    }
}
