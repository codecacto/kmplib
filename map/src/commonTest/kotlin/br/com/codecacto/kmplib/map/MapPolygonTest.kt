package br.com.codecacto.kmplib.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapPolygonTest {
    private val primaria = Color(0xFF2E7D32)

    // Talhão quadrado perto de Sorriso-MT (~1,1 km de lado).
    private val talhao = listOf(
        LatLng(-12.55, -55.72),
        LatLng(-12.55, -55.71),
        LatLng(-12.56, -55.71),
        LatLng(-12.56, -55.72),
    )
    private val sede = listOf(
        LatLng(-12.554, -55.716),
        LatLng(-12.554, -55.714),
        LatLng(-12.556, -55.714),
        LatLng(-12.556, -55.716),
    )
    private val meio = LatLng(-12.555, -55.715)
    private val dentroForaDaSede = LatLng(-12.551, -55.711)

    @AfterTest
    fun limpa() = IosMapLayersNotice.resetForTest()

    @Test
    fun coresDefaultSaoDoTema() {
        val r = assertNotNull(resolvePolygon(MapPolygon("t1", talhao), primaria))
        assertEquals(primaria.copy(alpha = DEFAULT_POLYGON_FILL_ALPHA), r.fillColor)
        assertEquals(primaria, r.strokeColor)
        assertEquals(2f, r.strokeWidthDp)
        assertEquals(talhao, r.points)
        assertTrue(r.holes.isEmpty())
    }

    @Test
    fun coresProprias() {
        val fill = Color(0x66FFC107)
        val r = assertNotNull(
            resolvePolygon(MapPolygon("t", talhao, fillColor = fill, strokeColor = Color.Red, strokeWidth = 0.dp), primaria),
        )
        assertEquals(fill, r.fillColor)
        assertEquals(Color.Red, r.strokeColor)
        assertEquals(0f, r.strokeWidthDp)
    }

    @Test
    fun menosDeTresPontosValidosNaoDesenha() {
        assertNull(resolvePolygon(MapPolygon("a", emptyList()), primaria))
        assertNull(resolvePolygon(MapPolygon("b", talhao.take(2)), primaria))
        // Fechado repetindo o primeiro ponto: 2 vértices de verdade.
        assertNull(resolvePolygon(MapPolygon("c", talhao.take(2) + talhao.first()), primaria))
        // Coordenada inválida não conta.
        assertNull(resolvePolygon(MapPolygon("d", talhao.take(2) + LatLng(Double.NaN, 0.0)), primaria))
    }

    @Test
    fun pontoFinalRepetidoSaiEInvalidosSaoIgnorados() {
        val r = assertNotNull(resolvePolygon(MapPolygon("t", talhao + talhao.first() + LatLng(95.0, 0.0)), primaria))
        assertEquals(talhao, r.points)
    }

    @Test
    fun buracoInvalidoEhIgnoradoEOPoligonoFica() {
        val r = assertNotNull(resolvePolygon(MapPolygon("t", talhao, holes = listOf(sede, sede.take(2))), primaria))
        assertEquals(listOf(sede), r.holes)
    }

    @Test
    fun contemPontoRespeitandoBuraco() {
        val semBuraco = assertNotNull(resolvePolygon(MapPolygon("t", talhao), primaria))
        assertTrue(semBuraco.contains(meio))
        assertFalse(semBuraco.contains(LatLng(-12.50, -55.715)))
        val comSede = assertNotNull(resolvePolygon(MapPolygon("t", talhao, holes = listOf(sede)), primaria))
        assertFalse(comSede.contains(meio), "no buraco não é do talhão")
        assertTrue(comSede.contains(dentroForaDaSede))
    }

    @Test
    fun contemEmPoligonoConcavo() {
        // "L": o canto de dentro não é da área.
        val l = listOf(LatLng(0.0, 0.0), LatLng(0.0, 2.0), LatLng(1.0, 2.0), LatLng(1.0, 1.0), LatLng(2.0, 1.0), LatLng(2.0, 0.0))
        assertTrue(ringContains(l, LatLng(0.5, 1.5)))
        assertTrue(ringContains(l, LatLng(1.5, 0.5)))
        assertFalse(ringContains(l, LatLng(1.5, 1.5)))
    }

    @Test
    fun toqueVaiParaODeCima() {
        val baixo = assertNotNull(resolvePolygon(MapPolygon("baixo", talhao), primaria))
        val cima = assertNotNull(resolvePolygon(MapPolygon("cima", sede), primaria))
        assertEquals("cima", topPolygonAt(listOf(baixo, cima), meio)?.id)
        assertEquals("baixo", topPolygonAt(listOf(cima, baixo), meio)?.id)
        assertEquals("baixo", topPolygonAt(listOf(baixo, cima), dentroForaDaSede)?.id)
        assertNull(topPolygonAt(listOf(baixo, cima), LatLng(0.0, 0.0)))
    }

    @Test
    fun zIndexFicaAbaixoDoCirculoEOUltimoPorCima() {
        assertEquals(listOf(-4, -3, -2), (0 until 3).map { polygonZIndex(it, 3) })
        assertEquals(-2, polygonZIndex(0, 1))
        assertTrue((0 until 10).all { polygonZIndex(it, 10) < -1 })
    }

    @Test
    fun enquadramentoIncluiOsPoligonos() {
        val pino = MapItem("p", LatLng(-12.40, -55.60))
        val pontos = nativeMapFitPoints(
            items = listOf(pino),
            polylines = emptyList(),
            circles = emptyList(),
            polygons = listOf(MapPolygon("t", talhao + talhao.first(), holes = listOf(sede)), MapPolygon("x", talhao.take(2))),
        )
        assertEquals(listOf(pino.position) + talhao, pontos)
        val caixa = assertNotNull(LatLngBounds.of(pontos))
        assertTrue(talhao.all { caixa.contains(it) })
        assertTrue(caixa.contains(pino.position))
    }

    @Test
    fun soPoligonoJaEnquadra() {
        val pontos = nativeMapFitPoints(emptyList(), emptyList(), emptyList(), listOf(MapPolygon("t", talhao)))
        assertEquals(talhao, pontos)
    }

    @Test
    fun relevoViraNormalNoMapKit() {
        assertEquals(MapType.NORMAL, MapType.TERRAIN.supportedByMapKit())
        assertEquals(MapType.SATELLITE, MapType.SATELLITE.supportedByMapKit())
        assertEquals(MapType.HYBRID, MapType.HYBRID.supportedByMapKit())
        assertEquals(MapType.NORMAL, MapType.NORMAL.supportedByMapKit())
    }

    @Test
    fun avisoDeCamadasSoQuandoHaOQueAPonteNaoDesenhaEUmaVez() {
        assertFalse(IosMapLayersNotice.claim(hasPolygons = false, mapType = MapType.NORMAL))
        assertTrue(IosMapLayersNotice.claim(hasPolygons = false, mapType = MapType.SATELLITE))
        assertFalse(IosMapLayersNotice.claim(hasPolygons = true, mapType = MapType.NORMAL))
    }

    @Test
    fun igualdadeDecideORedesenho() {
        val a = MapPolygon("t", talhao, fillColor = Color.Green)
        assertEquals(resolvePolygon(a, primaria), resolvePolygon(a.copy(), primaria))
        assertFalse(resolvePolygon(a, primaria) == resolvePolygon(a.copy(fillColor = Color.Red), primaria))
    }
}
