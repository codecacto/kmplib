package br.com.codecacto.kmplib.platform

/**
 * Formato de saída da compressão de imagem.
 */
enum class ImageCompressFormat { JPEG, PNG }

/**
 * Compressor/redimensionador de imagem no cliente (expect/actual).
 *
 * Reduz **dimensão** e **qualidade** de uma imagem em bytes antes do upload, atendendo ao NFR
 * de custo de Storage (canteiro de obra envia muitas fotos). Distinto do
 * [encodeBitmapToPng] (que só codifica um [androidx.compose.ui.graphics.ImageBitmap] já
 * desenhado, sem reduzir) — aqui a entrada são bytes de uma foto (galeria/câmera).
 *
 * Genérico, sem regra de negócio. O app escolhe os limites (ex.: `maxDimension = 1600`,
 * `quality = 80`) conforme sua política de upload.
 *
 * Uso típico (antes de `StorageService.uploadBytesWithProgress`):
 * ```kotlin
 * val compressed = createImageCompressor().compress(rawBytes, maxDimension = 1600, quality = 80)
 * storageService.uploadBytesWithProgress(path, compressed, "image/jpeg").collect { ... }
 * ```
 */
interface ImageCompressor {
    /**
     * Comprime [bytes] (PNG/JPEG decodificável pela plataforma), redimensionando para que o
     * maior lado não exceda [maxDimension] (mantendo a proporção) e recodificando com a
     * [quality] informada (0..100, relevante para [ImageCompressFormat.JPEG]).
     *
     * @param bytes imagem de origem.
     * @param maxDimension maior lado máximo, em pixels. Imagens menores não são ampliadas.
     * @param quality qualidade de recodificação 0..100 (ignorada para PNG, que é lossless).
     * @param format formato de saída (default JPEG — melhor para fotos).
     * @return bytes da imagem comprimida. Em caso de imagem indecodificável, retorna os
     *   [bytes] originais (best-effort, nunca lança por entrada inválida).
     */
    fun compress(
        bytes: ByteArray,
        maxDimension: Int = 1600,
        quality: Int = 80,
        format: ImageCompressFormat = ImageCompressFormat.JPEG,
    ): ByteArray
}

/** Fábrica do [ImageCompressor] da plataforma atual. */
expect fun createImageCompressor(): ImageCompressor
