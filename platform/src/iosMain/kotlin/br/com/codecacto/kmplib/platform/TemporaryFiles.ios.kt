@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970

private const val TAG = "KmpLibTempFiles"

/**
 * No iOS gravam arquivo: o compartilhamento e, desde a 2.286.0, o vídeo gravado
 * ([VIDEO_CAPTURE_TEMP_DIRECTORY]) e o comprimido ([VIDEO_PREPARED_TEMP_DIRECTORY]), as duas pastas
 * dentro do `NSTemporaryDirectory()`. O `PdfViewer` lê com `PDFDocument(data:)` e a impressão
 * entrega o `NSData` em `printingItem` — nenhum dos dois toca o disco.
 */
internal actual fun clearPlatformTemporaryFiles(olderThanMillis: Long): Int {
    val compartilhados = runCatching { IosShareHandler().clearSharedFiles(olderThanMillis) }
        .onFailure { AppLogger.w(TAG, "falha ao limpar temporários: ${it::class.simpleName}") }
        .getOrDefault(0)
    val gravados = purgeTemporaryDirectory(VIDEO_CAPTURE_TEMP_DIRECTORY, olderThanMillis)
    val preparados = purgeTemporaryDirectory(VIDEO_PREPARED_TEMP_DIRECTORY, olderThanMillis)
    return compartilhados + gravados + preparados
}

/** Apaga de `NSTemporaryDirectory()/[name]` o que tem idade ≥ [olderThanMillis]. Nunca lança. */
private fun purgeTemporaryDirectory(name: String, olderThanMillis: Long): Int = runCatching {
    val dir = NSTemporaryDirectory().trimEnd('/') + "/" + name
    val fileManager = NSFileManager.defaultManager
    val nomes = fileManager.contentsOfDirectoryAtPath(dir, null) ?: return@runCatching 0
    val agora = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    var apagados = 0
    nomes.filterIsInstance<String>().forEach { nome ->
        val path = "$dir/$nome"
        val attrs = fileManager.attributesOfItemAtPath(path, null)
        val modificado = ((attrs?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0) * 1000.0
        if (!shouldPurgeSharedFile(modificado.toLong(), agora, olderThanMillis)) return@forEach
        if (fileManager.removeItemAtPath(path, null)) {
            apagados++
        } else {
            AppLogger.w(TAG, "não foi possível apagar um temporário")
        }
    }
    apagados
}.onFailure { AppLogger.w(TAG, "falha ao limpar $name: ${it::class.simpleName}") }.getOrDefault(0)
