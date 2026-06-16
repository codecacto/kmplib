package br.com.codecacto.kmplib.pdf

/**
 * Implementação iOS de [renderPdfPagesToImages].
 *
 * PENDÊNCIA (herda o item do backlog da kmplib — render iOS a partir de host macOS): o real deve
 * usar `CGPDFDocument` + `UIGraphicsImageRenderer` (UIKit/CoreGraphics) para rasterizar cada
 * página em PNG, espelhando o `PdfRenderer` do Android. Só pode ser escrita/validada em macOS.
 *
 * Até lá, lança [OsPdfNotSupportedException] — o consumidor deve tratar com fallback (ex.: anexar
 * só fotos diretas, sem rasterizar PDF, no iOS).
 */
actual fun renderPdfPagesToImages(pdfBytes: ByteArray): List<ByteArray> {
    // TODO(iOS/macOS): CGPDFDocumentCreateWithProvider(...) + UIGraphicsImageRenderer por página → PNG.
    throw OsPdfNotSupportedException(
        "Rasterização de PDF ainda não implementada no iOS (requer CGPDFDocument + " +
            "UIGraphicsImageRenderer em host macOS).",
    )
}
