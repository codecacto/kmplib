package br.com.codecacto.kmplib.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    onFilePicked: (FileData?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        onFilePicked(uri?.let { readFileFromUri(context, it) })
    }

    return {
        val resolvedMimeTypes = mimeTypes
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("*/*") }
            .toTypedArray()
        launcher.launch(resolvedMimeTypes)
    }
}

private fun readFileFromUri(context: Context, uri: Uri): FileData? {
    return try {
        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri) ?: "arquivo"
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val data = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        FileData(
            name = fileName,
            mimeType = mimeType,
            data = data,
            size = data.size.toLong()
        )
    } catch (e: Exception) {
        null
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
