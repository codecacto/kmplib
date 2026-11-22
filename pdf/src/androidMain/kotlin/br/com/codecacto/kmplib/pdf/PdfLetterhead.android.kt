package br.com.codecacto.kmplib.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.ByteArrayInputStream

private const val TAG = "KmpLibPdfLetterhead"

/**
 * O timbrado pronto para desenhar no `PdfDocument`: as faixas medidas ([frame]) e as imagens já
 * decodificadas na medida em que vão ao papel. Preparado UMA vez por documento e desenhado em
 * cada página por [drawOn].
 */
internal class AndroidLetterhead(
    val frame: LetterheadPageFrame,
    private val headerBitmap: Bitmap?,
    private val footerBitmap: Bitmap?,
    private val pageWidth: Float,
    private val margin: Float,
    private val textColor: Int,
    private val lineColor: Int,
) {
    fun drawOn(canvas: Canvas) {
        frame.header?.let { drawBand(canvas, it, frame.headerTop, headerBitmap) }
        frame.footer?.let { drawBand(canvas, it, frame.footerTop, footerBitmap) }
        val line = Paint().apply { color = lineColor; strokeWidth = 0.75f }
        frame.headerSeparatorY?.let { canvas.drawLine(margin, it, pageWidth - margin, it, line) }
        frame.footerSeparatorY?.let { canvas.drawLine(margin, it, pageWidth - margin, it, line) }
    }

    private fun drawBand(canvas: Canvas, band: LetterheadBandLayout, top: Float, bitmap: Bitmap?) {
        val image = band.image
        if (image != null && bitmap != null) {
            val dst = RectF(image.x, top + image.y, image.x + image.width, top + image.y + image.height)
            canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        val paint = Paint().apply {
            isAntiAlias = true
            color = textColor
            textSize = band.fontSize
            typeface = Typeface.DEFAULT
            textAlign = when (band.align) {
                PdfLetterheadAlign.LEFT -> Paint.Align.LEFT
                PdfLetterheadAlign.CENTER -> Paint.Align.CENTER
                PdfLetterheadAlign.RIGHT -> Paint.Align.RIGHT
            }
        }
        band.lines.forEachIndexed { i, text -> canvas.drawText(text, band.textAnchorX, top + band.baselines[i], paint) }
    }

    fun recycle() {
        headerBitmap?.recycle()
        footerBitmap?.recycle()
    }
}

/**
 * Mede as faixas e decodifica as imagens do [letterhead]. `null` quando não há timbrado (o PDF segue
 * o caminho de sempre, idêntico ao de antes).
 */
internal fun prepareAndroidLetterhead(
    letterhead: PdfLetterhead?,
    pageWidth: Float,
    pageHeight: Float,
    margin: Float,
    textColor: Int,
    lineColor: Int,
): AndroidLetterhead? {
    if (letterhead == null || letterhead.isEmpty) return null
    val width = pageWidth - 2 * margin

    fun measurer(spec: LetterheadBandSpec): (String) -> Float {
        val p = Paint().apply { isAntiAlias = true; textSize = spec.fontSize; typeface = Typeface.DEFAULT }
        return { p.measureText(it) }
    }

    val headerSource = decodeLetterheadImage(letterhead.headerImage)
    val footerSource = decodeLetterheadImage(letterhead.footerImage)

    val header = if (letterhead.hasHeader) {
        PdfLetterheadLayout.layoutBand(
            PdfLetterheadLayout.HEADER, letterhead.headerAlign, margin, width,
            headerSource?.width, headerSource?.height, letterhead.headerText, measurer(PdfLetterheadLayout.HEADER),
        )
    } else null
    val footer = if (letterhead.hasFooter) {
        PdfLetterheadLayout.layoutBand(
            PdfLetterheadLayout.FOOTER, letterhead.footerAlign, margin, width,
            footerSource?.width, footerSource?.height, letterhead.footerText, measurer(PdfLetterheadLayout.FOOTER),
        )
    } else null
    if (header == null && footer == null) {
        headerSource?.recycle(); footerSource?.recycle()
        return null
    }

    return AndroidLetterhead(
        frame = PdfLetterheadLayout.pageFrame(pageHeight, margin, header, footer),
        headerBitmap = headerSource?.let { fitToRender(it, header?.image) },
        footerBitmap = footerSource?.let { fitToRender(it, footer?.image) },
        pageWidth = pageWidth,
        margin = margin,
        textColor = textColor,
        lineColor = lineColor,
    )
}

/** Reduz a imagem decodificada para os pixels que de fato vão ao papel (≈ 216 dpi). */
private fun fitToRender(source: Bitmap, rect: LetterheadRect?): Bitmap? {
    if (rect == null) {
        source.recycle()
        return null
    }
    val (w, h) = PdfLetterheadLayout.renderPixels(rect.width, rect.height, source.width, source.height)
    if (w == source.width && h == source.height) return source
    val scaled = Bitmap.createScaledBitmap(source, w, h, true)
    if (scaled !== source) source.recycle()
    return scaled
}

/**
 * PNG/JPEG → `Bitmap` em pé, com o maior lado ≤ [PdfLetterheadLayout.MAX_DECODE_PX]: medidas
 * primeiro (`inJustDecodeBounds`), depois `inSampleSize` — a decodificação nunca aloca a imagem
 * inteira. Arquivo recusado ou ilegível = `null` (a faixa fica só com o texto).
 */
internal fun decodeLetterheadImage(bytes: ByteArray?): Bitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    if (!PdfLetterheadLayout.isAcceptedImage(bytes)) {
        AppLogger.w(TAG, "Imagem do timbrado ignorada: não é PNG/JPEG ou passa de ${PdfLetterheadLayout.MAX_IMAGE_BYTES} bytes.")
        return null
    }
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            AppLogger.w(TAG, "Imagem do timbrado sem medida legível.")
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = PdfLetterheadLayout.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: run {
            AppLogger.w(TAG, "Imagem do timbrado não pôde ser decodificada.")
            return null
        }
        val upright = applyExif(decoded, bytes)
        val (w, h) = PdfLetterheadLayout.boundedSize(upright.width, upright.height)
        if (w == upright.width && h == upright.height) {
            upright
        } else {
            Bitmap.createScaledBitmap(upright, w, h, true).also { if (it !== upright) upright.recycle() }
        }
    } catch (e: OutOfMemoryError) {
        AppLogger.w(TAG, "Imagem do timbrado grande demais para a memória livre.")
        null
    } catch (e: Exception) {
        AppLogger.w(TAG, "Imagem do timbrado ilegível (${e::class.simpleName}).")
        null
    }
}

private fun applyExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    if (PdfLetterheadLayout.detectFormat(bytes) != LetterheadImageFormat.JPEG) return bitmap
    val orientation = try {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
