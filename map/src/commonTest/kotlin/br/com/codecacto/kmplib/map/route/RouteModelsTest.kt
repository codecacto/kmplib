package br.com.codecacto.kmplib.map.route

import br.com.codecacto.kmplib.map.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteModelsTest {

    @Test
    fun routeResult_distanceKm_derives_from_meters() {
        val r = RouteResult(distanceMeters = 12_500, durationSeconds = 900)
        assertEquals(12.5, r.distanceKm)
        assertEquals(900L, r.durationSeconds)
    }

    @Test
    fun routeResult_defaults_emptyPolyline_nullDuration() {
        val r = RouteResult(distanceMeters = 1000)
        assertNull(r.durationSeconds)
        assertEquals(0, r.polyline.size)
    }

    @Test
    fun geocodeResult_holds_label_and_position() {
        val g = GeocodeResult(label = "Av. Paulista, 1000", position = LatLng(-23.56, -46.65), secondaryLabel = "São Paulo · SP")
        assertEquals("Av. Paulista, 1000", g.label)
        assertEquals(-23.56, g.position.latitude)
        assertEquals("São Paulo · SP", g.secondaryLabel)
    }
}
