package br.com.codecacto.kmplib.ui.components

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Faixa de referência no `LineChart` e no `ReferenceBandChart` (2.285.0) — a parte pura. */
class ReferenceBandChartTest {

    @Test
    fun escalaIncluiOsLimitesDaFaixa() {
        // Valores todos dentro da faixa: a banda não pode sumir para fora da escala.
        assertEquals(70.0 to 99.0, lineChartValueBounds(listOf(80.0, 85.0), ReferenceBand(70.0, 99.0)))
        // Valor acima da faixa estica o topo.
        assertEquals(70.0 to 150.0, lineChartValueBounds(listOf(150.0), ReferenceBand(70.0, 99.0)))
        // Sem faixa = comportamento anterior.
        assertEquals(lineChartValueBounds(listOf(1.0, 3.0)), lineChartValueBounds(listOf(1.0, 3.0), null))
    }

    @Test
    fun fracoesDaFaixaComLadoAbertoVaoAteABorda() {
        val (top, bottom) = referenceBandFractions(ReferenceBand(25.0, 75.0), 0.0, 100.0)
        assertEquals(0.25f, top, 1e-6f)
        assertEquals(0.75f, bottom, 1e-6f)
        assertEquals(0f to 0.5f, referenceBandFractions(ReferenceBand(min = 50.0), 0.0, 100.0))
        assertEquals(0.5f to 1f, referenceBandFractions(ReferenceBand(max = 50.0), 0.0, 100.0))
    }

    @Test
    fun faixaInvalidaRecusada() {
        assertFailsWith<IllegalArgumentException> { ReferenceBand() }
        assertFailsWith<IllegalArgumentException> { ReferenceBand(min = 10.0, max = 5.0) }
    }

    @Test
    fun classificaComLimitesInclusivos() {
        val band = ReferenceBand(70.0, 99.0)
        assertEquals(BandPosition.BELOW, classifyAgainstBand(69.9, band))
        assertEquals(BandPosition.WITHIN, classifyAgainstBand(70.0, band))
        assertEquals(BandPosition.WITHIN, classifyAgainstBand(99.0, band))
        assertEquals(BandPosition.ABOVE, classifyAgainstBand(99.1, band))
        assertEquals(BandPosition.WITHIN, classifyAgainstBand(5.0, ReferenceBand(max = 200.0)))
        assertEquals(BandPosition.BELOW, classifyAgainstBand(30.0, ReferenceBand(min = 40.0)))
        assertNull(classifyAgainstBand(5.0, null))
    }

    @Test
    fun eixoXProporcionalAoTempoQuandoTodosTemData() {
        val pts = listOf(
            ReferencePoint("a", 1.0, LocalDate(2026, 1, 1)),
            ReferencePoint("b", 1.0, LocalDate(2026, 1, 11)),
            ReferencePoint("c", 1.0, LocalDate(2026, 2, 10)),
        )
        val x = referenceChartXFractions(pts)
        assertEquals(0f, x[0])
        assertEquals(0.25f, x[1], 1e-6f) // 10 de 40 dias
        assertEquals(1f, x[2])
    }

    @Test
    fun eixoXPorPosicaoSemDataOuComDataUnica() {
        val semData = listOf(ReferencePoint("a", 1.0), ReferencePoint("b", 1.0, LocalDate(2026, 1, 1)), ReferencePoint("c", 1.0))
        assertEquals(listOf(0f, 0.5f, 1f), referenceChartXFractions(semData))
        val mesmoDia = listOf(ReferencePoint("a", 1.0, LocalDate(2026, 1, 1)), ReferencePoint("b", 2.0, LocalDate(2026, 1, 1)))
        assertEquals(listOf(0f, 1f), referenceChartXFractions(mesmoDia))
        assertEquals(listOf(0.5f), referenceChartXFractions(listOf(ReferencePoint("a", 1.0))))
        assertEquals(emptyList(), referenceChartXFractions(emptyList()))
    }

    @Test
    fun pontoMaisProximoDoToque() {
        val x = listOf(0f, 0.25f, 1f)
        assertEquals(0, nearestReferencePoint(x, 0.1f))
        assertEquals(1, nearestReferencePoint(x, 0.5f))
        assertEquals(2, nearestReferencePoint(x, 0.9f))
        assertNull(nearestReferencePoint(emptyList(), 0.5f))
    }
}

/** Segunda faixa no `ReferenceBandChart` (2.322.0, GAP-VIT-T05) — a parte pura. */
class ReferenceBandChartSecondaryBandTest {

    private val texts = ReferenceBandChartTexts(
        bandRange = { a, b -> "Faixa de referência: $a a $b" },
        bandFrom = { a -> "Faixa de referência: a partir de $a" },
        bandUpTo = { b -> "Faixa de referência: até $b" },
        below = "Abaixo da faixa",
        within = "Na faixa",
        above = "Acima da faixa",
        summary = { n, l -> "$n medições. Última: $l" },
        point = { l, v, s -> listOfNotNull("$l: $v", s).joinToString(", ") },
        next = "Próxima",
        previous = "Anterior",
        empty = "Sem dados",
    )
    private val fmt: (Double) -> String = { it.toInt().toString() }

    @Test
    fun escalaIncluiAsDuasFaixas() {
        assertEquals(30.0 to 100.0, lineChartValueBounds(listOf(50.0), ReferenceBand(40.0, 80.0), ReferenceBand(30.0, 100.0)))
        assertEquals(lineChartValueBounds(listOf(50.0), ReferenceBand(40.0, 80.0)), lineChartValueBounds(listOf(50.0), ReferenceBand(40.0, 80.0), null))
    }

    @Test
    fun textoDaFaixaUsaONomeQuandoHa() {
        assertEquals("Faixa de referência: 40 a 80", referenceBandText(ReferenceBand(40.0, 80.0), texts, fmt))
        assertEquals("Seu padrão: 40 a 80", referenceBandText(ReferenceBand(40.0, 80.0, label = "Seu padrão"), texts, fmt))
        assertEquals("Lab: a partir de 30", referenceBandText(ReferenceBand(min = 30.0, label = "Lab"), texts, fmt))
        assertEquals("Segunda faixa: até 100", referenceBandText(ReferenceBand(max = 100.0), texts, fmt, fallbackName = "Segunda faixa"))
    }

    @Test
    fun leituraDizAsDuasFaixasEmTexto() {
        val lab = ReferenceBand(30.0, 100.0, label = "Faixa do laboratório")
        val principal = ReferenceStatus("Abaixo do seu padrão", StatusTone.WARNING)
        assertEquals(
            "Abaixo do seu padrão · Faixa do laboratório: Na faixa",
            referencePointStatusText(principal, 24.0 + 10.0, lab, null, texts),
        )
        // Status vindo do servidor para a 2ª faixa vence o derivado.
        assertEquals(
            "Faixa do laboratório: normal",
            referencePointStatusText(null, 34.0, lab, ReferenceStatus("normal", StatusTone.SUCCESS), texts),
        )
        // Sem segunda faixa, só o principal; sem nada, null.
        assertEquals("Abaixo do seu padrão", referencePointStatusText(principal, 34.0, null, null, texts))
        assertNull(referencePointStatusText(null, 34.0, null, null, texts))
    }
}
