@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextFillEllipseInRect
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
import platform.CoreGraphics.CGPoint
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
 * Render do PDF da **carteira de vacinação** no iOS via `UIGraphicsPDFRenderer` (UIKit), com
 * **paridade de layout** com o Android ([AndroidVaccinationCardPdfGenerator]). Modelado no
 * `ReciboPdf.ios.kt`, que já usa `UIGraphicsPDFRenderer` em produção — portanto NÃO é placeholder.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** este `actual` foi escrito por construção para compilar e
 * rodar em iOS, mas o ambiente de build atual é Windows (targets iOS desabilitados; a kmplib não
 * publica artefatos iOS no mavenLocal aqui). A **validação visual** do PDF no iOS precisa ser feita
 * em host macOS/CI. Ver o handoff do lib-mobile (não foi validado visualmente neste ambiente).
 *
 * Coordenadas em pontos (A4 = 595 x 842 pt, mesma grade do Android). O `drawAtPoint` do iOS ancora
 * o TOPO da caixa de texto; as constantes de baseline foram ajustadas para casar com o Android.
 */
class IosVaccinationCardPdfGenerator : VaccinationCardPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        const val ROW_H = 22.0
        const val HEADER_ROW_H = 22.0
        const val CELL_PAD = 6.0
        const val STATUS_DOT_R = 3.0
    }

    override fun generate(data: VaccinationCardPdfData): ByteArray {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN

        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val zebraBg = rgb(0xFA, 0xFB, 0xFC)
        val noticeBg = rgb(0xFF, 0xF6, 0xE5)
        val noticeBorder = rgb(0xE0, 0xB8, 0x4D)
        val noticeText = rgb(0x7A, 0x5A, 0x12)
        val watermarkColor = rgb(0x1A, 0x1A, 0x1A)

        fun font(size: Double, bold: Boolean): UIFont =
            if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)

        fun attrs(color: UIColor, size: Double, bold: Boolean): Map<Any?, *> =
            mapOf<Any?, Any?>(
                NSFontAttributeName to font(size, bold),
                NSForegroundColorAttributeName to color,
            )

        val format = UIGraphicsPDFRendererFormat()
        val bounds: CValue<CGRect> = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        // Larguras das colunas (peso → largura).
        val colBounds = columnBounds(data.columns, left, right)

        val nsData = renderer.PDFDataWithActions { ctx ->
            val cg = ctx?.CGContext

            fun beginPage() = ctx?.beginPage()
            beginPage()

            fun drawText(text: String, x: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
                val s = NSString.create(string = text)
                val ascent = font(size, bold).ascender
                // Converte baseline (grade Android) → topo da caixa que o iOS ancora.
                val topY = baselineY - ascent
                s.drawAtPoint(point(x, topY), withAttributes = attrs(color, size, bold))
            }

            fun textWidth(text: String, size: Double, bold: Boolean): Double {
                val s = NSString.create(string = text)
                return s.sizeWithAttributes(attrs(ink, size, bold)).useContents { width }
            }

            fun drawAligned(text: String, cb: ColBoundIos, baseline: Double, color: UIColor, size: Double, bold: Boolean, indent: Double) {
                when (cb.align) {
                    VaccinationAlign.START -> drawText(text, cb.xStart + CELL_PAD + indent, baseline, color, size, bold)
                    VaccinationAlign.CENTER -> {
                        val w = textWidth(text, size, bold)
                        drawText(text, (cb.xStart + cb.xEnd) / 2.0 - w / 2.0, baseline, color, size, bold)
                    }
                    VaccinationAlign.END -> {
                        val w = textWidth(text, size, bold)
                        drawText(text, cb.xEnd - CELL_PAD - w, baseline, color, size, bold)
                    }
                }
            }

            fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double, color: UIColor, width: Double) {
                cg ?: return
                CGContextSetStrokeColorWithColor(cg, color.CGColor)
                CGContextSetLineWidth(cg, width)
                CGContextMoveToPoint(cg, x1, y1)
                CGContextAddLineToPoint(cg, x2, y2)
                CGContextStrokePath(cg)
            }

            fun fillRect(x: Double, y: Double, w: Double, h: Double, color: UIColor) {
                cg ?: return
                CGContextSetFillColorWithColor(cg, color.CGColor)
                CGContextFillRect(cg, CGRectMake(x, y, w, h))
            }

            fun drawTruncated(text: String, cb: ColBoundIos, baseline: Double, color: UIColor, size: Double, indent: Double) {
                val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD - indent
                val shown = truncateToWidth(text, maxW) { t -> textWidth(t, size, false) }
                drawAligned(shown, cb, baseline, color, size, bold = false, indent = indent)
            }

            fun drawTableHeader(top: Double): Double {
                fillRect(left, top, right - left, HEADER_ROW_H, headerBg)
                val baseline = top + 15.0
                data.columns.forEachIndexed { i, col ->
                    drawAligned(col.label, colBounds[i], baseline, muted, 9.0, bold = true, indent = 0.0)
                }
                val y = top + HEADER_ROW_H
                drawLine(left, y, right, y, line, 1.0)
                return y
            }

            // Marca d'água -45° (centro da página). Lambda local que captura o contexto/desenho,
            // evitando tipar o CGContext na assinatura (mais robusto entre versões do K/N).
            fun drawWatermarkOnPage() {
                val text = data.watermarkText
                if (!data.watermark || text.isNullOrBlank()) return
                cg ?: return
                val cx = PAGE_WIDTH / 2.0
                val cy = PAGE_HEIGHT / 2.0
                CGContextSaveGState(cg)
                CGContextSetAlpha(cg, 0.12)
                CGContextTranslateCTM(cg, cx, cy)
                CGContextConcatCTM(cg, CGAffineTransformMakeRotation(-0.7853981634)) // -45°
                val w = textWidth(text, 54.0, true)
                val baselineOffset = font(54.0, true).capHeight / 2.0
                drawText(text, -w / 2.0, baselineOffset, watermarkColor, 54.0, true)
                CGContextRestoreGState(cg)
            }

            // Estado de página (paginação simples).
            var y = MARGIN
            val pageBottom = PAGE_HEIGHT - MARGIN

            fun ensureSpace(needed: Double): Boolean {
                if (y <= pageBottom - needed) return false
                // watermark da página atual
                drawWatermarkOnPage()
                beginPage()
                y = MARGIN
                if (data.columns.isNotEmpty()) y = drawTableHeader(y)
                return true
            }

            // --- Cabeçalho -------------------------------------------------------
            var textX = left
            val top = y
            data.logoBytes?.let { bytes ->
                val img = UIImage.imageWithData(bytes.toNSData())
                if (img != null) {
                    val (iw, ih) = img.size.useContents { width to height }
                    if (iw > 0.0 && ih > 0.0) {
                        val ratio = minOf(64.0 / iw, 64.0 / ih, 1.0)
                        val w = iw * ratio; val h = ih * ratio
                        img.drawInRect(CGRectMake(left, top, w, h))
                        textX = left + w + 14.0
                    }
                }
            }
            var ly = top + 16.0
            drawText(data.title, textX, ly, ink, 18.0, bold = true)
            ly += 16.0
            drawText(data.holder.name, textX, ly, ink, 13.0, bold = true)

            var ry = top + 16.0
            data.subtitle?.takeIf { it.isNotBlank() }?.let {
                val w = textWidth(it, 10.0, false)
                drawText(it, right - w, ry, muted, 10.0, bold = false)
                ry += 14.0
            }
            run {
                val w = textWidth(data.generatedAtLabel, 10.0, false)
                drawText(data.generatedAtLabel, right - w, ry, muted, 10.0, bold = false)
            }
            val headerBottom = maxOf(ly, ry) + 12.0
            drawLine(left, headerBottom, right, headerBottom, line, 1.0)
            y = headerBottom + 6.0 + 12.0

            // --- Bloco do titular -----------------------------------------------
            for (hl in data.holder.lines) {
                y += 14.0
                drawText("${hl.label}:", left, y, muted, 9.5, bold = true)
                drawText(hl.value, left + 110.0, y, ink, 10.5, bold = false)
            }
            if (data.holder.lines.isNotEmpty()) y += 4.0

            // --- Caixa de aviso (comprovante não-oficial) -----------------------
            data.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                y += 10.0
                val pad = 8.0
                val maxW = (right - left) - 2 * pad
                val lines = wrapLines(notice, maxW) { t -> textWidth(t, 9.5, true) }
                val lineHeight = 12.0
                val boxH = lines.size * lineHeight + 2 * pad
                ensureSpace(boxH + 8.0)
                cg?.let {
                    CGContextSetFillColorWithColor(it, noticeBg.CGColor)
                    CGContextFillRect(it, CGRectMake(left, y, right - left, boxH))
                    CGContextSetStrokeColorWithColor(it, noticeBorder.CGColor)
                    CGContextSetLineWidth(it, 1.0)
                    CGContextStrokeRect(it, CGRectMake(left, y, right - left, boxH))
                }
                var ny = y + pad + 10.0
                for (l in lines) {
                    drawText(l, left + pad, ny, noticeText, 9.5, bold = true)
                    ny += lineHeight
                }
                y += boxH
            }

            y += 14.0

            // --- Tabela ----------------------------------------------------------
            if (data.columns.isNotEmpty()) {
                y = drawTableHeader(y)
                if (data.items.isEmpty()) {
                    ensureSpace(ROW_H)
                    drawText(data.emptyText, left + CELL_PAD, y + 14.0, muted, 10.0, bold = false)
                    y += ROW_H
                } else {
                    data.items.forEachIndexed { index, item ->
                        ensureSpace(ROW_H)
                        if (index % 2 == 1) fillRect(left, y, right - left, ROW_H, zebraBg)
                        val baseline = y + 15.0
                        val color = if (item.muted) muted else ink

                        var firstIndent = 0.0
                        item.statusColorArgb?.let { argb ->
                            colBounds.firstOrNull()?.let { cb ->
                                cg?.let {
                                    CGContextSetFillColorWithColor(it, argbToColor(argb).CGColor)
                                    val cx = cb.xStart + CELL_PAD
                                    val cy = y + ROW_H / 2.0 - STATUS_DOT_R
                                    CGContextFillEllipseInRect(it, CGRectMake(cx, cy, STATUS_DOT_R * 2, STATUS_DOT_R * 2))
                                }
                                firstIndent = STATUS_DOT_R * 2 + 4.0
                            }
                        }

                        data.columns.indices.forEach { i ->
                            val raw = item.cells.getOrNull(i).orEmpty()
                            val indent = if (i == 0) firstIndent else 0.0
                            drawTruncated(raw, colBounds[i], baseline, color, 10.0, indent)
                        }
                        val ny = y + ROW_H
                        drawLine(left, ny, right, ny, line, 0.5)
                        y = ny
                    }
                }
            }

            // --- Rodapé ----------------------------------------------------------
            data.footer?.takeIf { it.isNotBlank() }?.let { footer ->
                ensureSpace(30.0)
                y += 14.0
                val lines = wrapLines(footer, right - left) { t -> textWidth(t, 9.0, false) }
                for (l in lines) {
                    drawText(l, left, y, muted, 9.0, bold = false)
                    y += 12.0
                }
            }

            // Data de geração ancorada ao pé da página.
            val gy = PAGE_HEIGHT - MARGIN + 8.0
            drawLine(left, gy - 16.0, right, gy - 16.0, line, 1.0)
            drawText(data.generatedAtLabel, left, gy, muted, 9.0, bold = false)

            // Marca d'água da última página.
            drawWatermarkOnPage()
        }

        return nsData.toByteArray()
    }

    // --- Layout helpers ------------------------------------------------------

    private data class ColBoundIos(val xStart: Double, val xEnd: Double, val align: VaccinationAlign)

    private fun columnBounds(columns: List<VaccinationColumn>, left: Double, right: Double): List<ColBoundIos> {
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
}

