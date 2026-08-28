package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF da carteira/cronograma de vacinação no iOS (`UIGraphicsPDFRenderer` +
 * CoreText), paridade com [AndroidVaccinationCardPdfGenerator]: cabeçalho + bloco do titular + caixa
 * de aviso opcional + tabela de itens (ponto de status colorido opcional, zebra, paginação) + rodapé
 * + marca d'água.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS)** — espelha o par Android; build iOS não roda em Linux.
 */
private class IosVaccinationCardPdfGenerator : VaccinationCardPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_ZEBRA_BG = PdfColor.argb(0xFFFAFBFC)
        val COLOR_NOTICE_BG = PdfColor.argb(0xFFFFF6E5)
        val COLOR_NOTICE_BORDER = PdfColor.argb(0xFFE0B84D)
        val COLOR_NOTICE_TEXT = PdfColor.argb(0xFF7A5A12)

        const val ROW_H = 22.0
        const val HEADER_ROW_H = 22.0
        const val CELL_PAD = 6.0
        const val STATUS_DOT_R = 3.0
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    private data class ColBound(val xStart: Double, val xEnd: Double, val align: VaccinationAlign)

    override fun generate(data: VaccinationCardPdfData): ByteArray {
        val watermark = if (data.watermark) data.watermarkText else null
        val bounds = columnBounds(data.columns)
        return renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark) {
            var y = MARGIN
            y = drawHeader(canvas, data, y)
            y += 12.0
            y = drawHolderBlock(canvas, data.holder, y)

            data.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                y += 10.0
                y = ensureSpace(this, y, 48.0, data.columns, bounds)
                y = drawNotice(canvas, notice, y)
            }

            y += 14.0

            if (data.columns.isNotEmpty()) {
                y = drawTableHeader(canvas, data.columns, bounds, y)
                if (data.items.isEmpty()) {
                    y = ensureSpace(this, y, ROW_H, data.columns, bounds)
                    canvas.text(data.emptyText, left + CELL_PAD, y + 14.0, 10.0, bold = false, color = COLOR_MUTED)
                    y += ROW_H
                } else {
                    data.items.forEachIndexed { index, item ->
                        y = ensureSpace(this, y, ROW_H, data.columns, bounds)
                        y = drawItemRow(canvas, data.columns, bounds, item, y, zebra = index % 2 == 1)
                    }
                }
            }

            data.footer?.takeIf { it.isNotBlank() }?.let { footer ->
                y = ensureSpace(this, y, 30.0, data.columns, bounds)
                y += 14.0
                y = canvas.wrappedText(footer, left, y, right - left, 9.0, bold = false, color = COLOR_MUTED, lineHeight = 12.0)
            }

            drawGeneratedAt(canvas, data.generatedAtLabel)
        }
    }

    /** Garante espaço; em página nova redesenha o cabeçalho da tabela. Retorna o novo y. */
    private fun ensureSpace(flow: IosPageFlow, y: Double, needed: Double, columns: List<VaccinationColumn>, bounds: List<ColBound>): Double {
        if (y <= PAGE_HEIGHT - MARGIN - needed) return y
        flow.newPage()
        return if (columns.isNotEmpty()) drawTableHeader(flow.canvas, columns, bounds, MARGIN) else MARGIN
    }

    private fun columnBounds(columns: List<VaccinationColumn>): List<ColBound> {
        if (columns.isEmpty()) return emptyList()
        val usable = right - left
        val totalWeight = columns.sumOf { it.weight.coerceAtLeast(0.0001f).toDouble() }
        val result = ArrayList<ColBound>(columns.size)
        var x = left
        for (col in columns) {
            val w = usable * (col.weight.coerceAtLeast(0.0001f) / totalWeight)
            result.add(ColBound(x, x + w, col.align))
            x += w
        }
        return result
    }

    private fun drawHeader(c: IosPdfCanvas, data: VaccinationCardPdfData, top: Double): Double {
        var textX = MARGIN
        var y = top
        data.logoBytes?.let {
            c.image(it, MARGIN, top, 64.0, 64.0)
            textX = MARGIN + 64.0 + 14.0
        }
        y += 16.0
        c.text(data.title, textX, y, 18.0, bold = true, color = COLOR_TEXT)
        y += 16.0
        c.text(data.holder.name, textX, y, 13.0, bold = true, color = COLOR_TEXT)

        var ry = top + 16.0
        data.subtitle?.takeIf { it.isNotBlank() }?.let {
            c.text(it, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
            ry += 14.0
        }
        c.text(data.generatedAtLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
    }

    private fun drawHolderBlock(c: IosPdfCanvas, holder: VaccinationHolder, top: Double): Double {
        if (holder.lines.isEmpty()) return top
        var y = top
        for (line in holder.lines) {
            y += 14.0
            c.text("${line.label}:", MARGIN, y, 9.5, bold = true, color = COLOR_MUTED)
            c.text(line.value, MARGIN + 110.0, y, 10.5, bold = false, color = COLOR_TEXT)
        }
        return y + 4.0
    }

    private fun drawNotice(c: IosPdfCanvas, notice: String, top: Double): Double {
        val pad = 8.0
        val measured = c.measureWrappedHeight(notice, right - MARGIN - 2 * pad, 9.5, bold = true, lineHeight = 12.0)
        val boxTop = top
        val boxBottom = boxTop + measured + 2 * pad
        c.fillRoundRect(left, boxTop, right - left, boxBottom - boxTop, 6.0, COLOR_NOTICE_BG)
        c.strokeRoundRect(left, boxTop, right - left, boxBottom - boxTop, 6.0, COLOR_NOTICE_BORDER, 1.0)
        c.wrappedText(notice, left + pad, boxTop + pad + 10.0, right - pad - (left + pad), 9.5, bold = true, color = COLOR_NOTICE_TEXT, lineHeight = 12.0)
        return boxBottom
    }

    private fun drawTableHeader(c: IosPdfCanvas, columns: List<VaccinationColumn>, bounds: List<ColBound>, top: Double): Double {
        c.fillRect(left, top, right - left, HEADER_ROW_H, COLOR_HEADER_BG)
        val baseline = top + 15.0
        columns.forEachIndexed { i, col ->
            drawAligned(c, col.label, bounds[i], baseline, 9.0, bold = true, color = COLOR_MUTED, indent = 0.0)
        }
        val y = top + HEADER_ROW_H
        c.line(left, y, right, y, COLOR_LINE, 1.0)
        return y
    }

    private fun drawItemRow(c: IosPdfCanvas, columns: List<VaccinationColumn>, bounds: List<ColBound>, item: VaccinationItem, top: Double, zebra: Boolean): Double {
        if (zebra) c.fillRect(left, top, right - left, ROW_H, COLOR_ZEBRA_BG)
        val baseline = top + 15.0
        val textColor = if (item.muted) COLOR_MUTED else COLOR_TEXT

        var firstIndent = 0.0
        item.statusColorArgb?.let { argb ->
            val cb = bounds.firstOrNull() ?: return@let
            val cx = cb.xStart + CELL_PAD + STATUS_DOT_R
            val cy = top + ROW_H / 2.0
            c.fillCircle(cx, cy, STATUS_DOT_R, PdfColor.argb(argb.toLong() and 0xFFFFFFFFL))
            firstIndent = STATUS_DOT_R * 2 + 4.0
        }

        columns.indices.forEach { i ->
            val cb = bounds[i]
            val raw = item.cells.getOrNull(i).orEmpty()
            val indent = if (i == 0) firstIndent else 0.0
            val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD - indent
            drawAligned(c, c.truncate(raw, maxW, 10.0, false), cb, baseline, 10.0, bold = false, color = textColor, indent = indent)
        }
        val y = top + ROW_H
        c.line(left, y, right, y, COLOR_LINE, 0.5)
        return y
    }

    private fun drawAligned(c: IosPdfCanvas, text: String, cb: ColBound, baseline: Double, size: Double, bold: Boolean, color: PdfColor, indent: Double) {
        when (cb.align) {
            VaccinationAlign.START -> c.text(text, cb.xStart + CELL_PAD + indent, baseline, size, bold, color, PdfTextAlign.Left)
            VaccinationAlign.CENTER -> c.text(text, (cb.xStart + cb.xEnd) / 2.0, baseline, size, bold, color, PdfTextAlign.Center)
            VaccinationAlign.END -> c.text(text, cb.xEnd - CELL_PAD, baseline, size, bold, color, PdfTextAlign.Right)
        }
    }

    private fun drawGeneratedAt(c: IosPdfCanvas, label: String) {
        val y = PAGE_HEIGHT - MARGIN + 8.0
        c.line(MARGIN, y - 16.0, right, y - 16.0, COLOR_LINE, 1.0)
        c.text(label, MARGIN, y, 9.0, bold = false, color = COLOR_MUTED)
    }
}

actual fun createVaccinationCardPdfGenerator(): VaccinationCardPdfGenerator = IosVaccinationCardPdfGenerator()
