package br.com.codecacto.kmplib.ui.components

import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Desktop (2.353.0): a foto escolhida vira o MESMO [PickedImage] do Android/iOS — JPEG, em pé, sem
 * EXIF (o GPS não viaja), maior lado ≤ [maxDimension]. O JDK não aplica a orientação EXIF sozinho,
 * então ela é lida do segmento APP1 e aplicada aqui antes de reduzir.
 */
internal fun encodePickedImageDesktop(bytes: ByteArray, maxDimension: Int, jpegQuality: Int): PickedImage? {
    if (bytes.isEmpty()) return null
    val lida = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
    val emPe = applyExifOrientation(lida, exifOrientation(bytes))
    val teto = maxDimension.coerceIn(64, 4096)
    val maior = max(emPe.width, emPe.height)
    val escala = if (maior > teto) teto.toDouble() / maior else 1.0
    val w = (emPe.width * escala).roundToInt().coerceAtLeast(1)
    val h = (emPe.height * escala).roundToInt().coerceAtLeast(1)
    val destino = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = destino.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, w, h)
        g.drawImage(emPe, 0, 0, w, h, null)
    } finally {
        g.dispose()
    }
    val out = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    try {
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = jpegQuality.coerceIn(1, 100) / 100f
        }
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(destino, null, null), param)
        }
    } finally {
        writer.dispose()
    }
    return PickedImage(out.toByteArray(), w, h, "image/jpeg")
}

/** Orientação EXIF (1..8) de um JPEG; 1 quando não há (ou não é JPEG). Pura, testada. */
internal fun exifOrientation(jpeg: ByteArray): Int {
    fun u16(i: Int, le: Boolean) = if (le) (jpeg[i].toInt() and 0xFF) or ((jpeg[i + 1].toInt() and 0xFF) shl 8)
    else ((jpeg[i].toInt() and 0xFF) shl 8) or (jpeg[i + 1].toInt() and 0xFF)
    fun u32(i: Int, le: Boolean) = if (le) u16(i, true) or (u16(i + 2, true) shl 16) else (u16(i, false) shl 16) or u16(i + 2, false)
    if (jpeg.size < 4 || (jpeg[0].toInt() and 0xFF) != 0xFF || (jpeg[1].toInt() and 0xFF) != 0xD8) return 1
    var i = 2
    try {
        while (i + 4 <= jpeg.size) {
            if ((jpeg[i].toInt() and 0xFF) != 0xFF) return 1
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) return 1 // começou a imagem: sem EXIF antes dela
            val len = u16(i + 2, false)
            if (marker == 0xE1 && len >= 8 &&
                String(jpeg, i + 4, 4, Charsets.US_ASCII) == "Exif"
            ) {
                val tiff = i + 10
                val le = jpeg[tiff].toInt() == 'I'.code
                val ifd = tiff + u32(tiff + 4, le)
                val n = u16(ifd, le)
                for (k in 0 until n) {
                    val e = ifd + 2 + k * 12
                    if (u16(e, le) == 0x0112) return u16(e + 8, le).takeIf { it in 1..8 } ?: 1
                }
                return 1
            }
            i += 2 + len
        }
    } catch (_: IndexOutOfBoundsException) {
        return 1
    }
    return 1
}

/** Aplica a [orientation] EXIF, devolvendo a imagem como ela deve ser VISTA. */
internal fun applyExifOrientation(img: BufferedImage, orientation: Int): BufferedImage {
    if (orientation !in 2..8) return img
    val w = img.width.toDouble()
    val h = img.height.toDouble()
    val gira = orientation >= 5
    val t = AffineTransform()
    when (orientation) {
        2 -> { t.translate(w, 0.0); t.scale(-1.0, 1.0) }
        3 -> { t.translate(w, h); t.rotate(Math.PI) }
        4 -> { t.translate(0.0, h); t.scale(1.0, -1.0) }
        5 -> { t.rotate(Math.PI / 2); t.scale(1.0, -1.0) }
        6 -> { t.translate(h, 0.0); t.rotate(Math.PI / 2) }
        7 -> { t.translate(h, w); t.scale(-1.0, -1.0); t.rotate(Math.PI / 2); t.scale(1.0, -1.0) }
        8 -> { t.translate(0.0, w); t.rotate(3 * Math.PI / 2) }
    }
    val out = BufferedImage(if (gira) img.height else img.width, if (gira) img.width else img.height, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    try {
        g.drawImage(img, t, null)
    } finally {
        g.dispose()
    }
    return out
}
