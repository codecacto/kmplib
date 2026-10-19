package br.com.codecacto.kmplib.pdf.report

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.pdf.DocumentAlign
import br.com.codecacto.kmplib.pdf.DocumentCard
import br.com.codecacto.kmplib.pdf.DocumentColumn
import br.com.codecacto.kmplib.pdf.DocumentInfoRow
import br.com.codecacto.kmplib.pdf.DocumentPdfCompany
import br.com.codecacto.kmplib.pdf.DocumentPdfData
import br.com.codecacto.kmplib.pdf.DocumentRow
import br.com.codecacto.kmplib.pdf.DocumentSection
import br.com.codecacto.kmplib.pdf.defaultDocumentPdfFileName
import br.com.codecacto.kmplib.pdf.generateAndShareDocumentPdf
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * **O DRE do celular** (2.306.0) — o resumo que todo app com recebimentos mostra na tela de
 * Relatórios, e o mesmo dado que vira o PDF ([DreSummaryPdf]).
 *
 * A premissa da fábrica (fundador, 18/out/2026): **relatório completo é coisa de WEB**. No app vai o
 * DRE do período, uns poucos números que importam ([highlights]), **Exportar PDF** e **Ver relatório
 * completo** — que abre o [FullReportOnWebDialog] e leva ao portal. Abas de relatório, tabelas largas
 * e filtros finos ficam no portal (`@codecacto/weblib/reports`), onde cabem.
 *
 * **Tudo chega já formatado** (moeda, período, rótulos) — a lib não soma nem formata dinheiro, como o
 * [DocumentPdfData]. O dinheiro é do servidor; o app só traduz o DTO para esta forma.
 */
@Immutable
data class DreSummary(
    /** O período do DRE como a tela diz ("Outubro de 2026", "01/10 a 18/10"). */
    val periodLabel: String,
    /** As linhas do demonstrativo, na ordem de leitura (receita → deduções → … → custos). */
    val lines: List<DreSummaryLine>,
    /** A última linha — o resultado do período. Vai em destaque no topo e no fim do PDF. */
    val result: DreSummaryLine,
    /** Os números que importam além do DRE (recebido, a receber, vencido). Máximo útil: 4. */
    val highlights: List<ReportHighlight> = emptyList(),
    /**
     * O resultado aberto num eixo do negócio ("Por área": Locações · Ordens de serviço · Compartilhadas),
     * desenhado logo abaixo do resultado. Vazio = sem o bloco.
     */
    val breakdown: List<DreSummaryLine> = emptyList(),
    val breakdownTitle: String? = null,
    /** Notas de rodapé que dão sentido ao número ("Regime: recebido (caixa)"). */
    val notes: List<String> = emptyList(),
)

/** Uma linha do DRE. [negative] pinta o valor de vermelho (o servidor diz o sinal; a lib não lê). */
@Immutable
data class DreSummaryLine(
    val label: String,
    val value: String,
    val kind: DreLineKind = DreLineKind.ITEM,
    val negative: Boolean = false,
)

/** O papel da linha no demonstrativo — decide recuo, peso e divisória, na tela e no PDF. */
enum class DreLineKind {
    /** Receita bruta: abre o demonstrativo, em negrito. */
    REVENUE,

    /** "(−) Impostos", "(−) Descontos": recuada e em tom secundário. */
    DEDUCTION,

    /** Receita líquida, margem: divisória acima, semi-negrito. */
    SUBTOTAL,

    /** Uma categoria de custo/despesa: recuada. */
    ITEM,

    /** "Total de custos e despesas": divisória acima, semi-negrito. */
    TOTAL,
}

/** Um número de destaque ao lado do DRE. */
@Immutable
data class ReportHighlight(
    val label: String,
    val value: String,
    val tone: ReportTone = ReportTone.NEUTRAL,
)

enum class ReportTone { NEUTRAL, POSITIVE, NEGATIVE, WARNING }

