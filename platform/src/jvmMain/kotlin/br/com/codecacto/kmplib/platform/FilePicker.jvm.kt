package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desktop (2.353.0): seletor nativo do SO (`FileDialog`); a leitura roda fora da thread de UI. */
@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    maxBytes: Long,
    onResult: (FilePickResult) -> Unit,
): () -> Unit {
    val limite = resolveMaxFileBytes(maxBytes)
    val entregar by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return {
        val escolhido = DesktopFiles.chooseFiles("Selecionar arquivo", mimeTypes, multiple = false).firstOrNull()
        if (escolhido == null) {
            entregar(FilePickResult.Cancelled)
        } else {
            scope.launch {
                val r = withContext(Dispatchers.IO) { DesktopFiles.readPicked(escolhido, limite) }
                entregar(r)
            }
        }
    }
}

/** Desktop (2.353.0): seletor nativo com seleção múltipla; cada [PickedFile] é lido sob demanda. */
@Composable
actual fun rememberMultiFilePicker(
    mimeTypes: List<String>,
    allowMultiple: Boolean,
    onResult: (List<PickedFile>) -> Unit,
): () -> Unit {
    val entregar by rememberUpdatedState(onResult)
    return {
        val arquivos = DesktopFiles.chooseFiles("Selecionar arquivos", mimeTypes, multiple = allowMultiple)
        entregar(
            arquivos.map { path ->
                PickedFile(
                    name = path.fileName.toString(),
                    mimeType = DesktopFiles.mimeTypeOf(path),
                    sizeBytes = runCatching { java.nio.file.Files.size(path) }.getOrDefault(-1L),
                    reader = { max -> withContext(Dispatchers.IO) { DesktopFiles.readPicked(path, max) } },
                    stream = PickedFileStream { campo, arquivo, mime ->
                        br.com.codecacto.kmplib.sync.rest.StreamingMultipartPart.fromLocalFile(campo, path.toString(), arquivo, mime)
                    },
                )
            },
        )
    }
}
