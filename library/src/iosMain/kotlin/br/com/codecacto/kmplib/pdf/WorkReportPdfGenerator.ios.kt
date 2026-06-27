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
 * Render nativo do PDF do **relatório de acompanhamento de obra** no iOS via
 * `UIGraphicsPDFRenderer` (UIKit), espelhando o MESMO layout lógico do Android
 * ([AndroidWorkReportPdfGenerator]): cabeçalho (logo+empresa+período/emissão), bloco da obra
 * (nome/cliente/endereço + barra de progresso geral), seção **Etapas** (nome+status+barra+%+
 * nota), seção **Registro fotográfico** (grade de fotos com center-crop + etapa/legenda/data) e
 * **Diário de obra** (data/clima/equipe/autor + anotações). Marca d'água -45° quando
 * `watermark=true`. A4 a 72pt (595 × 842 pt), com paginação automática.
 *
 * Modelado nos geradores iOS já em produção (`VaccinationCardPdfGenerator.ios.kt`,
 * `DocumentPdfGenerator.ios.kt`) — NÃO é placeholder.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** ambiente atual Linux (targets Apple SKIPPED).
 */
class IosWorkReportPdfGenerator : WorkReportPdfGenerator {

    private companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
    }

    override fun generate(data: WorkReportPdfData): ByteArray {
        val left = MARGIN
        val right = PAGE_W - MARGIN

        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val barTrack = rgb(0xE6, 0xE8, 0xEB)
        val barFill = rgb(0x2E, 0x7D, 0x32)
        val watermarkColor = rgb(0x1A, 0x1A, 0x1A)

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

            // Retorna a baseline final (após a última linha desenhada).
            fun drawWrapped(text: String, x: Double, startBaseline: Double, maxW: Double, color: UIColor, size: Double, lineHeight: Double): Double {
                var yy = startBaseline
                val words = text.split(Regex("\\s+"))
                val lineSb = StringBuilder()
                for (w in words) {
                    val candidate = if (lineSb.isEmpty()) w else "$lineSb $w"
                    if (textWidth(candidate, size, false) > maxW && lineSb.isNotEmpty()) {
                        drawText(lineSb.toString(), x, yy, color, size, false)
                        yy += lineHeight
                        lineSb.clear(); lineSb.append(w)
                    } else {
                        lineSb.clear(); lineSb.append(candidate)
                    }
                }
                if (lineSb.isNotEmpty()) { drawText(lineSb.toString(), x, yy, color, size, false); yy += lineHeight }
                return yy
            }

            // Barra de progresso (cantos retos no iOS; mesmo conteúdo/fração do Android). Retorna top+height.
            fun progressBar(x: Double, top: Double, rightX: Double, frac: Double, height: Double): Double {
                val w = rightX - x
                val f = frac.coerceIn(0.0, 1.0)
                fillRect(x, top, w, height, barTrack)
                if (f > 0.0) fillRect(x, top, w * f, height, barFill)
                return top + height
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
                drawRight("Acompanhamento de obra", right, ry, ink, 14.0, true)
                data.periodLabel?.let { ry += 14.0; drawRight(it, right, ry, muted, 10.0, false) }
                ry += 14.0; drawRight(data.generatedAtLabel, right, ry, muted, 10.0, false)
                val bottom = maxOf(hy, ry) + 12.0
                drawLine(left, bottom, right, bottom, line, 1.0)
                y = bottom + 6.0
            }
            y += 12.0

            // --- Bloco da obra -------------------------------------------------
            run {
                y += 14.0
                drawText(data.workName, left, y, ink, 15.0, true)
                data.clientName?.let { y += 14.0; drawText("Cliente: $it", left, y, muted, 10.0, false) }
                data.address?.let { y += 14.0; drawText(it, left, y, muted, 10.0, false) }
                y += 18.0
                drawText("Progresso geral", left, y, muted, 9.5, true)
                y += 6.0
                val frac = (data.overallProgress / 100.0).coerceIn(0.0, 1.0)
                y = progressBar(left, y, right, frac, 10.0)
            }
            y += 16.0

            // --- Etapas --------------------------------------------------------
            ensureSpace(60.0)
            run {
                y += 12.0
                drawText("ETAPAS", left, y, ink, 12.0, true)
                y += 8.0
            }
            if (data.stages.isEmpty()) {
                y += 14.0
                drawText("Nenhuma etapa cadastrada.", left + 6.0, y, muted, 10.0, false)
                y += 8.0
            } else {
                for (s in data.stages) {
                    ensureSpace(34.0)
                    y += 12.0
                    val clamped = s.progress.coerceIn(0, 100)
                    drawText(truncate(s.name, right - MARGIN - 120.0, 10.5, true), left, y, ink, 10.5, true)
                    drawRight("${s.statusLabel}  ·  $clamped%", right, y, muted, 9.5, false)
                    y += 6.0
                    y = progressBar(left, y, right, clamped / 100.0, 7.0)
                    s.note?.takeIf { it.isNotBlank() }?.let {
                        y = drawWrapped(it, left, y + 11.0, right - MARGIN, muted, 8.5, 11.0)
                    }
                    y += 8.0
                    drawLine(left, y, right, y, line, 0.5)
                }
            }
            y += 16.0

            // --- Registro fotográfico -----------------------------------------
            if (data.photos.isNotEmpty()) {
                ensureSpace(80.0)
                y += 12.0
                drawText("REGISTRO FOTOGRÁFICO", left, y, ink, 12.0, true)
                y += 8.0

                val colsN = 2
                val gap = 12.0
                val cellW = (right - MARGIN - gap * (colsN - 1)) / colsN
                val imgH = cellW * 0.66
                val captionH = 38.0
                val cellH = imgH + captionH

                var i = 0
                while (i < data.photos.size) {
                    ensureSpace(cellH + 8.0)
                    val rowTop = y
                    for (c in 0 until colsN) {
                        val idx = i + c
                        if (idx >= data.photos.size) break
                        val photo = data.photos[idx]
                        val x = MARGIN + c * (cellW + gap)
                        fillRect(x, rowTop, cellW, imgH, headerBg)
                        drawImageCover(photo.imageBytes, x, rowTop, cellW, imgH)
                        strokeRect(x, rowTop, cellW, imgH, line, 0.5)
                        var ty = rowTop + imgH + 11.0
                        photo.stageName?.takeIf { it.isNotBlank() }?.let {
                            drawText(truncate(it, cellW, 8.5, true), x, ty, ink, 8.5, true); ty += 11.0
                        }
                        photo.caption?.let {
                            drawText(truncate(it, cellW, 8.5, false), x, ty, ink, 8.5, false); ty += 11.0
                        }
                        photo.takenAtLabel?.let {
                            drawText(truncate(it, cellW, 8.0, false), x, ty, muted, 8.0, false)
                        }
                    }
                    y = rowTop + cellH + 10.0
                    i += colsN
                }
                y += 16.0
            }

            // --- Diário de obra ------------------------------------------------
            if (data.diaryEntries.isNotEmpty()) {
                ensureSpace(60.0)
                y += 12.0
                drawText("DIÁRIO DE OBRA", left, y, ink, 12.0, true)
                y += 8.0
                for (d in data.diaryEntries) {
                    ensureSpace(50.0)
                    y += 12.0
                    val header = buildString {
                        append(d.dateLabel)
                        d.weatherLabel?.let { append("  ·  $it") }
                        d.crewLabel?.let { append("  ·  $it") }
                        d.author?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                    }
                    drawText(header, left, y, ink, 10.0, true)
                    y += 4.0
                    y = drawWrapped(d.text, left, y + 10.0, right - MARGIN, muted, 9.5, 13.0)
                    y += 8.0
                    drawLine(left, y, right, y, line, 0.5)
                }
            }

            watermark()
        }

        return nsData.toByteArray()
    }

    private fun rgb(r: Int, g: Int, b: Int): UIColor =
        UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)
}

actual fun createWorkReportPdfGenerator(): WorkReportPdfGenerator =
    IosWorkReportPdfGenerator()

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
