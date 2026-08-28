package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de PDF de **relatório financeiro** (genérico, multiplataforma).
 *
 * Segue o MESMO padrão de API do [OsPdfGenerator]:
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument`.
 *  - **iOS:** render nativo via `UIGraphicsPDFRenderer` (UIKit), com o MESMO layout lógico do
 *    Android — **funcional** (não é placeholder). Validação visual em host macOS/CI.
 *
 * Uso típico (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = FinanceReportPdfData(
 *     company = ..., periodLabel = "Junho/2026", generatedAtLabel = "Emitido em 07/06/2026",
 *     receipts = ..., totalReceived = "1230.00",
 *     receivables = ..., totalReceivable = "450.00",
 * )
 * generateAndShareFinanceReportPdf(data)           // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar, fazer upload, etc.):
 * val bytes = generateFinanceReportPdfBytes(data)
 * ```
 */
interface FinanceReportPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException se a plataforma atual ainda não suporta render
     *   de PDF (caso do iOS sem host macOS).
     */
    fun generate(data: FinanceReportPdfData): ByteArray
}

/**
 * Fábrica do [FinanceReportPdfGenerator] da plataforma atual.
 */
expect fun createFinanceReportPdfGenerator(): FinanceReportPdfGenerator

/**
 * Gera os bytes do PDF do relatório financeiro a partir de [data], usando o gerador da
 * plataforma. Atalho para `createFinanceReportPdfGenerator().generate(data)`.
 *
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateFinanceReportPdfBytes(data: FinanceReportPdfData): ByteArray =
    createFinanceReportPdfGenerator().generate(data)

/**
 * Gera o PDF do relatório financeiro e abre o share sheet do sistema
 * (salvar/enviar/imprimir) via [ShareHandler], reusando a infraestrutura de
 * compartilhamento já existente na lib.
 *
 * @param data dados do relatório.
 * @param shareHandler handler de share (default: o da plataforma).
 * @param fileName nome do arquivo gerado (default derivado do período).
 * @param shareTitle título exibido no chooser de compartilhamento.
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateAndShareFinanceReportPdf(
    data: FinanceReportPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultFinanceReportPdfFileName(data.periodLabel),
    shareTitle: String = "Compartilhar relatório financeiro",
): ByteArray {
    val bytes = generateFinanceReportPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF do relatório financeiro, derivado do [periodLabel].
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultFinanceReportPdfFileName(periodLabel: String): String {
    val rawBase = "relatorio-financeiro-$periodLabel"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "relatorio-financeiro" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}
