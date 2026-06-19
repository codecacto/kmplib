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
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage

/**
 * Render nativo do PDF de documento estruturado no iOS via `UIGraphicsPDFRenderer`,
 * espelhando o MESMO layout lógico do Android ([AndroidDocumentPdfGenerator]). A4 a
 * 72pt (595 × 842 pt). **Funcional** — não é placeholder; valida em host macOS.
 */
class IosDocumentPdfGenerator : DocumentPdfGenerator {

    private companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
        const val ROW_H = 20.0
        const val HEADER_ROW_H = 22.0
        const val CELL_PAD = 6.0
    }

    private val left = MARGIN
    private val right = PAGE_W - MARGIN

    override fun generate(data: DocumentPdfData): ByteArray {
        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val accent = rgb(0x0F, 0x17, 0x2A)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val zebraBg = rgb(0xFA, 0xFB, 0xFC)
        val totalBg = rgb(0xEF, 0xF3, 0xF8)
        val watermarkColor = rgb(0x1A, 0x1A, 0x1A)

        fun font(size: Double, bold: Boolean): UIFont =
            if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)

        fun attrs(color: UIColor, size: Double, bold: Boolean): Map<Any?, *> = mapOf<Any?, Any?>(
            platform.UIKit.NSFontAttributeName to font(size, bold),
            platform.UIKit.NSForegroundColorAttributeName to color,
        )

        val format = UIGraphicsPDFRendererFormat()
        val bounds: CValue<CGRect> = CGRectMake(0.0, 0.0, PAGE_W, PAGE_H)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val nsData = renderer.PDFDataWithActions { ctx ->
            val pdf = ctx ?: return@PDFDataWithActions
            val state = State(pdf)
            state.beginPage()

            fun cg() = pdf.CGContext

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

            fun drawCenter(text: String, centerX: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
                drawText(text, centerX - textWidth(text, size, bold) / 2.0, baselineY, color, size, bold)
            }

            fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double, color: UIColor, width: Double) {
                val c = cg() ?: return
                CGContextSetStrokeColorWithColor(c, color.CGColor)
                CGContextSetLineWidth(c, width)
                CGContextMoveToPoint(c, x1, y1)
                CGContextAddLineToPoint(c, x2, y2)
                CGContextStrokePath(c)
            }

            fun fillRect(x: Double, y: Double, w: Double, h: Double, color: UIColor) {
                val c = cg() ?: return
                CGContextSetFillColorWithColor(c, color.CGColor)
                CGContextFillRect(c, CGRectMake(x, y, w, h))
            }

            fun drawImage(bytes: ByteArray, x: Double, yTop: Double, boxW: Double, boxH: Double) {
                val img = UIImage.imageWithData(bytes.toNSData()) ?: return
                val (iw, ih) = img.size.useContents { width to height }
                if (iw <= 0.0 || ih <= 0.0) return
                val ratio = minOf(boxW / iw, boxH / ih)
                img.drawInRect(CGRectMake(x, yTop, iw * ratio, ih * ratio))
            }

            fun watermark() {
                if (!data.watermark || data.watermarkText.isBlank()) return
                val c = cg() ?: return
                CGContextSaveGState(c)
                CGContextSetAlpha(c, 0.12)
                CGContextTranslateCTM(c, PAGE_W / 2.0, PAGE_H / 2.0)
                CGContextConcatCTM(c, CGAffineTransformMakeRotation(-0.7853981634))
                val w = textWidth(data.watermarkText, 54.0, true)
                drawText(data.watermarkText, -w / 2.0, font(54.0, true).capHeight / 2.0, watermarkColor, 54.0, true)
                CGContextRestoreGState(c)
            }

            fun newPage() {
                watermark()
                state.beginPage()
            }

            fun ensureSpace(needed: Double): Boolean {
                if (state.y <= PAGE_H - MARGIN - needed) return false
                newPage()
                return true
            }

            fun columnX(columns: List<DocumentColumn>): List<Triple<Double, Double, DocumentAlign>> {
                if (columns.isEmpty()) return emptyList()
                val usable = right - left
                val total = columns.sumOf { it.weight.coerceAtLeast(0.0001f).toDouble() }
                val res = ArrayList<Triple<Double, Double, DocumentAlign>>(columns.size)
                var x = left
                for (col in columns) {
                    val w = usable * (col.weight.coerceAtLeast(0.0001f).toDouble() / total)
                    res.add(Triple(x, x + w, col.align))
                    x += w
                }
                return res
            }

            fun drawAligned(text: String, xs: Double, xe: Double, align: DocumentAlign, baseline: Double, color: UIColor, size: Double, bold: Boolean) {
                when (align) {
                    DocumentAlign.START -> drawText(text, xs + CELL_PAD, baseline, color, size, bold)
                    DocumentAlign.CENTER -> drawCenter(text, (xs + xe) / 2.0, baseline, color, size, bold)
                    DocumentAlign.END -> drawRight(text, xe - CELL_PAD, baseline, color, size, bold)
                }
            }

            fun drawTableHeader(columns: List<DocumentColumn>, cols: List<Triple<Double, Double, DocumentAlign>>): Double {
                fillRect(left, state.y, right - left, HEADER_ROW_H, headerBg)
                val baseline = state.y + 15.0
                columns.forEachIndexed { i, col -> drawAligned(col.label, cols[i].first, cols[i].second, cols[i].third, baseline, muted, 9.0, true) }
                state.y += HEADER_ROW_H
                drawLine(left, state.y, right, state.y, line, 1.0)
                return state.y
            }

            fun wrapText(text: String, x: Double, baselineStart: Double, size: Double, lineHeight: Double): Double {
                var y = baselineStart
                val maxW = right - x
                text.split("\n").forEach { para ->
                    val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val sb = StringBuilder()
                    for (w in words) {
                        val candidate = if (sb.isEmpty()) w else "$sb $w"
                        if (textWidth(candidate, size, false) > maxW && sb.isNotEmpty()) {
                            drawText(sb.toString(), x, y, ink, size, false)
                            y += lineHeight
                            sb.clear(); sb.append(w)
                        } else {
                            sb.clear(); sb.append(candidate)
                        }
                    }
                    if (sb.isNotEmpty()) { drawText(sb.toString(), x, y, ink, size, false); y += lineHeight }
                }
                return y
            }

            // ---- Header ----
            var textX = left
            data.company.logoBytes?.let { drawImage(it, left, MARGIN, 64.0, 64.0); textX = left + 64.0 + 14.0 }
            var hy = MARGIN + 16.0
            drawText(data.company.name, textX, hy, ink, 18.0, true)
            listOfNotNull(data.company.phone, data.company.email, data.company.address).forEach { hy += 14.0; drawText(it, textX, hy, muted, 10.0, false) }
            var ry = MARGIN + 16.0
            drawRight(data.title, right, ry, accent, 16.0, true)
            data.subtitle?.takeIf { it.isNotBlank() }?.let { ry += 14.0; drawRight(it, right, ry, muted, 10.0, false) }
            var bottom = maxOf(hy, ry) + 12.0
            drawLine(left, bottom, right, bottom, line, 1.0)
            bottom += 6.0
            if (data.headerInfo.isNotEmpty()) {
                bottom += 8.0
                data.headerInfo.forEach { row ->
                    drawText(row.label, left, bottom + 11.0, muted, 9.0, true)
                    drawText(row.value, left + 130.0, bottom + 11.0, ink, 10.0, false)
                    bottom += 16.0
                }
            }
            state.y = bottom

            fun sectionTitle(title: String?) {
                title?.takeIf { it.isNotBlank() }?.let {
                    ensureSpace(28.0)
                    state.y += 4.0
                    drawText(it.uppercase(), left, state.y + 10.0, accent, 11.0, true)
                    state.y += 18.0
                }
            }

            // ---- Sections ----
            data.sections.forEach { section ->
                state.y += 14.0
                when (section) {
                    is DocumentSection.Info -> {
                        sectionTitle(section.title)
                        if (section.rows.isEmpty()) {
                            ensureSpace(ROW_H); drawText(section.emptyText, left, state.y + 12.0, muted, 10.0, false); state.y += ROW_H
                        } else section.rows.forEach { row ->
                            ensureSpace(18.0)
                            drawText(row.label, left, state.y + 11.0, muted, 9.0, true)
                            drawText(row.value, left + 150.0, state.y + 11.0, ink, 10.0, false)
                            state.y += 16.0
                        }
                    }
                    is DocumentSection.Table -> {
                        sectionTitle(section.title)
                        val cols = columnX(section.columns)
                        ensureSpace(HEADER_ROW_H + ROW_H)
                        drawTableHeader(section.columns, cols)
                        if (section.rows.isEmpty()) {
                            drawText(section.emptyText, left + CELL_PAD, state.y + 14.0, muted, 10.0, false); state.y += ROW_H
                        } else section.rows.forEachIndexed { index, row ->
                            if (ensureSpace(ROW_H)) drawTableHeader(section.columns, cols)
                            if (index % 2 == 1) fillRect(left, state.y, right - left, ROW_H, zebraBg)
                            val baseline = state.y + 14.0
                            section.columns.indices.forEach { i ->
                                drawAligned(row.cells.getOrNull(i).orEmpty(), cols[i].first, cols[i].second, cols[i].third, baseline, ink, 10.0, false)
                            }
                            state.y += ROW_H
                            drawLine(left, state.y, right, state.y, line, 0.5)
                        }
                    }
                    is DocumentSection.Cards -> {
                        sectionTitle(section.title)
                        if (section.cards.isEmpty()) {
                            ensureSpace(ROW_H); drawText(section.emptyText, left, state.y + 12.0, muted, 10.0, false); state.y += ROW_H
                        } else {
                            val cardH = 48.0; val gap = 8.0; val perRow = 3
                            var i = 0
                            while (i < section.cards.size) {
                                ensureSpace(cardH + gap)
                                val rowCards = section.cards.subList(i, minOf(i + perRow, section.cards.size))
                                val cardW = (right - left - gap * (perRow - 1)) / perRow
                                rowCards.forEachIndexed { idx, card ->
                                    val x = left + idx * (cardW + gap)
                                    fillRect(x, state.y, cardW, cardH, headerBg)
                                    drawText(card.label, x + 8.0, state.y + 16.0, muted, 9.0, true)
                                    drawText(card.value, x + 8.0, state.y + 38.0, ink, 16.0, true)
                                }
                                state.y += cardH + gap
                                i += perRow
                            }
                        }
                    }
                    is DocumentSection.Paragraph -> {
                        sectionTitle(section.title)
                        ensureSpace(40.0)
                        state.y = wrapText(section.text, left, state.y + 12.0, 10.5, 14.0)
                    }
                    is DocumentSection.Total -> {
                        sectionTitle(section.title)
                        ensureSpace(38.0)
                        val boxTop = state.y + 4.0; val boxH = 30.0
                        fillRect(left, boxTop, right - left, boxH, totalBg)
                        drawText(section.label.uppercase(), left + 10.0, boxTop + 20.0, muted, 10.0, true)
                        drawRight(section.value, right - 10.0, boxTop + 21.0, accent, 16.0, true)
                        state.y = boxTop + boxH + 4.0
                    }
                }
            }

            data.footer?.takeIf { it.isNotBlank() }?.let {
                val y = PAGE_H - MARGIN + 8.0
                drawLine(left, y - 16.0, right, y - 16.0, line, 1.0)
                drawText(it, left, y, muted, 9.0, false)
            }

            watermark()
        }

        return nsData.toByteArray()
    }

    /** Estado de paginação: `beginPage()` reseta o cursor para o topo. */
    private inner class State(val ctx: platform.UIKit.UIGraphicsPDFRendererContext) {
        var y = MARGIN
        fun beginPage() {
            ctx.beginPage()
            y = MARGIN
        }
    }

    private fun rgb(r: Int, g: Int, b: Int): UIColor = UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)
}

actual fun createDocumentPdfGenerator(): DocumentPdfGenerator = IosDocumentPdfGenerator()

// --- ByteArray <-> NSData (locais ao arquivo; espelham os helpers do ReciboPdf.ios) ---

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
