package br.com.codecacto.kmplib.camera.barcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.camera.CameraXPreview
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Implementação Android do [BarcodeCameraPreview] — **CameraX** (preview + `ImageAnalysis`) com
 * **ML Kit Barcode Scanning**, a abordagem oficial do Google para leitura ao vivo.
 *
 * O modelo é **embarcado** (`com.google.mlkit:barcode-scanning`, não a variante que baixa do Google
 * Play Services): num depósito ou numa loja com Wi-Fi ruim, a leitura tem de funcionar no primeiro
 * uso, sem esperar download de modelo.
 *
 * A infraestrutura de câmera (bind/unbind, lanterna, falha) é a mesma do OCR de placa —
 * [CameraXPreview].
 */
@Composable
actual fun BarcodeCameraPreview(
    onBarcodesDetected: (List<ScannedBarcode>) -> Unit,
    modifier: Modifier,
    formats: Set<BarcodeFormat>,
    torchEnabled: Boolean,
    onCameraStatus: (BarcodeCameraStatus) -> Unit,
) {
    val currentOnDetected by rememberUpdatedState(onBarcodesDetected)
    val currentOnStatus by rememberUpdatedState(onCameraStatus)
    // O analisador roda numa thread de fundo (ver CameraXPreview); os callbacks públicos da lib
    // são entregues na main, para que o app possa mexer em estado de Compose sem cuidado extra.
    val mainScope = rememberCoroutineScope()

    val scanner = remember(formats) {
        val requested = formats.ifEmpty { BarcodeFormats.RETAIL }.map { it.toMlKitFormat() }
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(requested.first(), *requested.drop(1).toIntArray())
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(scanner) {
        onDispose { runCatching { scanner.close() } }
    }

    val analyzer = remember(scanner) {
        ImageAnalysis.Analyzer { imageProxy -> imageProxy.scanWith(scanner) { results ->
            if (results.isNotEmpty()) {
                mainScope.launch(Dispatchers.Main) { currentOnDetected(results) }
            }
        } }
    }

    CameraXPreview(
        modifier = modifier,
        analyzer = analyzer,
        torchEnabled = torchEnabled,
        onReady = { torchAvailable ->
            currentOnStatus(BarcodeCameraStatus.Ready(torchAvailable))
        },
        onFailure = { message -> currentOnStatus(BarcodeCameraStatus.Failed(message)) },
        onUnavailable = { currentOnStatus(BarcodeCameraStatus.Unavailable) },
    )
}

/**
 * Roda o leitor no frame e **sempre** fecha o [ImageProxy] — sem isso o `ImageAnalysis` para de
 * entregar frames após alguns quadros (a fila do CameraX enche).
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private inline fun ImageProxy.scanWith(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    crossinline onResults: (List<ScannedBarcode>) -> Unit,
) {
    val mediaImage = image
    if (mediaImage == null) {
        close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes -> onResults(barcodes.toScannedBarcodes()) }
        .addOnCompleteListener { close() }
}
