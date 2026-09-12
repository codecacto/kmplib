package br.com.codecacto.kmplib.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File

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
): ImagePickerLauncher {
    val context = LocalContext.current
    var showChooser by remember { mutableStateOf(false) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        // `null` = a pessoa fechou a galeria sem escolher. Desistir NAO e erro.
        uri?.let { processImageUri(context, it, onImagePicked, onError) }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            photoUri?.let { uri -> processImageUri(context, uri, onImagePicked, onError) }
        }
    }

    fun openGallery() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun launchCamera() {
        try {
            val photosDir = File(context.cacheDir, "photos")
            photosDir.mkdirs()
            val photoFile = File(photosDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            photoUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            // Ate 2.131.0 isto era so `printStackTrace()`: a camera nao abria e a tela nao dizia
            // nada. Falha de camera vira AVISO no app, sempre.
            AppLogger.w(TAG, "Câmera de foto indisponível: ${e.message}")
            onError(ImagePickerError.CAMERA_UNAVAILABLE)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            launchCamera()
        } else {
            // Negada pela pessoa — ou o app nao declarou `CAMERA` no manifest, e ai o sistema nega
            // sem nem mostrar o dialogo. O `if` sem `else` que havia aqui transformava os dois
            // casos em "o botao nao faz nada".
            onError(ImagePickerError.CAMERA_PERMISSION_DENIED)
        }
    }

    if (showChooser) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showChooser = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
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
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
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

    return remember(source, pickMedia, takePicture) {
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
 * Decodifica, **gira pelo EXIF**, reduz e recodifica em JPEG — e mede o resultado.
 *
 * A medida sai do bitmap FINAL, depois da rotação e da redução, porque é ele que vira os bytes. E o
 * `Bitmap.compress` não escreve tag de orientação: os bytes que saem daqui já estão em pé, sem
 * depender de o leitor do outro lado interpretar EXIF.
 */
private fun processImageUri(
    context: Context,
    uri: Uri,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return onError(ImagePickerError.IMAGE_UNREADABLE)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (bitmap == null) {
            onError(ImagePickerError.IMAGE_UNREADABLE)
        } else {
            val corrected = correctOrientation(context, uri, bitmap)
            val scaled = scaleBitmap(corrected, PICKED_IMAGE_MAX_DIMENSION)
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            // Lido ANTES do recycle: depois dele, `width`/`height` de um bitmap reciclado não valem.
            val largura = scaled.width
            val altura = scaled.height
            val bytes = outputStream.toByteArray()

            if (scaled != corrected) scaled.recycle()
            if (corrected != bitmap) corrected.recycle()
            bitmap.recycle()

            onImagePicked(PickedImage(bytes = bytes, widthPx = largura, heightPx = altura))
        }
    } catch (e: Exception) {
        AppLogger.w(TAG, "Foto escolhida não pôde ser lida: ${e.message}")
        onError(ImagePickerError.IMAGE_UNREADABLE)
    }
}

private fun correctOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val rotation = try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(inputStream)
        inputStream.close()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }

    if (rotation == 0f) return bitmap

    val matrix = Matrix().apply { postRotate(rotation) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
    val (newWidth, newHeight) = scaledImageSize(bitmap.width, bitmap.height, maxSize)
    if (newWidth == bitmap.width && newHeight == bitmap.height) return bitmap

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private const val TAG = "KmpLibImagePicker"
