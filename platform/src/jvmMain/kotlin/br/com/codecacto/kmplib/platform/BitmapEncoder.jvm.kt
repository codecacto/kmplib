package br.com.codecacto.kmplib.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Desktop: o bitmap do Compose É um bitmap Skia; o PNG sai pelo codificador do próprio Skia. */
actual fun encodeBitmapToPng(bitmap: ImageBitmap): ByteArray =
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: throw IllegalStateException("Skia não codificou o PNG")
