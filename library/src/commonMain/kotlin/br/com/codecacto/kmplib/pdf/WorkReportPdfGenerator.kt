package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de PDF de **relatório de acompanhamento de obra** (genérico, multiplataforma).
 *
 * Segue o MESMO padrão de API do [FinanceReportPdfGenerator]/[OsPdfGenerator]:
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument`.
 *  - **iOS:** render nativo via `UIGraphicsPDFRenderer` (UIKit), com o MESMO layout lógico do
 *    Android — **funcional** (não é placeholder). Validação visual em host macOS/CI.
 *
 * Contrato C-5 da Onda 4 do MinhaObra (par com a weblib GAP-MO-W-09). Uso típico
 * (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = WorkReportPdfData(
 *     company = WorkReportPdfCompany(name = "Construtora X"),
 *     workName = "Residência Família Silva",
 *     generatedAtLabel = "Emitido em 07/06/2026",
 *     overallProgress = 62,
 *     stages = ..., photos = ..., diaryEntries = ...,
 *     watermark = isFreePlan,
 * )
 * generateAndShareWorkReportPdf(data)           // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar/upload):
 * val bytes = generateWorkReportPdfBytes(data)
 * ```
 */
interface WorkReportPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException se a plataforma atual ainda não suporta render de PDF
     *   (caso do iOS sem host macOS).
     */
    fun generate(data: WorkReportPdfData): ByteArray
}

/** Fábrica do [WorkReportPdfGenerator] da plataforma atual. */
expect fun createWorkReportPdfGenerator(): WorkReportPdfGenerator

/**
 * Gera os bytes do PDF do relatório de obra a partir de [data], usando o gerador da
 * plataforma. Atalho para `createWorkReportPdfGenerator().generate(data)`.
 *
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateWorkReportPdfBytes(data: WorkReportPdfData): ByteArray =
    createWorkReportPdfGenerator().generate(data)

/**
 * Gera o PDF do relatório de obra e abre o share sheet do sistema
 * (salvar/enviar/imprimir) via [ShareHandler].
 *
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateAndShareWorkReportPdf(
    data: WorkReportPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultWorkReportPdfFileName(data.workName),
    shareTitle: String = "Compartilhar relatório da obra",
): ByteArray {
    val bytes = generateWorkReportPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF do relatório de obra, derivado do [workName].
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultWorkReportPdfFileName(workName: String): String {
    val rawBase = "relatorio-obra-$workName"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "relatorio-obra" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}
