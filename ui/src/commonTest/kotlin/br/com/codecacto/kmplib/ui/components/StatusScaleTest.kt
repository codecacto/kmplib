package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusScaleTest {

    private val green = Color(0xFF10B981)
    private val amber = Color(0xFFF59E0B)
    private val red = Color(0xFFDC3545)
    private val yellow = Color(0xFFFACC15)
    private val white = Color(0xFFFFFFFF)

    private val scale = StatusScale(
        StatusLevel("ok", "Em dia", green, severity = 0),
        StatusLevel("30d", "30 dias", amber, severity = 3),
        StatusLevel("venc", "Vencido", red, severity = 4),
    )

    @Test
    fun level_lookup_andRequire() {
        assertEquals("30 dias", scale.level("30d")?.label)
        assertNull(scale.level("x"))
        assertFailsWith<IllegalArgumentException> { scale.require("x") }
    }

    @Test
    fun mostSevere_ignoresUnknown_andPicksHighest() {
        assertEquals("venc", scale.mostSevere(listOf("ok", "venc", "x", "30d"))?.key)
        assertEquals("ok", scale.mostSevere(listOf("ok"))?.key)
        assertNull(scale.mostSevere(listOf("x")))
        assertNull(scale.mostSevere(emptyList()))
    }

    @Test
    fun bySeverityDescending_isStable() {
        val s = StatusScale(
            StatusLevel("a", "A", green, severity = 1),
            StatusLevel("b", "B", amber, severity = 2),
            StatusLevel("c", "C", red, severity = 1),
        )
        assertEquals(listOf("b", "a", "c"), s.bySeverityDescending.map { it.key })
    }

    @Test
    fun scale_rejectsEmptyAndDuplicateKeys() {
        assertFailsWith<IllegalArgumentException> { StatusScale(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            StatusScale(StatusLevel("a", "A", green), StatusLevel("a", "B", red))
        }
    }

    @Test
    fun rampColors_hitsStopsAtEndsAndMiddle() {
        val ramp = rampColors(listOf(green, amber, red), 5)
        assertEquals(5, ramp.size)
        assertEquals(green, ramp[0])
        assertEquals(amber, ramp[2])
        assertEquals(red, ramp[4])
    }

    @Test
    fun rampColors_edgeCases() {
        assertEquals(listOf(green, green), rampColors(listOf(green), 2))
        assertEquals(listOf(green), rampColors(listOf(green, red), 1))
        assertFailsWith<IllegalArgumentException> { rampColors(emptyList(), 3) }
        assertFailsWith<IllegalArgumentException> { rampColors(listOf(green), 0) }
    }

    @Test
    fun tintedChip_textReachesAA_evenForYellow() {
        for (c in listOf(green, amber, red, yellow)) {
            val (content, background) = statusChipColors(c, StatusChipStyle.TINTED, white)
            assertTrue(
                ColorContrast.contrastRatio(content, background) >= ColorContrast.AA_TEXT,
                "cor $c: ${ColorContrast.contrastRatio(content, background)}",
            )
        }
    }

    @Test
    fun solidChip_contentIsBestOfLightDark() {
        val (content, background) = statusChipColors(yellow, StatusChipStyle.SOLID, white)
        assertEquals(yellow, background)
        assertEquals(ColorContrast.pickOnColor(yellow), content)
    }

    @Test
    fun adjustForContrast_keepsColorWhenAlreadyReadable() {
        val dark = Color(0xFF1B1B1F)
        assertEquals(dark, ColorContrast.adjustForContrast(dark, white))
    }

    @Test
    fun adjustForContrast_onDarkBackground_lightens() {
        val bg = Color(0xFF121212)
        val adjusted = ColorContrast.adjustForContrast(Color(0xFF3B0A0A), bg)
        assertTrue(ColorContrast.contrastRatio(adjusted, bg) >= ColorContrast.AA_TEXT)
        assertTrue(ColorContrast.relativeLuminance(adjusted) > ColorContrast.relativeLuminance(Color(0xFF3B0A0A)))
    }
}
