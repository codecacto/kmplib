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

@Composable
actual fun rememberOriginalPhotoFilePicker(
    maxItems: Int,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    val teto = rememberUpdatedState(maxItems)

    fun entregar(uris: List<Uri>) {
        // O SAF não limita a quantidade: fica com as primeiras, o resto nem é copiado.
        val escolhidas = if (teto.value > 0) uris.take(teto.value) else uris
        if (escolhidas.isEmpty()) {
            callback.value(OriginalPhotoPickResult(emptyList()))
            return
        }
        scope.launch {
            val copias = withContext(Dispatchers.IO) {
                // Arquivo do gerenciador: tipo declarado que não é imagem = não é foto (sem cair em JPEG).
                escolhidas.map { copyOriginalPhoto(context, it, fallbackMimeType = null) }
            }
            val fotos = copias.filterNotNull()
            callback.value(OriginalPhotoPickResult(fotos, failedCount = copias.size - fotos.size))
        }
    }

    // Storage Access Framework (ACTION_OPEN_DOCUMENT): Arquivos, Drive, cartão, pendrive — sem permissão.
    val uma = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        entregar(listOfNotNull(uri))
    }
    val varias = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        entregar(uris)
    }
    return remember(maxItems) {
        {
            runCatching { if (maxItems == 1) uma.launch(IMAGE_DOCUMENT_TYPES) else varias.launch(IMAGE_DOCUMENT_TYPES) }
                .onFailure { AppLogger.w(TAG, "seletor de arquivos não abriu: ${it::class.simpleName}") }
            Unit
        }
    }
}

private val IMAGE_DOCUMENT_TYPES = arrayOf("image/*")

/**
 * Copia o conteúdo do provedor (Photo Picker ou documento do SAF) **byte a byte**, por stream, para o
 * `cacheDir`. O Photo Picker entrega o arquivo da fototeca (sem transcodificar), menos a localização do
 * EXIF — política do sistema; o SAF entrega o arquivo inteiro.
 */
internal fun copyOriginalPhoto(
    context: Context,
    uri: Uri,
    fallbackMimeType: String? = "image/jpeg",
): OriginalPhoto? = runCatching {
    val resolver = context.contentResolver
    var sugerido: String? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) sugerido = c.getString(0)
    }
    // Do Photo Picker vem sempre imagem (o fallback é JPEG); do gerenciador de arquivos, sem fallback.
    val mime = resolveOriginalPhotoMimeType(resolver.getType(uri), sugerido, fallbackMimeType)
        ?: error("arquivo escolhido não é imagem")
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
