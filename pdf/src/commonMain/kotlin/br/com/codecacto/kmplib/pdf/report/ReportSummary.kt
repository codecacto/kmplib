package br.com.codecacto.kmplib.pdf.report

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.pdf.DocumentAlign
import br.com.codecacto.kmplib.pdf.DocumentCard
import br.com.codecacto.kmplib.pdf.DocumentColumn
import br.com.codecacto.kmplib.pdf.DocumentPdfCompany
import br.com.codecacto.kmplib.pdf.DocumentPdfData
import br.com.codecacto.kmplib.pdf.DocumentRow
import br.com.codecacto.kmplib.pdf.DocumentSection
import br.com.codecacto.kmplib.pdf.defaultDocumentPdfFileName
import br.com.codecacto.kmplib.pdf.generateAndShareDocumentPdf
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * **O resumo de relatório do app, de QUALQUER relatório** (2.315.0) — o mesmo molde do DRE
 * ([DreSummary], que virou um caso deste) para relatório que não é financeiro: rondas, atendimentos,
 * estoque, frequência.
 *
 * A premissa da fábrica (fundador, 18/out/2026): **no app, relatório é RESUMO**. Vai o período, até
 * [MAX_HIGHLIGHTS] números de destaque (com variação opcional), umas poucas listas curtas
 * ([sections] — "os 5 locais com mais ocorrências"), **Exportar PDF** e **Ver relatório completo** no
 * portal. Tabela larga, aba e filtro fino ficam no portal.
 *
 * **Tudo chega já formatado** (números, percentuais, datas, variação): a lib não soma nem formata.
 * Desenhado por [ReportSummaryContent]; o PDF é o [ReportSummaryPdf].
 */
@Immutable
data class ReportSummary(
    /** O nome do relatório ("Rondas do período"). Título do PDF; na tela, cabeçalho quando não há [hero]. */
    val title: String,
    /** O período como a tela diz ("01/10 a 07/10/2026"). */
    val periodLabel: String,
    /** O número principal, em destaque grande no topo (opcional). Não conta no limite de destaques. */
    val hero: ReportHighlight? = null,
    /** Até [MAX_HIGHLIGHTS] números; os excedentes NÃO são desenhados (nem no PDF). */
    val highlights: List<ReportHighlight> = emptyList(),
    /** Listas curtas, na ordem de leitura. Para lista longa, o lugar é o portal. */
    val sections: List<ReportListSection> = emptyList(),
    /** Notas de rodapé que dão sentido ao número ("Conta só rondas encerradas"). */
    val notes: List<String> = emptyList(),
) {
    /** Os destaques que entram na tela e no PDF: os primeiros [MAX_HIGHLIGHTS]. */
    val visibleHighlights: List<ReportHighlight> get() = highlights.take(MAX_HIGHLIGHTS)

    companion object {
        /** Dois por linha, duas linhas: em 360 dp, mais que isso vira parede de número. */
        const val MAX_HIGHLIGHTS: Int = 4
    }
}

/**
 * A variação de um destaque em relação a outro período — o texto chega pronto ("+12% vs. setembro").
 *
 * [trend] escolhe a seta; [tone] escolhe a cor, e são separados de propósito: subir nem sempre é bom
 * (ocorrências subindo é [ReportTone.NEGATIVE]).
 */
@Immutable
data class ReportChange(
    val text: String,
    val trend: ReportTrend = ReportTrend.FLAT,
    val tone: ReportTone = ReportTone.NEUTRAL,
    /** O que o leitor de tela lê no lugar de [text] ("aumento de 12% em relação a setembro"). */
    val contentDescription: String? = null,
)

/** A direção da [ReportChange] — só a seta; a cor é do [ReportChange.tone]. */
enum class ReportTrend { UP, DOWN, FLAT }

/**
 * Uma lista curta do resumo: um título, poucas linhas rótulo → valor e, opcionalmente, uma linha de
 * total destacada no fim (no PDF, a caixa de total).
 *
 * Sem itens nem [total]: com [emptyText], aparece o cartão com a frase; sem ela, a seção não entra.
 */
@Immutable
data class ReportListSection(
    val title: String?,
    val items: List<ReportListItem>,
    val total: ReportListItem? = null,
    val emptyText: String? = null,
    /** Id para o Maestro; `null` = `relatorio-secao-<índice>`. */
    val testTag: String? = null,
) {
    /** A seção aparece (tela e PDF)? */
    val isVisible: Boolean get() = items.isNotEmpty() || total != null || emptyText != null
}

/** Uma linha de [ReportListSection]. [supporting] é a linha menor sob o rótulo ("12 rondas"). */
@Immutable
data class ReportListItem(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val style: ReportLineStyle = ReportLineStyle.NORMAL,
    /** Cor do valor: [ReportTone.NEGATIVE] vermelho, [ReportTone.POSITIVE] verde, etc. */
    val tone: ReportTone = ReportTone.NEUTRAL,
)

/** Recuo, peso e divisória da linha — na tela e no PDF (recuo). */
enum class ReportLineStyle {
    NORMAL,

