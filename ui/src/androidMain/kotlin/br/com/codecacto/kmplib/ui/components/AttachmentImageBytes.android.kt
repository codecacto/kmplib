package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.KmpLibUiInternalApi
import java.io.ByteArrayInputStream

/**
 * Bytes de um arquivo de imagem (o "Arquivo" do seletor de anexo em modo só-foto, 2.332.0) → o MESMO
 * [encodePickedImage] da galeria: JPEG, em pé pelo EXIF, sem metadados, reduzido. HEIC é decodificado
 * pelo `BitmapFactory` do sistema (Android 9+).
 */
@OptIn(KmpLibUiInternalApi::class)
internal actual fun encodePickedImageFromBytes(bytes: ByteArray, maxDimension: Int, jpegQuality: Int): PickedImage? = try {
    encodePickedImage(
        open = { ByteArrayInputStream(bytes) },
        exifOrientation = null,
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )
} catch (e: Exception) {
    AppLogger.w("KmpLibAttachmentPicker", "Arquivo de imagem ilegível: ${e::class.simpleName}")
    null
} catch (e: OutOfMemoryError) {
    // `OutOfMemoryError` não é `Exception`: sem este ramo, uma imagem enorme derrubava o app.
    AppLogger.w("KmpLibAttachmentPicker", "Arquivo de imagem grande demais para a memória disponível.")
    null
}
