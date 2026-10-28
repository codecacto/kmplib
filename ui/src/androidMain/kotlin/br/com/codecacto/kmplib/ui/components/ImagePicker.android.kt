package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.util.redactMediaUrlsIn
import br.com.codecacto.kmplib.platform.automation.exposeTestTagsAsResourceId
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import androidx.compose.ui.platform.testTag
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class ImagePickerLauncher(
    private val launcher: () -> Unit
) {
    actual fun launch() {
        launcher()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun rememberImagePickerLauncher(
    source: ImagePickerSource,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher {
    val context = LocalContext.current
    val currentMaxDimension by rememberUpdatedState(maxDimension)
    val currentJpegQuality by rememberUpdatedState(jpegQuality)
    var showChooser by remember { mutableStateOf(false) }

    val escopo = rememberCoroutineScope()
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    val currentOnError by rememberUpdatedState(onError)

    // Decodificar/girar/recomprimir fora da main thread (2.278.0): com `maxDimension` até 4096 px,
    // o trabalho que passava despercebido em 1024 congelaria a tela. O retorno volta na main.
    fun processar(uri: Uri, depois: () -> Unit = {}) {
        val teto = currentMaxDimension
        val qualidade = currentJpegQuality
        escopo.launch {
            val resultado = try {
                withContext(Dispatchers.Default) { processImageUri(context, uri, teto, qualidade) }
            } finally {
                depois()
            }
            resultado.fold(
                onSuccess = { currentOnImagePicked(it) },
                onFailure = { currentOnError(ImagePickerError.IMAGE_UNREADABLE) },
            )
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        // `null` = a pessoa fechou a galeria sem escolher. Desistir NAO e erro.
        uri?.let { processar(it) }
    }

    fun openGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // A câmera (permissão, FileProvider, original apagado) mora no lançador interno (2.332.0) —
    // o mesmo que a folha de anexo (`rememberAttachmentPicker`) usa para "Tirar foto".
    val camera = rememberCameraPhotoLauncher(
        onImagePicked = { currentOnImagePicked(it) },
        onError = { currentOnError(it) },
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )

    if (showChooser) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showChooser = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            // Outra janela: religa o `testTagsAsResourceId` da raiz (ver `DialogTestTags`).
            modifier = Modifier.exposeTestTagsAsResourceId().testTag(DialogTestTags.FOLHA)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Adicionar foto",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                TextButton(
                    onClick = {
                        showChooser = false
                        camera.launch()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Tirar foto", fontSize = 16.sp)
                    }
                }

                TextButton(
                    onClick = {
                        showChooser = false
                        openGallery()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Escolher da galeria", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    return remember(source, pickMedia, camera) {
        ImagePickerLauncher {
            when (source) {
                // Sem folha de escolha: uma folha com uma opção só é um toque a mais para nada.
                ImagePickerSource.GALLERY_ONLY -> openGallery()
                ImagePickerSource.GALLERY_AND_CAMERA -> showChooser = true
            }
        }
    }
}

/**
 * Decodifica, **gira pelo EXIF**, reduz e recodifica em JPEG — e mede o resultado. O trabalho mora
 * em [encodePickedImage] (2.278.0), o mesmo do seletor múltiplo e da câmera guiada.
 */
internal fun processImageUri(
    context: Context,
    uri: Uri,
    maxDimension: Int,
    jpegQuality: Int,
): Result<PickedImage> = try {
    encodePickedImage(context, uri, maxDimension, jpegQuality)
        ?.let { Result.success(it) }
        ?: Result.failure(IllegalStateException("imagem ilegível"))
} catch (e: Exception) {
    AppLogger.w(TAG, "Foto escolhida não pôde ser lida: ${redactMediaUrlsIn(e.message)}")
    Result.failure(e)
} catch (e: OutOfMemoryError) {
    // `OutOfMemoryError` não é `Exception`: sem este ramo, uma foto enorme derrubava o app.
    AppLogger.w(TAG, "Foto grande demais para a memória disponível.")
    Result.failure(e)
}

private const val TAG = "KmpLibImagePicker"
