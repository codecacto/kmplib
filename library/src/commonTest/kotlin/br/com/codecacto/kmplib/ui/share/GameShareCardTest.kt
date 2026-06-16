package br.com.codecacto.kmplib.ui.share

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameShareCardTest {

    private fun styleOf() = GameShareCardStyle(
        background = ShareCardBackground.gradient(Color.White, Color.Black),
        accentColor = Color.Red,
        onAccentColor = Color.White,
        titleColor = Color.Black,
        narrativeColor = Color.Gray,
        brandColor = Color.Gray,
        disclaimerColor = Color.Gray,
    )

    @Test
    fun specDefaultsToSquareThreeColumnsAndEmptyTexts() {
        val spec = GameShareCardSpec(numbers = listOf(4, 11, 23, 37, 48, 55))
        assertEquals(ShareCardFormat.SQUARE, spec.format)
        assertEquals(3, spec.columns)
        assertEquals("", spec.label)
        assertEquals("", spec.icon)
        assertEquals("", spec.narrative)
        assertEquals("", spec.brand)
        assertEquals("", spec.disclaimer)
    }

    @Test
    fun specPreservesNumberOrder() {
        val numbers = listOf(55, 4, 23, 11, 48, 37)
        val spec = GameShareCardSpec(numbers = numbers)
        assertEquals(numbers, spec.numbers)
    }

    @Test
    fun specSupportsStoriesFormat() {
        val spec = GameShareCardSpec(numbers = listOf(1, 2, 3), format = ShareCardFormat.STORIES)
        assertEquals(ShareCardFormat.STORIES, spec.format)
        assertEquals(9f / 16f, spec.format.aspectRatio)
    }

    @Test
    fun styleFromColorSchemeKeepsExplicitAccent() {
        val style = styleOf()
        assertEquals(Color.Red, style.accentColor)
        assertEquals(Color.White, style.onAccentColor)
    }

    @Test
    fun gradientBackgroundIsNotSolid() {
        assertFalse(styleOf().background.isSolid)
    }

    @Test
    fun solidBackgroundIsSolid() {
        val s = styleOf().copy(background = ShareCardBackground.solid(Color.White))
        assertTrue(s.background.isSolid)
    }

    @Test
    fun copyAllowsOverridingDisclaimerForCompliance() {
        val spec = GameShareCardSpec(numbers = listOf(1, 2, 3, 4, 5, 6))
            .copy(disclaimer = "Apenas diversão — não garante prêmios. +18.")
        assertEquals("Apenas diversão — não garante prêmios. +18.", spec.disclaimer)
    }

    @Test
    fun layoutConstantsAreSane() {
        assertTrue(GameShareCardLayout.BALL_SIZE_DP > 48f) // alvo a11y persona 55+
        assertTrue(GameShareCardLayout.CORNER_RADIUS_DP > 0f)
        assertTrue(GameShareCardLayout.PADDING_H_DP > 0f)
    }
}
