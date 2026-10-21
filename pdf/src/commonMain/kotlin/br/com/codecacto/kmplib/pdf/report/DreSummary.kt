package br.com.codecacto.kmplib.pdf.report

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.pdf.DocumentPdfCompany
import br.com.codecacto.kmplib.pdf.DocumentPdfData
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

/**
 * Um número de destaque do resumo (do DRE ou do [ReportSummary] genérico). [change] (2.315.0) é a
 * variação opcional em relação a outro período, desenhada sob o valor com seta e cor próprias.
 */
@Immutable
data class ReportHighlight(
    val label: String,
    val value: String,
    val tone: ReportTone = ReportTone.NEUTRAL,
    val change: ReportChange? = null,
)

enum class ReportTone { NEUTRAL, POSITIVE, NEGATIVE, WARNING }

/**
 * O [DreSummary] como caso do resumo genérico (2.315.0): o resultado vira o [ReportSummary.hero], o
 * demonstrativo vira a primeira seção (com o resultado como linha de total, id `relatorio-dre`), a
 * abertura por eixo ([DreSummary.breakdown]) a segunda, e as notas seguem iguais.
 *
 * @param title o título do demonstrativo (`ReportSummaryTexts.dreTitle`).
 */
fun DreSummary.toReportSummary(title: String): ReportSummary {
    val resultTone = if (result.negative) ReportTone.NEGATIVE else ReportTone.POSITIVE
    return ReportSummary(
        title = title,
        periodLabel = periodLabel,
        hero = ReportHighlight(result.label, result.value, resultTone),
        highlights = highlights,
        sections = listOfNotNull(
            ReportListSection(
                title = title,
                items = lines.map { it.toReportListItem() },
                total = ReportListItem(result.label, result.value, tone = resultTone),
                testTag = DRE_SECTION_TEST_TAG,
            ),
            breakdown.takeIf { it.isNotEmpty() }?.let { linhas ->
                // Seção própria: a linha "item" não tem pai acima dela, então não recua.
                ReportListSection(
                    title = breakdownTitle,
                    items = linhas.map { l ->
                        l.toReportListItem().let { if (l.kind == DreLineKind.ITEM) it.copy(style = ReportLineStyle.NORMAL) else it }
                    },
                    testTag = DRE_BREAKDOWN_TEST_TAG,
                )
            },
        ),
        notes = notes,
    )
}

internal const val DRE_SECTION_TEST_TAG = "relatorio-dre"
internal const val DRE_BREAKDOWN_TEST_TAG = "relatorio-dre-abertura"

/** A linha do DRE no vocabulário genérico: o papel vira estilo, o sinal vira tom. */
internal fun DreSummaryLine.toReportListItem(): ReportListItem = ReportListItem(
    label = label,
    value = value,
    style = when (kind) {
        DreLineKind.REVENUE -> ReportLineStyle.STRONG
        DreLineKind.DEDUCTION -> ReportLineStyle.MUTED_INDENTED
        DreLineKind.ITEM -> ReportLineStyle.INDENTED
        DreLineKind.SUBTOTAL, DreLineKind.TOTAL -> ReportLineStyle.SUBTOTAL
    },
    tone = if (negative) ReportTone.NEGATIVE else ReportTone.NEUTRAL,
)

/**
 * O PDF do [DreSummary] — desde 2.315.0 é o [ReportSummaryPdf] do [DreSummary.toReportSummary]:
 * cabeçalho da empresa, o período, os destaques em cartões (resultado primeiro), o demonstrativo em
 * tabela, o resultado em caixa, a abertura por eixo em tabela e as notas.
 */
object DreSummaryPdf {

    /** Recuo das linhas filhas na coluna "Descrição". */
    internal const val INDENT = ReportSummaryPdf.INDENT

    fun build(
        summary: DreSummary,
        company: DocumentPdfCompany,
        texts: ReportSummaryTexts,
        /** Data/hora da emissão já formatada ("18/10/2026 14:32"). */
        generatedAt: String,
    ): DocumentPdfData = ReportSummaryPdf.build(summary.toReportSummary(texts.dreTitle), company, texts, generatedAt)

    /**
     * `DRE_Marco_de_2026.pdf` — o nome que aparece no compartilhar. O acento sai antes da limpeza do
     * [defaultDocumentPdfFileName], senão "Março" viraria "Mar_o".
     */
    fun fileName(summary: DreSummary): String =
        defaultDocumentPdfFileName(stripReportAccents("DRE ${summary.periodLabel}"))
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
