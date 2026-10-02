package br.com.codecacto.kmplib.platform.brand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * O ícone do app lido do bundle: `CFBundleIcons` (e `CFBundleIcons~ipad`) do `Info.plist` dizem o
 * nome, `UIImage(named:)` carrega, e o PNG vira `ImageBitmap` pelo Skia. Tudo API pública.
 */
@Composable
actual fun rememberAppIconPainter(): Painter? =
    remember { runCatching { loadAppIcon() }.getOrNull()?.let { BitmapPainter(it) } }

private fun loadAppIcon(): ImageBitmap? {
    val candidates = listOf("CFBundleIcons", "CFBundleIcons~ipad").flatMap { key ->
        val primary = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? Map<*, *>)
            ?.get("CFBundlePrimaryIcon") as? Map<*, *>
        appIconCandidates(
            iconFiles = (primary?.get("CFBundleIconFiles") as? List<*>).orEmpty().filterIsInstance<String>(),
            iconName = primary?.get("CFBundleIconName") as? String,
        )
    }.distinct()
    val image = candidates.firstNotNullOfOrNull { UIImage.imageNamed(it) } ?: return null
    val bytes = UIImagePNGRepresentation(image)?.toByteArray() ?: return null
    if (bytes.isEmpty()) return null
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
