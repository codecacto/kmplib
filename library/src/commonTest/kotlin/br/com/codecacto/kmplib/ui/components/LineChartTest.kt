package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LineChartTest {

    @Test
    fun bounds_vazio_expande_em_torno_de_zero() {
        val (min, max) = lineChartValueBounds(emptyList())
        assertEquals(-1.0, min)
        assertEquals(1.0, max)
    }

    @Test
    fun bounds_valores_iguais_expandem_para_nao_colapsar() {
        val (min, max) = lineChartValueBounds(listOf(420.0, 420.0, 420.0))
        assertEquals(419.0, min)
        assertEquals(421.0, max)
    }

    @Test
    fun bounds_min_max_normais() {
        val (min, max) = lineChartValueBounds(listOf(300.0, 450.0, 380.0))
        assertEquals(300.0, min)
        assertEquals(450.0, max)
    }

    @Test
    fun normalize_topo_e_base() {
        assertEquals(1f, normalizeToFraction(450.0, 300.0, 450.0))
        assertEquals(0f, normalizeToFraction(300.0, 300.0, 450.0))
        assertEquals(0.5f, normalizeToFraction(375.0, 300.0, 450.0))
    }

    @Test
    fun normalize_clampa_fora_de_faixa() {
        assertEquals(1f, normalizeToFraction(999.0, 300.0, 450.0))
        assertEquals(0f, normalizeToFraction(0.0, 300.0, 450.0))
    }

    @Test
    fun normalize_faixa_degenerada_retorna_meio() {
        assertEquals(0.5f, normalizeToFraction(10.0, 5.0, 5.0))
    }

    @Test
    fun xlabels_poucos_pontos_mostra_todos() {
        assertEquals(listOf(0, 1, 2), xAxisLabelIndices(3, maxLabels = 4))
    }

    @Test
    fun xlabels_um_ponto() {
        assertEquals(listOf(0), xAxisLabelIndices(1, maxLabels = 4))
    }

    @Test
    fun xlabels_zero_pontos() {
        assertTrue(xAxisLabelIndices(0, maxLabels = 4).isEmpty())
    }

    @Test
    fun xlabels_muitos_pontos_inclui_primeiro_e_ultimo() {
        val idx = xAxisLabelIndices(30, maxLabels = 4)
        assertTrue(idx.size <= 4)
        assertEquals(0, idx.first())
        assertEquals(29, idx.last())
    }

    @Test
    fun xlabels_distribuicao_uniforme() {
        // 10 pontos, 3 rótulos → primeiro, meio, último.
        assertEquals(listOf(0, 4, 9), xAxisLabelIndices(10, maxLabels = 3))
    }
}
