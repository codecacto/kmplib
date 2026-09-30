package br.com.codecacto.kmplib.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FileSaver"

/**
 * Storage Access Framework: `ACTION_CREATE_DOCUMENT` com `CATEGORY_OPENABLE`, o tipo MIME e o nome
 * sugerido em `EXTRA_TITLE`. É o caminho oficial para "salvar como" sem permissão de armazenamento
 * (a gravação direta em Downloads pelo `MediaStore` só existe a partir do Android 10 e não deixa a
 * pessoa escolher onde).
 */
@Composable
actual fun rememberFileSaver(onResult: (FileSaveResult) -> Unit): FileSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val pending = remember { PendingSave() }

    val launcher = rememberLauncherForActivityResult(CreateDocumentForSave()) { uri ->
        val bytes = pending.take()
        when {
            uri == null -> currentOnResult(FileSaveResult.Cancelled)
            bytes == null -> {
                // Processo recriado com o seletor aberto: o conteúdo não sobreviveu. Tira o
                // documento vazio que o seletor já criou, para não deixar um arquivo de 0 byte.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                currentOnResult(FileSaveResult.Failed("o conteúdo a salvar se perdeu (o app foi recriado)"))
            }
            else -> scope.launch {
                val result = withContext(Dispatchers.IO) { writeTo(context, uri, bytes) }
                currentOnResult(result)
            }
        }
    }

    return remember(launcher) {
        object : FileSaver {
            override fun save(bytes: ByteArray, fileName: String, mimeType: String) {
                pending.put(bytes)
                try {
                    launcher.launch(SaveRequest(sanitizeSharedFileName(fileName), mimeType))
                } catch (e: ActivityNotFoundException) {
                    pending.take()
                    AppLogger.e(TAG, "nenhum seletor de documentos no aparelho", e)
                    currentOnResult(FileSaveResult.Failed("nenhum seletor de documentos disponível", e))
                }
            }
        }
    }
}

private fun writeTo(context: Context, uri: Uri, bytes: ByteArray): FileSaveResult = try {
    // "w" (e não "wt"): alguns provedores de nuvem não aceitam o modo com truncamento, e o
    // documento acabou de ser criado vazio pelo seletor.
    val stream = context.contentResolver.openOutputStream(uri, "w")
        ?: throw IllegalStateException("o provedor não abriu o documento para escrita")
    stream.use { it.write(bytes) }
    FileSaveResult.Saved(uri.toString())
} catch (e: Exception) {
    AppLogger.e(TAG, "falha ao gravar o arquivo escolhido", e)
    FileSaveResult.Failed("falha ao gravar o arquivo", e)
}

private class SaveRequest(val fileName: String, val mimeType: String)

private class CreateDocumentForSave : ActivityResultContract<SaveRequest, Uri?>() {
    override fun createIntent(context: Context, input: SaveRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Conteúdo aguardando o seletor. Uma gravação por vez: a nova substitui a pendente. */
private class PendingSave {
    private var bytes: ByteArray? = null

    fun put(value: ByteArray) {
        bytes = value
    }

    fun take(): ByteArray? = bytes.also { bytes = null }
}
