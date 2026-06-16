package br.com.codecacto.kmplib.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Compressor de imagem Android via `Bitmap`/`BitmapFactory`. Sem necessidade de `Context`.
 */
class AndroidImageCompressor : ImageCompressor {

    override fun compress(
        bytes: ByteArray,
        maxDimension: Int,
        quality: Int,
        format: ImageCompressFormat,
    ): ByteArray {
        val src = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        } ?: return bytes // best-effort: entrada indecodificável volta como veio

        val scaled = scaleDown(src, maxDimension)
        val out = ByteArrayOutputStream()
        val cf = when (format) {
            ImageCompressFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageCompressFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        scaled.compress(cf, quality.coerceIn(0, 100), out)
        return out.toByteArray()
    }

    private fun scaleDown(src: Bitmap, maxDimension: Int): Bitmap {
        val max = maxOf(src.width, src.height)
        if (maxDimension <= 0 || max <= maxDimension) return src
        val ratio = maxDimension.toFloat() / max
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}

actual fun createImageCompressor(): ImageCompressor = AndroidImageCompressor()
