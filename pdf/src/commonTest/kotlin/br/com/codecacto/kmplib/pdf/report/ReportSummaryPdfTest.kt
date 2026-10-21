package br.com.codecacto.kmplib.pdf.report

import br.com.codecacto.kmplib.pdf.DocumentPdfCompany
import br.com.codecacto.kmplib.pdf.DocumentSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 2.315.0 — o resumo de relatório genérico e o PDF dele (o DRE virou um caso: `DreSummaryPdfTest`). */
class ReportSummaryPdfTest {

    private val summary = ReportSummary(
        title = "Rondas do período",
        periodLabel = "01/10 a 07/10/2026",
        highlights = listOf(
            ReportHighlight("No horário", "92%", ReportTone.POSITIVE, ReportChange("+4 p.p. vs. semana anterior", ReportTrend.UP, ReportTone.POSITIVE)),
            ReportHighlight("Pontos", "1.240"),
            ReportHighlight("Ocorrências", "7", ReportTone.WARNING),
            ReportHighlight("Alertas em aberto", "2", ReportTone.NEGATIVE),
            ReportHighlight("Quinto", "não entra"),
        ),
        sections = listOf(
            ReportListSection(
                title = "Locais com mais ocorrências",
                items = listOf(
                    ReportListItem("Portaria norte", "3", supporting = "Condomínio Jardim das Flores"),
                    ReportListItem("Garagem", "2", style = ReportLineStyle.INDENTED),
                ),
                total = ReportListItem("Total", "5"),
            ),
            ReportListSection(title = "Rondas perdidas", items = emptyList(), emptyText = "Nenhuma ronda perdida."),
            ReportListSection(title = "Invisível", items = emptyList()),
        ),
        notes = listOf("Conta só rondas encerradas."),
    )

    private val pdf = ReportSummaryPdf.build(summary, DocumentPdfCompany("Vigilância Horizonte"), ReportSummaryTexts(), "07/10/2026 18:00")

    @Test
    fun cabecalhoUsaTituloEPeriodoDoResumo() {
        assertEquals("Rondas do período", pdf.title)
        assertEquals("01/10 a 07/10/2026", pdf.subtitle)
        assertEquals("Gerado em 07/10/2026 18:00", pdf.footer)
    }

    @Test
    fun soQuatroDestaquesETextoDaVariacaoNoValor() {
        assertEquals(4, summary.visibleHighlights.size)
        val cards = pdf.sections.filterIsInstance<DocumentSection.Cards>().single().cards
        assertEquals(listOf("No horário", "Pontos", "Ocorrências", "Alertas em aberto"), cards.map { it.label })
        assertEquals("92% (+4 p.p. vs. semana anterior)", cards.first().value)
        assertEquals("1.240", cards[1].value)
    }

    @Test
    fun heroEntraPrimeiroNosCartoesSemContarNoLimite() {
        val comHero = summary.copy(hero = ReportHighlight("Rondas", "48"))
        val cards = ReportSummaryPdf.build(comHero, DocumentPdfCompany("X"), ReportSummaryTexts(), "agora")
            .sections.filterIsInstance<DocumentSection.Cards>().single().cards
        assertEquals(5, cards.size)
        assertEquals("Rondas", cards.first().label)
    }

    @Test
    fun secaoViraTabelaComApoioERecuoEDepoisOTotal() {
        val tables = pdf.sections.filterIsInstance<DocumentSection.Table>()
        assertEquals(listOf("Locais com mais ocorrências", "Rondas perdidas"), tables.map { it.title })
        val linhas = tables.first().rows.map { it.cells }
        assertEquals(listOf("Portaria norte · Condomínio Jardim das Flores", "3"), linhas[0])
        assertEquals(listOf(ReportSummaryPdf.INDENT + "Garagem", "2"), linhas[1])
        val iTabela = pdf.sections.indexOf(tables.first())
        val total = pdf.sections[iTabela + 1] as DocumentSection.Total
        assertEquals("Total" to "5", total.label to total.value)
    }

    @Test
    fun secaoVaziaComFraseApareceESemFraseSome() {
        val vazia = pdf.sections.filterIsInstance<DocumentSection.Table>().single { it.title == "Rondas perdidas" }
        assertTrue(vazia.rows.isEmpty())
        assertEquals("Nenhuma ronda perdida.", vazia.emptyText)
        assertTrue(pdf.sections.none { it.title == "Invisível" })
        assertTrue(summary.sections[1].isVisible)
        assertTrue(!summary.sections[2].isVisible)
    }

    @Test
    fun notasNoFim() {
        val p = pdf.sections.last() as DocumentSection.Paragraph
        assertEquals("Conta só rondas encerradas.", p.text)
    }

    @Test
    fun semDestaquesNemHeroNaoHaCartoes() {
        val doc = ReportSummaryPdf.build(ReportSummary("T", "P"), DocumentPdfCompany("X"), ReportSummaryTexts(), "agora")
        assertTrue(doc.sections.isEmpty())
    }

    @Test
    fun nomeDoArquivoSemAcento() {
        assertEquals("Rondas_do_periodo_01_10_a_07_10_2026.pdf", ReportSummaryPdf.fileName(summary))
    }

    @Test
    fun idDaSecaoSemTagPropria() {
        assertEquals("relatorio-secao-2", reportSectionTestTag(2))
    }
}
