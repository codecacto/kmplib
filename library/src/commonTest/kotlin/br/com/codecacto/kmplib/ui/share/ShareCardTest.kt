package br.com.codecacto.kmplib.ui.share

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareCardTest {

    @Test
    fun storiesFormatHas9by16AspectRatio() {
        assertEquals(9f / 16f, ShareCardFormat.STORIES.aspectRatio)
    }

    @Test
    fun squareFormatHasUnitAspectRatio() {
        assertEquals(1f, ShareCardFormat.SQUARE.aspectRatio)
    }

    @Test
    fun storiesPixelSizeKeeps9by16Proportion() {
        val (w, h) = ShareCardFormat.STORIES.pixelSize(targetWidthPx = 1080)
        assertEquals(1080, w)
        assertEquals(1920, h) // 1080 * 16 / 9
    }

    @Test
    fun squarePixelSizeIsSquare() {
        val (w, h) = ShareCardFormat.SQUARE.pixelSize(targetWidthPx = 1080)
        assertEquals(1080, w)
        assertEquals(1080, h)
    }

    @Test
    fun solidBackgroundReportsIsSolid() {
        val bg = ShareCardBackground.solid(Color.White)
        assertTrue(bg.isSolid)
        assertEquals(bg.topColor, bg.bottomColor)
    }

    @Test
    fun gradientBackgroundWithDistinctColorsIsNotSolid() {
        val bg = ShareCardBackground.gradient(Color.White, Color.Black)
        assertFalse(bg.isSolid)
    }

    @Test
    fun specDefaultsToStoriesAndNoBrand() {
        val spec = ShareCardSpec(quote = "trecho", reference = "Salmo 1")
        assertEquals(ShareCardFormat.STORIES, spec.format)
        assertEquals("", spec.brand)
    }
}
