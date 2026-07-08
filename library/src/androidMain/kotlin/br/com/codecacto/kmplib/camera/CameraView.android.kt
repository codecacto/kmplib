package br.com.codecacto.kmplib.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Intervalo mínimo (ms) entre disparos de captura para evitar leituras
 * repetidas do mesmo frame/placa.
 */
private const val CAPTURE_THROTTLE_MS = 2_000L

/**
 * Implementação Android do [CameraView] só-placa com **CameraX** + **ML Kit**.
 *
 * Mantém a assinatura histórica: reconhece a placa e dispara [onPlateCaptured]
 * com a placa normalizada. Delega ao pipeline compartilhado [CameraViewImpl]
 * SEM capturar foto (`onPhoto = null`).
 */
@Composable
actual fun CameraView(
    onPlateCaptured: (String) -> Unit,
    modifier: Modifier
) {
    CameraViewImpl(
        modifier = modifier,
        onPlate = onPlateCaptured,
        onPhoto = null
    )
}

/**
 * Implementação Android do [CameraView] com **captura de foto** (JPEG).
 *
 * Mesmo pipeline de OCR; ao reconhecer a placa, aciona `ImageCapture` para
 * obter um JPEG nítido do veículo e dispara [onCapture] com placa + bytes.
 */
@Composable
actual fun CameraView(
    onCapture: (placa: String, jpegBytes: ByteArray) -> Unit,
    modifier: Modifier
) {
    CameraViewImpl(
        modifier = modifier,
        // A placa só é entregue via onCapture (com a foto); o canal só-placa
        // fica inativo nesta variante para não haver disparo duplicado.
        onPlate = {},
        onPhoto = onCapture
    )
}

/**
 * Pipeline compartilhado de câmera + OCR.
 *
 * Monta `PreviewView` + `ImageAnalysis` (ML Kit) e, quando [onPhoto] != null,
 * também `ImageCapture`. Ao extrair uma placa válida via [extractPlate]
 * (respeitando o throttle de [CAPTURE_THROTTLE_MS]):
 * - dispara [onPlate] com a placa; e
 * - se [onPhoto] != null, aciona `ImageCapture.takePicture`, converte o
 *   resultado em JPEG upright ([imageProxyToUprightJpeg]) e dispara [onPhoto].
 *
 * Se a permissão de câmera não estiver concedida, exibe um placeholder claro
 * (o app consumidor é responsável por solicitar `CAMERA`).
 */
@Composable
private fun CameraViewImpl(
    modifier: Modifier,
    onPlate: (String) -> Unit,
    onPhoto: ((String, ByteArray) -> Unit)?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPlate by rememberUpdatedState(onPlate)
    val currentOnPhoto by rememberUpdatedState(onPhoto)

    val hasPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    if (!hasPermission) {
        CameraPlaceholder(
            modifier = modifier,
            message = "Permissão de câmera necessária para ler a placa."
        )
        return
    }

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val imageCapture = remember(onPhoto != null) {
        if (onPhoto != null) {
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        } else {
            null
        }
    }
    var lastCaptureTime by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
            recognizer.close()
        }
    }

    LaunchedEffect(imageCapture) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            processFrame(
                imageProxy = imageProxy,
                recognizer = recognizer,
                canCapture = {
                    System.currentTimeMillis() - lastCaptureTime >= CAPTURE_THROTTLE_MS
                },
                onPlate = { plate ->
                    lastCaptureTime = System.currentTimeMillis()
                    currentOnPlate(plate)
                    val capture = imageCapture
                    val photoCallback = currentOnPhoto
                    if (capture != null && photoCallback != null) {
                        capturePhoto(capture, captureExecutor) { jpeg ->
                            if (jpeg != null) photoCallback(plate, jpeg)
                        }
                    }
                }
            )
        }

        runCatching {
            cameraProvider.unbindAll()
            val useCases = buildList {
                add(preview)
                add(analysis)
                imageCapture?.let { add(it) }
            }.toTypedArray()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases
            )
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

/**
 * Processa um frame da câmera: roda OCR e, se houver placa válida e o throttle
 * permitir, chama [onPlate]. Sempre fecha o [ImageProxy] ao final.
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processFrame(
    imageProxy: ImageProxy,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    canCapture: () -> Boolean,
    onPlate: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || !canCapture()) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(
        mediaImage,
        imageProxy.imageInfo.rotationDegrees
    )

    recognizer.process(image)
        .addOnSuccessListener { result ->
            extractPlate(result.text)?.let { plate ->
                if (canCapture()) onPlate(plate)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

/**
 * Dispara [ImageCapture.takePicture] em memória e devolve um **JPEG upright**
 * (rotação já aplicada aos pixels) via [onJpeg], ou `null` em caso de falha.
 * Nunca lança: erros de captura resultam em `null`.
 */
private fun capturePhoto(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onJpeg: (ByteArray?) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val jpeg = runCatching { imageProxyToUprightJpeg(image) }.getOrNull()
                image.close()
                onJpeg(jpeg)
            }

            override fun onError(exception: ImageCaptureException) {
                // Falha na captura — sem foto, sem crash.
                onJpeg(null)
            }
        }
    )
}

/**
 * Placeholder exibido quando a permissão de câmera não foi concedida.
 */
@Composable
private fun CameraPlaceholder(modifier: Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
