package br.com.codecacto.kmplib.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun encodeBitmapToPng(bitmap: ImageBitmap): ByteArray {
    val skiaBitmap = bitmap.asSkiaBitmap()
    val image = Image.makeFromBitmap(skiaBitmap)
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("Failed to encode image")
    return data.bytes
}
