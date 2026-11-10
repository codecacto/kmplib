package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.storage.writeFileDurably
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desktop (2.353.0): diálogo nativo de "Salvar como" e gravação durável no destino escolhido. */
@Composable
actual fun rememberFileSaver(onResult: (FileSaveResult) -> Unit): FileSaver {
    val entregar by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember {
        object : FileSaver {
            override fun save(bytes: ByteArray, fileName: String, mimeType: String) {
                val destino = DesktopFiles.chooseSaveTarget("Salvar", fileName)
                if (destino == null) {
                    entregar(FileSaveResult.Cancelled)
                    return
                }
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { writeFileDurably(destino, bytes) }
                            .fold({ FileSaveResult.Saved(destino.toString()) }, { FileSaveResult.Failed("não foi possível salvar o arquivo", it) })
                    }
                    entregar(r)
                }
            }
        }
    }
}
