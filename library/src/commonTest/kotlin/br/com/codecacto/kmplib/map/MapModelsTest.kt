package br.com.codecacto.kmplib.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MapModelsTest {

    @Test
    fun latLng_holdsCoordinates() {
        val p = LatLng(latitude = -23.5505, longitude = -46.6333)
        assertEquals(-23.5505, p.latitude)
        assertEquals(-46.6333, p.longitude)
    }

    @Test
    fun latLng_equalityByValue() {
        assertEquals(LatLng(1.0, 2.0), LatLng(1.0, 2.0))
        assertNotEquals(LatLng(1.0, 2.0), LatLng(2.0, 1.0))
    }

    @Test
    fun cameraPosition_defaultZoomIs14() {
        val cam = CameraPosition(target = LatLng(0.0, 0.0))
        assertEquals(14f, cam.zoom)
    }

    @Test
    fun cameraPosition_customZoomPreserved() {
        val cam = CameraPosition(target = LatLng(10.0, 20.0), zoom = 18f)
        assertEquals(18f, cam.zoom)
        assertEquals(LatLng(10.0, 20.0), cam.target)
    }

    @Test
    fun mapMarkerStatus_hasAllExpectedStates() {
        val expected = setOf("FREE", "OCCUPIED", "EXPIRING", "EXPIRED", "INSTALLING")
        val actual = MapMarkerStatus.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun mapMarkerStatus_isExhaustiveAndOrdered() {
        // Garante estabilidade da ordem (ordinal) — consumidores podem persistir.
        assertEquals(MapMarkerStatus.FREE, MapMarkerStatus.entries.first())
        assertTrue(MapMarkerStatus.entries.size == 5)
    }
}
