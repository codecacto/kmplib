package br.com.codecacto.kmplib.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** `Bitmap` de um overlay (Android). */
internal class AndroidOverlayImage(val bitmap: Bitmap) : DecodedOverlayImage

/** Maior lado aceito: acima disso a imagem é amostrada (memória do `GroundOverlay`). */
internal const val MAX_OVERLAY_SIDE_PX: Int = 2048

/** Menor potência de 2 que deixa os dois lados ≤ [maxSide] — o `inSampleSize` do `BitmapFactory`. */
internal fun overlaySampleSize(width: Int, height: Int, maxSide: Int = MAX_OVERLAY_SIDE_PX): Int {
    var sample = 1
    while (width / sample > maxSide || height / sample > maxSide) sample *= 2
    return sample
}

internal actual suspend fun decodeOverlayImage(bytes: ByteArray): DecodedOverlayImage? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    val options = BitmapFactory.Options().apply {
        inSampleSize = overlaySampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::AndroidOverlayImage)
}
