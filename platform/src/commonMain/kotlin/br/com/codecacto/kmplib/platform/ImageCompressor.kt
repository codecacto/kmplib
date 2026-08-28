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

/**
 * Par comprimido de uma foto: [full] (variante grande, p/ PDF/tela cheia) e [thumb] (miniatura,
 * p/ listas/tiras de evidência). Padrão recorrente em captura de foto-prova (vistoria, registro de
 * obra, visita de campo): guarda-se/exibe-se a miniatura barata e usa-se a full só quando precisa.
 */
class CompressedImagePair(val full: ByteArray, val thumb: ByteArray)

/**
 * Comprime [rawBytes] em **duas variantes JPEG** de uma vez, usando este [ImageCompressor] (não
 * reimplementa compressão): [CompressedImagePair.full] (`fullMaxDimension`, alta qualidade) e
 * [CompressedImagePair.thumb] (`thumbMaxDimension`, qualidade menor). Best-effort — herda o
 * comportamento tolerante do [compress] (entrada indecodificável devolve os bytes originais).
 *
 * Defaults calibrados para foto-prova de vistoria (full ~1024px q82, thumb ~256px q75); o app pode
 * ajustar conforme sua política de upload.
 *
 * ```kotlin
 * val pair = createImageCompressor().compressToPair(rawJpegBytes)
 * // pair.thumb → lista/tira; pair.full → PDF/tela cheia/upload
 * ```
 */
fun ImageCompressor.compressToPair(
    rawBytes: ByteArray,
    fullMaxDimension: Int = 1024,
    fullQuality: Int = 82,
    thumbMaxDimension: Int = 256,
    thumbQuality: Int = 75,
): CompressedImagePair = CompressedImagePair(
    full = compress(rawBytes, maxDimension = fullMaxDimension, quality = fullQuality, format = ImageCompressFormat.JPEG),
    thumb = compress(rawBytes, maxDimension = thumbMaxDimension, quality = thumbQuality, format = ImageCompressFormat.JPEG),
)
