@file:OptIn(ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.platform.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.newUuidV7
import br.com.codecacto.kmplib.platform.topViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.conformsToType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val TAG = "OriginalPhotoPicker"

/** Identificador do pacote de Live Photo: escolhe-se a foto dentro dele, não o pacote. */
private const val LIVE_PHOTO_TYPE = "com.apple.live-photo"

@Composable
actual fun rememberOriginalPhotoPicker(
    maxItems: Int,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val holder = remember { OriginalPhotoPickerHolder() }
    return remember(holder, maxItems) { { holder.present(maxItems) { callback.value(it) } } }
}

/** Segura o delegate enquanto o seletor está aberto (o UIKit guarda referência FRACA ao delegate). */
private class OriginalPhotoPickerHolder {
    private var delegate: OriginalPhotoPickerDelegate? = null

    fun present(maxItems: Int, done: (OriginalPhotoPickResult) -> Unit) {
        if (delegate != null) return // já aberto
        val config = PHPickerConfiguration(photoLibrary = PHPhotoLibrary.sharedPhotoLibrary())
        config.selectionLimit = maxItems.coerceAtLeast(0).toLong()
        config.filter = PHPickerFilter.imagesFilter
        // "Current" = o arquivo como está na fototeca (HEIC fica HEIC). "Automatic" (o default) deixa o
        // sistema transcodificar para o formato "mais compatível" — JPEG, com perda.
        config.preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
        val picker = PHPickerViewController(configuration = config)
        val d = OriginalPhotoPickerDelegate(done) { delegate = null }
        delegate = d
        picker.delegate = d
        val topo = topViewController()
        if (topo == null) {
            AppLogger.w(TAG, "sem controlador para apresentar o seletor de fotos")
            delegate = null
            return
        }
        topo.presentViewController(picker, animated = true, completion = null)
    }
}

private class OriginalPhotoPickerDelegate(
    private val done: (OriginalPhotoPickResult) -> Unit,
    private val release: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val resultados = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (resultados.isEmpty()) {
            release()
            done(OriginalPhotoPickResult(emptyList()))
            return
        }
        // Na ordem escolhida, mesmo com as cópias terminando fora de ordem.
        val copias = arrayOfNulls<OriginalPhoto>(resultados.size)
        var restantes = resultados.size
        resultados.forEachIndexed { i, resultado ->
            carregarOriginal(resultado) { foto ->
                dispatch_async(dispatch_get_main_queue()) {
                    copias[i] = foto
                    restantes -= 1
                    if (restantes == 0) {
                        val fotos = copias.filterNotNull()
                        release()
                        done(OriginalPhotoPickResult(fotos, failedCount = resultados.size - fotos.size))
                    }
                }
            }
        }
    }
}

/**
 * Pede ao `NSItemProvider` o ARQUIVO no tipo registrado da foto (HEIC, JPEG, PNG, DNG…) e o copia. A URL
 * entregue só vale dentro do bloco — a cópia é feita ali, antes de voltar à fila principal.
 */
private fun carregarOriginal(resultado: PHPickerResult, entregar: (OriginalPhoto?) -> Unit) {
    val provedor = resultado.itemProvider
    val tipo = provedor.registeredTypeIdentifiers.filterIsInstance<String>()
        .filter { it != LIVE_PHOTO_TYPE }
        .firstOrNull { id -> UTType.typeWithIdentifier(id)?.conformsToType(UTTypeImage) == true }
        ?: UTTypeImage.identifier
    val sugerido = provedor.suggestedName
    provedor.loadFileRepresentationForTypeIdentifier(tipo) { url, erro ->
        val foto = if (url == null) {
            AppLogger.w(TAG, "foto escolhida sem arquivo (${erro?.code ?: "sem erro"})")
            null
        } else {
            copiarParaTemporario(url, tipo, sugerido)
        }
        entregar(foto)
    }
}

private fun copiarParaTemporario(origem: NSURL, tipo: String, sugerido: String?): OriginalPhoto? {
    val fm = NSFileManager.defaultManager
    val dir = NSTemporaryDirectory().trimEnd('/') + "/" + ORIGINAL_PHOTO_TEMP_DIRECTORY
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val ut = UTType.typeWithIdentifier(tipo)
    val ext = ut?.preferredFilenameExtension ?: origem.pathExtension?.takeIf { it.isNotEmpty() }
    val mime = ut?.preferredMIMEType
        ?: origem.lastPathComponent?.let(::imageMimeTypeForFileName)
        ?: "image/jpeg"
    val nome = originalPhotoFileName(sugerido ?: origem.lastPathComponent?.substringBeforeLast('.'), ext)
    val caminho = "$dir/${newUuidV7()}" + (ext?.let { ".$it" } ?: "")
    if (!fm.copyItemAtURL(origem, toURL = NSURL.fileURLWithPath(caminho), error = null)) {
        AppLogger.w(TAG, "foto escolhida não copiou")
        return null
    }
    val tamanho = (fm.attributesOfItemAtPath(caminho, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    if (tamanho <= 0L) {
        fm.removeItemAtPath(caminho, error = null)
        return null
    }
    return OriginalPhoto(caminho, nome, mime, tamanho)
}

internal actual fun deleteOriginalPhotoFile(path: String): Boolean {
    val fm = NSFileManager.defaultManager
    return !fm.fileExistsAtPath(path) || fm.removeItemAtPath(path, error = null)
}
