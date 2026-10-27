package br.com.codecacto.kmplib.map.osm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Itens técnicos da política de uso dos tiles do OSM que a lib cumpre (2.329.0). */
class OsmTileUsageTest {

    @Test
    fun hostUnicoSemSubdominio() {
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", OsmLeafletHtml.TILE_URL)
        val html = OsmLeafletHtml.build(
            OsmMapSpec(markers = emptyList(), center = OsmLatLng(-15.6, -56.1), zoom = 12f, interactive = true, pickable = false),
            withWebkitShim = false,
        )
        assertFalse("{s}." in html, "o documento não pode pedir tiles nos subdomínios aposentados")
        assertTrue("openstreetmap.org/copyright" in html, "atribuição obrigatória")
    }

    @Test
    fun userAgentIdentificaOApp() {
        assertEquals("br.com.codecacto.cidade/1.4.0 kmplib-osm", osmUserAgentSuffix("br.com.codecacto.cidade", "1.4.0"))
        assertEquals("br.com.codecacto.cidade kmplib-osm", osmUserAgentSuffix("br.com.codecacto.cidade", null))
        assertEquals("kmplib-osm", osmUserAgentSuffix(null, "1.0"))
        assertEquals("kmplib-osm", osmUserAgentSuffix("  ", null))
        // Nada que quebre o cabeçalho (espaço, quebra de linha, parênteses) passa.
        assertEquals("app.x/1.0beta kmplib-osm", osmUserAgentSuffix("app.x\r\n", "1.0 (beta)"))
    }
}
