package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

@Composable
actual fun rememberMultiFilePicker(
    mimeTypes: List<String>,
    onResult: (List<PickedFile>) -> Unit,
): () -> Unit {
    val entregar by rememberUpdatedState(onResult)
    // Guardado no `remember`: o `delegate` do UIDocumentPickerViewController é FRACO.
    val delegate = remember { MultiDocumentPickerDelegate { entregar(it) } }

    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem))
        picker.delegate = delegate
        picker.allowsMultipleSelection = true
        val presenter = topViewController()
        if (presenter == null) {
            AppLogger.w(MULTI_TAG, "nenhuma janela ativa para apresentar o seletor de arquivos")
            entregar(emptyList())
        } else {
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class MultiDocumentPickerDelegate(
    private val onResult: (List<PickedFile>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onResult(didPickDocumentsAtURLs.filterIsInstance<NSURL>().map(::pickedFileOf))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(emptyList())
    }
}

private fun pickedFileOf(url: NSURL): PickedFile {
    val nome = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: "arquivo"
    // Atributos de arquivo fora do sandbox exigem o escopo de segurança aberto.
    val escopo = url.startAccessingSecurityScopedResource()
    val tamanho = try {
        declaredFileSize(url)
    } finally {
        if (escopo) url.stopAccessingSecurityScopedResource()
    }
    return PickedFile(name = nome, mimeType = mimeTypeForFileName(nome), sizeBytes = tamanho) { maxBytes ->
        // Leitura coordenada pode baixar da nuvem: fora da thread principal.
        withContext(Dispatchers.Default) { readPickedFile(url, maxBytes) }
    }
}

// Constante de arquivo: campo em companion de subclasse de NSObject não compila no K/N.
private const val MULTI_TAG = "MultiFilePicker"
