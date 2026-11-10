package br.com.codecacto.kmplib.ui.components

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Foto escolhida no desktop (2.353.0): em pé, reduzida, JPEG sem EXIF. */
class DesktopImageEncodingTest {

    /** Imagem 4×2 com o pixel (0,0) vermelho e o resto azul — dá para seguir para onde ele vai. */
    private fun amostra(): BufferedImage = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB).apply {
        for (x in 0 until 4) for (y in 0 until 2) setRGB(x, y, Color.BLUE.rgb)
        setRGB(0, 0, Color.RED.rgb)
    }

    private fun vermelho(img: BufferedImage): Pair<Int, Int> {
        for (x in 0 until img.width) for (y in 0 until img.height) {
            if (img.getRGB(x, y) and 0xFFFFFF == 0xFF0000) return x to y
        }
        error("sem vermelho")
    }

    @Test
    fun orientacoes_exif_levam_o_canto_para_o_lugar_certo() {
        // (x, y) esperado do pixel (0,0) depois de aplicar a orientação, numa imagem 4×2.
        val esperado = mapOf(
            1 to (0 to 0), 2 to (3 to 0), 3 to (3 to 1), 4 to (0 to 1),
            5 to (0 to 0), 6 to (1 to 0), 7 to (1 to 3), 8 to (0 to 3),
        )
        for ((o, pos) in esperado) {
            val out = applyExifOrientation(amostra(), o)
            if (o >= 5) assertEquals(2 to 4, out.width to out.height, "orientação $o gira")
            assertEquals(pos, vermelho(out), "orientação $o")
        }
    }

    @Test
    fun le_a_orientacao_do_segmento_exif() {
        // JPEG mínimo: SOI + APP1 "Exif" com TIFF big-endian, 1 entrada 0x0112 = 6 + SOS.
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // MM, 42, IFD em 8
            0x00, 0x01, // 1 entrada
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
        )
        val app1 = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val len = app1.size + 2
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) +
            app1 + byteArrayOf(0xFF.toByte(), 0xDA.toByte())
        assertEquals(6, exifOrientation(jpeg))
        assertEquals(1, exifOrientation(byteArrayOf(1, 2, 3)))
        assertEquals(1, exifOrientation(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun reduz_para_o_teto_e_sai_jpeg() {
        val grande = BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(grande, "png", it) }.toByteArray()
        val foto = assertNotNull(encodePickedImageDesktop(png, 1024, 85))
        assertEquals(1024 to 512, foto.widthPx to foto.heightPx)
        assertEquals("image/jpeg", foto.mimeType)
        assertTrue(foto.bytes[0] == 0xFF.toByte() && foto.bytes[1] == 0xD8.toByte())
        val relida = ImageIO.read(ByteArrayInputStream(foto.bytes))
        assertEquals(1024, relida.width)
    }

    @Test
    fun bytes_que_nao_sao_imagem_devolvem_null() {
        assertNull(encodePickedImageDesktop(byteArrayOf(1, 2, 3), 1024, 85))
        assertNull(encodePickedImageDesktop(ByteArray(0), 1024, 85))
    }
}
