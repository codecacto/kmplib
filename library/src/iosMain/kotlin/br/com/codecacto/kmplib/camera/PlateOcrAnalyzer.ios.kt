@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.camera

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageRef
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * Implementação iOS **real** do [PlateOcrAnalyzer] via **Apple Vision** (`VNRecognizeTextRequest`) —
 * padrão-ouro de OCR on-device do iOS (sem serviço de terceiros, sem rede). Espelha o papel do ML Kit
 * no Android: recebe os bytes de uma imagem, reconhece o texto e delega a extração da placa ao núcleo
 * puro [extractPlate] (commonMain, testado).
 *
 * Fluxo (documentado pela Apple):
 *  1. `imageBytes` → `UIImage` → `CGImage`.
 *  2. `VNRecognizeTextRequest` (nível **accurate**, correção de idioma **off** — placa é alfanumérica).
 *  3. `VNImageRequestHandler.performRequests` (síncrono; a `completionHandler` roda antes de retornar).
 *  4. Concatena os `topCandidates` de cada `VNRecognizedTextObservation` e chama [extractPlate].
 *
 * **Best-effort — NUNCA lança:** bytes inválidos / sem CGImage / falha da Vision ⇒ `null`.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito conforme a API oficial da Apple; o build
 * Kotlin/Native iOS não roda em Linux — compilar/testar num device é o passo final em macOS.
 */
actual class PlateOcrAnalyzer actual constructor() {

    actual suspend fun analyzePlate(imageBytes: ByteArray): String? {
        if (imageBytes.isEmpty()) return null
        val data = imageBytes.toNSData()
        val cgImage: CGImageRef = UIImage(data = data)?.CGImage ?: return null

        val recognized = StringBuilder()
        val request = VNRecognizeTextRequest { req, error ->
            if (error != null) return@VNRecognizeTextRequest
            val results = req?.results ?: return@VNRecognizeTextRequest
            for (result in results) {
                val observation = result as? VNRecognizedTextObservation ?: continue
                val candidate = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText
                candidate?.string?.let { recognized.append(it).append('\n') }
            }
        }.apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = false
        }

        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
        handler.performRequests(listOf(request), error = null)

        return extractPlate(recognized.toString())
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
