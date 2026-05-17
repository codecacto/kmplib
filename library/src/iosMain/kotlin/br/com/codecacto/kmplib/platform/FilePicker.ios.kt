package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
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
    onFilePicked: (FileData?) -> Unit
): () -> Unit {
    val delegate = remember { DocumentPickerDelegate(onFilePicked) }

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
    private val onFilePicked: (FileData?) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onFilePicked(url?.let { readFileFromUrl(it) })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onFilePicked(null)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun readFileFromUrl(url: NSURL): FileData? {
        return try {
            val securityScoped = url.startAccessingSecurityScopedResource()
            val data = NSData.dataWithContentsOfURL(url) ?: return null
            val byteArray = ByteArray(data.length.toInt())
            memcpy(byteArray.refTo(0), data.bytes, data.length)
            val fileName = url.lastPathComponent ?: "arquivo"
            val mimeType = guessMimeType(fileName)
            if (securityScoped) url.stopAccessingSecurityScopedResource()
            FileData(
                name = fileName,
                mimeType = mimeType,
                data = byteArray,
                size = byteArray.size.toLong()
            )
        } catch (e: Exception) {
            null
        }
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
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }
}
