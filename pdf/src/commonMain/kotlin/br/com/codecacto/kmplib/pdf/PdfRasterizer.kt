package br.com.codecacto.kmplib.pdf

/**
 * Rasterizador de PDF → imagens (uma por página), multiplataforma (expect/actual).
 *
 * Serve ao fluxo de **comprovantes embarcados como imagem** (GAP-MH-M-02): quando o usuário
 * anexa um comprovante em PDF, o app o rasteriza com [renderPdfPagesToImages] e entrega cada
 * página como [HoursReportAttachment.imageBytes] ao [HoursReportPdfGenerator] (que só lida com
 * imagens). Genérico — útil a qualquer app que precise "achatar" um PDF em imagens.
 *
 * Implementação por plataforma:
 *  - **Android:** nativo via `android.graphics.pdf.PdfRenderer` (sem dependência externa),
 *    rasterizando cada página em bitmap a DPI moderado e codificando em PNG.
 *  - **iOS:** render nativo via `CGPDFDocument` + `UIGraphicsImageRenderer` (CoreGraphics/UIKit),
 *    espelhando o `PdfRenderer` do Android — **funcional** (não é placeholder). Validação em macOS/CI.
 *
 * @param pdfBytes bytes de um arquivo PDF válido.
 * @return lista de imagens (PNG bytes), uma por página, na ordem do documento. Pode ser vazia
 *   se o PDF não tiver páginas.
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
expect fun renderPdfPagesToImages(pdfBytes: ByteArray): List<ByteArray>
