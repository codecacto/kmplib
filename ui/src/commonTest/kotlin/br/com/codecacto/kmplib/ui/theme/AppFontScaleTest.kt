package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da lógica pura de escala de fonte de acessibilidade: degraus de [AppFontScale],
 * [clampFontScale] e a multiplicação de [scaleTypography].
 */
class AppFontScaleTest {

    // --- AppFontScale (degraus) ---

    @Test
    fun `degraus tem os fatores esperados`() {
        assertEquals(0.9f, AppFontScale.Small.scale)
        assertEquals(1.0f, AppFontScale.Medium.scale)
        assertEquals(1.3f, AppFontScale.Large.scale)
        assertEquals(1.6f, AppFontScale.ExtraLarge.scale)
    }

    @Test
    fun `next e previous saturam nas pontas`() {
        assertEquals(AppFontScale.Medium, AppFontScale.Small.next())
        assertEquals(AppFontScale.ExtraLarge, AppFontScale.ExtraLarge.next())
        assertEquals(AppFontScale.Small, AppFontScale.Small.previous())
        assertEquals(AppFontScale.Large, AppFontScale.ExtraLarge.previous())
    }

    @Test
    fun `fromScale resolve o degrau mais proximo`() {
        assertEquals(AppFontScale.Medium, AppFontScale.fromScale(1.05f))
        assertEquals(AppFontScale.Large, AppFontScale.fromScale(1.25f))
        assertEquals(AppFontScale.ExtraLarge, AppFontScale.fromScale(2.0f))
        assertEquals(AppFontScale.Small, AppFontScale.fromScale(0.5f))
    }

    // --- clampFontScale ---

    @Test
    fun `clampFontScale respeita o intervalo`() {
        assertEquals(MIN_FONT_SCALE, clampFontScale(0.1f))
        assertEquals(MAX_FONT_SCALE, clampFontScale(9f))
        assertEquals(1.3f, clampFontScale(1.3f))
    }

    @Test
    fun `clampFontScale trata invalidos como 1f`() {
        assertEquals(1f, clampFontScale(Float.NaN))
        assertEquals(1f, clampFontScale(0f))
        assertEquals(1f, clampFontScale(-2f))
        assertEquals(1f, clampFontScale(Float.POSITIVE_INFINITY))
    }

    // --- scaleTypography ---

    @Test
    fun `scaleTypography com 1f devolve a mesma instancia`() {
        val base = createAppTypography()
        assertTrue(scaleTypography(base, 1f) === base)
    }

    @Test
    fun `scaleTypography multiplica o tamanho da fonte`() {
        val base = createAppTypography()
        val scaled = scaleTypography(base, 1.5f)
        assertEquals(base.bodyLarge.fontSize * 1.5f, scaled.bodyLarge.fontSize)
        assertEquals(base.titleLarge.fontSize * 1.5f, scaled.titleLarge.fontSize)
        assertEquals(base.labelSmall.fontSize * 1.5f, scaled.labelSmall.fontSize)
    }

    @Test
    fun `scaleTypography aplica o clamp ao fator`() {
        val base = createAppTypography()
        val scaled = scaleTypography(base, 99f)
        // 99f é clampado em MAX_FONT_SCALE.
        assertEquals(base.bodyLarge.fontSize * MAX_FONT_SCALE, scaled.bodyLarge.fontSize)
    }

    @Test
    fun `scaleTypography preserva peso e usa sp corretamente`() {
        val base = createAppTypography()
        val scaled = scaleTypography(base, 1.3f)
        assertEquals(base.titleLarge.fontWeight, scaled.titleLarge.fontWeight)
        // sanity: 16.sp * 1.3 aplicado ao bodyLarge (16.sp base)
        assertEquals(16.sp * 1.3f, scaled.bodyLarge.fontSize)
    }
}