// --- Funções puras compartilhadas (wrap/truncate) ----------------------------

private fun wrapLines(text: String, maxW: Double, measure: (String) -> Double): List<String> {
    if (maxW <= 0.0) return listOf(text)
    val words = text.split(Regex("\\s+"))
    val out = ArrayList<String>()
    var line = StringBuilder()
    for (w in words) {
        val candidate = if (line.isEmpty()) w else "$line $w"
        if (measure(candidate) > maxW && line.isNotEmpty()) {
            out.add(line.toString())
            line = StringBuilder(w)
        } else {
            line = StringBuilder(candidate)
        }
    }
    if (line.isNotEmpty()) out.add(line.toString())
    return out.ifEmpty { listOf("") }
}

private fun truncateToWidth(text: String, maxW: Double, measure: (String) -> Double): String {
    if (maxW <= 0.0) return ""
    if (measure(text) <= maxW) return text
    val ellipsis = "…"
    var end = text.length
    while (end > 0 && measure(text.substring(0, end) + ellipsis) > maxW) end--
    return text.substring(0, end).trimEnd() + ellipsis
}

// --- Cor/bytes helpers (file-private; espelham ReciboPdf.ios.kt) --------------

private fun rgb(r: Int, g: Int, b: Int): UIColor =
    UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)

