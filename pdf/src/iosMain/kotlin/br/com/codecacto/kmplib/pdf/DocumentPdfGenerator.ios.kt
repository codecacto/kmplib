package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF de documento estruturado no iOS via `UIGraphicsPDFRenderer` + CoreText
 * (helpers [IosPdfCanvas]/[renderIosPdfPaged]), com o **MESMO layout lógico e as MESMAS cores** do
 * renderer Android ([AndroidDocumentPdfGenerator]) — paridade Android=iOS. Multi-página (seções
 * Info/Table/Cards/Paragraph/Total, paginação automática, marca d'água −45°).
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito espelhando fielmente o par Android; o build aqui é
 * Linux (alvos Apple SKIPPED). Validação visual em macOS/CI.
 */
private class IosDocumentPdfGenerator : DocumentPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_ACCENT = PdfColor.argb(0xFF0F172A)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_ZEBRA_BG = PdfColor.argb(0xFFFAFBFC)
        val COLOR_CARD_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_TOTAL_BG = PdfColor.argb(0xFFEFF3F8)

        const val ROW_H = 20.0
        const val HEADER_ROW_H = 22.0
        const val CELL_PAD = 6.0
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    private data class ColBound(val xStart: Double, val xEnd: Double, val align: DocumentAlign)

    override fun generate(data: DocumentPdfData): ByteArray {
        val watermark = if (data.watermark) data.watermarkText else null
        return renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark) {
            var y = drawHeader(canvas, data, MARGIN)

            data.sections.forEach { section ->
                y += 14.0
                y = when (section) {
                    is DocumentSection.Info -> drawInfoSection(this, section, y)
                    is DocumentSection.Table -> drawTableSection(this, section, y)
                    is DocumentSection.Cards -> drawCardsSection(this, section, y)
                    is DocumentSection.Paragraph -> drawParagraphSection(this, section, y)
                    is DocumentSection.Total -> drawTotalSection(this, section, y)
                }
            }

            data.footer?.takeIf { it.isNotBlank() }?.let { drawFooter(canvas, it) }
        }
    }

    /** Abre página nova se [y] + [needed] estourar a área útil. Retorna o novo y. */
    private fun ensureSpace(flow: IosPageFlow, y: Double, needed: Double): Double =
        if (y <= PAGE_HEIGHT - MARGIN - needed) y else { flow.newPage(); MARGIN }

    private fun drawHeader(c: IosPdfCanvas, data: DocumentPdfData, top: Double): Double {
        var textX = MARGIN
        var y = top
        data.company.logoBytes?.let {
            c.image(it, MARGIN, top, 64.0, 64.0)
            textX = MARGIN + 64.0 + 14.0
        }
        y += 16.0
        c.text(data.company.name, textX, y, 18.0, bold = true, color = COLOR_TEXT)
        listOfNotNull(data.company.phone, data.company.email, data.company.address).forEach { line ->
            y += 14.0
            c.text(line, textX, y, 10.0, bold = false, color = COLOR_MUTED)
        }

        var ry = top + 16.0
        c.text(data.title, right, ry, 16.0, bold = true, color = COLOR_ACCENT, align = PdfTextAlign.Right)
        data.subtitle?.takeIf { it.isNotBlank() }?.let {
            ry += 14.0
            c.text(it, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        }

        var bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        bottom += 6.0

        if (data.headerInfo.isNotEmpty()) {
            bottom += 8.0
            data.headerInfo.forEach { row ->
                c.text(row.label, left, bottom + 11.0, 9.0, bold = true, color = COLOR_MUTED)
                c.text(row.value, left + 130.0, bottom + 11.0, 10.0, bold = false, color = COLOR_TEXT)
                bottom += 16.0
            }
        }
        return bottom
    }

    private fun drawSectionTitle(flow: IosPageFlow, title: String?, top: Double): Double {
        title?.takeIf { it.isNotBlank() } ?: return top
        var y = ensureSpace(flow, top, 28.0)
        y += 4.0
        flow.canvas.text(title.uppercase(), left, y + 10.0, 11.0, bold = true, color = COLOR_ACCENT)
        return y + 18.0
    }

    private fun drawInfoSection(flow: IosPageFlow, section: DocumentSection.Info, top: Double): Double {
        var y = drawSectionTitle(flow, section.title, top)
        if (section.rows.isEmpty()) {
            y = ensureSpace(flow, y, ROW_H)
            flow.canvas.text(section.emptyText, left, y + 12.0, 10.0, bold = false, color = COLOR_MUTED)
            return y + ROW_H
        }
        section.rows.forEach { row ->
            y = ensureSpace(flow, y, 18.0)
            flow.canvas.text(row.label, left, y + 11.0, 9.0, bold = true, color = COLOR_MUTED)
            flow.canvas.text(row.value, left + 150.0, y + 11.0, 10.0, bold = false, color = COLOR_TEXT)
            y += 16.0
        }
        return y
    }

    private fun drawTableSection(flow: IosPageFlow, section: DocumentSection.Table, top: Double): Double {
        var y = drawSectionTitle(flow, section.title, top)
        val bounds = columnBounds(section.columns)
        y = ensureSpace(flow, y, HEADER_ROW_H + ROW_H)
        y = drawTableHeader(flow.canvas, section.columns, bounds, y)

        if (section.rows.isEmpty()) {
            flow.canvas.text(section.emptyText, left + CELL_PAD, y + 14.0, 10.0, bold = false, color = COLOR_MUTED)
            return y + ROW_H
        }
        section.rows.forEachIndexed { index, row ->
            val before = y
            y = ensureSpace(flow, y, ROW_H)
            if (y != before) y = drawTableHeader(flow.canvas, section.columns, bounds, y)
            y = drawTableRow(flow.canvas, section.columns, bounds, row, y, zebra = index % 2 == 1)
        }
        return y
    }

    private fun drawCardsSection(flow: IosPageFlow, section: DocumentSection.Cards, top: Double): Double {
        var y = drawSectionTitle(flow, section.title, top)
        if (section.cards.isEmpty()) {
            y = ensureSpace(flow, y, ROW_H)
            flow.canvas.text(section.emptyText, left, y + 12.0, 10.0, bold = false, color = COLOR_MUTED)
            return y + ROW_H
        }
        val cardH = 48.0
        val gap = 8.0
        val perRow = 3
        var i = 0
        while (i < section.cards.size) {
            y = ensureSpace(flow, y, cardH + gap)
            val rowCards = section.cards.subList(i, minOf(i + perRow, section.cards.size))
            val cardW = (right - left - gap * (perRow - 1)) / perRow
            rowCards.forEachIndexed { idx, card ->
                val x = left + idx * (cardW + gap)
                flow.canvas.fillRect(x, y, cardW, cardH, COLOR_CARD_BG)
                flow.canvas.text(card.label, x + 8.0, y + 16.0, 9.0, bold = true, color = COLOR_MUTED)
                flow.canvas.text(card.value, x + 8.0, y + 38.0, 16.0, bold = true, color = COLOR_TEXT)
            }
            y += cardH + gap
            i += perRow
        }
        return y
    }

    private fun drawParagraphSection(flow: IosPageFlow, section: DocumentSection.Paragraph, top: Double): Double {
        var y = drawSectionTitle(flow, section.title, top)
        y = ensureSpace(flow, y, 40.0)
        return flow.canvas.wrappedText(section.text, left, y + 12.0, right - left, 10.5, bold = false, color = COLOR_TEXT, lineHeight = 14.0)
    }

    private fun drawTotalSection(flow: IosPageFlow, section: DocumentSection.Total, top: Double): Double {
        var y = drawSectionTitle(flow, section.title, top)
        y = ensureSpace(flow, y, 38.0)
        val boxTop = y + 4.0
        val boxH = 30.0
        flow.canvas.fillRect(left, boxTop, right - left, boxH, COLOR_TOTAL_BG)
        flow.canvas.text(section.label.uppercase(), left + 10.0, boxTop + 20.0, 10.0, bold = true, color = COLOR_MUTED)
        flow.canvas.text(section.value, right - 10.0, boxTop + 21.0, 16.0, bold = true, color = COLOR_ACCENT, align = PdfTextAlign.Right)
        return boxTop + boxH + 4.0
    }

    private fun columnBounds(columns: List<DocumentColumn>): List<ColBound> {
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

    private fun drawTableHeader(c: IosPdfCanvas, columns: List<DocumentColumn>, bounds: List<ColBound>, top: Double): Double {
        c.fillRect(left, top, right - left, HEADER_ROW_H, COLOR_HEADER_BG)
        val baseline = top + 15.0
        columns.forEachIndexed { i, col ->
            drawAligned(c, col.label, bounds[i], baseline, 9.0, bold = true, color = COLOR_MUTED)
        }
        val y = top + HEADER_ROW_H
        c.line(left, y, right, y, COLOR_LINE, 1.0)
        return y
    }

    private fun drawTableRow(c: IosPdfCanvas, columns: List<DocumentColumn>, bounds: List<ColBound>, row: DocumentRow, top: Double, zebra: Boolean): Double {
        if (zebra) c.fillRect(left, top, right - left, ROW_H, COLOR_ZEBRA_BG)
        val baseline = top + 14.0
        columns.indices.forEach { i ->
            val cb = bounds[i]
            val raw = row.cells.getOrNull(i).orEmpty()
            val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD
            drawAligned(c, c.truncate(raw, maxW, 10.0, false), cb, baseline, 10.0, bold = false, color = COLOR_TEXT)
        }
        val y = top + ROW_H
        c.line(left, y, right, y, COLOR_LINE, 0.5)
        return y
    }

    private fun drawAligned(c: IosPdfCanvas, text: String, cb: ColBound, baseline: Double, size: Double, bold: Boolean, color: PdfColor) {
        when (cb.align) {
            DocumentAlign.START -> c.text(text, cb.xStart + CELL_PAD, baseline, size, bold, color, PdfTextAlign.Left)
            DocumentAlign.CENTER -> c.text(text, (cb.xStart + cb.xEnd) / 2.0, baseline, size, bold, color, PdfTextAlign.Center)
            DocumentAlign.END -> c.text(text, cb.xEnd - CELL_PAD, baseline, size, bold, color, PdfTextAlign.Right)
        }
    }

    private fun drawFooter(c: IosPdfCanvas, footer: String) {
        val y = PAGE_HEIGHT - MARGIN + 8.0
        c.line(MARGIN, y - 16.0, right, y - 16.0, COLOR_LINE, 1.0)
        c.text(footer, MARGIN, y, 9.0, bold = false, color = COLOR_MUTED)
    }
}

actual fun createDocumentPdfGenerator(): DocumentPdfGenerator = IosDocumentPdfGenerator()
