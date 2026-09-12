package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
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
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.drawInRect
import platform.darwin.NSObject
import platform.posix.memcpy

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
): ImagePickerLauncher = remember(source, onImagePicked, onError) {
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
                abrirGaleriaDeFotos(raiz, onImagePicked, onError)

            else -> escolherOrigemDaFoto(raiz, onImagePicked, onError)
        }
    }
}

private fun escolherOrigemDaFoto(
    raiz: UIViewController,
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
            abrirCameraDeFoto(raiz, onImagePicked, onError)
        },
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Escolher da galeria", style = UIAlertActionStyleDefault) {
            abrirGaleriaDeFotos(raiz, onImagePicked, onError)
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
                    AppLogger.w(TAG, "Foto da galeria não pôde ser lida: ${error?.localizedDescription}")
                    onError(ImagePickerError.IMAGE_UNREADABLE)
                    return@loadDataRepresentationForTypeIdentifier
                }
                val imagem = UIImage(data = data)
                    ?: return@loadDataRepresentationForTypeIdentifier onError(
                        ImagePickerError.IMAGE_UNREADABLE,
                    )
                entregarFoto(imagem, onImagePicked, onError)
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
            entregarFoto(imagem, onImagePicked, onError)
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

/**
 * Normaliza, reduz, codifica em JPEG e **mede o que saiu**.
 *
 * ⚠️ A `UIImage` de uma foto de iPhone quase nunca está em pé no arquivo: os pixels vêm deitados e a
 * orientação vem à parte, em `imageOrientation` (o EXIF). Duas consequências que esta função existe
 * para fechar:
 *
 * 1. `image.size` já é a medida **exibida** (o UIKit aplica a orientação), mas
 *    `UIImageJPEGRepresentation` grava os **pixels crus mais a tag** — então a medida devolvida
 *    poderia não bater com a que o backend lê dos bytes.
 * 2. Por isso a imagem é **sempre redesenhada** antes de codificar: `drawInRect` aplica a
 *    orientação, e o que sai não tem mais tag de rotação. Depois disso, medida devolvida e medida
 *    contida nos bytes são a mesma coisa — para nós, para o backend e para o navegador.
 */
@OptIn(ExperimentalForeignApi::class)
private fun entregarFoto(
    imagem: UIImage,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
) {
    // `size` está em PONTOS; `scale` os converte em pixels. Numa foto vinda de arquivo a escala é
    // 1.0, mas a de câmera nem sempre — e a medida que o app publica é em pixels.
    val (larguraPt, alturaPt) = imagem.size.useContents { width to height }
    val escala = imagem.scale
    val larguraPx = (larguraPt * escala).toInt()
    val alturaPx = (alturaPt * escala).toInt()
    if (larguraPx <= 0 || alturaPx <= 0) {
        AppLogger.w(TAG, "Foto sem medida utilizável.")
        onError(ImagePickerError.IMAGE_UNREADABLE)
        return
    }

    val (largura, altura) = scaledImageSize(larguraPx, alturaPx, PICKED_IMAGE_MAX_DIMENSION)

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(largura.toDouble(), altura.toDouble()), false, 1.0)
    imagem.drawInRect(CGRectMake(0.0, 0.0, largura.toDouble(), altura.toDouble()))
    val normalizada = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    if (normalizada == null) {
        AppLogger.w(TAG, "Não foi possível redesenhar a foto para normalizar a orientação.")
        onError(ImagePickerError.IMAGE_UNREADABLE)
        return
    }

    val jpeg = UIImageJPEGRepresentation(normalizada, 0.85)
    if (jpeg == null) {
        AppLogger.w(TAG, "Não foi possível codificar a foto em JPEG.")
        onError(ImagePickerError.IMAGE_UNREADABLE)
        return
    }

    onImagePicked(
        PickedImage(bytes = jpeg.toByteArray(), widthPx = largura, heightPx = altura),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}

private const val TAG = "KmpLibImagePicker"
