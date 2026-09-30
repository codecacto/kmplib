@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

private const val TAG = "FileSaver"

/** Pasta das cópias temporárias entregues ao seletor, dentro do `NSTemporaryDirectory()`. */
private const val EXPORT_DIRECTORY = "kmplib_export"

/**
 * `UIDocumentPickerViewController(forExportingURLs:asCopy: true)` — o "Salvar em Arquivos" do iOS
 * (iCloud Drive, No meu iPhone, provedores de terceiros). O arquivo passa por uma cópia
 * temporária, apagada quando o seletor fecha — salvo ou cancelado.
 */
@Composable
actual fun rememberFileSaver(onResult: (FileSaveResult) -> Unit): FileSaver {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { IosFileSaver { currentOnResult(it) } }
}

private class IosFileSaver(private val onResult: (FileSaveResult) -> Unit) : FileSaver {

    override fun save(bytes: ByteArray, fileName: String, mimeType: String) {
        if (activeExport != null) {
            onResult(FileSaveResult.Failed("já há um salvamento em andamento"))
            return
        }
        val presenter = topViewController()
        if (presenter == null) {
            onResult(FileSaveResult.Failed("nenhuma janela ativa para abrir o seletor"))
            return
        }
        // Pasta única por salvamento: o nome do arquivo é o que a pessoa vê no seletor, então ele
        // vai sem prefixo — e dois salvamentos com o mesmo nome não colidem.
        val dir = NSTemporaryDirectory().trimEnd('/') + "/" + EXPORT_DIRECTORY + "/" + NSUUID().UUIDString
        val fileManager = NSFileManager.defaultManager
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$dir/${sanitizeSharedFileName(fileName)}"
        if (!bytes.toNSData().writeToFile(path, atomically = true)) {
            fileManager.removeItemAtPath(dir, null)
            onResult(FileSaveResult.Failed("não foi possível preparar o arquivo"))
            return
        }

        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true)
        val session = ExportSession { result ->
            activeExport = null
            if (!fileManager.removeItemAtPath(dir, null)) AppLogger.w(TAG, "cópia temporária não apagada")
            onResult(result)
        }
        activeExport = session
        picker.delegate = session
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

/** Retém o delegado (a propriedade `delegate` do seletor é fraca) até o seletor fechar. */
private var activeExport: ExportSession? = null

private class ExportSession(
    private val finish: (FileSaveResult) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val destination = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        finish(FileSaveResult.Saved(destination?.path))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(FileSaveResult.Cancelled)
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
