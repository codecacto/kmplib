package br.com.codecacto.kmplib.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * Monta o preview + análise via [CameraXPreview] (infra comum do módulo `camera`, dividida com o
 * leitor de código de barras) e, quando [onPhoto] != null, também `ImageCapture`. Ao extrair uma
 * placa válida via [extractPlate] (respeitando o throttle de [CAPTURE_THROTTLE_MS]):
 * - dispara [onPlate] com a placa; e
 * - se [onPhoto] != null, aciona `ImageCapture.takePicture`, converte o
 *   resultado em JPEG upright ([imageProxyToUprightJpeg]) e dispara [onPhoto].
 *
 * Permissão: usa o [rememberPermissionState] da lib. Diferente da versão anterior — que lia a
 * permissão **uma única vez** e ficava presa no placeholder mesmo depois de o usuário conceder o
 * acesso — o estado é reconsultado e a permissão é solicitada quando ainda não foi.
 */
@Composable
private fun CameraViewImpl(
    modifier: Modifier,
    onPlate: (String) -> Unit,
    onPhoto: ((String, ByteArray) -> Unit)?
) {
    val currentOnPlate by rememberUpdatedState(onPlate)
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val permission = rememberPermissionState(AppPermission.CAMERA)
    val mainScope = rememberCoroutineScope()

    if (!permission.isGranted) {
        CameraPlaceholder(
            modifier = modifier,
            message = "Permissão de câmera necessária para ler a placa."
        )
        return
    }

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
            captureExecutor.shutdown()
            recognizer.close()
        }
    }

    val analyzer = remember(recognizer, imageCapture) {
        ImageAnalysis.Analyzer { imageProxy ->
            processFrame(
                imageProxy = imageProxy,
                recognizer = recognizer,
                canCapture = {
                    System.currentTimeMillis() - lastCaptureTime >= CAPTURE_THROTTLE_MS
                },
                onPlate = { plate ->
                    lastCaptureTime = System.currentTimeMillis()
                    // A análise roda em thread de fundo; o callback público vai para a main.
                    mainScope.launch(Dispatchers.Main) { currentOnPlate(plate) }
                    val capture = imageCapture
                    val photoCallback = currentOnPhoto
                    if (capture != null && photoCallback != null) {
                        capturePhoto(capture, captureExecutor) { jpeg ->
                            if (jpeg != null) {
                                mainScope.launch(Dispatchers.Main) { photoCallback(plate, jpeg) }
                            }
                        }
                    }
                }
            )
        }
    }

    CameraXPreview(
        modifier = modifier,
        analyzer = analyzer,
        imageCapture = imageCapture,
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
