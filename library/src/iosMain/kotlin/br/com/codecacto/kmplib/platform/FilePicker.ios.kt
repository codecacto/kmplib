package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    maxBytes: Long,
    onResult: (FilePickResult) -> Unit
): () -> Unit {
    val limite = resolveMaxFileBytes(maxBytes)
    val delegate = remember { DocumentPickerDelegate(limite, onResult) }

    return {
        val documentPicker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeItem)
        )
        documentPicker.delegate = delegate
        documentPicker.allowsMultipleSelection = false

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(documentPicker, animated = true, completion = null)
    }
}

private class DocumentPickerDelegate(
    private val maxBytes: Long,
    private val onResult: (FilePickResult) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onResult(
            if (url == null) FilePickResult.Cancelled else readFileFromUrl(url)
        )
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(FilePickResult.Cancelled)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readFileFromUrl(url: NSURL): FilePickResult {
        val fileName = url.lastPathComponent ?: "arquivo"
        var securityScoped = false
        return try {
            securityScoped = url.startAccessingSecurityScopedResource()

            // Teto ANTES de materializar: no iOS o tamanho de um arquivo local é sempre conhecido,
            // então nem se chega a criar o NSData de um arquivo grande demais.
            val declarado = declaredSize(url)
            if (exceedsFilePickLimit(declarado, maxBytes)) {
                return FilePickResult.TooLarge(fileName, declarado, maxBytes)
            }

            val data = NSData.dataWithContentsOfURL(url)
                ?: return FilePickResult.Failed(fileName, FilePickFailure.Unreadable)

            // Segunda barreira: provedor que não informou tamanho (declarado == -1).
            val tamanho = data.length.toLong()
            if (exceedsFilePickLimit(tamanho, maxBytes)) {
                return FilePickResult.TooLarge(fileName, tamanho, maxBytes)
            }

            val byteArray = ByteArray(tamanho.toInt())
            if (tamanho > 0) memcpy(byteArray.refTo(0), data.bytes, data.length)
            FilePickResult.Picked(
                FileData(
                    name = fileName,
                    mimeType = guessMimeType(fileName),
                    data = byteArray,
                    size = byteArray.size.toLong()
                )
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "não foi possível ler $fileName: ${e.message}")
            FilePickResult.Failed(fileName, FilePickFailure.Unreadable)
        } finally {
            if (securityScoped) url.stopAccessingSecurityScopedResource()
        }
    }

    /** `-1` quando o sistema não informa o tamanho. */
    @OptIn(ExperimentalForeignApi::class)
    private fun declaredSize(url: NSURL): Long {
        val path = url.path ?: return -1L
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return -1L
        return (attrs[NSFileSize] as? NSNumber)?.longValue ?: -1L
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        const val TAG = "FilePicker"
    }
}
