package br.com.codecacto.kmplib.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapGroundOverlayTest {

    private val area = LatLngBounds(LatLng(-16.0, -57.0), LatLng(-15.0, -56.0))
    private fun png(vararg b: Int) = MapOverlayImage.Bytes(b.map { it.toByte() }.toByteArray())
    private fun overlay(id: String, z: Float = 0f, bounds: LatLngBounds = area, opacity: Float = 1f) =
        MapGroundOverlay(id, png(1, 2, 3), bounds, opacity = opacity, zIndex = z)

    @AfterTest
    fun reset() = IosMapGroundNotice.resetForTest()

    @Test
    fun overlays_ficam_abaixo_de_todo_poligono_ordenados_por_zIndex_e_lista() {
        val r = resolveGroundOverlays(listOf(overlay("a", z = 1f), overlay("b"), overlay("c")), polygonCount = 2)
        assertEquals(listOf("b", "c", "a"), r.map { it.id })
        // Polígonos ocupam -3…-2: o overlay de cima tem de ficar em -4.
        assertEquals(listOf(-6, -5, -4), r.map { it.sdkZIndex })
        assertTrue(r.all { it.sdkZIndex < polygonZIndex(0, 2) })
    }

    @Test
    fun sem_poligono_o_overlay_de_cima_fica_abaixo_do_circulo() {
        val r = resolveGroundOverlays(listOf(overlay("a")), polygonCount = 0)
        assertEquals(-2, r.single().sdkZIndex)
    }

    @Test
    fun area_invalida_e_id_repetido_nao_desenham() {
        val inverted = LatLngBounds(LatLng(-15.0, -56.0), LatLng(-16.0, -57.0))
        val outOfRange = LatLngBounds(LatLng(-95.0, -57.0), LatLng(-15.0, -56.0))
        val flat = LatLngBounds(LatLng(-16.0, -57.0), LatLng(-16.0, -56.0))
        val r = resolveGroundOverlays(
            listOf(overlay("ok"), overlay("ok"), overlay("x", bounds = inverted), overlay("y", bounds = outOfRange), overlay("z", bounds = flat)),
            polygonCount = 0,
        )
        assertEquals(listOf("ok"), r.map { it.id })
    }

    @Test
    fun opacidade_e_limitada_e_nan_vira_opaca() {
        assertEquals(1f, overlayOpacity(Float.NaN))
        assertEquals(0f, overlayOpacity(-1f))
        assertEquals(1f, overlayOpacity(3f))
        assertEquals(0.75f, resolveGroundOverlays(listOf(overlay("a", opacity = 0.75f)), 0).single().opacity)
    }

    @Test
    fun bytes_comparam_pela_chave_ou_pelo_conteudo() {
        assertEquals(png(1, 2, 3), png(1, 2, 3))
        assertNotEquals(png(1, 2, 3), png(1, 2, 4))
        val a = MapOverlayImage.Bytes(byteArrayOf(1), cacheKey = "chuva-7d")
        val b = MapOverlayImage.Bytes(byteArrayOf(9, 9), cacheKey = "chuva-7d")
        assertEquals(a, b)
        assertEquals(a.key, b.key)
        assertNotEquals(png(1, 2, 3).key, png(1, 2, 4).key)
    }

    @Test
    fun toString_nao_vaza_query_nem_valor_de_cabecalho() {
        val url = MapOverlayImage.Url("https://cdn.x.com/chuva/7d.png?X-Amz-Signature=segredo", mapOf("Authorization" to "Bearer tok"))
        val text = url.toString()
        assertFalse("segredo" in text)
        assertFalse("tok" in text)
        assertTrue("https://cdn.x.com/chuva/7d.png" in text)
        assertFalse("1, 2" in png(1, 2, 3).toString())
    }

    @Test
    fun ready_usa_a_imagem_carregada_do_id_e_omite_quem_nao_tem() {
        val img = object : DecodedOverlayImage {}
        val r = resolveGroundOverlays(listOf(overlay("a"), overlay("b")), 0)
        val ready = readyGroundOverlays(r, mapOf("a" to ("chave-antiga" to img)))
        assertEquals(listOf("a"), ready.map { it.overlay.id })
        assertEquals("chave-antiga", ready.single().imageKey)
    }

    @Test
    fun loader_baixa_uma_vez_e_reusa_da_memoria() = runTest {
        var fetches = 0
        var decodes = 0
        val loader = GroundOverlayImageLoader(
            fetch = { fetches++; byteArrayOf(1, 2) },
            decode = { decodes++; object : DecodedOverlayImage {} },
        )
        val image = MapOverlayImage.Url("https://x.com/a.png")
        val first = loader.load(image)
        val second = loader.load(image)
        assertSame(first, second)
        assertEquals(1, fetches)
        assertEquals(1, decodes)
        assertSame(first, loader.cached(image.key))
    }

    @Test
    fun loader_pedidos_simultaneos_viram_um_so() = runTest {
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val loader = GroundOverlayImageLoader(
            fetch = { fetches++; gate.await(); byteArrayOf(1) },
            decode = { object : DecodedOverlayImage {} },
        )
        val image = MapOverlayImage.Url("https://x.com/a.png")
        val a = async { loader.load(image) }
        val b = async { loader.load(image) }
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)
        assertSame(a.await(), b.await())
        assertEquals(1, fetches)
    }

    @Test
    fun loader_falha_devolve_null_e_nao_guarda() = runTest {
        var calls = 0
        val loader = GroundOverlayImageLoader(
            fetch = { calls++; if (calls == 1) throw IllegalStateException("rede") else byteArrayOf(1) },
            decode = { object : DecodedOverlayImage {} },
        )
        val image = MapOverlayImage.Url("https://x.com/a.png")
        assertNull(loader.load(image))
        assertTrue(loader.load(image) != null)
    }

    @Test
    fun loader_bytes_que_nao_sao_imagem_devolvem_null() = runTest {
        val loader = GroundOverlayImageLoader(fetch = { null }, decode = { null })
        assertNull(loader.load(png(1)))
        assertNull(loader.load(MapOverlayImage.Bytes(ByteArray(0))))
        assertNull(loader.load(MapOverlayImage.Url("https://x.com/404.png")))
    }

    @Test
    fun loader_respeita_o_teto_de_memoria() = runTest {
        val loader = GroundOverlayImageLoader(fetch = { null }, decode = { object : DecodedOverlayImage {} }, maxEntries = 2)
        val a = png(1); val b = png(2); val c = png(3)
        loader.load(a); loader.load(b)
        loader.load(a) // a vira o mais recente
        loader.load(c) // sai o b
        assertTrue(loader.cached(a.key) != null)
        assertNull(loader.cached(b.key))
        assertTrue(loader.cached(c.key) != null)
    }

    @Test
    fun folga_resolve_start_end_pela_direcao_e_ignora_negativo() {
        val p = PaddingValues(start = 8.dp, top = 4.dp, end = 16.dp, bottom = 48.dp)
        assertEquals(MapPadding(4f, 8f, 48f, 16f), p.toMapPadding(LayoutDirection.Ltr))
        assertEquals(MapPadding(4f, 16f, 48f, 8f), p.toMapPadding(LayoutDirection.Rtl))
        assertTrue(PaddingValues().toMapPadding(LayoutDirection.Ltr).isZero)
    }

    @Test
    fun area_util_desconta_a_folga_e_respeita_vista_sem_medida() {
        val p = MapPadding(10f, 20f, 50f, 30f)
        assertEquals(350.0 to 540.0, p.innerSize(400.0, 600.0))
        assertEquals(1.0 to 1.0, p.innerSize(40.0, 50.0))
        assertEquals(0.0 to 0.0, p.innerSize(0.0, 0.0))
    }

    @Test
    fun aviso_da_ponte_so_quando_falta_algo_e_uma_vez() {
        assertFalse(IosMapGroundNotice.claim(missingOverlays = false, missingPadding = false))
        assertTrue(IosMapGroundNotice.claim(missingOverlays = true, missingPadding = false))
        assertFalse(IosMapGroundNotice.claim(missingOverlays = false, missingPadding = true))
    }
}
