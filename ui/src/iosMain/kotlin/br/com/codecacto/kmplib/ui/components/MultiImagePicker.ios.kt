package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class MultiImagePickerLauncher(
    private val onLaunch: () -> Unit,
) {
    actual fun launch() {
        onLaunch()
    }
}

/**
 * O delegate em uso, preso a uma referência **forte** de módulo — pelo mesmo motivo de
 * `delegateDeFotoEmUso` em `ImagePicker.ios.kt`.
 *
 * ⚠️ `PHPickerViewController.delegate` é **weak**. Um delegate criado dentro do bloco de lançamento
 * fica sem dono assim que o bloco retorna: o ARC o libera, o seletor continua aberto, a pessoa
 * escolhe as fotos e **nada acontece** — sem erro, sem log. Do lado de quem usa o app, o botão de
 * adicionar foto simplesmente parou de funcionar.
 */
private var delegateDeSelecaoMultipla: NSObject? = null

@Composable
actual fun rememberMultiImagePickerLauncher(
    selectionLimit: Int,
    onImagesPicked: (List<PickedImage>) -> Unit,
    onError: (ImagePickerError) -> Unit,
): MultiImagePickerLauncher = remember(selectionLimit, onImagesPicked, onError) {
    MultiImagePickerLauncher {
        val raiz = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (raiz == null) {
            AppLogger.w(TAG, "Sem rootViewController: seletor múltiplo de fotos não abriu.")
            onError(ImagePickerError.IMAGE_UNREADABLE)
            return@MultiImagePickerLauncher
        }
        abrirGaleriaMultipla(raiz, selectionLimit.coerceAtLeast(2), onImagesPicked, onError)
    }
}

/**
 * `PHPickerViewController` com `selectionLimit > 1` — **sem pedir permissão**: ele roda fora do
 * processo do app, e por isso não exige `NSPhotoLibraryUsageDescription`.
 *
 * ## Por que há um contador, e por que ele vive na fila principal
 * Cada item é carregado por uma chamada assíncrona própria (`loadDataRepresentation`), e elas
 * terminam **fora de ordem e em filas quaisquer**. O resultado de cada uma é gravado na **posição**
 * dela num vetor pré-dimensionado — é assim que a ordem em que a pessoa escolheu sobrevive —, e só
 * quando o contador fecha é que a lista sai. Toda essa escrita compartilhada é despachada para a
 * `main queue`: serializa o acesso sem lock e entrega o retorno na thread em que a tela vive.
 */
private fun abrirGaleriaMultipla(
    raiz: platform.UIKit.UIViewController,
    teto: Int,
    onImagesPicked: (List<PickedImage>) -> Unit,
    onError: (ImagePickerError) -> Unit,
) {
    val configuracao = PHPickerConfiguration().apply {
        selectionLimit = teto.toLong()
        filter = PHPickerFilter.imagesFilter
    }

    val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)

            val escolhidos = didFinishPicking.filterIsInstance<PHPickerResult>()
            // Lista vazia = fechou sem escolher. Desistir NÃO é erro.
            if (escolhidos.isEmpty()) return

            val total = escolhidos.size
            val emOrdem = arrayOfNulls<PickedImage>(total)
            var concluidas = 0
            var houveFalha = false

            fun registrar(indice: Int, foto: PickedImage?) {
                dispatch_async(dispatch_get_main_queue()) {
                    emOrdem[indice] = foto
                    if (foto == null) houveFalha = true
                    concluidas += 1
                    if (concluidas < total) return@dispatch_async

                    val prontas = emOrdem.filterNotNull()
                    // O erro vem UMA vez, e depois do que deu certo: vinte avisos iguais
                    // empilhados é o mesmo que nenhum.
                    if (prontas.isNotEmpty()) onImagesPicked(prontas)
                    if (houveFalha) onError(ImagePickerError.IMAGE_UNREADABLE)
                }
            }

            escolhidos.forEachIndexed { indice, resultado ->
                val provedor = resultado.itemProvider
                if (provedor == null || !provedor.hasItemConformingToTypeIdentifier(TIPO_IMAGEM)) {
                    AppLogger.w(TAG, "Item $indice da seleção não é imagem.")
                    registrar(indice, null)
                    return@forEachIndexed
                }
                provedor.loadDataRepresentationForTypeIdentifier(TIPO_IMAGEM) { data, erro ->
                    // Uma imagem ilegível no meio da seleção não pode custar as outras: ela vira
                    // `null` na posição dela e o conjunto segue.
                    val foto = converter(data, erro?.localizedDescription)
                    registrar(indice, foto)
                }
            }
        }
    }

    delegateDeSelecaoMultipla = delegate
    val seletor = PHPickerViewController(configuration = configuracao)
    seletor.delegate = delegate
    raiz.presentViewController(seletor, animated = true, completion = null)
}

/** Bytes crus → [PickedImage], reusando a normalização de orientação de `ImagePicker.ios.kt`. */
@OptIn(ExperimentalForeignApi::class)
private fun converter(data: NSData?, erro: String?): PickedImage? {
    if (data == null) {
        AppLogger.w(TAG, "Foto da seleção múltipla não pôde ser lida: $erro")
        return null
    }
    val imagem = UIImage(data = data)
    if (imagem == null) {
        AppLogger.w(TAG, "Foto da seleção múltipla não pôde ser decodificada.")
        return null
    }
    return imagem.paraPickedImage()
}

private const val TIPO_IMAGEM = "public.image"
private const val TAG = "KmpLibMultiImagePicker"