/**
 * O PDF do [DreSummary] — cabeçalho da empresa, o período, os destaques em cartões, o demonstrativo
 * em tabela e o resultado em caixa. Desenhado pelo [br.com.codecacto.kmplib.pdf.DocumentPdfGenerator]
 * nativo (Android `PdfDocument`, iOS `UIGraphicsPDFRenderer`).
 */
object DreSummaryPdf {

    /** Recuo das linhas filhas na coluna "Descrição" (a tabela do documento não tem recuo próprio). */
    internal const val INDENT = "    "

    fun build(
        summary: DreSummary,
        company: DocumentPdfCompany,
        texts: ReportSummaryTexts,
        /** Data/hora da emissão já formatada ("18/10/2026 14:32"). */
        generatedAt: String,
    ): DocumentPdfData {
        val cards = buildList {
            add(DocumentCard(summary.result.label, summary.result.value))
            summary.highlights.forEach { add(DocumentCard(it.label, it.value)) }
        }
        val rows = summary.lines.map { line ->
            val label = when (line.kind) {
                DreLineKind.DEDUCTION, DreLineKind.ITEM -> INDENT + line.label
                else -> line.label
            }
            DocumentRow(listOf(label, line.value))
        }
        return DocumentPdfData(
            company = company,
            title = texts.dreTitle,
            subtitle = summary.periodLabel,
            sections = listOfNotNull(
                DocumentSection.Cards(title = texts.highlightsTitle, cards = cards),
                DocumentSection.Table(
                    title = texts.dreTitle,
                    columns = listOf(
                        DocumentColumn(texts.pdfColumnLine, weight = 3f),
                        DocumentColumn(texts.pdfColumnValue, align = DocumentAlign.END, weight = 1.2f),
                    ),
                    rows = rows,
                ),
                DocumentSection.Total(label = summary.result.label, value = summary.result.value),
                summary.breakdown.takeIf { it.isNotEmpty() }?.let { linhas ->
                    DocumentSection.Info(title = summary.breakdownTitle, rows = linhas.map { DocumentInfoRow(it.label, it.value) })
                },
                summary.notes.takeIf { it.isNotEmpty() }?.let { DocumentSection.Paragraph(text = it.joinToString("\n")) },
            ),
            footer = texts.generatedAt(generatedAt),
        )
    }

    /**
     * `DRE_Marco_de_2026.pdf` — o nome que aparece no compartilhar. O acento sai antes da limpeza do
     * [defaultDocumentPdfFileName], senão "Março" viraria "Mar_o".
     */
    fun fileName(summary: DreSummary): String =
        defaultDocumentPdfFileName("DRE ${summary.periodLabel}".map { ACCENTS[it] ?: it }.joinToString(""))

    private val ACCENTS: Map<Char, Char> = buildMap {
        "áàâãä".forEach { put(it, 'a') }; "ÁÀÂÃÄ".forEach { put(it, 'A') }
        "éèêë".forEach { put(it, 'e') }; "ÉÈÊË".forEach { put(it, 'E') }
        "íìîï".forEach { put(it, 'i') }; "ÍÌÎÏ".forEach { put(it, 'I') }
        "óòôõö".forEach { put(it, 'o') }; "ÓÒÔÕÖ".forEach { put(it, 'O') }
        "úùûü".forEach { put(it, 'u') }; "ÚÙÛÜ".forEach { put(it, 'U') }
        put('ç', 'c'); put('Ç', 'C'); put('ñ', 'n'); put('Ñ', 'N')
    }
}

/**
 * Gera o PDF do DRE e abre o compartilhar do sistema (salvar, WhatsApp, e-mail, imprimir).
 *
 * @return os bytes do PDF.
 */
fun exportDreSummaryPdf(
    summary: DreSummary,
    company: DocumentPdfCompany,
    texts: ReportSummaryTexts,
    generatedAt: String,
    shareHandler: ShareHandler = getShareHandler(),
): ByteArray = generateAndShareDocumentPdf(
    data = DreSummaryPdf.build(summary, company, texts, generatedAt),
    shareHandler = shareHandler,
    fileName = DreSummaryPdf.fileName(summary),
    shareTitle = texts.exportPdf,
)
