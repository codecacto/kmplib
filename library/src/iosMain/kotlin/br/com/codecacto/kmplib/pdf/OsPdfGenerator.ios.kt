@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.CValue
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetAlpha
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage

/**
 * Render nativo do PDF da Ordem de Serviço / orçamento / recibo no iOS via
 * `UIGraphicsPDFRenderer` (UIKit), espelhando o MESMO layout lógico do Android
 * ([AndroidOsPdfGenerator]). A4 a 72pt (595 × 842 pt), **página única** como no Android.
 *
 * Modelado nos geradores iOS já em produção (`ReciboPdf.ios.kt`,
 * `DocumentPdfGenerator.ios.kt`, `VaccinationCardPdfGenerator.ios.kt`) — portanto NÃO é
 * placeholder. O `drawAtPoint` do iOS ancora o TOPO da caixa de texto; convertemos a
 * baseline (grade Android) → topo via `ascender` da `UIFont`, igual aos demais.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito por construção para compilar/rodar em
 * iOS, mas o ambiente de build atual é Linux (targets Apple SKIPPED — Kotlin/Native só
 * compila iOS em macOS). Validação visual do PDF precisa ser feita em host macOS/CI.
 */
class IosOsPdfGenerator : OsPdfGenerator {

    private companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
    }

    override fun generate(data: OsPdfData): ByteArray {
        val left = MARGIN
        val right = PAGE_W - MARGIN

        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val totalBg = rgb(0x1A, 0x1A, 0x1A)
        val totalText = rgb(0xFF, 0xFF, 0xFF)
        val watermarkColor = rgb(0x00, 0x00, 0x00)

        fun font(size: Double, bold: Boolean): UIFont =
            if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)

        fun attrs(color: UIColor, size: Double, bold: Boolean): Map<Any?, *> = mapOf<Any?, Any?>(
            NSFontAttributeName to font(size, bold),
            NSForegroundColorAttributeName to color,
        )

        val format = UIGraphicsPDFRendererFormat()
        val bounds: CValue<CGRect> = CGRectMake(0.0, 0.0, PAGE_W, PAGE_H)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val nsData = renderer.PDFDataWithActions { ctx ->
            val pdf = ctx ?: return@PDFDataWithActions
            pdf.beginPage()
            val cg = pdf.CGContext

            fun textWidth(text: String, size: Double, bold: Boolean): Double {
                val s = NSString.create(string = text)
                return s.sizeWithAttributes(attrs(ink, size, bold)).useContents { width }
            }

            fun drawText(text: String, x: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
                val s = NSString.create(string = text)
                val topY = baselineY - font(size, bold).ascender
                s.drawAtPoint(CGPointMake(x, topY), withAttributes = attrs(color, size, bold))
            }

            fun drawRight(text: String, rightX: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
                drawText(text, rightX - textWidth(text, size, bold), baselineY, color, size, bold)
            }

            fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double, color: UIColor, w: Double) {
                cg ?: return
                CGContextSetStrokeColorWithColor(cg, color.CGColor)
                CGContextSetLineWidth(cg, w)
                CGContextMoveToPoint(cg, x1, y1)
                CGContextAddLineToPoint(cg, x2, y2)
                CGContextStrokePath(cg)
            }

            fun fillRect(x: Double, y: Double, w: Double, h: Double, color: UIColor) {
                cg ?: return
                CGContextSetFillColorWithColor(cg, color.CGColor)
                CGContextFillRect(cg, CGRectMake(x, y, w, h))
            }

            fun truncate(text: String, maxW: Double, size: Double, bold: Boolean): String {
                if (maxW <= 0.0) return ""
                if (textWidth(text, size, bold) <= maxW) return text
                val ell = "…"
                var end = text.length
                while (end > 0 && textWidth(text.substring(0, end) + ell, size, bold) > maxW) end--
                return text.substring(0, end).trimEnd() + ell
            }

            fun drawWrapped(text: String, x: Double, startBaseline: Double, maxW: Double, color: UIColor, size: Double, lineHeight: Double): Double {
                var y = startBaseline
                val words = text.split(' ')
                val lineSb = StringBuilder()
                fun flush() {
                    if (lineSb.isNotEmpty()) {
                        drawText(lineSb.toString(), x, y, color, size, false)
                        y += lineHeight
                        lineSb.clear()
                    }
                }
                for (word in words) {
                    val candidate = if (lineSb.isEmpty()) word else "$lineSb $word"
                    if (textWidth(candidate, size, false) > maxW) {
                        flush()
                        lineSb.append(word)
                    } else {
                        lineSb.clear(); lineSb.append(candidate)
                    }
                }
                flush()
                return y
            }

            fun watermark() {
                if (!data.watermark || data.watermarkText.isBlank()) return
                cg ?: return
                CGContextSaveGState(cg)
                CGContextSetAlpha(cg, 0.08)
                CGContextTranslateCTM(cg, PAGE_W / 2.0, PAGE_H / 2.0)
                CGContextConcatCTM(cg, CGAffineTransformMakeRotation(-0.5235987756)) // -30°
                val w = textWidth(data.watermarkText, 48.0, true)
                drawText(data.watermarkText, -w / 2.0, font(48.0, true).capHeight / 2.0, watermarkColor, 48.0, true)
                CGContextRestoreGState(cg)
            }

            // --- Header --------------------------------------------------------
            var textX = left
            var y = MARGIN
            data.company.logoBytes?.let { bytes ->
                val img = UIImage.imageWithData(bytes.toNSData())
                if (img != null) {
                    val (iw, ih) = img.size.useContents { width to height }
                    if (iw > 0.0 && ih > 0.0) {
                        val ratio = minOf(64.0 / iw, 64.0 / ih, 1.0)
                        val dw = iw * ratio; val dh = ih * ratio
                        img.drawInRect(CGRectMake(left, MARGIN, dw, dh))
                        textX = left + dw + 14.0
                    }
                }
            }
            y += 16.0
            drawText(data.company.name, textX, y, ink, 18.0, true)
            listOfNotNull(data.company.phone, data.company.email, data.company.address).forEach {
                y += 14.0
                drawText(it, textX, y, muted, 10.0, false)
            }
            var ry = MARGIN + 16.0
            drawRight(data.title, right, ry, ink, 14.0, true)
            ry += 14.0
            drawRight("Nº ${data.number}", right, ry, muted, 10.0, false)
            data.status?.let { ry += 14.0; drawRight(it, right, ry, muted, 10.0, false) }
            var bottom = maxOf(y, ry) + 12.0
            drawLine(left, bottom, right, bottom, line, 1.0)
            y = bottom + 6.0
            y += 18.0

            // --- Cliente -------------------------------------------------------
            data.client?.let { client ->
                drawText("CLIENTE", left, y, muted, 9.0, true)
                y += 14.0
                drawText(client.name, left, y, ink, 12.0, true)
                listOfNotNull(client.phone, client.address).forEach { ln ->
                    y += 13.0
                    drawText(ln, left, y, muted, 10.0, false)
                }
                y += 10.0
            }

            // --- Descrição -----------------------------------------------------
            data.description?.takeIf { it.isNotBlank() }?.let { desc ->
                y += 4.0
                drawText("DESCRIÇÃO", left, y, muted, 9.0, true)
                y += 14.0
                y = drawWrapped(desc, left, y, right - left, ink, 11.0, 14.0)
                y += 8.0
            }

            // --- Tabela de itens ----------------------------------------------
            if (data.items.isNotEmpty()) {
                y += 8.0
                val colQty = right - 230.0
                val colUnit = right - 150.0
                val colSub = right
                val headerH = 22.0
                fillRect(left, y, right - left, headerH, headerBg)
                val hb = y + 15.0
                drawText("DESCRIÇÃO", left + 6.0, hb, muted, 9.0, true)
                drawRight("QTD", colQty, hb, muted, 9.0, true)
                drawRight("UNIT.", colUnit, hb, muted, 9.0, true)
                drawRight("SUBTOTAL", colSub - 6.0, hb, muted, 9.0, true)
                y += headerH
                data.items.forEach { item ->
                    val b = y + 14.0
                    val descMax = colQty - (left + 6.0) - 40.0
                    drawText(truncate(item.description, descMax, 10.0, false), left + 6.0, b, ink, 10.0, false)
                    drawRight(item.quantity.toString(), colQty, b, ink, 10.0, false)
                    drawRight(OsPdfFormat.money(item.unitPrice), colUnit, b, ink, 10.0, false)
                    drawRight(OsPdfFormat.money(item.subtotal), colSub - 6.0, b, ink, 10.0, false)
                    y += 20.0
                    drawLine(left, y, right, y, line, 0.5)
                }
            }

            // --- Totais --------------------------------------------------------
            y += 6.0
            y += 16.0
            if (OsPdfFormat.isNonZero(data.discount)) {
                drawRight("Desconto", right - 130.0, y, muted, 11.0, false)
                drawRight("- ${OsPdfFormat.money(data.discount)}", right, y, ink, 11.0, false)
                y += 18.0
            }
            run {
                val boxH = 34.0
                val boxLeft = right - 230.0
                fillRect(boxLeft, y, right - boxLeft, boxH, totalBg)
                val cy = y + 22.0
                drawText("TOTAL", boxLeft + 12.0, cy, totalText, 11.0, true)
                drawRight(OsPdfFormat.money(data.total), right - 12.0, cy, totalText, 15.0, true)
                y += boxH
            }

            // --- Observações ---------------------------------------------------
            data.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                y += 16.0
                drawText("OBSERVAÇÕES", left, y, muted, 9.0, true)
                y += 14.0
                drawWrapped(notes, left, y, right - left, muted, 10.0, 13.0)
            }

            // --- Rodapé (ancorado ao pé) --------------------------------------
            data.footer?.takeIf { it.isNotBlank() }?.let { footer ->
                val fy = PAGE_H - MARGIN + 8.0
                drawLine(left, fy - 16.0, right, fy - 16.0, line, 1.0)
                drawText(footer, left, fy, muted, 9.0, false)
            }

            watermark()
        }

        return nsData.toByteArray()
    }

    private fun rgb(r: Int, g: Int, b: Int): UIColor =
        UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)
}

actual fun createOsPdfGenerator(): OsPdfGenerator = IosOsPdfGenerator()

// --- ByteArray <-> NSData (file-private; espelham os helpers do ReciboPdf.ios) ---

private fun ByteArray.toNSData(): NSData = memScoped {
    if (isEmpty()) return@memScoped NSData.create(bytes = null, length = 0u)
    val ptr = allocArray<kotlinx.cinterop.ByteVar>(size)
    for (i in indices) ptr[i] = this@toNSData[i]
    NSData.create(bytes = ptr, length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val out = ByteArray(length)
    if (length == 0) return out
    memScoped {
        val buffer = allocArray<kotlinx.cinterop.ByteVar>(length)
        platform.posix.memcpy(buffer, this@toByteArray.bytes, this@toByteArray.length)
        for (i in 0 until length) out[i] = buffer[i]
    }
    return out
}
