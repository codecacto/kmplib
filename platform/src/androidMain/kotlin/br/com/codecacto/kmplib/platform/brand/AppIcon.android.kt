package br.com.codecacto.kmplib.platform.brand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/** Lado, em pixels, do bitmap do ícone — 108dp (a medida do ícone adaptativo) em xxxhdpi. */
private const val ICON_SIZE_PX = 432

/**
 * `PackageManager.getApplicationIcon` — o mesmo drawable que o launcher desenha, já com a máscara do
 * aparelho quando o ícone é adaptativo. Rasterizado uma vez por composição (`remember`).
 */
@Composable
actual fun rememberAppIconPainter(): Painter? {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
            BitmapPainter(drawable.toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX).asImageBitmap())
        }.getOrNull()
    }
}
