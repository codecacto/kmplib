package br.com.codecacto.kmplib.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MULTI_TAG = "MultiFilePicker"

@Composable
actual fun rememberMultiFilePicker(
    mimeTypes: List<String>,
    onResult: (List<PickedFile>) -> Unit,
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    // O seletor volta depois de uma recomposição qualquer: entregar ao callback ATUAL, não ao da
    // primeira composição (que pode ter capturado um ViewModel/estado antigo).
    val entregar by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            entregar(emptyList())
            return@rememberLauncherForActivityResult
        }
        // Nome/tipo/tamanho são consultas ao provedor (pode ser a nuvem): fora da thread principal.
        scope.launch {
            val arquivos = withContext(Dispatchers.IO) { uris.map { pickedFileOf(context, it) } }
            entregar(arquivos)
        }
    }

    return {
        val tipos = mimeTypes
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("*/*") }
            .toTypedArray()
        launcher.launch(tipos)
    }
}

private fun pickedFileOf(context: Context, uri: Uri): PickedFile {
    val nome = runCatching { getFileName(context, uri) }.getOrNull()?.takeIf { it.isNotBlank() } ?: "arquivo"
    val tipo = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    val tamanho = queryDeclaredSize(context, uri)
    return PickedFile(name = nome, mimeType = tipo, sizeBytes = tamanho) { maxBytes ->
        try {
            withContext(Dispatchers.IO) { readFileFromUri(context, uri, maxBytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // `readFileFromUri` já devolve desfecho para toda falha de leitura; isto é a última
            // linha (permissão revogada no meio, provedor morto) — nunca lançar para a tela.
            AppLogger.w(MULTI_TAG, "não foi possível ler o arquivo escolhido: ${e::class.simpleName}")
            FilePickResult.Failed(nome, FilePickFailure.Unreadable)
        }
    }
}
