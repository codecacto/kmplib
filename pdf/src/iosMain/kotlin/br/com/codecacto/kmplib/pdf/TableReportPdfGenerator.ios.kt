package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF de tabela genérico no iOS (`UIGraphicsPDFRenderer` + CoreText), paridade com
 * [AndroidTableReportPdfGenerator]: cabeçalho + tabela com zebra + paginação (repete o cabeçalho da
 * tabela no topo de cada página) + resumo/rodapé + marca d'água.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS)** — espelha o par Android; build iOS não roda em Linux.
 */
private class IosTableReportPdfGenerator : TableReportPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_ZEBRA_BG = PdfColor.argb(0xFFFAFBFC)

        const val ROW_H = 20.0
        const val HEADER_ROW_H = 22.0
        const val CELL_PAD = 6.0
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    private data class ColBound(val xStart: Double, val xEnd: Double, val align: TableReportAlign)

    override fun generate(data: TableReportPdfData): ByteArray {
        val watermark = if (data.watermark) data.watermarkText else null
        val bounds = columnBounds(data.columns)
        return renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark) {
            var y = MARGIN
            y = drawHeader(canvas, data, y)
            y += 14.0
            y = drawTableHeader(canvas, data.columns, bounds, y)

            if (data.rows.isEmpty()) {
                y = ensureSpace(this, y, ROW_H, data.columns, bounds)
                canvas.text(data.emptyText, left + CELL_PAD, y + 14.0, 10.0, bold = false, color = COLOR_MUTED)
                y += ROW_H
            } else {
                data.rows.forEachIndexed { index, row ->
                    y = ensureSpace(this, y, ROW_H, data.columns, bounds)
                    y = drawRow(canvas, data.columns, bounds, row, y, zebra = index % 2 == 1)
                }
            }

            data.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                y = ensureSpace(this, y, 40.0, data.columns, bounds)
                y += 16.0
                canvas.text("RESUMO", left, y, 9.0, bold = true, color = COLOR_MUTED)
                y += 14.0
                y = canvas.wrappedText(summary, left, y, right - left, 10.5, bold = false, color = COLOR_TEXT, lineHeight = 13.0)
            }

            data.footer?.takeIf { it.isNotBlank() }?.let { drawFooter(canvas, it) }
        }
    }

    /** Garante espaço; em página nova redesenha o cabeçalho da tabela. Retorna o novo y. */
    private fun ensureSpace(flow: IosPageFlow, y: Double, needed: Double, columns: List<TableReportColumn>, bounds: List<ColBound>): Double {
        if (y <= PAGE_HEIGHT - MARGIN - needed) return y
        flow.newPage()
        return drawTableHeader(flow.canvas, columns, bounds, MARGIN)
    }

    private fun columnBounds(columns: List<TableReportColumn>): List<ColBound> {
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

    private fun drawHeader(c: IosPdfCanvas, data: TableReportPdfData, top: Double): Double {
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
        c.text(data.title, right, ry, 14.0, bold = true, color = COLOR_TEXT, align = PdfTextAlign.Right)
        data.subtitle?.takeIf { it.isNotBlank() }?.let {
            ry += 14.0
            c.text(it, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        }

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
    }

    private fun drawTableHeader(c: IosPdfCanvas, columns: List<TableReportColumn>, bounds: List<ColBound>, top: Double): Double {
        c.fillRect(left, top, right - left, HEADER_ROW_H, COLOR_HEADER_BG)
        val baseline = top + 15.0
        columns.forEachIndexed { i, col ->
            drawAligned(c, col.label, bounds[i], baseline, 9.0, bold = true, color = COLOR_MUTED)
        }
        val y = top + HEADER_ROW_H
        c.line(left, y, right, y, COLOR_LINE, 1.0)
        return y
    }

    private fun drawRow(c: IosPdfCanvas, columns: List<TableReportColumn>, bounds: List<ColBound>, row: TableReportRow, top: Double, zebra: Boolean): Double {
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
            TableReportAlign.START -> c.text(text, cb.xStart + CELL_PAD, baseline, size, bold, color, PdfTextAlign.Left)
            TableReportAlign.CENTER -> c.text(text, (cb.xStart + cb.xEnd) / 2.0, baseline, size, bold, color, PdfTextAlign.Center)
            TableReportAlign.END -> c.text(text, cb.xEnd - CELL_PAD, baseline, size, bold, color, PdfTextAlign.Right)
        }
    }

    private fun drawFooter(c: IosPdfCanvas, footer: String) {
        val y = PAGE_HEIGHT - MARGIN + 8.0
        c.line(MARGIN, y - 16.0, right, y - 16.0, COLOR_LINE, 1.0)
        c.text(footer, MARGIN, y, 9.0, bold = false, color = COLOR_MUTED)
    }
}

actual fun createTableReportPdfGenerator(): TableReportPdfGenerator = IosTableReportPdfGenerator()
