@file:OptIn(br.com.codecacto.kmplib.platform.KmpLibPlatformInternalApi::class)

package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.kmplibTopViewController

/**
 * Só a câmera (2.332.0): permissão conferida, `UIImagePickerController` e a MESMA codificação do
 * seletor (ver [abrirCameraDeFoto]).
 */
@Composable
internal actual fun rememberCameraPhotoLauncher(
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher {
    val entregar by rememberUpdatedState(onImagePicked)
    val falhar by rememberUpdatedState(onError)
    return remember(maxDimension, jpegQuality) {
        val codificacao = Codificacao(maxDimension, jpegQuality)
        ImagePickerLauncher {
            val raiz = kmplibTopViewController()
            if (raiz == null) {
                AppLogger.w("KmpLibImagePicker", "Sem controlador para apresentar a câmera.")
                falhar(ImagePickerError.CAMERA_UNAVAILABLE)
            } else {
                abrirCameraDeFoto(raiz, codificacao, { entregar(it) }, { falhar(it) })
            }
        }
    }
}
