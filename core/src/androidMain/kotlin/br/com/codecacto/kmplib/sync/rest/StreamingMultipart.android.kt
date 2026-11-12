package br.com.codecacto.kmplib.sync.rest

import android.content.res.AssetFileDescriptor
import android.net.Uri
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.asSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** URI com esquema que o `ContentResolver` abre (`content://`, `file://`, `android.resource://`). */
private fun uriOrNull(reference: String): Uri? {
    val esquema = reference.substringBefore("://", "").lowercase()
    return if (esquema in setOf("content", "file", "android.resource")) Uri.parse(reference) else null
}

internal actual fun openLocalFileSource(reference: String): RawSource {
    val uri = uriOrNull(reference) ?: return SystemFileSystem.source(Path(reference))
    if (uri.scheme == "file") return SystemFileSystem.source(Path(uri.path ?: throw IOException("URI sem caminho")))
    val context = AndroidAppContext.get()
        ?: throw IOException("kmplib-core: chame initKmpLibCore (ou KmpLib.init) antes de enviar o arquivo.")
    val entrada = context.contentResolver.openInputStream(uri) ?: throw IOException("arquivo indisponível")
    return entrada.asSource()
}

internal actual fun localFileSizeOrNull(reference: String): Long? = runCatching {
    val uri = uriOrNull(reference)
    when {
        uri == null -> SystemFileSystem.metadataOrNull(Path(reference))?.size
        uri.scheme == "file" -> uri.path?.let { SystemFileSystem.metadataOrNull(Path(it))?.size }
        else -> AndroidAppContext.get()?.contentResolver?.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length.takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH && it >= 0L }
        }
    }
}.getOrNull()?.takeIf { it >= 0L }
