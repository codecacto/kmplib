@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.CValue
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextClipToRect
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
import platform.CoreGraphics.CGContextStrokeRect
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
 * Render nativo do PDF do **relatório de horas extras** no iOS via `UIGraphicsPDFRenderer`
 * (UIKit), espelhando o MESMO layout lógico do Android ([AndroidHoursReportPdfGenerator]):
 * cabeçalho (logo+emissor+período/emissão), bloco da empresa cobrada, tabela de lançamentos
 * (data, entrada–saída, duração, valor, status), bloco de totais com destaque do pendente, e
 * grade de **Comprovantes** (imagem com center-crop + legenda). Marca d'água -45° quando
 * `watermark=true`. A4 a 72pt (595 × 842 pt), com paginação automática.
 *
 * Modelado nos geradores iOS já em produção (`VaccinationCardPdfGenerator.ios.kt`,
 * `DocumentPdfGenerator.ios.kt`) — NÃO é placeholder.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** ambiente atual Linux (targets Apple SKIPPED).
 */
class IosHoursReportPdfGenerator : HoursReportPdfGenerator {

    private companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0

        const val COL_DATE = 0.22
        const val COL_TIME = 0.26
        const val COL_DURATION = 0.18
        const val COL_VALUE = 0.18
    }

    override fun generate(data: HoursReportPdfData): ByteArray {
        val left = MARGIN
        val right = PAGE_W - MARGIN

        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val totalBg = rgb(0xEF, 0xF6, 0xEF)
        val pending = rgb(0xB2, 0x6A, 0x00)
        val watermarkColor = rgb(0x1A, 0x1A, 0x1A)

        val usable = right - MARGIN
        val xDate = MARGIN
        val xTime = MARGIN + usable * COL_DATE
        val xDuration = MARGIN + usable * (COL_DATE + COL_TIME)
        val xValue = MARGIN + usable * (COL_DATE + COL_TIME + COL_DURATION)
        val xStatus = MARGIN + usable * (COL_DATE + COL_TIME + COL_DURATION + COL_VALUE)

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

            var y = MARGIN

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

            fun fillRect(x: Double, yy: Double, w: Double, h: Double, color: UIColor) {
                cg ?: return
                CGContextSetFillColorWithColor(cg, color.CGColor)
                CGContextFillRect(cg, CGRectMake(x, yy, w, h))
            }

            fun strokeRect(x: Double, yy: Double, w: Double, h: Double, color: UIColor, lw: Double) {
                cg ?: return
                CGContextSetStrokeColorWithColor(cg, color.CGColor)
                CGContextSetLineWidth(cg, lw)
                CGContextStrokeRect(cg, CGRectMake(x, yy, w, h))
            }

            fun truncate(text: String, maxW: Double, size: Double, bold: Boolean): String {
                if (maxW <= 0.0) return ""
                if (textWidth(text, size, bold) <= maxW) return text
                val ell = "…"
                var end = text.length
                while (end > 0 && textWidth(text.substring(0, end) + ell, size, bold) > maxW) end--
                return text.substring(0, end).trimEnd() + ell
            }

            fun drawImageCover(bytes: ByteArray, x: Double, yy: Double, w: Double, h: Double) {
                val img = UIImage.imageWithData(bytes.toNSData()) ?: return
                val (iw, ih) = img.size.useContents { width to height }
                if (iw <= 0.0 || ih <= 0.0) return
                cg ?: return
                val scale = maxOf(w / iw, h / ih)
                val dw = iw * scale; val dh = ih * scale
                val dx = x + (w - dw) / 2.0; val dy = yy + (h - dh) / 2.0
                CGContextSaveGState(cg)
                CGContextClipToRect(cg, CGRectMake(x, yy, w, h))
                img.drawInRect(CGRectMake(dx, dy, dw, dh))
                CGContextRestoreGState(cg)
            }

            fun watermark() {
                val text = data.watermarkText
                if (!data.watermark || text.isNullOrBlank()) return
                cg ?: return
                CGContextSaveGState(cg)
                CGContextSetAlpha(cg, 0.12)
                CGContextTranslateCTM(cg, PAGE_W / 2.0, PAGE_H / 2.0)
                CGContextConcatCTM(cg, CGAffineTransformMakeRotation(-0.7853981634)) // -45°
                val w = textWidth(text, 54.0, true)
                drawText(text, -w / 2.0, font(54.0, true).capHeight / 2.0, watermarkColor, 54.0, true)
                CGContextRestoreGState(cg)
            }

            fun newPage() {
                watermark()
                pdf.beginPage()
                y = MARGIN
            }

            fun ensureSpace(needed: Double) {
                if (y > PAGE_H - MARGIN - needed) newPage()
            }

            // --- Header --------------------------------------------------------
            run {
                var textX = left
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
                var hy = MARGIN + 16.0
                drawText(data.company.name, textX, hy, ink, 18.0, true)
                data.company.phone?.let { hy += 14.0; drawText(it, textX, hy, muted, 10.0, false) }
                var ry = MARGIN + 16.0
                drawRight("Relatório de horas extras", right, ry, ink, 14.0, true)
                ry += 14.0; drawRight(data.periodLabel, right, ry, muted, 10.0, false)
                ry += 14.0; drawRight(data.generatedAtLabel, right, ry, muted, 10.0, false)
                val bottom = maxOf(hy, ry) + 12.0
                drawLine(left, bottom, right, bottom, line, 1.0)
                y = bottom + 6.0
            }
            y += 12.0
            // Bloco da empresa cobrada.
            run {
                y += 14.0
                drawText(truncate(data.companyLabel, right - MARGIN, 11.0, true), left, y, ink, 11.0, true)
            }
            y += 16.0

            // --- Lançamentos ---------------------------------------------------
            ensureSpace(60.0)
            run {
                y += 12.0
                drawText("LANÇAMENTOS", left, y, ink, 12.0, true)
                y += 8.0
            }
            // Cabeçalho da tabela.
            run {
                y += 12.0
                drawText("Data", xDate, y, muted, 9.0, true)
                drawText("Entrada–Saída", xTime, y, muted, 9.0, true)
                drawText("Duração", xDuration, y, muted, 9.0, true)
                drawText("Valor", xValue, y, muted, 9.0, true)
                drawText("Status", xStatus, y, muted, 9.0, true)
                val ly = y + 5.0
                drawLine(left, ly, right, ly, line, 1.0)
                y = ly + 4.0
            }
            if (data.entries.isEmpty()) {
                y += 14.0
                drawText("Nenhum lançamento no período.", left + 6.0, y, muted, 10.0, false)
                y += 8.0
            } else {
                for (e in data.entries) {
                    ensureSpace(26.0)
                    y += 12.0
                    drawText(truncate(e.date, xTime - xDate - 4.0, 9.5, false), xDate, y, ink, 9.5, false)
                    drawText(truncate("${e.start}–${e.end}", xDuration - xTime - 4.0, 9.5, false), xTime, y, ink, 9.5, false)
                    drawText(truncate(e.durationLabel, xValue - xDuration - 4.0, 9.5, false), xDuration, y, ink, 9.5, false)
                    drawText(truncate(e.valueLabel ?: "—", xStatus - xValue - 4.0, 9.5, false), xValue, y, ink, 9.5, false)
                    drawText(truncate(e.statusLabel, right - xStatus, 9.5, false), xStatus, y, ink, 9.5, false)
                    val ly = y + 5.0
                    drawLine(left, ly, right, ly, line, 0.5)
                    y = ly + 3.0
                }
            }
            y += 14.0

            // --- Totais --------------------------------------------------------
            ensureSpace(90.0)
            run {
                y += 14.0
                drawText("TOTAIS", left, y, ink, 12.0, true)
                y += 6.0

                fun totalRow(label: String, value: String, bold: Boolean) {
                    y += 14.0
                    drawText(label, left, y, muted, 10.0, bold)
                    drawRight(value, right, y, ink, 10.5, bold)
                    y += 2.0
                }

                totalRow("Total de horas", data.totalHoursLabel, true)

                data.totalPendingLabel?.let { p ->
                    y += 6.0
                    val boxTop = y
                    fillRect(left, boxTop, right - left, 26.0, totalBg)
                    drawText("A receber (pendente)", left + 8.0, boxTop + 17.0, pending, 11.0, true)
                    drawRight(p, right - 8.0, boxTop + 17.0, pending, 13.0, true)
                    y = boxTop + 26.0 + 4.0
                }

                data.totalPaidLabel?.let { totalRow("Pago", it, false) }
                data.totalContestedLabel?.let { totalRow("Contestado", it, false) }
            }
            y += 16.0

            // --- Comprovantes (grade de imagens) -------------------------------
            if (data.attachments.isNotEmpty()) {
                ensureSpace(80.0)
                y += 12.0
                drawText("COMPROVANTES", left, y, ink, 12.0, true)
                y += 8.0

                val colsN = 2
                val gap = 12.0
                val cellW = (right - MARGIN - gap * (colsN - 1)) / colsN
                val imgH = cellW * 0.66
                val captionH = 16.0
                val cellH = imgH + captionH

                var i = 0
                while (i < data.attachments.size) {
                    ensureSpace(cellH + 8.0)
                    val rowTop = y
                    for (c in 0 until colsN) {
                        val idx = i + c
                        if (idx >= data.attachments.size) break
                        val att = data.attachments[idx]
                        val x = MARGIN + c * (cellW + gap)
                        fillRect(x, rowTop, cellW, imgH, headerBg)
                        drawImageCover(att.imageBytes, x, rowTop, cellW, imgH)
                        strokeRect(x, rowTop, cellW, imgH, line, 0.5)
                        att.caption?.let {
                            drawText(truncate(it, cellW, 8.5, false), x, rowTop + imgH + 11.0, muted, 8.5, false)
                        }
                    }
                    y = rowTop + cellH + 10.0
                    i += colsN
                }
            }

            watermark()
        }

        return nsData.toByteArray()
    }

    private fun rgb(r: Int, g: Int, b: Int): UIColor =
        UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)
}

actual fun createHoursReportPdfGenerator(): HoursReportPdfGenerator =
    IosHoursReportPdfGenerator()

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
