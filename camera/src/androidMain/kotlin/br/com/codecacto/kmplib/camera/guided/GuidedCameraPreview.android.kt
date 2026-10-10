package br.com.codecacto.kmplib.camera.guided

import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.components.encodePickedImage
import br.com.codecacto.kmplib.ui.components.exifOrientationForRotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

private const val TAG = "KmpLibGuidedCamera"

/**
 * CameraX: `Preview` + `ImageCapture`, os dois em **4:3** (a proporção nativa do sensor), com o
 * `PreviewView` em `FIT_CENTER` — o preview inteiro, sem corte, na caixa 3:4 que o código comum já
 * dimensionou. É isso que faz a tela mostrar exatamente o enquadramento da foto.
 *
 * - Provider fora da main thread (`ProcessCameraProvider.getInstance().get()` bloqueia).
 * - `bindToLifecycle` no dono do ciclo de vida da tela; **`unbind` no `onDispose`** — sair da tela
 *   desliga a câmera (sem isso o indicador do sistema seguiria aceso em outra tela).
 * - Disparo em executor próprio; a codificação (decodificar, girar pelos graus do CameraX, reduzir,
 *   JPEG sem EXIF) roda nele, e o resultado volta pela main thread.
 */
@Composable
internal actual fun GuidedCameraPreview(
    modifier: Modifier,
    lens: CameraLens,
    flashMode: CameraFlashMode,
    maxDimension: Int,
    jpegQuality: Int,
    handle: GuidedCaptureHandle,
    onStatus: (GuidedCameraStatus) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStatus by rememberUpdatedState(onStatus)
    val currentMaxDimension by rememberUpdatedState(maxDimension)
    val currentJpegQuality by rememberUpdatedState(jpegQuality)

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    val resolution = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolution)
            .build()
    }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundPreview by remember { mutableStateOf<Preview?>(null) }

    LaunchedEffect(Unit) {
        val provider = runCatching {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        }.getOrElse { erro ->
            AppLogger.e(TAG, "Falha ao obter o ProcessCameraProvider", erro)
            currentOnStatus(GuidedCameraStatus.Failed(erro.message))
            return@LaunchedEffect
        }

        val disponiveis = buildSet {
            if (runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)) {
                add(CameraLens.BACK)
            }
            if (runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)) {
                add(CameraLens.FRONT)
            }
        }
        val ativa = resolveCameraLens(lens, disponiveis)
        if (ativa == null) {
            AppLogger.w(TAG, "Nenhuma câmera disponível no aparelho.")
            currentOnStatus(GuidedCameraStatus.Unavailable)
            return@LaunchedEffect
        }
        val seletor = if (ativa == CameraLens.BACK) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val preview = Preview.Builder()
            .setResolutionSelector(resolution)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, seletor, preview, imageCapture)
        }.onSuccess { vinculada ->
            camera = vinculada
            boundProvider = provider
            boundPreview = preview
            currentOnStatus(
                GuidedCameraStatus.Ready(
                    flashAvailable = runCatching { vinculada.cameraInfo.hasFlashUnit() }.getOrDefault(false),
                    availableLenses = disponiveis,
                    activeLens = ativa,
                ),
            )
        }.onFailure { erro ->
            AppLogger.e(TAG, "Falha ao vincular a câmera ao ciclo de vida", erro)
            currentOnStatus(GuidedCameraStatus.Failed(erro.message))
        }
    }

    // Câmera sem flash (a frontal, quase sempre) dispara com o flash desligado, mesmo que o modo
    // escolhido na traseira tenha ficado em "ligado".
    LaunchedEffect(camera, flashMode) {
        val temFlash = camera?.let { runCatching { it.cameraInfo.hasFlashUnit() }.getOrDefault(false) } ?: false
        imageCapture.flashMode = if (!temFlash) {
            ImageCapture.FLASH_MODE_OFF
        } else {
            when (flashMode) {
                CameraFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            }
        }
    }

    DisposableEffect(handle) {
        val principal = ContextCompat.getMainExecutor(context)
        handle.capture = { onResult ->
            // A rotação de destino é a da TELA no instante do disparo: é ela que diz ao CameraX
            // quantos graus a foto precisa girar para sair em pé.
            imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val resultado = try {
                            codificar(image, currentMaxDimension, currentJpegQuality)
                        } finally {
                            image.close()
                        }
                        principal.execute { onResult(resultado) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        AppLogger.w(TAG, "Disparo falhou (código ${exception.imageCaptureError}).")
                        principal.execute { onResult(GuidedCaptureResult.Failure(exception.message)) }
                    }
                },
            )
        }
        onDispose {
            handle.capture = null
            // Solta só o que esta tela ligou, e desliga a câmera ao sair.
            val usos = listOfNotNull(boundPreview, imageCapture)
            runCatching { boundProvider?.unbind(*usos.toTypedArray()) }
            camera = null
            boundProvider = null
            boundPreview = null
            captureExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * O JPEG do CameraX (bytes ainda deitados, a rotação vem em `rotationDegrees`) → [GuidedCaptureResult],
 * pelo codificador do seletor de imagem.
 */
private fun codificar(image: ImageProxy, maxDimension: Int, jpegQuality: Int): GuidedCaptureResult = try {
    val buffer = image.planes[0].buffer
    val crus = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val foto = encodePickedImage(
        open = { ByteArrayInputStream(crus) },
        exifOrientation = exifOrientationForRotation(image.imageInfo.rotationDegrees),
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )
    if (foto == null) GuidedCaptureResult.Failure("foto não pôde ser codificada") else GuidedCaptureResult.Success(foto)
} catch (e: Exception) {
    AppLogger.w(TAG, "Foto capturada não pôde ser codificada: ${e::class.simpleName}")
    GuidedCaptureResult.Failure(e.message)
} catch (e: OutOfMemoryError) {
    AppLogger.w(TAG, "Foto capturada grande demais para a memória disponível.")
    GuidedCaptureResult.Failure("sem memória")
}