    /** Em negrito, sem recuo (abre o bloco — "Receita bruta"). */
    STRONG,

    /** Recuada (filha da linha de cima). */
    INDENTED,

    /** Recuada e em tom secundário ("(−) Impostos"). */
    MUTED_INDENTED,

    /** Divisória acima, semi-negrito (subtotal). */
    SUBTOTAL,
}

/**
 * O PDF do [ReportSummary]: cabeçalho da empresa, título e período, os destaques em cartões (o
 * [ReportSummary.hero] primeiro), cada seção em tabela (+ a caixa de total) e as notas. Desenhado
 * pelo gerador de documento nativo (Android `PdfDocument`, iOS `UIGraphicsPDFRenderer`).
 */
object ReportSummaryPdf {

    /** Recuo das linhas filhas na coluna "Descrição" (a tabela do documento não tem recuo próprio). */
    internal const val INDENT = "    "

    fun build(
        summary: ReportSummary,
        company: DocumentPdfCompany,
        texts: ReportSummaryTexts,
        /** Data/hora da emissão já formatada ("18/10/2026 14:32"). */
        generatedAt: String,
    ): DocumentPdfData {
        val cards = buildList {
            summary.hero?.let { add(it.toCard()) }
            summary.visibleHighlights.forEach { add(it.toCard()) }
        }
        val sections = buildList {
            if (cards.isNotEmpty()) add(DocumentSection.Cards(title = texts.highlightsTitle, cards = cards))
            summary.sections.filter { it.isVisible }.forEach { section ->
                if (section.items.isNotEmpty() || section.emptyText != null) {
                    add(
                        DocumentSection.Table(
                            title = section.title,
                            columns = listOf(
                                DocumentColumn(texts.pdfColumnLine, weight = 3f),
                                DocumentColumn(texts.pdfColumnValue, align = DocumentAlign.END, weight = 1.2f),
                            ),
                            rows = section.items.map { DocumentRow(listOf(pdfLabel(it), it.value)) },
                            emptyText = section.emptyText.orEmpty(),
                        ),
                    )
                }
                section.total?.let { add(DocumentSection.Total(label = it.label, value = it.value)) }
            }
            summary.notes.takeIf { it.isNotEmpty() }?.let { add(DocumentSection.Paragraph(text = it.joinToString("\n"))) }
        }
        return DocumentPdfData(
            company = company,
            title = summary.title,
            subtitle = summary.periodLabel,
            sections = sections,
            footer = texts.generatedAt(generatedAt),
        )
    }

    /** `Rondas_do_periodo_01_10_a_07_10_2026.pdf` — sem acento, para o nome não perder letra. */
    fun fileName(summary: ReportSummary): String =
        defaultDocumentPdfFileName(stripReportAccents("${summary.title} ${summary.periodLabel}"))

    internal fun pdfLabel(item: ReportListItem): String {
        val base = item.supporting?.takeIf { it.isNotBlank() }?.let { "${item.label} · $it" } ?: item.label
        return when (item.style) {
            ReportLineStyle.INDENTED, ReportLineStyle.MUTED_INDENTED -> INDENT + base
            else -> base
        }
    }

    private fun ReportHighlight.toCard(): DocumentCard =
        DocumentCard(label = label, value = change?.let { "$value (${it.text})" } ?: value)
}

/**
 * Gera o PDF do [ReportSummary] e abre o compartilhar do sistema (salvar, WhatsApp, e-mail, imprimir).
 *
 * @return os bytes do PDF.
 */
fun exportReportSummaryPdf(
    summary: ReportSummary,
    company: DocumentPdfCompany,
    texts: ReportSummaryTexts,
    generatedAt: String,
    shareHandler: ShareHandler = getShareHandler(),
): ByteArray = generateAndShareDocumentPdf(
    data = ReportSummaryPdf.build(summary, company, texts, generatedAt),
    shareHandler = shareHandler,
    fileName = ReportSummaryPdf.fileName(summary),
    shareTitle = texts.exportPdf,
)

/** Tira o acento antes da limpeza do [defaultDocumentPdfFileName], senão "Março" viraria "Mar_o". */
internal fun stripReportAccents(text: String): String = text.map { REPORT_ACCENTS[it] ?: it }.joinToString("")

private val REPORT_ACCENTS: Map<Char, Char> = buildMap {
    "áàâãä".forEach { put(it, 'a') }; "ÁÀÂÃÄ".forEach { put(it, 'A') }
    "éèêë".forEach { put(it, 'e') }; "ÉÈÊË".forEach { put(it, 'E') }
    "íìîï".forEach { put(it, 'i') }; "ÍÌÎÏ".forEach { put(it, 'I') }
    "óòôõö".forEach { put(it, 'o') }; "ÓÒÔÕÖ".forEach { put(it, 'O') }
    "úùûü".forEach { put(it, 'u') }; "ÚÙÛÜ".forEach { put(it, 'U') }
    put('ç', 'c'); put('Ç', 'C'); put('ñ', 'n'); put('Ñ', 'N')
}
