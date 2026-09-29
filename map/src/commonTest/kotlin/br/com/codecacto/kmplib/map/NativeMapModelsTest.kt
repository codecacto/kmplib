package br.com.codecacto.kmplib.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NativeMapModelsTest {

    private val cuiaba = LatLng(-15.60, -56.10)
    private val varzea = LatLng(-15.65, -56.13)
    private val primary = Color(0xFF1E40AF)

    // --- LatLngBounds ------------------------------------------------------------------------

    @Test
    fun bounds_ofEmpty_isNull() {
        assertNull(LatLngBounds.of(emptyList()))
    }

    @Test
    fun bounds_contains_allPoints() {
        val pts = listOf(cuiaba, varzea, LatLng(-15.55, -56.20))
        val b = LatLngBounds.of(pts)!!
        assertEquals(LatLng(-15.65, -56.20), b.southwest)
        assertEquals(LatLng(-15.55, -56.10), b.northeast)
        pts.forEach { assertTrue(b.contains(it)) }
        assertFalse(b.isSinglePoint)
    }

    @Test
    fun bounds_ignoresInvalidCoordinates() {
        val b = LatLngBounds.of(listOf(LatLng(Double.NaN, 0.0), LatLng(200.0, 10.0), cuiaba))!!
        assertTrue(b.isSinglePoint)
        assertEquals(cuiaba, b.center)
    }

    @Test
    fun bounds_onlyInvalid_isNull() {
        assertNull(LatLngBounds.of(listOf(LatLng(Double.NaN, Double.NaN))))
    }

    // --- glyph ---------------------------------------------------------------------------------

    @Test
    fun glyph_blankIsNull_andTrimmed() {
        assertNull(limitGlyph(null))
        assertNull(limitGlyph("   "))
        assertEquals("12", limitGlyph(" 12 "))
    }

    @Test
    fun glyph_cutsAtThreeVisibleChars_withoutSplittingEmoji() {
        assertEquals("123", limitGlyph("12345"))
        val fire = "🔥" // 🔥 (par substituto)
        assertEquals("$fire$fire$fire", limitGlyph("$fire$fire$fire$fire"))
        assertEquals("a$fire", limitGlyph("a$fire", max = 2))
    }

    // --- grupo ---------------------------------------------------------------------------------

    @Test
    fun clusterRepresentative_isMostSevere_firstOnTie() {
        val a = MapItem("a", cuiaba, priority = 1)
        val b = MapItem("b", cuiaba, priority = 4)
        val c = MapItem("c", cuiaba, priority = 4)
        assertSame(b, clusterRepresentative(listOf(a, b, c)))
        assertNull(clusterRepresentative(emptyList()))
    }

    @Test
    fun clusterCountLabel_capsAt99() {
        assertEquals("2", clusterCountLabel(2))
        assertEquals("99", clusterCountLabel(99))
        assertEquals("99+", clusterCountLabel(100))
    }

    @Test
    fun resolveCluster_takesColorOfMostSevere_withReadableLabel() {
        val red = Color(0xFFDC3545)
        val green = Color(0xFF10B981)
        val members = listOf(
            resolveMarker(MapItem("ok", cuiaba, style = MapMarkerStyle(green), priority = 0), primary),
            resolveMarker(MapItem("venc", varzea, style = MapMarkerStyle(red), priority = 4), primary),
        )
        val cluster = resolveCluster(members, primary)
        assertEquals(red, cluster.color)
        assertEquals("2", cluster.label)
        assertEquals(ColorContrast.pickOnColor(red), cluster.labelColor)
    }

    @Test
    fun defaultClusterTap_spreadZoomsIn_colocatedOpensMostSevere() {
        val spread = listOf(MapItem("a", cuiaba), MapItem("b", varzea))
        assertIs<ClusterTapAction.ZoomIn>(defaultClusterTap(spread))

        val worst = MapItem("w", cuiaba, priority = 9)
        val same = listOf(MapItem("a", cuiaba), worst, MapItem("c", cuiaba))
        val action = defaultClusterTap(same)
        assertIs<ClusterTapAction.OpenItem>(action)
        assertSame(worst, action.item)

        assertNull(defaultClusterTap(emptyList()))
    }

    // --- aparência -----------------------------------------------------------------------------

    @Test
    fun resolveMarker_defaultsToPrimary_andPicksContrastingGlyph() {
        val r = resolveMarker(MapItem("x", cuiaba), primary)
        assertEquals(primary, r.color)
        assertNull(r.glyph)
        assertTrue(ColorContrast.contrastRatio(r.glyphColor, r.color) >= ColorContrast.AA_GRAPHIC)
    }

    @Test
    fun resolveMarker_keepsExplicitGlyphColor_andDropsAlpha() {
        val style = MapMarkerStyle(Color(0x80FF0000), glyph = "1234", glyphColor = Color.Black)
        val r = resolveMarker(MapItem("x", cuiaba, style = style), primary)
        assertEquals(1f, r.color.alpha)
        assertEquals("123", r.glyph)
        assertEquals(Color.Black, r.glyphColor)
    }

    @Test
    fun resolveMarker_yellowGetsDarkGlyph() {
        val r = resolveMarker(MapItem("x", cuiaba, style = MapMarkerStyle(Color(0xFFFACC15), "!")), primary)
        assertTrue(ColorContrast.relativeLuminance(r.glyphColor) < 0.2)
    }

    // --- zoom ↔ extensão -----------------------------------------------------------------------

    @Test
    fun zoomAndSpan_areInverse() {
        for (zoom in listOf(3f, 8.5f, 12f, 15f, 18f)) {
            val span = longitudeSpanForZoom(zoom, 390.0)
            assertTrue(abs(zoomForLongitudeSpan(span, 390.0) - zoom) < 0.001f, "zoom $zoom")
        }
    }

    @Test
    fun zoomZero_showsWholeWorldOnTileWidth() {
        assertEquals(360.0, longitudeSpanForZoom(0f, 256.0), 1e-9)
    }

    @Test
    fun zoomForSpan_degenerateInputs_areClamped() {
        assertEquals(21f, zoomForLongitudeSpan(0.0, 390.0))
        assertEquals(21f, zoomForLongitudeSpan(1.0, 0.0))
        assertEquals(0f, zoomForLongitudeSpan(1e6, 256.0))
    }

    @Test
    fun latitudeSpan_shrinksWithLatitude() {
        assertEquals(10.0, latitudeSpanFor(10.0, 0.0), 1e-9)
        assertTrue(latitudeSpanFor(10.0, 60.0) < 5.01)
    }

    // --- enquadramento e controller ------------------------------------------------------------

    @Test
    fun fitCommand_emptyIsNull_singlePointMoves_manyFits() {
        assertNull(fitCommandFor(emptyList(), 48.dp, 15f, true))

        val single = fitCommandFor(listOf(cuiaba, cuiaba), 48.dp, 15f, false)
        assertIs<MapCameraCommand.Move>(single)
        assertEquals(CameraPosition(cuiaba, 15f), single.position)
        assertFalse(single.animate)

        val many = fitCommandFor(listOf(cuiaba, varzea), 32.dp, 16f, true)
        assertIs<MapCameraCommand.Fit>(many)
        assertEquals(32f, many.paddingDp)
        assertEquals(16f, many.maxZoom)
    }

    @Test
    fun controller_keepsLastCommandUntilAttached_thenForwards() {
        val controller = MapController(CameraPosition(cuiaba, 5f))
        val received = mutableListOf<MapCameraCommand>()
        val sink = MapCameraSink { received += it }

        controller.moveTo(varzea, zoom = 10f)
        controller.fitTo(listOf(cuiaba, varzea))
        assertIs<MapCameraCommand.Fit>(controller.pending)

        controller.attach(sink)
        assertEquals(1, received.size)
        assertIs<MapCameraCommand.Fit>(received.single())
        assertNull(controller.pending)

        controller.moveTo(cuiaba, zoom = 12f, animate = false)
        assertEquals(MapCameraCommand.Move(CameraPosition(cuiaba, 12f), false), received.last())
    }

    @Test
    fun controller_detach_onlyBySameSink_andQueuesAgain() {
        val controller = MapController(CameraPosition(cuiaba, 5f))
        val a = MapCameraSink { }
        val b = MapCameraSink { }
        controller.attach(a)
        controller.detach(b)
        assertTrue(controller.isAttached)
        controller.detach(a)
        assertFalse(controller.isAttached)
        controller.moveTo(varzea)
        assertIs<MapCameraCommand.Move>(controller.pending)
    }

    @Test
    fun controller_fitToEmpty_doesNothing() {
        val controller = MapController(CameraPosition(cuiaba, 5f))
        controller.fitTo(emptyList())
        assertNull(controller.pending)
    }
}
