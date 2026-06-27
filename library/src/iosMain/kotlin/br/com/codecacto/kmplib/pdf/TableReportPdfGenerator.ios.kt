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
 * Render nativo do PDF de **tabela genérico** (não-financeiro) no iOS via
 * `UIGraphicsPDFRenderer` (UIKit), espelhando o MESMO layout lógico do Android
 * ([AndroidTableReportPdfGenerator]): cabeçalho do documento, linha de cabeçalho da tabela
 * (negrito, fundo neutro), linhas com **zebra** e strokes leves, colunas ponderadas com
 * alinhamento por coluna, **paginação automática** (cabeçalho da tabela repetido no topo de
 * cada página), resumo textual, rodapé e marca d'água -45°. **Sem nenhum bloco monetário.**
 * A4 a 72pt (595 × 842 pt).
 *
 * Modelado nos geradores iOS já em produção (`VaccinationCardPdfGenerator.ios.kt`,
 * `DocumentPdfGenerator.ios.kt`) — NÃO é placeholder.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** ambiente atual Linux (targets Apple SKIPPED).
 */
class IosTableReportPdfGenerator : TableReportPdfGenerator {

    private companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
        const val ROW_H = 20.0
        const val HEADER_ROW_H = 22.0
        const val CELL_PAD = 6.0
    }

    private data class ColBoundIos(val xStart: Double, val xEnd: Double, val align: TableReportAlign)

    private fun columnBounds(columns: List<TableReportColumn>, left: Double, right: Double): List<ColBoundIos> {
        if (columns.isEmpty()) return emptyList()
        val usable = right - left
        val totalWeight = columns.sumOf { it.weight.coerceAtLeast(0.0001f).toDouble() }
        val result = ArrayList<ColBoundIos>(columns.size)
        var x = left
        for (col in columns) {
            val w = usable * (col.weight.coerceAtLeast(0.0001f).toDouble() / totalWeight)
            result.add(ColBoundIos(x, x + w, col.align))
            x += w
        }
        return result
    }

    override fun generate(data: TableReportPdfData): ByteArray {
        val left = MARGIN
        val right = PAGE_W - MARGIN

        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val zebraBg = rgb(0xFA, 0xFB, 0xFC)
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

        val cols = columnBounds(data.columns, left, right)

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

            fun truncate(text: String, maxW: Double, size: Double): String {
                if (maxW <= 0.0) return ""
                if (textWidth(text, size, false) <= maxW) return text
                val ell = "…"
                var end = text.length
                while (end > 0 && textWidth(text.substring(0, end) + ell, size, false) > maxW) end--
                return text.substring(0, end).trimEnd() + ell
            }

            fun drawAligned(text: String, cb: ColBoundIos, baseline: Double, color: UIColor, size: Double, bold: Boolean) {
                when (cb.align) {
                    TableReportAlign.START -> drawText(text, cb.xStart + CELL_PAD, baseline, color, size, bold)
                    TableReportAlign.CENTER -> {
                        val w = textWidth(text, size, bold)
                        drawText(text, (cb.xStart + cb.xEnd) / 2.0 - w / 2.0, baseline, color, size, bold)
                    }
                    TableReportAlign.END -> {
                        val w = textWidth(text, size, bold)
                        drawText(text, cb.xEnd - CELL_PAD - w, baseline, color, size, bold)
                    }
                }
            }

            fun drawWatermarkOnPage() {
                if (!data.watermark || data.watermarkText.isBlank()) return
                cg ?: return
                CGContextSaveGState(cg)
                CGContextSetAlpha(cg, 0.12)
                CGContextTranslateCTM(cg, PAGE_W / 2.0, PAGE_H / 2.0)
                CGContextConcatCTM(cg, CGAffineTransformMakeRotation(-0.7853981634)) // -45°
                val w = textWidth(data.watermarkText, 54.0, true)
                drawText(data.watermarkText, -w / 2.0, font(54.0, true).capHeight / 2.0, watermarkColor, 54.0, true)
                CGContextRestoreGState(cg)
            }

            fun drawTableHeader(): Double {
                fillRect(left, y, right - left, HEADER_ROW_H, headerBg)
                val baseline = y + 15.0
                data.columns.forEachIndexed { i, col -> drawAligned(col.label, cols[i], baseline, muted, 9.0, true) }
                y += HEADER_ROW_H
                drawLine(left, y, right, y, line, 1.0)
                return y
            }

            // Retorna true se trocou de página (e redesenhou o cabeçalho da tabela).
            fun ensureSpace(needed: Double): Boolean {
                if (y <= PAGE_H - MARGIN - needed) return false
                drawWatermarkOnPage()
                pdf.beginPage()
                y = MARGIN
                drawTableHeader()
                return true
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
                listOfNotNull(data.company.phone, data.company.email, data.company.address).forEach {
                    hy += 14.0; drawText(it, textX, hy, muted, 10.0, false)
                }
                var ry = MARGIN + 16.0
                drawText(data.title, right - textWidth(data.title, 14.0, true), ry, ink, 14.0, true)
                data.subtitle?.takeIf { it.isNotBlank() }?.let {
                    ry += 14.0
                    drawText(it, right - textWidth(it, 10.0, false), ry, muted, 10.0, false)
                }
                val bottom = maxOf(hy, ry) + 12.0
                drawLine(left, bottom, right, bottom, line, 1.0)
                y = bottom + 6.0
            }
            y += 14.0

            // --- Tabela --------------------------------------------------------
            drawTableHeader()
            if (data.rows.isEmpty()) {
                ensureSpace(ROW_H)
                drawText(data.emptyText, left + CELL_PAD, y + 14.0, muted, 10.0, false)
                y += ROW_H
            } else {
                data.rows.forEachIndexed { index, row ->
                    ensureSpace(ROW_H)
                    if (index % 2 == 1) fillRect(left, y, right - left, ROW_H, zebraBg)
                    val baseline = y + 14.0
                    data.columns.indices.forEach { i ->
                        val cb = cols[i]
                        val raw = row.cells.getOrNull(i).orEmpty()
                        val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD
                        drawAligned(truncate(raw, maxW, 10.0), cb, baseline, ink, 10.0, false)
                    }
                    y += ROW_H
                    drawLine(left, y, right, y, line, 0.5)
                }
            }

            // --- Resumo --------------------------------------------------------
            data.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                ensureSpace(40.0)
                y += 16.0
                drawText("RESUMO", left, y, muted, 9.0, true)
                y += 14.0
                val maxW = right - left
                val words = summary.split(Regex("\\s+"))
                val lineSb = StringBuilder()
                for (w in words) {
                    val candidate = if (lineSb.isEmpty()) w else "$lineSb $w"
                    if (textWidth(candidate, 10.5, false) > maxW && lineSb.isNotEmpty()) {
                        drawText(lineSb.toString(), left, y, ink, 10.5, false)
                        y += 13.0
                        lineSb.clear(); lineSb.append(w)
                    } else {
                        lineSb.clear(); lineSb.append(candidate)
                    }
                }
                if (lineSb.isNotEmpty()) { drawText(lineSb.toString(), left, y, ink, 10.5, false); y += 13.0 }
            }

            // --- Rodapé (ancorado ao pé) --------------------------------------
            data.footer?.takeIf { it.isNotBlank() }?.let { footer ->
                val fy = PAGE_H - MARGIN + 8.0
                drawLine(left, fy - 16.0, right, fy - 16.0, line, 1.0)
                drawText(footer, left, fy, muted, 9.0, false)
            }

            drawWatermarkOnPage()
        }

        return nsData.toByteArray()
    }

    private fun rgb(r: Int, g: Int, b: Int): UIColor =
        UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)
}

actual fun createTableReportPdfGenerator(): TableReportPdfGenerator =
    IosTableReportPdfGenerator()

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
