package br.com.codecacto.kmplib.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.InputStream

private const val TAG = "FilePicker"

/** Tamanho do bloco de leitura quando o provedor não informa o tamanho do arquivo. */
private const val READ_CHUNK_BYTES = 64 * 1024

@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    maxBytes: Long,
    onResult: (FilePickResult) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val limite = resolveMaxFileBytes(maxBytes)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onResult(
            if (uri == null) FilePickResult.Cancelled else readFileFromUri(context, uri, limite)
        )
    }

    return {
        val resolvedMimeTypes = mimeTypes
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("*/*") }
            .toTypedArray()
        launcher.launch(resolvedMimeTypes)
    }
}

private fun readFileFromUri(context: Context, uri: Uri, maxBytes: Long): FilePickResult {
    val contentResolver = context.contentResolver
    val fileName = getFileName(context, uri) ?: "arquivo"
    return try {
        // 1ª barreira: o tamanho declarado pelo provedor. Passando do teto, nem abrimos o arquivo.
        val declarado = queryDeclaredSize(context, uri)
        if (exceedsFilePickLimit(declarado, maxBytes)) {
            return FilePickResult.TooLarge(fileName, declarado, maxBytes)
        }

        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val stream = contentResolver.openInputStream(uri)
            ?: return FilePickResult.Failed(fileName, FilePickFailure.Unreadable)

        // 2ª barreira: provedor que não informa tamanho (declarado == -1) é lido COM teto e a leitura
        // é abortada ao estourar — nunca se materializa o arquivo inteiro para descobrir depois.
        val acumulador = stream.use { readBounded(it, maxBytes) }
        if (acumulador.exceeded) {
            return FilePickResult.TooLarge(fileName, declarado, maxBytes)
        }

        val data = acumulador.toByteArray()
        FilePickResult.Picked(
            FileData(
                name = fileName,
                mimeType = mimeType,
                data = data,
                size = data.size.toLong()
            )
        )
    } catch (e: OutOfMemoryError) {
        // Última linha: `catch (Exception)` NÃO pega OutOfMemoryError, e era por isso que um arquivo
        // grande derrubava o app em vez de virar desfecho tratável.
        AppLogger.e(TAG, "memória insuficiente ao ler $fileName", e)
        FilePickResult.Failed(fileName, FilePickFailure.OutOfMemory)
    } catch (e: Exception) {
        AppLogger.w(TAG, "não foi possível ler $fileName: ${e.message}")
        FilePickResult.Failed(fileName, FilePickFailure.Unreadable)
    }
}

private fun readBounded(stream: InputStream, maxBytes: Long): BoundedByteAccumulator {
    val acumulador = BoundedByteAccumulator(maxBytes)
    val chunk = ByteArray(READ_CHUNK_BYTES)
    while (true) {
        val lidos = stream.read(chunk)
        if (lidos < 0) break
        if (!acumulador.append(chunk, lidos)) break
    }
    return acumulador
}

/** `-1` quando o provedor não informa o tamanho (acontece, e é o caso que exige leitura com teto). */
private fun queryDeclaredSize(context: Context, uri: Uri): Long {
    if (uri.scheme != "content") return -1L
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use -1L
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index == -1 || cursor.isNull(index)) -1L else cursor.getLong(index)
            } ?: -1L
    } catch (e: Exception) {
        AppLogger.w(TAG, "provedor não informou o tamanho: ${e.message}")
        -1L
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) return cursor.getString(index)
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}
