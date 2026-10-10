package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoMarkersTest {

    private fun m(id: String, at: Long, label: String? = null) = VideoMarker(id, at, label)

    @Test
    fun normalizaOrdenaPorTempoEDesempataPeloId() {
        val r = normalizeVideoMarkers(listOf(m("c", 5_000), m("b", 1_000), m("a", 5_000)), 60_000)
        assertEquals(listOf("b", "a", "c"), r.map { it.id })
    }

    @Test
    fun normalizaGrampeiaDentroDoVideoSemSumirComMarca() {
        val r = normalizeVideoMarkers(listOf(m("neg", -10), m("fim", 62_000), m("ok", 30_000)), 61_900)
        assertEquals(listOf(0L, 30_000L, 61_900L), r.map { it.atMillis })
        assertEquals(3, r.size)
    }

    @Test
    fun normalizaComDuracaoDesconhecidaSoGrampeiaNegativo() {
        val r = normalizeVideoMarkers(listOf(m("a", 120_000), m("b", -1)), 0)
        assertEquals(listOf(0L, 120_000L), r.map { it.atMillis })
    }

    @Test
    fun agrupaPontosQueSeSobreporiam() {
        // 60 s numa barra de 600 px = 10 px/s; espaço mínimo 24 px = 2,4 s.
        val marcas = listOf(m("a", 10_000), m("b", 11_000), m("c", 12_000), m("d", 30_000))
        val grupos = clusterVideoMarkers(marcas, 60_000, 600f, 24f)
        assertEquals(2, grupos.size)
        assertEquals(listOf("a", "b", "c"), grupos[0].markers.map { it.id })
        assertEquals("a", grupos[0].first.id)
        assertEquals(10_000L, grupos[0].atMillis)
        assertEquals(100f / 600f, grupos[0].fraction, 1e-6f)
        assertEquals(listOf("d"), grupos[1].markers.map { it.id })
    }

    @Test
    fun agrupamentoMedeAPartirDaPrimeiraDoGrupoNaoDaUltima() {
        // Uma marca a cada 2 s (20 px): a terceira já está a 40 px da primeira e abre outro grupo.
        val marcas = (0 until 6).map { m("m$it", it * 2_000L) }
        val grupos = clusterVideoMarkers(marcas, 60_000, 600f, 24f)
        assertEquals(listOf(listOf("m0", "m1"), listOf("m2", "m3"), listOf("m4", "m5")), grupos.map { g -> g.markers.map { it.id } })
    }

    @Test
    fun agrupamentoSemDuracaoOuSemLarguraPoeTudoNoInicio() {
        val marcas = listOf(m("a", 1_000), m("b", 50_000))
        assertEquals(1, clusterVideoMarkers(marcas, 0, 600f, 24f).size)
        assertEquals(0f, clusterVideoMarkers(marcas, 60_000, 0f, 24f).single().fraction)
        assertTrue(clusterVideoMarkers(emptyList(), 60_000, 600f, 24f).isEmpty())
    }

    @Test
    fun marcaDaVezDuraAJanela() {
        val marcas = listOf(m("a", 10_000), m("b", 20_000))
        assertNull(videoMarkerAt(marcas, 9_999))
        assertEquals("a", videoMarkerAt(marcas, 10_000)?.id)
        assertEquals("a", videoMarkerAt(marcas, 13_000)?.id)
        assertNull(videoMarkerAt(marcas, 13_001))
        assertEquals("b", videoMarkerAt(marcas, 21_000)?.id)
        assertEquals("a", videoMarkerAt(marcas, 15_000, windowMillis = 10_000)?.id)
    }

    @Test
    fun marcaDaVezEscolheAMaisRecente() {
        val marcas = listOf(m("a", 10_000), m("b", 11_000))
        assertEquals("b", videoMarkerAt(marcas, 12_000)?.id)
    }

    @Test
    fun proximaMarcaPulaAAtual() {
        val marcas = listOf(m("a", 10_000), m("b", 20_000))
        assertEquals("a", nextVideoMarker(marcas, 0)?.id)
        // Logo depois de pular para "a" (posição 10.000 ou arredondada perto), a próxima é "b".
        assertEquals("b", nextVideoMarker(marcas, 10_000)?.id)
        assertEquals("b", nextVideoMarker(marcas, 9_800)?.id)
        assertNull(nextVideoMarker(marcas, 20_000))
    }

    @Test
    fun marcaAnteriorLogoDepoisDeUmaVoltaParaADeAntes() {
        val marcas = listOf(m("a", 10_000), m("b", 20_000))
        assertEquals("a", previousVideoMarker(marcas, 20_500)?.id)
        assertEquals("b", previousVideoMarker(marcas, 21_500)?.id)
        assertNull(previousVideoMarker(marcas, 10_500))
        assertNull(previousVideoMarker(marcas, 0))
    }

    @Test
    fun instanteSobODedo() {
        assertEquals(0L, timelineMillisAt(0f, 600f, 60_000))
        assertEquals(30_000L, timelineMillisAt(300f, 600f, 60_000))
        assertEquals(60_000L, timelineMillisAt(700f, 600f, 60_000))
        assertEquals(0L, timelineMillisAt(-50f, 600f, 60_000))
        assertEquals(0L, timelineMillisAt(300f, 0f, 60_000))
        assertEquals(0L, timelineMillisAt(300f, 600f, 0))
        assertEquals(0L, timelineMillisAt(Float.NaN, 600f, 60_000))
    }
}
