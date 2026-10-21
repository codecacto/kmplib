package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.util.AppLogger
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

@Composable
actual fun rememberFilePicker(
    mimeTypes: List<String>,
    maxBytes: Long,
    onResult: (FilePickResult) -> Unit
): () -> Unit {
    val limite = resolveMaxFileBytes(maxBytes)
    // O delegate vive entre recomposições (o UIKit o guarda FRACO: sem o `remember` ele morre antes
    // de o usuário escolher), mas entrega ao callback ATUAL — não ao da primeira composição.
    val entregar by rememberUpdatedState(onResult)
    val delegate = remember(limite) { DocumentPickerDelegate(limite) { entregar(it) } }

    return {
        val documentPicker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeItem)
        )
        documentPicker.delegate = delegate
        documentPicker.allowsMultipleSelection = false

        // Do controlador no TOPO (2.311.0): do `rootViewController`, com uma sheet aberta por cima,
        // o UIKit só logava "already presenting" e o toque morria.
        val presenter = topViewController()
        if (presenter == null) {
            AppLogger.w(TAG, "nenhuma janela ativa para apresentar o seletor de arquivo")
            entregar(FilePickResult.Failed(null, FilePickFailure.Unreadable))
        } else {
            presenter.presentViewController(documentPicker, animated = true, completion = null)
        }
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
            if (url == null) FilePickResult.Cancelled else readPickedFile(url, maxBytes)
        )
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(FilePickResult.Cancelled)
    }
}

// Constante de ARQUIVO, e não `companion object` dentro da classe: o Kotlin/Native recusa campos em
// companion de subclasse de tipo Obj-C ("Fields are not supported for Companion of subclass of ObjC
// type"), e `DocumentPickerDelegate` estende `NSObject`. O padrão de pôr o TAG num companion — que é
// o certo no resto da lib — não vale para delegates do UIKit.
private const val TAG = "FilePicker"
