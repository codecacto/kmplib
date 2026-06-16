package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de **PDF de tabela genérico** (relatório não-financeiro), multiplataforma.
 *
 * Segue o MESMO padrão de API dos demais geradores da lib
 * ([OsPdfGenerator]/[FinanceReportPdfGenerator]/[WorkReportPdfGenerator]):
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument` (cabeçalho do
 *    documento + linha de cabeçalho da tabela + linhas com zebra/strokes neutros +
 *    paginação automática quando as linhas estouram a página). **Sem nenhum bloco
 *    monetário** ("TOTAL R$").
 *  - **iOS:** placeholder (lança [OsPdfNotSupportedException]; TODO `UIGraphicsPDFRenderer`
 *    em host macOS — herda o item de backlog da kmplib, igual aos demais geradores).
 *
 * Distinto do [OsPdfGenerator] (documento financeiro com colunas de dinheiro e caixa
 * "TOTAL R$"). Use este para relatórios tabulares puros: frequência/presença, listas,
 * inventário, agendas, etc.
 *
 * Uso típico (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = TableReportPdfData(
 *     company = TableReportPdfCompany(name = "Prof. Ana"),
 *     title = "Relatório de Frequência",
 *     subtitle = "Turma 3º A — Maio/2026",
 *     columns = listOf(
 *         TableReportColumn("Aluno", weight = 3f),
 *         TableReportColumn("Presenças", TableReportAlign.END),
 *         TableReportColumn("Total", TableReportAlign.END),
 *         TableReportColumn("Frequência", TableReportAlign.END),
 *     ),
 *     rows = alunos.map { TableReportRow(listOf(it.nome, "${it.presencas}", "${it.total}", "${it.pct}%")) },
 *     summary = "Frequência média da turma: 92%",
 *     footer = "Emitido em 14/06/2026",
 *     watermark = isFreePlan,
 * )
 * generateAndShareTablePdf(data)            // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar/upload):
 * val bytes = generateTablePdfBytes(data)
 * ```
 */
interface TableReportPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException se a plataforma atual ainda não suporta render
     *   de PDF (caso do iOS sem host macOS).
     */
    fun generate(data: TableReportPdfData): ByteArray
}

/** Fábrica do [TableReportPdfGenerator] da plataforma atual. */
expect fun createTableReportPdfGenerator(): TableReportPdfGenerator

/**
 * Gera os bytes do PDF de tabela a partir de [data], usando o gerador da plataforma.
 * Atalho para `createTableReportPdfGenerator().generate(data)`.
 *
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateTablePdfBytes(data: TableReportPdfData): ByteArray =
    createTableReportPdfGenerator().generate(data)

/**
 * Gera o PDF de tabela e abre o share sheet do sistema (salvar/enviar/imprimir) via
 * [ShareHandler], reusando a infraestrutura de compartilhamento já existente na lib.
 *
 * @param data dados do relatório de tabela.
 * @param shareHandler handler de share (default: o da plataforma).
 * @param fileName nome do arquivo gerado (default derivado do título).
 * @param shareTitle título exibido no chooser de compartilhamento.
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateAndShareTablePdf(
    data: TableReportPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultTablePdfFileName(data.title),
    shareTitle: String = "Compartilhar relatório",
): ByteArray {
    val bytes = generateTablePdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF de tabela, derivado do [title].
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultTablePdfFileName(title: String): String {
    val rawBase = "relatorio-$title"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "relatorio" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}
