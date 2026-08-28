package br.com.codecacto.kmplib.camera

/**
 * Analisador de OCR de placas brasileiras (GAP-ME-01).
 *
 * Recebe os bytes de uma imagem (frame de câmera ou foto) e tenta extrair uma
 * placa veicular brasileira válida, retornando-a **normalizada** via
 * [br.com.codecacto.kmplib.mask.normalizePlate] (maiúsculas, sem separadores),
 * ou `null` quando nenhuma placa válida é encontrada.
 *
 * - **Android:** OCR on-device com **ML Kit Text Recognition**
 *   (`com.google.mlkit:text-recognition`).
 * - **iOS:** placeholder (retorna `null`) — a implementação real usará
 *   `VNRecognizeTextRequest` (Apple Vision) quando construída em host macOS.
 *
 * O analyzer não lança exceção em falha de reconhecimento: erros são tratados
 * internamente e resultam em `null`.
 *
 * ```kotlin
 * val analyzer = PlateOcrAnalyzer()
 * val plate: String? = analyzer.analyzePlate(imageBytes) // "ABC1D23" ou null
 * ```
 */
expect class PlateOcrAnalyzer() {

    /**
     * Analisa [imageBytes] e retorna a placa normalizada (ex.: `"ABC1D23"`),
     * ou `null` se nenhuma placa brasileira válida for reconhecida.
     */
    suspend fun analyzePlate(imageBytes: ByteArray): String?
}
