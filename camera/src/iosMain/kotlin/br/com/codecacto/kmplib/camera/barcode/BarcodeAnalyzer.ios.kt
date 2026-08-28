@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package br.com.codecacto.kmplib.camera.barcode

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler

/**
 * Implementação iOS do [BarcodeAnalyzer] — **Apple Vision** (`VNDetectBarcodesRequest`), a API
 * oficial para detectar códigos em **imagem parada**.
 *
 * (A leitura **ao vivo** usa `AVCaptureMetadataOutput`; ver [BarcodeCameraPreview].)
 *
 * **Best-effort:** imagem ilegível ou falha do motor devolvem lista vazia — nunca lança.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** o build Kotlin/Native iOS não roda em Linux.
 */
actual class BarcodeAnalyzer actual constructor() {

    actual suspend fun analyze(
        imageBytes: ByteArray,
        formats: Set<BarcodeFormat>,
    ): List<ScannedBarcode> {
        if (imageBytes.isEmpty()) return emptyList()

        val effective = formats.ifEmpty { BarcodeFormats.RETAIL }
        val symbologies = effective.flatMap { it.toVisionSymbologies() }.distinct()
        if (symbologies.isEmpty()) return emptyList()

        val results = mutableListOf<ScannedBarcode>()
        val request = VNDetectBarcodesRequest { req, error ->
            if (error != null) return@VNDetectBarcodesRequest
            val observations = req?.results ?: return@VNDetectBarcodesRequest
            for (item in observations) {
                val observation = item as? VNBarcodeObservation ?: continue
                val format = (observation.symbology as? String)?.visionSymbologyToBarcodeFormat()
                    ?: continue
                parseBarcode(observation.payloadStringValue, format)?.let { results.add(it) }
            }
        }
        request.symbologies = symbologies

        val handler = VNImageRequestHandler(
            data = imageBytes.toNSData(),
            options = emptyMap<Any?, Any?>(),
        )
        handler.performRequests(listOf(request), error = null)
        return results
    }

    /** Vision não mantém recurso nativo por instância — nada a liberar. */
    actual fun close() = Unit
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
