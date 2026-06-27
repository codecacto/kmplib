@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGDataProviderCreateWithData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGPDFBox
import platform.CoreGraphics.CGPDFDocumentCreateWithProvider
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImagePNGRepresentation

/**
 * Rasterização de PDF → PNG (uma imagem por página) no iOS via `CGPDFDocument` + contexto de
 * imagem UIKit (`UIGraphicsBeginImageContextWithOptions`/CoreGraphics nativos, sem dependência
 * externa), espelhando o comportamento do Android ([renderPdfPagesToImages] android via
 * `PdfRenderer`): cada página é renderizada a [TARGET_DPI] sobre fundo branco e codificada em PNG.
 *
 * Estratégia:
 *  - Cria um `CGDataProvider` direto sobre os bytes (mantidos *pinned* durante toda a operação,
 *    pois o `CGPDFDocument` lê do provider de forma preguiçosa).
 *  - Para cada página, obtém o `kCGPDFMediaBox`, calcula o tamanho-alvo em [TARGET_DPI] (PDF é
 *    definido a 72pt/pol) e desenha a página com o eixo Y invertido (PDF é bottom-left; UIKit é
 *    top-left) sobre fundo branco, num contexto de imagem com escala 1 (tamanho já ampliado).
 *  - Codifica cada imagem em PNG via `UIImagePNGRepresentation`.
 *
 * DPI moderado ([TARGET_DPI] = 150) equilibra nitidez do comprovante e tamanho do arquivo —
 * mesmo valor do Android.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito por construção para compilar/rodar em iOS,
 * mas o ambiente atual é Linux (targets Apple SKIPPED); validação precisa ser feita em macOS/CI.
 */
private const val TARGET_DPI = 150.0
private const val PDF_POINTS_PER_INCH = 72.0

actual fun renderPdfPagesToImages(pdfBytes: ByteArray): List<ByteArray> {
    if (pdfBytes.isEmpty()) return emptyList()
    val scaleFactor = TARGET_DPI / PDF_POINTS_PER_INCH

    return pdfBytes.usePinned { pinned ->
        val provider = CGDataProviderCreateWithData(
            info = null,
            data = pinned.addressOf(0),
            size = pdfBytes.size.toULong(),
            releaseData = null,
        ) ?: return@usePinned emptyList()

        val document = CGPDFDocumentCreateWithProvider(provider)
        if (document == null) {
            CGDataProviderRelease(provider)
            return@usePinned emptyList()
        }

        try {
            val pageCount = CGPDFDocumentGetNumberOfPages(document).toInt()
            val out = ArrayList<ByteArray>(pageCount.coerceAtLeast(0))
            val white = UIColor.colorWithRed(1.0, 1.0, 1.0, 1.0)

            for (pageNumber in 1..pageCount) {
                // CGPDFDocumentGetPage é 1-indexado.
                val page = CGPDFDocumentGetPage(document, pageNumber.toULong()) ?: continue
                val box = CGPDFPageGetBoxRect(page, CGPDFBox.kCGPDFMediaBox)
                val (pw, ph) = box.useContents { size.width to size.height }
                if (pw <= 0.0 || ph <= 0.0) continue

                val targetW = pw * scaleFactor
                val targetH = ph * scaleFactor

                // Contexto de imagem com escala 1 (o tamanho já está ampliado pelo scaleFactor).
                UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetW, targetH), opaque = true, scale = 1.0)
                val png: ByteArray? = run {
                    val cg = UIGraphicsGetCurrentContext() ?: return@run null
                    // Fundo branco (páginas com transparência).
                    CGContextSetFillColorWithColor(cg, white.CGColor)
                    CGContextFillRect(cg, CGRectMake(0.0, 0.0, targetW, targetH))
                    // PDF é bottom-left; UIKit é top-left → inverte o eixo Y e aplica a escala.
                    CGContextTranslateCTM(cg, 0.0, targetH)
                    CGContextScaleCTM(cg, scaleFactor, -scaleFactor)
                    CGContextDrawPDFPage(cg, page)
                    val image = UIGraphicsGetImageFromCurrentImageContext()
                    image?.let { UIImagePNGRepresentation(it)?.toByteArray() }
                }
                UIGraphicsEndImageContext()
                if (png != null) out.add(png)
            }
            out
        } finally {
            CGPDFDocumentRelease(document)
            CGDataProviderRelease(provider)
        }
    }
}

// --- NSData → ByteArray (file-private; espelha os helpers dos geradores iOS) ---

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val out = ByteArray(length)
    if (length == 0) return out
    memScoped {
        val buffer = allocArray<kotlinx.cinterop.ByteVar>(length)
        platform.posix.memcpy(buffer, this@toByteArray.bytes, this@toByteArray.length)
        for (i in 0 until length) out[i] = buffer[i]
    }
    return out
}
