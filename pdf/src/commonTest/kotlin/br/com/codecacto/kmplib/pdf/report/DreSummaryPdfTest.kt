package br.com.codecacto.kmplib.pdf.report

import br.com.codecacto.kmplib.pdf.DocumentAlign
import br.com.codecacto.kmplib.pdf.DocumentPdfCompany
import br.com.codecacto.kmplib.pdf.DocumentSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DreSummaryPdfTest {

    private val summary = DreSummary(
        periodLabel = "Março de 2026",
        lines = listOf(
            DreSummaryLine("Receita bruta", "R$ 10.000,00", DreLineKind.REVENUE),
            DreSummaryLine("(−) Impostos", "R$ 600,00", DreLineKind.DEDUCTION),
            DreSummaryLine("Receita líquida", "R$ 9.400,00", DreLineKind.SUBTOTAL),
            DreSummaryLine("Combustível", "R$ 2.000,00", DreLineKind.ITEM),
            DreSummaryLine("Total de despesas", "R$ 2.000,00", DreLineKind.TOTAL),
        ),
        result = DreSummaryLine("Resultado do período", "R$ 7.400,00", DreLineKind.TOTAL),
        highlights = listOf(ReportHighlight("Recebido", "R$ 8.000,00", ReportTone.POSITIVE)),
        notes = listOf("Regime: recebido (caixa)"),
    )

    private val pdf = DreSummaryPdf.build(
        summary = summary,
        company = DocumentPdfCompany(name = "Studio Bella Moda"),
        texts = ReportSummaryTexts(),
        generatedAt = "18/10/2026 14:32",
    )

    @Test
    fun cabecalhoTemEmpresaTituloEPeriodo() {
        assertEquals("Studio Bella Moda", pdf.company.name)
        assertEquals("Demonstrativo de resultado", pdf.title)
        assertEquals("Março de 2026", pdf.subtitle)
        assertEquals("Gerado em 18/10/2026 14:32", pdf.footer)
    }

    @Test
    fun cartoesComecamPeloResultadoESeguemOsDestaques() {
        val cards = pdf.sections.filterIsInstance<DocumentSection.Cards>().single().cards
        assertEquals(listOf("Resultado do período", "Recebido"), cards.map { it.label })
        assertEquals("R$ 7.400,00", cards.first().value)
    }

    @Test
    fun tabelaRecuaDeducaoEItemENaoRecuaReceitaNemSubtotal() {
        val table = pdf.sections.filterIsInstance<DocumentSection.Table>().single()
        assertEquals(DocumentAlign.END, table.columns[1].align)
        val labels = table.rows.map { it.cells[0] }
        assertEquals("Receita bruta", labels[0])
        assertEquals(DreSummaryPdf.INDENT + "(−) Impostos", labels[1])
        assertEquals("Receita líquida", labels[2])
        assertEquals(DreSummaryPdf.INDENT + "Combustível", labels[3])
        assertEquals("Total de despesas", labels[4])
        assertEquals(summary.lines.map { it.value }, table.rows.map { it.cells[1] })
    }

    @Test
    fun resultadoEmCaixaENotasNoFim() {
        val total = pdf.sections.filterIsInstance<DocumentSection.Total>().single()
        assertEquals("Resultado do período", total.label)
        assertEquals("R$ 7.400,00", total.value)
        assertTrue(pdf.sections.last() is DocumentSection.Paragraph)
    }

    @Test
    fun semNotasNaoHaParagrafo() {
        val semNotas = DreSummaryPdf.build(summary.copy(notes = emptyList()), DocumentPdfCompany("X"), ReportSummaryTexts(), "agora")
        assertTrue(semNotas.sections.none { it is DocumentSection.Paragraph })
    }

    @Test
    fun aberturaDoResultadoViraBlocoDepoisDoTotal() {
        val comArea = summary.copy(
            breakdownTitle = "Por área",
            breakdown = listOf(DreSummaryLine("Resultado — Locações", "R$ 5.000,00"), DreSummaryLine("Compartilhadas", "-R$ 100,00", negative = true)),
        )
        val doc = DreSummaryPdf.build(comArea, DocumentPdfCompany("X"), ReportSummaryTexts(), "agora")
        val i = doc.sections.indexOfFirst { it is DocumentSection.Total }
        val info = doc.sections[i + 1] as DocumentSection.Info
        assertEquals("Por área", info.title)
        assertEquals(listOf("Resultado — Locações", "Compartilhadas"), info.rows.map { it.label })
        assertTrue(summary.breakdown.isEmpty() && pdf.sections.none { it is DocumentSection.Info })
    }

    @Test
    fun nomeDoArquivoTiraOAcento() {
        assertEquals("DRE_Marco_de_2026.pdf", DreSummaryPdf.fileName(summary))
    }
}
