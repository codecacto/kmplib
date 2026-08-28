package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes da LÓGICA PURA de escala do BarChart/StackedBarChart (sem Compose UI test).
 * A renderização (alturas proporcionais) deriva diretamente desses máximos.
 */
class BarChartTest {

    @Test
    fun `barChartMax retorna o maior valor`() {
        val data = listOf(
            BarChartEntry("Jan", 100.0),
            BarChartEntry("Fev", 250.0),
            BarChartEntry("Mar", 80.0),
        )
        assertEquals(250.0, data.barChartMax())
    }

    @Test
    fun `barChartMax retorna 0 para lista vazia`() {
        assertEquals(0.0, emptyList<BarChartEntry>().barChartMax())
    }

    @Test
    fun `barChartMax trata valores negativos como zero`() {
        val data = listOf(BarChartEntry("A", -50.0), BarChartEntry("B", -10.0))
        assertEquals(0.0, data.barChartMax())
    }

    @Test
    fun `barChartMax ignora negativo ao escolher o maximo`() {
        val data = listOf(BarChartEntry("A", -50.0), BarChartEntry("B", 30.0))
        assertEquals(30.0, data.barChartMax())
    }

    @Test
    fun `stackedBarChartMax soma os segmentos por barra e escolhe o maior total`() {
        val data = listOf(
            StackedBarEntry("Jan", listOf(BarSegment(100.0), BarSegment(50.0))),  // 150
            StackedBarEntry("Fev", listOf(BarSegment(80.0), BarSegment(200.0))),  // 280
        )
        assertEquals(280.0, data.stackedBarChartMax())
    }

    @Test
    fun `stackedBarChartMax retorna 0 para lista vazia`() {
        assertEquals(0.0, emptyList<StackedBarEntry>().stackedBarChartMax())
    }

    @Test
    fun `stackedBarChartMax trata segmento negativo como zero na soma`() {
        val data = listOf(
            StackedBarEntry("Jan", listOf(BarSegment(100.0), BarSegment(-50.0))), // 100
        )
        assertEquals(100.0, data.stackedBarChartMax())
    }

    @Test
    fun `stackedBarChartMax com todos os totais zero retorna 0`() {
        val data = listOf(
            StackedBarEntry("Jan", listOf(BarSegment(0.0), BarSegment(0.0))),
            StackedBarEntry("Fev", listOf(BarSegment(0.0))),
        )
        assertEquals(0.0, data.stackedBarChartMax())
    }
}
