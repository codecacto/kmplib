package br.com.codecacto.kmplib.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import br.com.codecacto.kmplib.platform.FilePickResult
import br.com.codecacto.kmplib.platform.FileSaveResult
import br.com.codecacto.kmplib.platform.PickedFile
import br.com.codecacto.kmplib.platform.getShareHandler
import br.com.codecacto.kmplib.platform.rememberFileSaver
import br.com.codecacto.kmplib.platform.rememberMultiFilePicker

/** As três ações de arquivo da cópia, ligadas aos seletores oficiais de cada plataforma. */
class BackupFileActions internal constructor(
    private val saveFn: (BackupFile) -> Unit,
    private val sendFn: (BackupFile) -> Unit,
    private val pickFn: () -> Unit,
) {
    /** "Salvar arquivo": seletor de destino do sistema (SAF `ACTION_CREATE_DOCUMENT` / `UIDocumentPicker` exportando). */
    fun save(file: BackupFile) = saveFn(file)

    /** "Enviar para mim": folha do sistema (e-mail, Drive, WhatsApp). */
    fun send(file: BackupFile) = sendFn(file)

    /** "Restaurar cópia": seletor de arquivo do sistema; o resultado chega em `onPicked`. */
    fun pick() = pickFn()
}

/**
 * Liga salvar / enviar / escolher arquivo da cópia (2.355.0) — sem tela própria: as folhas e o diálogo
 * são do kit do app (`FamilyBackupReadySheet`, `FamilyRestoreConfirmDialog`).
 *
 * @param onSaved resultado do "Salvar" (salvo, cancelado ou falhou).
 * @param onPicked os bytes do arquivo escolhido (até [maxBytes]) — passe a `AppBackupManager.inspect`.
 * @param onPickFailed arquivo grande demais ou ilegível.
 */
@Composable
fun rememberBackupFileActions(
    onSaved: (FileSaveResult) -> Unit,
    onPicked: (ByteArray) -> Unit,
    onPickFailed: () -> Unit = {},
    maxBytes: Long = DEFAULT_MAX_BACKUP_BYTES,
): BackupFileActions {
    val saved by rememberUpdatedState(onSaved)
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onPickFailed)
    val saver = rememberFileSaver { saved(it) }
    val scope = rememberCoroutineScope()
    val picker = rememberMultiFilePicker(mimeTypes = listOf("*/*"), allowMultiple = false) { files: List<PickedFile> ->
        val file = files.firstOrNull() ?: return@rememberMultiFilePicker
        scope.launch {
            when (val r = file.read(maxBytes)) {
                is FilePickResult.Picked -> picked(r.file.data)
                else -> failed()
            }
        }
    }
    return remember(saver, picker) {
        BackupFileActions(
            saveFn = { f -> saver.save(f.bytes, f.fileName, BackupArchive.MIME_TYPE) },
            sendFn = { f -> runCatching { getShareHandler().shareFile(f.bytes, f.fileName, BackupArchive.MIME_TYPE) } },
            pickFn = { picker() },
        )
    }
}

/** Teto padrão do arquivo de cópia lido (50 MB — dados + fotos de comprovante). */
const val DEFAULT_MAX_BACKUP_BYTES: Long = 50L * 1024 * 1024
