package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de PDF do **"Relatório de horas extras"** (dossiê de cobrança, genérico e
 * multiplataforma).
 *
 * Segue o MESMO padrão de API do [WorkReportPdfGenerator] / [FinanceReportPdfGenerator] /
 * [OsPdfGenerator]:
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument`.
 *  - **iOS:** render nativo via `UIGraphicsPDFRenderer` (UIKit), com o MESMO layout lógico do
 *    Android — **funcional** (não é placeholder). Validação visual em host macOS/CI.
 *
 * GAP-MH-M-02 da Onda 2 do MinhasHoras (par com a weblib). Uso típico (em ViewModel/repository,
 * fora da UI):
 * ```kotlin
 * val data = HoursReportPdfData(
 *     company = HoursReportPdfCompany(name = "João da Silva"),
 *     periodLabel = "01/05 a 31/05/2026",
 *     companyLabel = "Empresa: Construtora X",
 *     generatedAtLabel = "Emitido em 07/06/2026",
 *     entries = ...,
 *     totalHoursLabel = "32h30",
 *     totalPendingLabel = "R$ 1.200,00",
 *     attachments = ...,            // fotos OU páginas de PDF rasterizadas
 *     watermark = isFreePlan,
 * )
 * generateAndShareHoursReportPdf(data)          // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar/upload):
 * val bytes = generateHoursReportPdfBytes(data)
 * ```
 */
interface HoursReportPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException se a plataforma atual ainda não suporta render de PDF
     *   (caso do iOS sem host macOS).
     */
    fun generate(data: HoursReportPdfData): ByteArray
}

/** Fábrica do [HoursReportPdfGenerator] da plataforma atual. */
expect fun createHoursReportPdfGenerator(): HoursReportPdfGenerator

/**
 * Gera os bytes do PDF do relatório de horas a partir de [data], usando o gerador da
 * plataforma. Atalho para `createHoursReportPdfGenerator().generate(data)`.
 *
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateHoursReportPdfBytes(data: HoursReportPdfData): ByteArray =
    createHoursReportPdfGenerator().generate(data)

/**
 * Gera o PDF do relatório de horas e abre o share sheet do sistema
 * (salvar/enviar/imprimir) via [ShareHandler].
 *
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateAndShareHoursReportPdf(
    data: HoursReportPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultHoursReportPdfFileName(data.periodLabel),
    shareTitle: String = "Compartilhar relatório de horas",
): ByteArray {
    val bytes = generateHoursReportPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF do relatório de horas, derivado do [periodLabel].
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultHoursReportPdfFileName(periodLabel: String): String {
    val rawBase = "relatorio-horas-$periodLabel"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "relatorio-horas" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}