/** Converte um inteiro ARGB (0xAARRGGBB) para [UIColor]. */
private fun argbToColor(argb: Int): UIColor {
    val a = ((argb ushr 24) and 0xFF) / 255.0
    val r = ((argb ushr 16) and 0xFF) / 255.0
    val g = ((argb ushr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val alpha = if (a == 0.0) 1.0 else a
    return UIColor.colorWithRed(r, g, b, alpha)
}

private fun point(x: Double, y: Double): CValue<CGPoint> =
    platform.CoreGraphics.CGPointMake(x, y)

private fun ByteArray.toNSData(): NSData = kotlinx.cinterop.memScoped {
    if (isEmpty()) return@memScoped NSData.create(bytes = null, length = 0u)
    val ptr = allocArray<kotlinx.cinterop.ByteVar>(size)
    for (i in indices) ptr[i] = this@toNSData[i]
    NSData.create(bytes = ptr, length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val out = ByteArray(length)
    if (length == 0) return out
    kotlinx.cinterop.memScoped {
        val buffer = allocArray<kotlinx.cinterop.ByteVar>(length)
        platform.posix.memcpy(buffer, this@toByteArray.bytes, this@toByteArray.length)
        for (i in 0 until length) out[i] = buffer[i]
    }
    return out
}

actual fun createVaccinationCardPdfGenerator(): VaccinationCardPdfGenerator =
    IosVaccinationCardPdfGenerator()
