package br.com.codecacto.kmplib.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapCircleTest {
    private val cuiaba = LatLng(-15.6014, -56.0979)
    private val primaria = Color(0xFF1565C0)

    @Test
    fun coresDefaultSaoDoTema() {
        val r = assertNotNull(resolveCircle(MapCircle("p1", cuiaba, 30.0), primaria))
        assertEquals(primaria.copy(alpha = DEFAULT_CIRCLE_FILL_ALPHA), r.fillColor)
        assertEquals(primaria, r.strokeColor)
        assertEquals(2f, r.strokeWidthDp)
        assertEquals(30.0, r.radiusMeters)
    }

    @Test
    fun coresProprias() {
        val fill = Color(0x3300FF00)
        val r = assertNotNull(resolveCircle(MapCircle("p", cuiaba, 10.0, fillColor = fill, strokeColor = Color.Red, strokeWidth = 0.dp), primaria))
        assertEquals(fill, r.fillColor)
        assertEquals(Color.Red, r.strokeColor)
        assertEquals(0f, r.strokeWidthDp)
    }

    @Test
    fun raioOuCentroInvalidoNaoDesenha() {
        assertNull(resolveCircle(MapCircle("a", cuiaba, 0.0), primaria))
        assertNull(resolveCircle(MapCircle("b", cuiaba, -5.0), primaria))
        assertNull(resolveCircle(MapCircle("c", cuiaba, Double.NaN), primaria))
        assertNull(resolveCircle(MapCircle("d", cuiaba, Double.POSITIVE_INFINITY), primaria))
        assertNull(resolveCircle(MapCircle("e", LatLng(91.0, 0.0), 10.0), primaria))
        assertTrue(MapCircle("f", cuiaba, -1.0).extremePoints().isEmpty())
    }

    @Test
    fun extremosCobremORaioEmMetros() {
        val pontos = MapCircle("p", cuiaba, 1_000.0).extremePoints()
        assertEquals(4, pontos.size)
        val norte = pontos[0]
        val leste = pontos[2]
        // ~1 km para o norte = ~0,00898°; para o leste a ~15,6°S, ~0,00933°.
        assertTrue(abs((norte.latitude - cuiaba.latitude) - 0.008983) < 0.0001, "norte: ${norte.latitude}")
        assertTrue(abs((leste.longitude - cuiaba.longitude) - 0.009326) < 0.0002, "leste: ${leste.longitude}")
        val caixa = assertNotNull(LatLngBounds.of(pontos))
        assertTrue(caixa.contains(cuiaba))
    }

    @Test
    fun igualdadeDecideORedesenho() {
        val a = MapCircle("p", cuiaba, 30.0)
        assertEquals(resolveCircle(a, primaria), resolveCircle(a.copy(), primaria))
        assertTrue(resolveCircle(a, primaria) != resolveCircle(a.copy(radiusMeters = 31.0), primaria), "raio novo = círculo novo")
    }
}
