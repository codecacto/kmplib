package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.ui.KmpLibUiInternalApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Bytes de um arquivo de imagem (o "Arquivo" do seletor de anexo em modo só-foto, 2.332.0) → a MESMA
 * redução da galeria ([toPickedImage], ImageIO): JPEG em pé, sem EXIF/GPS, HEIC → JPEG.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, KmpLibUiInternalApi::class)
internal actual fun encodePickedImageFromBytes(bytes: ByteArray, maxDimension: Int, jpegQuality: Int): PickedImage? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return data.toPickedImage(maxDimension, jpegQuality)
}
