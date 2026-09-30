package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTypographyFamiliesTest {

    @Test
    fun umaFamiliaSoContinuaEmTudo() {
        val t = createAppTypography(FontFamily.Serif)
        listOf(t.displayLarge, t.headlineMedium, t.titleLarge, t.titleMedium, t.bodyLarge, t.labelSmall)
            .forEach { assertEquals(FontFamily.Serif, it.fontFamily) }
    }

    @Test
    fun duasFamiliasSeguemOPapelBrandDoMaterial3() {
        val t = createAppTypography(fontFamily = FontFamily.SansSerif, displayFontFamily = FontFamily.Serif)
        // Brand: display, headline e titleLarge.
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge,
        ).forEach { assertEquals(FontFamily.Serif, it.fontFamily) }
        // Plain: titleMedium/Small, body e label.
        listOf(
            t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEach { assertEquals(FontFamily.SansSerif, it.fontFamily) }
    }
}
