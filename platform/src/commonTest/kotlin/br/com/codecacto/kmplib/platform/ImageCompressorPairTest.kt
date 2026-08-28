package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageCompressorPairTest {

    /** Fake que registra as chamadas de [compress] para verificar os parâmetros das variantes. */
    private class RecordingCompressor : ImageCompressor {
        data class Call(val maxDimension: Int, val quality: Int, val format: ImageCompressFormat)
        val calls = mutableListOf<Call>()

        override fun compress(
            bytes: ByteArray,
            maxDimension: Int,
            quality: Int,
            format: ImageCompressFormat,
        ): ByteArray {
            calls.add(Call(maxDimension, quality, format))
            // Devolve bytes marcados com a dimensão para distinguir full/thumb no teste.
            return byteArrayOf(maxDimension.toByte())
        }
    }

    @Test
    fun compressToPair_usesDefaultsForFullAndThumb() {
        val c = RecordingCompressor()
        val pair = c.compressToPair(byteArrayOf(0))

        assertEquals(2, c.calls.size)
        // Full: 1024/q82/JPEG.
        assertEquals(ImageCompressorPairTest.RecordingCompressor.Call(1024, 82, ImageCompressFormat.JPEG), c.calls[0])
        // Thumb: 256/q75/JPEG.
        assertEquals(ImageCompressorPairTest.RecordingCompressor.Call(256, 75, ImageCompressFormat.JPEG), c.calls[1])
        assertEquals(1024.toByte(), pair.full.single())
        assertEquals(256.toByte(), pair.thumb.single())
    }

    @Test
    fun compressToPair_respectsCustomParams() {
        val c = RecordingCompressor()
        c.compressToPair(byteArrayOf(0), fullMaxDimension = 800, fullQuality = 90, thumbMaxDimension = 128, thumbQuality = 60)

        assertEquals(800, c.calls[0].maxDimension)
        assertEquals(90, c.calls[0].quality)
        assertEquals(128, c.calls[1].maxDimension)
        assertEquals(60, c.calls[1].quality)
        // Ambas as variantes são JPEG (melhor p/ foto).
        assertTrue(c.calls.all { it.format == ImageCompressFormat.JPEG })
    }
}
