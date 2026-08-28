package br.com.codecacto.kmplib.platform

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

/**
 * Compressor de imagem iOS via Skia (`org.jetbrains.skia`, já usado pelo [encodeBitmapToPng]).
 * commonMain-friendly — não depende de host de UI. Validar render/qualidade em build nativo
 * macOS (herda o item de backlog de publicação iOS).
 */
class IosImageCompressor : ImageCompressor {

    override fun compress(
        bytes: ByteArray,
        maxDimension: Int,
        quality: Int,
        format: ImageCompressFormat,
    ): ByteArray {
        val src = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            null
        } ?: return bytes // best-effort

        val max = maxOf(src.width, src.height)
        val scaled: Image = if (maxDimension <= 0 || max <= maxDimension) {
            src
        } else {
            val ratio = maxDimension.toFloat() / max
            val w = (src.width * ratio).toInt().coerceAtLeast(1)
            val h = (src.height * ratio).toInt().coerceAtLeast(1)
            resize(src, w, h)
        }

        val skiaFormat = when (format) {
            ImageCompressFormat.JPEG -> EncodedImageFormat.JPEG
            ImageCompressFormat.PNG -> EncodedImageFormat.PNG
        }
        val data = scaled.encodeToData(skiaFormat, quality.coerceIn(0, 100)) ?: return bytes
        return data.bytes
    }

    private fun resize(src: Image, w: Int, h: Int): Image {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(w, h)
        val canvas = Canvas(bitmap)
        canvas.drawImageRect(
            src,
            Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.LINEAR,
            Paint(),
            true,
        )
        return Image.makeFromBitmap(bitmap)
    }
}

actual fun createImageCompressor(): ImageCompressor = IosImageCompressor()
