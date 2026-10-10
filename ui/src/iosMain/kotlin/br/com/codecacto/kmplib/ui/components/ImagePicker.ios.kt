@file:OptIn(br.com.codecacto.kmplib.ui.KmpLibUiInternalApi::class)

package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.util.redactMediaUrlsIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

actual class ImagePickerLauncher(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

/**
 * O delegate em uso, preso a uma referência **forte** de módulo.
 *
 * ⚠️ **Não simplifique isto de volta para uma variável local.** `PHPickerViewController.delegate` e
 * `UIImagePickerController.delegate` são **weak** — é assim em todo UIKit, para não fechar ciclo de
 * retenção entre o controlador e quem o apresenta. Um delegate criado dentro do bloco de lançamento
 * fica sem dono assim que o bloco retorna: o ARC o libera, o seletor continua aberto, a pessoa
 * escolhe a foto e **nada acontece** — sem erro, sem log, sem travar. Do lado de quem usa o app, o
 * botão de enviar foto simplesmente parou de funcionar.
 *
 * Era exatamente este o defeito corrigido no `VideoPicker.ios.kt` (2.197.0); aqui ele valia para
 * `cameraDelegate` e `galleryDelegate`. Só um seletor fica aberto por vez, então uma referência
 * basta — e ela é substituída, não acumulada.
 */
private var delegateDeFotoEmUso: NSObject? = null

@Composable
actual fun rememberImagePickerLauncher(
    source: ImagePickerSource,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher = remember(source, onImagePicked, onError, maxDimension, jpegQuality) {
    val codificacao = Codificacao(maxDimension, jpegQuality)
    ImagePickerLauncher {
        val raiz = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (raiz == null) {
            // Antes isto era um `return@ImagePickerLauncher` mudo: o toque não abria nada e a tela
            // não dizia nada.
            AppLogger.w(TAG, "Sem rootViewController: seletor de foto não abriu.")
            onError(ImagePickerError.IMAGE_UNREADABLE)
            return@ImagePickerLauncher
        }
        val temCamera = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )
        when {
            source == ImagePickerSource.GALLERY_ONLY || !temCamera ->
                abrirGaleriaDeFotos(raiz, codificacao, onImagePicked, onError)

            else -> escolherOrigemDaFoto(raiz, codificacao, onImagePicked, onError)
        }
    }
}

/** Teto e qualidade pedidos pelo app (2.278.0), levados até a codificação. */
private class Codificacao(val maxDimension: Int, val jpegQuality: Int)

private fun escolherOrigemDaFoto(
    raiz: UIViewController,
    codificacao: Codificacao,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
) {
    val folha = UIAlertController.alertControllerWithTitle(
        title = "Adicionar foto",
        message = null,
        preferredStyle = UIAlertControllerStyleActionSheet,
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Tirar foto", style = UIAlertActionStyleDefault) {
            abrirCameraDeFoto(raiz, codificacao, onImagePicked, onError)
        },
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Escolher da galeria", style = UIAlertActionStyleDefault) {
            abrirGaleriaDeFotos(raiz, codificacao, onImagePicked, onError)
        },
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Cancelar", style = UIAlertActionStyleCancel, handler = null),
    )
    raiz.presentViewController(folha, animated = true, completion = null)
}

/**
 * O seletor de fotos do sistema (`PHPickerViewController`) — **sem pedir permissão**: ele roda fora
 * do processo do app, e por isso não exige `NSPhotoLibraryUsageDescription`.
 */
private fun abrirGaleriaDeFotos(
    raiz: UIViewController,
    codificacao: Codificacao,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
) {
    val configuracao = PHPickerConfiguration().apply {
        selectionLimit = 1
        filter = PHPickerFilter.imagesFilter
    }
    val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)

            // Lista vazia = fechou sem escolher. Desistir NAO e erro.
            val escolhido = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
            val provedor = escolhido.itemProvider
                ?: return onError(ImagePickerError.IMAGE_UNREADABLE)

            if (!provedor.hasItemConformingToTypeIdentifier("public.image")) {
                // Sem este `else` o item não-imagem terminava em silêncio absoluto.
                AppLogger.w(TAG, "Item escolhido não é imagem.")
                onError(ImagePickerError.IMAGE_UNREADABLE)
                return
            }

            provedor.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                if (error != null || data == null) {
                    AppLogger.w(TAG, "Foto da galeria não pôde ser lida: ${redactMediaUrlsIn(error?.localizedDescription)}")
                    naFilaPrincipal { onError(ImagePickerError.IMAGE_UNREADABLE) }
                    return@loadDataRepresentationForTypeIdentifier
                }
                // Já numa fila de fundo (a do `NSItemProvider`): a redução pelo ImageIO roda aqui,
                // e só o retorno vai para a fila principal, onde a tela vive.
                val foto = data.toPickedImage(codificacao.maxDimension, codificacao.jpegQuality)
                naFilaPrincipal {
                    if (foto == null) onError(ImagePickerError.IMAGE_UNREADABLE) else onImagePicked(foto)
                }
            }
        }
    }
    delegateDeFotoEmUso = delegate
    val seletor = PHPickerViewController(configuration = configuracao)
    seletor.delegate = delegate
    raiz.presentViewController(seletor, animated = true, completion = null)
}

private fun abrirCameraDeFoto(
    raiz: UIViewController,
    codificacao: Codificacao,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
) {
    val delegate = object :
        NSObject(),
        UIImagePickerControllerDelegateProtocol,
        UINavigationControllerDelegateProtocol {
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>,
        ) {
            picker.dismissViewControllerAnimated(true, null)
            val imagem = (
                didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                    ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]
                ) as? UIImage
                ?: return onError(ImagePickerError.IMAGE_UNREADABLE)
            // Redesenhar até 4096 px na fila principal congelaria a tela: fila de fundo, e o
            // retorno de volta à principal.
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) {
                val foto = imagem.toPickedImage(codificacao.maxDimension, codificacao.jpegQuality)
                naFilaPrincipal {
                    if (foto == null) onError(ImagePickerError.IMAGE_UNREADABLE) else onImagePicked(foto)
                }
            }
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, null)
        }
    }
    delegateDeFotoEmUso = delegate
    val camera = UIImagePickerController().apply {
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        this.delegate = delegate
    }
    raiz.presentViewController(camera, animated = true, completion = null)
}

private fun naFilaPrincipal(bloco: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { bloco() }
}

private const val TAG = "KmpLibImagePicker"
