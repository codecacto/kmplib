package br.com.codecacto.kmplib.pdf.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 2.315.0 — o DRE como caso do resumo genérico: a tela e o PDF passam por [toReportSummary]. */
class DreAsReportSummaryTest {

    private val dre = DreSummary(
        periodLabel = "Outubro de 2026",
        lines = listOf(
            DreSummaryLine("Receita bruta", "R$ 10.000,00", DreLineKind.REVENUE),
            DreSummaryLine("(−) Impostos", "R$ 600,00", DreLineKind.DEDUCTION),
            DreSummaryLine("Receita líquida", "R$ 9.400,00", DreLineKind.SUBTOTAL),
            DreSummaryLine("Combustível", "R$ 2.000,00", DreLineKind.ITEM, negative = true),
            DreSummaryLine("Total de despesas", "R$ 2.000,00", DreLineKind.TOTAL),
        ),
        result = DreSummaryLine("Resultado", "-R$ 100,00", DreLineKind.TOTAL, negative = true),
        highlights = listOf(ReportHighlight("Recebido", "R$ 8.000,00", ReportTone.POSITIVE)),
        breakdown = listOf(DreSummaryLine("Locações", "R$ 50,00")),
        breakdownTitle = "Por área",
        notes = listOf("Regime de caixa"),
    )

    private val r = dre.toReportSummary("Demonstrativo de resultado")

    @Test
    fun resultadoViraHeroComTomPeloSinal() {
        assertEquals(ReportHighlight("Resultado", "-R$ 100,00", ReportTone.NEGATIVE), r.hero)
        assertEquals(ReportTone.POSITIVE, dre.copy(result = dre.result.copy(negative = false)).toReportSummary("t").hero?.tone)
        assertEquals("Outubro de 2026", r.periodLabel)
        assertEquals(dre.highlights, r.highlights)
        assertEquals(dre.notes, r.notes)
    }

    @Test
    fun demonstrativoViraPrimeiraSecaoComTotalEId() {
        val s = r.sections[0]
        assertEquals("Demonstrativo de resultado", s.title)
        assertEquals("relatorio-dre", s.testTag)
        assertEquals(
            listOf(ReportLineStyle.STRONG, ReportLineStyle.MUTED_INDENTED, ReportLineStyle.SUBTOTAL, ReportLineStyle.INDENTED, ReportLineStyle.SUBTOTAL),
            s.items.map { it.style },
        )
        assertEquals(ReportTone.NEGATIVE, s.items[3].tone)
        assertEquals(ReportTone.NEUTRAL, s.items[0].tone)
        assertEquals("Resultado", s.total?.label)
        assertEquals(ReportTone.NEGATIVE, s.total?.tone)
    }

    @Test
    fun aberturaViraSegundaSecaoESemElaNaoHa() {
        assertEquals("Por área", r.sections[1].title)
        assertNull(r.sections[1].total)
        assertEquals(1, dre.copy(breakdown = emptyList()).toReportSummary("t").sections.size)
    }
}
