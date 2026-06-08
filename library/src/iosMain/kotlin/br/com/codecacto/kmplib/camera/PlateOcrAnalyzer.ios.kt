package br.com.codecacto.kmplib.camera

/**
 * Implementação iOS (placeholder) do [PlateOcrAnalyzer].
 *
 * TODO(GAP-ME-01/iOS): implementar com `VNRecognizeTextRequest` (Apple Vision)
 * quando construída em host macOS. O fluxo será:
 *  1. Converter [imageBytes] em `UIImage`/`CGImage`.
 *  2. Rodar `VNRecognizeTextRequest` via `VNImageRequestHandler`.
 *  3. Concatenar os `VNRecognizedText` e delegar a [extractPlate].
 *
 * O SDK Vision não está disponível neste ambiente Windows. Até lá, retorna
 * `null` (nunca lança).
 */
actual class PlateOcrAnalyzer actual constructor() {

    actual suspend fun analyzePlate(imageBytes: ByteArray): String? {
        // TODO(GAP-ME-01/iOS): implementar com VNRecognizeTextRequest em host macOS.
        return null
    }
}
