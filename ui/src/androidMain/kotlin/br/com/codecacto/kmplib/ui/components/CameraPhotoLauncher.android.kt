package br.com.codecacto.kmplib.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.redactMediaUrlsIn
import br.com.codecacto.kmplib.platform.CAMERA_CAPTURE_FILE_PREFIX
import br.com.codecacto.kmplib.platform.DEFAULT_CAMERA_CAPTURE_TTL_MILLIS
import br.com.codecacto.kmplib.platform.cameraCaptureDirectory
import br.com.codecacto.kmplib.platform.clearCameraCaptureFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Só a câmera (2.332.0): permissão `CAMERA` → `TakePicture` num arquivo do `FileProvider` da lib →
 * a MESMA codificação do seletor ([processImageUri]: JPEG em pé, sem EXIF/GPS, teto) → o original
 * apagado. Extraído do [rememberImagePickerLauncher] para a folha de anexo usar sem duplicar.
 */
@Composable
internal actual fun rememberCameraPhotoLauncher(
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher {
    val context = LocalContext.current
    val currentMaxDimension by rememberUpdatedState(maxDimension)
    val currentJpegQuality by rememberUpdatedState(jpegQuality)
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)
    val currentOnError by rememberUpdatedState(onError)
    val escopo = rememberCoroutineScope()

    // `rememberSaveable` porque o app de câmera costuma levar o sistema a destruir a nossa Activity:
    // com `remember` o retorno chegava sem saber qual arquivo ler — a foto se perdia e o arquivo
    // cru ficava no disco.
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun processar(arquivo: File) {
        val teto = currentMaxDimension
        val qualidade = currentJpegQuality
        escopo.launch {
            val resultado = try {
                withContext(Dispatchers.Default) {
                    processImageUri(context, Uri.fromFile(arquivo), teto, qualidade)
                }
            } finally {
                deleteCameraTempFile(arquivo)
            }
            resultado.fold(
                onSuccess = { currentOnImagePicked(it) },
                onFailure = { currentOnError(ImagePickerError.IMAGE_UNREADABLE) },
            )
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val arquivo = cameraFilePath?.let(::File)
        cameraFilePath = null
        if (arquivo == null) return@rememberLauncherForActivityResult
        // O original da câmera tem EXIF completo (GPS, modelo, horário): sai sempre — cancelado
        // também (arquivo vazio ou parcial), e só DEPOIS de a decodificação terminar.
        if (success) processar(arquivo) else deleteCameraTempFile(arquivo)
    }

    fun abrirCamera() {
        try {
            val photosDir = cameraCaptureDirectory(context)
            photosDir.mkdirs()
            // Originais de capturas anteriores que ficaram (processo morto no meio).
            clearCameraCaptureFiles(context, DEFAULT_CAMERA_CAPTURE_TTL_MILLIS)
            val photoFile = File(photosDir, "$CAMERA_CAPTURE_FILE_PREFIX${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            cameraFilePath = photoFile.absolutePath
            takePicture.launch(uri)
        } catch (e: Exception) {
            // Sem app de câmera, FileProvider ausente: vira AVISO na tela, nunca silêncio.
            AppLogger.w(CAMERA_TAG, "Câmera de foto indisponível: ${redactMediaUrlsIn(e.message)}")
            currentOnError(ImagePickerError.CAMERA_UNAVAILABLE)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        // Negada pela pessoa — ou o app não declarou `CAMERA` no manifest, e aí o sistema nega sem
        // nem mostrar o diálogo. Os dois viram o mesmo aviso.
        if (granted) abrirCamera() else currentOnError(ImagePickerError.CAMERA_PERMISSION_DENIED)
    }

    return remember(takePicture, cameraPermissionLauncher) {
        ImagePickerLauncher {
            val temPermissao = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (temPermissao) abrirCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

private const val CAMERA_TAG = "KmpLibImagePicker"

private fun deleteCameraTempFile(file: File) {
    if (file.exists() && !file.delete()) {
        AppLogger.w(CAMERA_TAG, "Original temporário da câmera não pôde ser apagado.")
    }
}
