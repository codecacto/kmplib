package br.com.codecacto.kmplib.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Rasterização de PDF → PNG (uma imagem por página) via `android.graphics.pdf.PdfRenderer`
 * (API nativa do Android, sem dependência externa).
 *
 * Estratégia:
 *  - Grava [pdfBytes] num arquivo temporário (o `PdfRenderer` exige um
 *    [ParcelFileDescriptor] sobre arquivo seekable — não aceita stream).
 *  - Para cada página, calcula o tamanho-alvo em [TARGET_DPI] (PDF é definido a 72pt/pol)
 *    e renderiza num [Bitmap] ARGB com fundo branco (modo de display).
 *  - Codifica cada bitmap em PNG.
 *  - Limpa bitmaps e o arquivo temporário ao final.
 *
 * DPI moderado ([TARGET_DPI] = 150) equilibra nitidez do comprovante e tamanho do arquivo.
 */
private const val TARGET_DPI = 150
private const val PDF_POINTS_PER_INCH = 72f

actual fun renderPdfPagesToImages(pdfBytes: ByteArray): List<ByteArray> {
    val tempFile = File.createTempFile("kmplib_raster_", ".pdf")
    try {
        tempFile.writeBytes(pdfBytes)
        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val out = ArrayList<ByteArray>(renderer.pageCount)
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = TARGET_DPI / PDF_POINTS_PER_INCH
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // Fundo branco para páginas com transparência.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        bitmap.recycle()
                        out.add(baos.toByteArray())
                    }
                }
                return out
            }
        }
    } finally {
        tempFile.delete()
    }
}
