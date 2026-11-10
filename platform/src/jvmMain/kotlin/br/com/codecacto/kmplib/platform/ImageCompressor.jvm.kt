package br.com.codecacto.kmplib.platform

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Desktop (2.353.0): `javax.imageio` + `Graphics2D` com interpolação bicúbica. Lê JPEG/PNG/BMP/GIF
 * (os decodificadores do JDK); formato que o JDK não lê (HEIC, WebP) lança, como o contrato pede
 * para bytes ilegíveis. O resultado não leva metadado (EXIF/GPS não viaja).
 */
internal class DesktopImageCompressor : ImageCompressor {
    override fun compress(bytes: ByteArray, maxDimension: Int, quality: Int, format: ImageCompressFormat): ByteArray {
        val original = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("imagem em formato não suportado")
        val maior = max(original.width, original.height)
        val escala = if (maxDimension in 1 until maior) maxDimension.toDouble() / maior else 1.0
        val w = (original.width * escala).roundToInt().coerceAtLeast(1)
        val h = (original.height * escala).roundToInt().coerceAtLeast(1)
        val tipo = if (format == ImageCompressFormat.PNG) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val destino = BufferedImage(w, h, tipo)
        val g = destino.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            if (format == ImageCompressFormat.JPEG) {
                g.color = java.awt.Color.WHITE // JPEG não tem alfa: fundo branco, nunca preto
                g.fillRect(0, 0, w, h)
            }
            g.drawImage(original, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        when (format) {
            ImageCompressFormat.PNG -> ImageIO.write(destino, "png", out)
            ImageCompressFormat.JPEG -> {
                val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                try {
                    val param = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = (quality.coerceIn(1, 100) / 100f)
                    }
                    ImageIO.createImageOutputStream(out).use { ios ->
                        writer.output = ios
                        writer.write(null, IIOImage(destino, null, null), param)
                    }
                } finally {
                    writer.dispose()
                }
            }
        }
        return out.toByteArray()
    }
}

actual fun createImageCompressor(): ImageCompressor = DesktopImageCompressor()
