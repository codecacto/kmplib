package br.com.codecacto.kmplib.map.osm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsmLeafletHtmlTest {

    private fun spec(
        markers: List<OsmMarker> = emptyList(),
        center: OsmLatLng = OsmDefaults.BRAZIL_CENTER,
        zoom: Float = 12f,
        interactive: Boolean = true,
        pickable: Boolean = false,
    ) = OsmMapSpec(markers, center, zoom, interactive, pickable)

    @Test
    fun build_includesLeafletAndOsmTiles_noApiKey() {
        val html = OsmLeafletHtml.build(spec(), withWebkitShim = false)
        assertTrue(html.contains("leaflet@1.9.4/dist/leaflet.js"), "deve carregar Leaflet")
        assertTrue(html.contains("tile.openstreetmap.org"), "deve usar tiles do OSM")
        // Nenhuma chave de API no template.
        assertTrue(!html.contains("key=") && !html.contains("apiKey"), "não deve haver chave de API")
    }

    @Test
    fun build_setsCenterAndZoom() {
        val html = OsmLeafletHtml.build(spec(center = OsmLatLng(-10.0, -50.5), zoom = 15f), withWebkitShim = false)
        assertTrue(html.contains("setView([-10.0, -50.5], 15.0)"))
    }

    @Test
    fun build_rendersMarkersWithIdAndTitle() {
        val html = OsmLeafletHtml.build(
            spec(markers = listOf(OsmMarker("c1", OsmLatLng(1.0, 2.0), "Ana"))),
            withWebkitShim = false,
        )
        assertTrue(html.contains("\"id\":\"c1\""))
        assertTrue(html.contains("\"lat\":1.0"))
        assertTrue(html.contains("\"title\":\"Ana\""))
    }

    @Test
    fun build_pickable_addsMapClickHandler() {
        val pick = OsmLeafletHtml.build(spec(pickable = true), withWebkitShim = false)
        val noPick = OsmLeafletHtml.build(spec(pickable = false), withWebkitShim = false)
        assertTrue(pick.contains("map.on('click'"))
        assertTrue(!noPick.contains("map.on('click'"))
    }

    @Test
    fun build_interactiveTogglesGestures() {
        val on = OsmLeafletHtml.build(spec(interactive = true), withWebkitShim = false)
        val off = OsmLeafletHtml.build(spec(interactive = false), withWebkitShim = false)
        assertTrue(on.contains("dragging: true"))
        assertTrue(off.contains("dragging: false"))
    }

    @Test
    fun build_webkitShimOnlyForIos() {
        val ios = OsmLeafletHtml.build(spec(), withWebkitShim = true)
        val android = OsmLeafletHtml.build(spec(), withWebkitShim = false)
        assertTrue(ios.contains("window.webkit.messageHandlers.kmpBridge"))
        assertTrue(!android.contains("window.webkit.messageHandlers.kmpBridge"))
    }

    @Test
    fun jsonString_escapesQuotesAndBackslashes() {
        assertEquals("\"a\\\"b\"", OsmLeafletHtml.jsonString("a\"b"))
        assertEquals("\"a\\\\b\"", OsmLeafletHtml.jsonString("a\\b"))
    }

    @Test
    fun parse_markerClick() {
        val msg = OsmLeafletHtml.parseBridgeMessage("{\"type\":\"marker\",\"id\":\"abc\"}")
        assertEquals(OsmBridgeMessage.MarkerClick("abc"), msg)
    }

    @Test
    fun parse_mapClick() {
        val msg = OsmLeafletHtml.parseBridgeMessage("{\"type\":\"map\",\"lat\":-23.5,\"lng\":-46.6}")
        assertEquals(OsmBridgeMessage.MapClick(OsmLatLng(-23.5, -46.6)), msg)
    }

    @Test
    fun parse_invalidReturnsNull() {
        assertNull(OsmLeafletHtml.parseBridgeMessage("not json"))
        assertNull(OsmLeafletHtml.parseBridgeMessage("{\"type\":\"other\"}"))
        assertNull(OsmLeafletHtml.parseBridgeMessage("{\"type\":\"map\",\"lat\":1.0}"))
    }
}
