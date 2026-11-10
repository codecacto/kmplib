package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.platform.KmpLibPlatformInternalApi
import br.com.codecacto.kmplib.platform.desktopChooseFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files

/**
 * Desktop (2.353.0): o PC não tem câmera do sistema nem galeria — "escolher foto" abre o seletor
 * NATIVO de arquivo filtrado para imagem, e a foto sai no mesmo [PickedImage] (JPEG em pé, sem EXIF,
 * reduzido). Com [ImagePickerSource.GALLERY_AND_CAMERA] o seletor abre direto (não há folha com câmera).
 */
actual class ImagePickerLauncher(private val onLaunch: () -> Unit) {
    actual fun launch() = onLaunch()
}

@OptIn(KmpLibPlatformInternalApi::class)
@Composable
actual fun rememberImagePickerLauncher(
    source: ImagePickerSource,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher {
    val entregar by rememberUpdatedState(onImagePicked)
    val falhar by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    return remember(maxDimension, jpegQuality) {
        ImagePickerLauncher {
            val path = desktopChooseFiles("Selecionar foto", listOf("image/*"), multiple = false).firstOrNull()
                ?: return@ImagePickerLauncher
            scope.launch {
                val foto = withContext(Dispatchers.IO) {
                    runCatching { encodePickedImageDesktop(Files.readAllBytes(path), maxDimension, jpegQuality) }.getOrNull()
                }
                if (foto != null) entregar(foto) else falhar(ImagePickerError.IMAGE_UNREADABLE)
            }
        }
    }
}

actual class MultiImagePickerLauncher(private val onLaunch: () -> Unit) {
    actual fun launch() = onLaunch()
}

@OptIn(KmpLibPlatformInternalApi::class)
@Composable
actual fun rememberMultiImagePickerLauncher(
    selectionLimit: Int,
    onImagesPicked: (List<PickedImage>) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): MultiImagePickerLauncher {
    val entregar by rememberUpdatedState(onImagesPicked)
    val falhar by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    return remember(selectionLimit, maxDimension, jpegQuality) {
        MultiImagePickerLauncher {
            val paths = desktopChooseFiles("Selecionar fotos", listOf("image/*"), multiple = true)
                .let { if (selectionLimit > 0) it.take(selectionLimit) else it }
            if (paths.isEmpty()) return@MultiImagePickerLauncher
            scope.launch {
                val fotos = withContext(Dispatchers.IO) {
                    paths.map { runCatching { encodePickedImageDesktop(Files.readAllBytes(it), maxDimension, jpegQuality) }.getOrNull() }
                }
                val boas = fotos.filterNotNull()
                if (boas.size < fotos.size) falhar(ImagePickerError.IMAGE_UNREADABLE)
                if (boas.isNotEmpty()) entregar(boas)
            }
        }
    }
}

/** Desktop: não há câmera do sistema. */
@Composable
internal actual fun rememberCameraPhotoLauncher(
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher {
    val falhar by rememberUpdatedState(onError)
    return remember { ImagePickerLauncher { falhar(ImagePickerError.CAMERA_UNAVAILABLE) } }
}

internal actual fun encodePickedImageFromBytes(bytes: ByteArray, maxDimension: Int, jpegQuality: Int): PickedImage? =
    encodePickedImageDesktop(bytes, maxDimension, jpegQuality)
