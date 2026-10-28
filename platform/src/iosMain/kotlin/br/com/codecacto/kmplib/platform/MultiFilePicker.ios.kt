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
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.UniformTypeIdentifiers.UTTypeText
import platform.darwin.NSObject

@Composable
actual fun rememberMultiFilePicker(
    mimeTypes: List<String>,
    allowMultiple: Boolean,
    onResult: (List<PickedFile>) -> Unit,
): () -> Unit {
    val entregar by rememberUpdatedState(onResult)
    // Guardado no `remember`: o `delegate` do UIDocumentPickerViewController é FRACO.
    val delegate = remember { MultiDocumentPickerDelegate { entregar(it) } }

    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypesForMimeTypes(mimeTypes))
        picker.delegate = delegate
        picker.allowsMultipleSelection = allowMultiple
        val presenter = topViewController()
        if (presenter == null) {
            AppLogger.w(MULTI_TAG, "nenhuma janela ativa para apresentar o seletor de arquivos")
            entregar(emptyList())
        } else {
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

/**
 * MIME → `UTType` (2.332.0). Até a 2.331.0 o iOS abria SEMPRE "qualquer item", e o filtro pedido
 * pelo app só valia no Android. Curinga de grupo (`image/` + `*`) vira o tipo-pai do grupo; MIME
 * exato passa por `UTType.typeWithMIMEType` (a tabela do sistema). Tipo desconhecido é ignorado, e
 * nenhum tipo reconhecido (ou `*` + `/` + `*`) = qualquer item — nunca um seletor que não deixa
 * escolher nada.
 */
internal fun contentTypesForMimeTypes(mimeTypes: List<String>): List<UTType> {
    val normalizados = mimeTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (normalizados.isEmpty() || "*/*" in normalizados) return listOf(UTTypeItem)
    val tipos = normalizados.mapNotNull { mime ->
        when (mime) {
            "image/*" -> UTTypeImage
            "video/*" -> UTTypeMovie
            "audio/*" -> UTTypeAudio
            "text/*" -> UTTypeText
            else -> if (mime.endsWith("/*")) null else UTType.typeWithMIMEType(mime)
        }
    }.distinct()
    if (tipos.isEmpty()) {
        AppLogger.w(MULTI_TAG, "nenhum tipo reconhecido no filtro; abrindo qualquer item")
        return listOf(UTTypeItem)
    }
    return tipos
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
