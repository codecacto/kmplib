package br.com.codecacto.kmplib.map

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

/** `UIImage` de um overlay (iOS). */
internal class IosOverlayImage(val image: UIImage) : DecodedOverlayImage

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun decodeOverlayImage(bytes: ByteArray): DecodedOverlayImage? = withContext(Dispatchers.Default) {
    if (bytes.isEmpty()) return@withContext null
    val data = bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
    UIImage.imageWithData(data)?.let(::IosOverlayImage)
}
