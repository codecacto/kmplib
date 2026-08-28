package br.com.codecacto.kmplib.camera.barcode

import android.graphics.BitmapFactory
import br.com.codecacto.kmplib.core.util.AppLogger
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implementação Android do [BarcodeAnalyzer] — **ML Kit Barcode Scanning** sobre uma imagem
 * parada.
 *
 * Os clientes do ML Kit são criados **por conjunto de formatos** e reaproveitados entre chamadas
 * (criar um por foto seria desperdício); [close] libera todos.
 */
actual class BarcodeAnalyzer actual constructor() {

    private val scanners = mutableMapOf<Set<BarcodeFormat>, BarcodeScanner>()

    actual suspend fun analyze(
        imageBytes: ByteArray,
        formats: Set<BarcodeFormat>,
    ): List<ScannedBarcode> {
        if (imageBytes.isEmpty()) return emptyList()

        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }.getOrNull() ?: run {
            AppLogger.w(TAG, "Imagem ilegível para leitura de código de barras")
            return emptyList()
        }

        val effective = formats.ifEmpty { BarcodeFormats.RETAIL }
        val scanner = scanners.getOrPut(effective) {
            val requested = effective.map { it.toMlKitFormat() }
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(requested.first(), *requested.drop(1).toIntArray())
                    .build()
            )
        }

        return runCatching {
            suspendCancellableCoroutine { continuation ->
                scanner.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { barcodes ->
                        continuation.resume(barcodes.toScannedBarcodes())
                    }
                    .addOnFailureListener { error ->
                        AppLogger.w(TAG, "Falha ao ler código de barras: ${error.message}")
                        continuation.resume(emptyList())
                    }
            }
        }.getOrElse { emptyList() }
    }

    actual fun close() {
        scanners.values.forEach { runCatching { it.close() } }
        scanners.clear()
    }

    private companion object {
        const val TAG = "BarcodeAnalyzer"
    }
}
