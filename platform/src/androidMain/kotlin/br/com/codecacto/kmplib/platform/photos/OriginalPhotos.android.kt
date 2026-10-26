package br.com.codecacto.kmplib.platform.photos

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.newUuidV7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "OriginalPhotoPicker"
private const val COPY_BUFFER_BYTES = 256 * 1024

@Composable
actual fun rememberOriginalPhotoPicker(
    maxItems: Int,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)

    fun entregar(uris: List<Uri>) {
        if (uris.isEmpty()) {
            callback.value(OriginalPhotoPickResult(emptyList()))
            return
        }
        scope.launch {
            val copias = withContext(Dispatchers.IO) { uris.map { copyOriginalPhoto(context, it) } }
            val fotos = copias.filterNotNull()
            callback.value(OriginalPhotoPickResult(fotos, failedCount = copias.size - fotos.size))
        }
    }

    // O contrato de vários exige teto >= 2; "uma só" é o PickVisualMedia.
    val uma = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        entregar(listOfNotNull(uri))
    }
    val varias = rememberLauncherForActivityResult(
        if (maxItems >= 2) ActivityResultContracts.PickMultipleVisualMedia(maxItems) else ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> entregar(uris) }
    val pedido = remember { PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) }
    return remember(maxItems) {
        {
            runCatching { if (maxItems == 1) uma.launch(pedido) else varias.launch(pedido) }
                .onFailure { AppLogger.w(TAG, "seletor de fotos não abriu: ${it::class.simpleName}") }
            Unit
        }
    }
}

/**
 * Copia o conteúdo do provedor do Photo Picker **byte a byte** para o `cacheDir`. O seletor entrega o
 * arquivo da fototeca (sem transcodificar), menos a localização do EXIF — política do sistema.
 */
internal fun copyOriginalPhoto(context: Context, uri: Uri): OriginalPhoto? = runCatching {
    val resolver = context.contentResolver
    var sugerido: String? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) sugerido = c.getString(0)
    }
    val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
        ?: sugerido?.let(::imageMimeTypeForFileName)
        ?: "image/jpeg"
    val ext = imageExtensionForMimeType(mime)
        ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        ?: sugerido?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    val nome = originalPhotoFileName(sugerido, ext)
    val dir = File(context.cacheDir, ORIGINAL_PHOTO_TEMP_DIRECTORY).apply { mkdirs() }
    val destino = File(dir, newUuidV7() + (ext?.let { ".$it" } ?: ""))
    val entrada = resolver.openInputStream(uri) ?: error("provedor sem conteúdo")
    try {
        entrada.use { input -> destino.outputStream().use { input.copyTo(it, COPY_BUFFER_BYTES) } }
    } catch (e: Exception) {
        destino.delete()
        throw e
    }
    if (destino.length() <= 0L) {
        destino.delete()
        error("foto vazia")
    }
    OriginalPhoto(destino.absolutePath, nome, mime, destino.length())
}.onFailure { AppLogger.w(TAG, "foto escolhida não copiou: ${it::class.simpleName}") }.getOrNull()

internal actual fun deleteOriginalPhotoFile(path: String): Boolean = runCatching {
    val f = File(path)
    !f.exists() || f.delete()
}.getOrDefault(false)
