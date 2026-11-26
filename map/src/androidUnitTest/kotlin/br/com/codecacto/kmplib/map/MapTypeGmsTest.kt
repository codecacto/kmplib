package br.com.codecacto.kmplib.map

import kotlin.test.Test
import kotlin.test.assertEquals
import com.google.maps.android.compose.MapType as GmsMapType

class MapTypeGmsTest {
    @Test
    fun cadaTipoVaiParaOMesmoDoSdk() {
        assertEquals(GmsMapType.NORMAL, MapType.NORMAL.toGms())
        assertEquals(GmsMapType.SATELLITE, MapType.SATELLITE.toGms())
        assertEquals(GmsMapType.HYBRID, MapType.HYBRID.toGms())
        assertEquals(GmsMapType.TERRAIN, MapType.TERRAIN.toGms())
        // Todo tipo da lib tem par, e nenhum cai no NONE (mapa sem ladrilho).
        assertEquals(MapType.entries.size, MapType.entries.map { it.toGms() }.toSet().size)
    }
}

class OverlaySampleSizeTest {
    @kotlin.test.Test
    fun amostra_so_acima_do_teto() {
        kotlin.test.assertEquals(1, overlaySampleSize(2048, 1024))
        kotlin.test.assertEquals(2, overlaySampleSize(4096, 1000))
        kotlin.test.assertEquals(4, overlaySampleSize(100, 5000))
    }
}
