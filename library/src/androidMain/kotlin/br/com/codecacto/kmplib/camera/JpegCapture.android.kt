package br.com.codecacto.kmplib.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * Qualidade de compressão JPEG das fotos capturadas (0..100).
 * 85 equilibra nitidez (leitura da placa) e tamanho de upload.
 */
private const val CAPTURE_JPEG_QUALITY = 85

/**
 * Converte o [ImageProxy] resultante de `ImageCapture.takePicture` (formato
 * **JPEG**) em bytes JPEG com a rotação **aplicada aos pixels** (upright).
 *
 * O `ImageCapture` entrega os bytes já em JPEG (`planes[0].buffer`), mas a
 * orientação vem em `imageInfo.rotationDegrees`. Para garantir que qualquer
 * consumidor (upload/Storage) veja a foto na orientação correta sem depender
 * de EXIF, decodificamos, rotacionamos via [Matrix] e recodificamos em JPEG.
 *
 * @return bytes JPEG upright.
 */
internal fun imageProxyToUprightJpeg(image: ImageProxy): ByteArray {
    val buffer = image.planes[0].buffer
    val raw = ByteArray(buffer.remaining())
    buffer.get(raw)

    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) {
        return raw
    }

    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )

    return ByteArrayOutputStream().use { out ->
        rotated.compress(Bitmap.CompressFormat.JPEG, CAPTURE_JPEG_QUALITY, out)
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
        out.toByteArray()
    }
}
