package br.com.codecacto.kmplib.map

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapPinPlanningTest {

    private fun marker(id: String, lat: Double, lng: Double, priority: Int = 0) =
        resolveMarker(MapItem(id, LatLng(lat, lng), priority = priority), Color.Blue)

    @Test
    fun semAgrupamentoUmPinoPorItemValido() {
        val plan = planPins(
            listOf(marker("a", -15.6, -56.1), marker("b", -15.6, -56.1), marker("x", Double.NaN, 0.0)),
            zoom = 5f,
            clustering = false,
        )
        assertEquals(listOf("a", "b"), plan.map { it.key })
        assertTrue(plan.all { it is PlannedPin.Single })
    }

    @Test
    fun pinosPertoViramGrupoNaPosicaoDoMaisGrave() {
        val plan = planPins(
            listOf(marker("leve", -15.6000, -56.1000, 0), marker("grave", -15.6001, -56.1001, 9), marker("longe", -23.5, -46.6)),
            zoom = 10f,
            clustering = true,
        )
        val group = plan.filterIsInstance<PlannedPin.Group>().single()
        assertEquals(setOf("leve", "grave"), group.members.map { it.item.id }.toSet())
        assertEquals(LatLng(-15.6001, -56.1001), group.position)
        assertEquals(GROUP_KEY_PREFIX + "grave", group.key)
        assertEquals("longe", plan.filterIsInstance<PlannedPin.Single>().single().key)
    }

    @Test
    fun aproximarSeparaOGrupo() {
        val itens = listOf(marker("a", -15.6000, -56.1000), marker("b", -15.6000, -56.1010))
        // ~107 m de distância: juntos de longe, separados de perto.
        assertEquals(1, planPins(itens, zoom = 12f, clustering = true).size)
        assertEquals(2, planPins(itens, zoom = 18f, clustering = true).size)
    }

    @Test
    fun resultadoNaoDependeDaOrdemDaLista() {
        val itens = (0 until 40).map { marker("p$it", -15.6 + it * 0.0003, -56.1 + (it % 5) * 0.0004, priority = it % 3) }
        val a = planPins(itens, zoom = 14f, clustering = true)
        val b = planPins(itens.reversed(), zoom = 14f, clustering = true)
        assertEquals(a, b)
        assertEquals(40, a.sumOf { if (it is PlannedPin.Group) it.members.size else 1 })
    }

    @Test
    fun enquadrarRespeitaZoomMaximoEVistaSemMedida() {
        val bounds = LatLngBounds(LatLng(-15.61, -56.11), LatLng(-15.60, -56.10))
        assertEquals(CameraPosition(bounds.center, 15f), cameraToFit(bounds, 0.0, 0.0, 48.0, 15f))
        val perto = cameraToFit(bounds, 400.0, 800.0, 48.0, 22f)
        assertTrue(perto.zoom in 14f..16f, "0,01° numa vista de ~300 pt úteis ≈ zoom 15 (deu ${perto.zoom})")
        assertTrue(abs(perto.target.latitude - -15.605) < 0.001 && abs(perto.target.longitude - -56.105) < 1e-9)
        assertEquals(12f, cameraToFit(bounds, 400.0, 800.0, 48.0, 12f).zoom)
    }

    @Test
    fun enquadrarOBrasilCabeNaTela() {
        val brasil = LatLngBounds(LatLng(-33.7, -73.9), LatLng(5.3, -34.8))
        val cam = cameraToFit(brasil, 390.0, 700.0, 48.0, 15f)
        assertTrue(cam.zoom in 2f..4f, "zoom ${cam.zoom}")
    }
}
