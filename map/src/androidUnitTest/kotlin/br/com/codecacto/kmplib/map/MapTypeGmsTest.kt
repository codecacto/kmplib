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
