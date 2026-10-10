package br.com.codecacto.kmplib.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.KmpLibUiInternalApi
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * A ÚNICA codificação de foto do Android (2.278.0): seletor de uma foto, seletor múltiplo e câmera
 * guiada (`kmplib-camera`) passam por aqui — antes eram duas cópias quase iguais, uma em cada
 * seletor.
 *
 * O que sai é sempre o [PickedImage] prometido: **JPEG**, em pé (a transformação de orientação é
 * aplicada aos pixels, e o `Bitmap.compress` não escreve EXIF — sem GPS, sem modelo do aparelho),
 * com o maior lado ≤ [maxDimension] e a medida lida do bitmap FINAL.
 *
 * Decodificação em duas passadas (a recomendação oficial, "Loading large bitmaps efficiently"):
 * primeiro só as medidas (`inJustDecodeBounds`), depois a imagem já amostrada por
 * [decodeSampleSize] — uma foto de 48 MP não é decodificada inteira só para virar 1024 px.
 *
 * @param open abre um fluxo NOVO a cada chamada (são até três leituras: medidas, pixels e, se
 *   [exifOrientation] for `null`, a orientação). Quem chama não fecha nada: cada fluxo é fechado
 *   aqui.
 * @param exifOrientation a orientação já conhecida (ex.: `ExifInterface.ORIENTATION_ROTATE_90`,
 *   ou [exifOrientationForRotation] a partir dos graus do CameraX); `null` = ler do próprio
 *   arquivo.
 * @param maxDimension teto do maior lado; preso em [PICKED_IMAGE_MIN_DIMENSION]..[PICKED_IMAGE_MAX_DIMENSION_LIMIT].
 * @param jpegQuality 1..100.
 * @return `null` quando a imagem não pôde ser lida — o log já nomeou o motivo.
 */
@KmpLibUiInternalApi
fun encodePickedImage(
    open: () -> InputStream?,
    exifOrientation: Int? = null,
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
    jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY,
): PickedImage? {
    val teto = coercePickedImageMaxDimension(maxDimension)
    val qualidade = coercePickedImageJpegQuality(jpegQuality)
    if (teto != maxDimension || qualidade != jpegQuality) {
        AppLogger.w(TAG, "Medida/qualidade fora da faixa ajustadas para $teto px / $qualidade.")
    }

    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // Com `inJustDecodeBounds` o `decodeStream` devolve SEMPRE `null` — o resultado está em
    // `outWidth`/`outHeight`. Só o fluxo ausente é falha aqui.
    val fluxoDeMedidas = open() ?: run {
        AppLogger.w(TAG, "Foto sem fluxo de leitura.")
        return null
    }
    fluxoDeMedidas.use { BitmapFactory.decodeStream(it, null, medidas) }
    if (medidas.outWidth <= 0 || medidas.outHeight <= 0) {
        AppLogger.w(TAG, "Foto sem medida utilizável (formato não reconhecido).")
        return null
    }

    val opcoes = BitmapFactory.Options().apply {
        inSampleSize = decodeSampleSize(medidas.outWidth, medidas.outHeight, teto)
    }
    val original = open()?.use { BitmapFactory.decodeStream(it, null, opcoes) }
    if (original == null) {
        AppLogger.w(TAG, "Foto não pôde ser decodificada.")
        return null
    }

    val orientacao = exifOrientation ?: readExifOrientation(open)
    val emPe = applyExifOrientation(original, orientacao)
    val reduzida = scaleToMaxDimension(emPe, teto)

    val saida = ByteArrayOutputStream()
    reduzida.compress(Bitmap.CompressFormat.JPEG, qualidade, saida)
    // Lidos ANTES do recycle: num bitmap reciclado `width`/`height` não valem mais.
    val largura = reduzida.width
    val altura = reduzida.height

    if (reduzida !== emPe) reduzida.recycle()
    if (emPe !== original) emPe.recycle()
    original.recycle()

    return PickedImage(bytes = saida.toByteArray(), widthPx = largura, heightPx = altura)
}

/**
 * Os graus de rotação que o CameraX informa (`ImageInfo.rotationDegrees`) como orientação EXIF —
 * para a câmera guiada usar o mesmo [encodePickedImage].
 */
@KmpLibUiInternalApi
fun exifOrientationForRotation(rotationDegrees: Int): Int = when (((rotationDegrees % 360) + 360) % 360) {
    90 -> ExifInterface.ORIENTATION_ROTATE_90
    180 -> ExifInterface.ORIENTATION_ROTATE_180
    270 -> ExifInterface.ORIENTATION_ROTATE_270
    else -> ExifInterface.ORIENTATION_NORMAL
}

/** [encodePickedImage] sobre um `content://`/`file://` — o caminho dos dois seletores. */
internal fun encodePickedImage(
    context: Context,
    uri: Uri,
    maxDimension: Int,
    jpegQuality: Int,
): PickedImage? {
    @OptIn(KmpLibUiInternalApi::class)
    return encodePickedImage(
        open = { context.contentResolver.openInputStream(uri) },
        exifOrientation = null,
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )
}

private fun readExifOrientation(open: () -> InputStream?): Int = try {
    open()?.use { ExifInterface(it) }
        ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ?: ExifInterface.ORIENTATION_NORMAL
} catch (_: Exception) {
    // Arquivo sem EXIF legível é foto sem rotação, não foto ilegível.
    ExifInterface.ORIENTATION_NORMAL
}

/**
 * Aplica as OITO orientações EXIF aos pixels. Até a 2.277.x só as três rotações eram tratadas, e
 * uma foto espelhada (selfie salva por alguns apps de câmera) chegava espelhada.
 */
private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matriz = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matriz.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matriz.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matriz.setRotate(180f); matriz.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> { matriz.setRotate(90f); matriz.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matriz.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matriz.setRotate(-90f); matriz.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matriz.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
}

private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val (largura, altura) = scaledImageSize(bitmap.width, bitmap.height, maxDimension)
    if (largura == bitmap.width && altura == bitmap.height) return bitmap
    return Bitmap.createScaledBitmap(bitmap, largura, altura, true)
}

private const val TAG = "KmpLibImageEncoder"
