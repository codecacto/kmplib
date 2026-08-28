package br.com.codecacto.kmplib.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Render nativo do PDF de documento estruturado via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context`. A4 a 72dpi (595 × 842 pt).
 *
 * Suporta cabeçalho (logo + empresa + título/subtítulo + metadados chave→valor) e as
 * variantes de [DocumentSection] (Info / Table / Cards / Paragraph / Total), com
 * paginação automática e marca d'água -45° opcional.
 */
class AndroidDocumentPdfGenerator : DocumentPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_ACCENT = 0xFF0F172A.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_ZEBRA_BG = 0xFFFAFBFC.toInt()
        const val COLOR_CARD_BG = 0xFFF2F4F7.toInt()
        const val COLOR_TOTAL_BG = 0xFFEFF3F8.toInt()
        const val COLOR_WATERMARK = 0x1F1A1A1A

        const val ROW_H = 20f
        const val HEADER_ROW_H = 22f
        const val CELL_PAD = 6f
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: DocumentPdfData): ByteArray {
        val doc = PdfDocument()
        val ctx = RenderCtx(doc, data.watermark, data.watermarkText)

        ctx.y = drawHeader(ctx.canvas, data, MARGIN)

        data.sections.forEach { section ->
            ctx.y += 14f
            when (section) {
                is DocumentSection.Info -> drawInfoSection(ctx, section)
                is DocumentSection.Table -> drawTableSection(ctx, section)
                is DocumentSection.Cards -> drawCardsSection(ctx, section)
                is DocumentSection.Paragraph -> drawParagraphSection(ctx, section)
                is DocumentSection.Total -> drawTotalSection(ctx, section)
            }
        }

        data.footer?.takeIf { it.isNotBlank() }?.let { drawFooter(ctx.canvas, it) }

        ctx.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private inner class RenderCtx(
        val doc: PdfDocument,
        val watermark: Boolean,
        val watermarkText: String?,
    ) {
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = MARGIN

        /** Garante espaço para [needed] pt; abre nova página se necessário. */
        fun ensureSpace(needed: Float): Boolean {
            if (y <= PAGE_HEIGHT - MARGIN - needed) return false
            newPage()
            return true
        }

        fun newPage() {
            drawWatermarkIfNeeded()
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        fun finish() {
            drawWatermarkIfNeeded()
            doc.finishPage(page)
        }

        private fun drawWatermarkIfNeeded() {
            if (watermark && !watermarkText.isNullOrBlank()) drawWatermark(canvas, watermarkText)
        }
    }

    // --- Paints --------------------------------------------------------------

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    // --- Header --------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, data: DocumentPdfData, top: Float): Float {
        var textX = MARGIN
        var y = top

        val logo = data.company.logoBytes?.let { decodeScaled(it, maxSize = 64) }
        if (logo != null) {
            canvas.drawBitmap(logo, MARGIN, top, null)
            textX = MARGIN + logo.width + 14f
        }

        y += 16f
        canvas.drawText(data.company.name, textX, y, paint(COLOR_TEXT, 18f, bold = true))
        listOfNotNull(data.company.phone, data.company.email, data.company.address).forEach { line ->
            y += 14f
            canvas.drawText(line, textX, y, paint(COLOR_MUTED, 10f))
        }

        val titlePaint = paint(COLOR_ACCENT, 16f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val mutedRight = paint(COLOR_MUTED, 10f).apply { textAlign = Paint.Align.RIGHT }
        var ry = top + 16f
        canvas.drawText(data.title, right, ry, titlePaint)
        data.subtitle?.takeIf { it.isNotBlank() }?.let {
            ry += 14f
            canvas.drawText(it, right, ry, mutedRight)
        }

        var bottom = maxOf(y, ry) + 12f
        canvas.drawLine(MARGIN, bottom, right, bottom, linePaint(1f))
        bottom += 6f

        if (data.headerInfo.isNotEmpty()) {
            bottom += 8f
            data.headerInfo.forEach { row ->
                canvas.drawText(row.label, left, bottom + 11f, paint(COLOR_MUTED, 9f, bold = true))
                canvas.drawText(row.value, left + 130f, bottom + 11f, paint(COLOR_TEXT, 10f))
                bottom += 16f
            }
        }
        return bottom
    }

    // --- Sections ------------------------------------------------------------

    private fun drawSectionTitle(ctx: RenderCtx, title: String?) {
        title?.takeIf { it.isNotBlank() }?.let {
            ctx.ensureSpace(28f)
            ctx.y += 4f
            ctx.canvas.drawText(it.uppercase(), left, ctx.y + 10f, paint(COLOR_ACCENT, 11f, bold = true))
            ctx.y += 18f
        }
    }

    private fun drawInfoSection(ctx: RenderCtx, section: DocumentSection.Info) {
        drawSectionTitle(ctx, section.title)
        if (section.rows.isEmpty()) {
            ctx.ensureSpace(ROW_H)
            ctx.canvas.drawText(section.emptyText, left, ctx.y + 12f, paint(COLOR_MUTED, 10f))
            ctx.y += ROW_H
            return
        }
        section.rows.forEach { row ->
            ctx.ensureSpace(18f)
            ctx.canvas.drawText(row.label, left, ctx.y + 11f, paint(COLOR_MUTED, 9f, bold = true))
            ctx.canvas.drawText(row.value, left + 150f, ctx.y + 11f, paint(COLOR_TEXT, 10f))
            ctx.y += 16f
        }
    }

    private fun drawTableSection(ctx: RenderCtx, section: DocumentSection.Table) {
        drawSectionTitle(ctx, section.title)
        val bounds = columnBounds(section.columns)
        ctx.ensureSpace(HEADER_ROW_H + ROW_H)
        ctx.y = drawTableHeader(ctx.canvas, section.columns, bounds, ctx.y)

        if (section.rows.isEmpty()) {
            ctx.canvas.drawText(section.emptyText, left + CELL_PAD, ctx.y + 14f, paint(COLOR_MUTED, 10f))
            ctx.y += ROW_H
            return
        }
        section.rows.forEachIndexed { index, row ->
            if (ctx.ensureSpace(ROW_H)) {
                ctx.y = drawTableHeader(ctx.canvas, section.columns, bounds, ctx.y)
            }
            ctx.y = drawTableRow(ctx.canvas, section.columns, bounds, row, ctx.y, zebra = index % 2 == 1)
        }
    }

    private fun drawCardsSection(ctx: RenderCtx, section: DocumentSection.Cards) {
        drawSectionTitle(ctx, section.title)
        if (section.cards.isEmpty()) {
            ctx.ensureSpace(ROW_H)
            ctx.canvas.drawText(section.emptyText, left, ctx.y + 12f, paint(COLOR_MUTED, 10f))
            ctx.y += ROW_H
            return
        }
        val cardH = 48f
        val gap = 8f
        val perRow = 3
        var i = 0
        while (i < section.cards.size) {
            ctx.ensureSpace(cardH + gap)
            val rowCards = section.cards.subList(i, minOf(i + perRow, section.cards.size))
            val cardW = (right - left - gap * (perRow - 1)) / perRow
            rowCards.forEachIndexed { idx, card ->
                val x = left + idx * (cardW + gap)
                ctx.canvas.drawRect(x, ctx.y, x + cardW, ctx.y + cardH, Paint().apply { color = COLOR_CARD_BG })
                ctx.canvas.drawText(card.label, x + 8f, ctx.y + 16f, paint(COLOR_MUTED, 9f, bold = true))
                ctx.canvas.drawText(card.value, x + 8f, ctx.y + 38f, paint(COLOR_TEXT, 16f, bold = true))
            }
            ctx.y += cardH + gap
            i += perRow
        }
    }

    private fun drawParagraphSection(ctx: RenderCtx, section: DocumentSection.Paragraph) {
        drawSectionTitle(ctx, section.title)
        ctx.ensureSpace(40f)
        ctx.y = drawWrappedText(ctx.canvas, section.text, left, ctx.y + 12f, right, paint(COLOR_TEXT, 10.5f), 14f)
    }

    private fun drawTotalSection(ctx: RenderCtx, section: DocumentSection.Total) {
        drawSectionTitle(ctx, section.title)
        ctx.ensureSpace(38f)
        val boxTop = ctx.y + 4f
        val boxH = 30f
        ctx.canvas.drawRect(left, boxTop, right, boxTop + boxH, Paint().apply { color = COLOR_TOTAL_BG })
        ctx.canvas.drawText(section.label.uppercase(), left + 10f, boxTop + 20f, paint(COLOR_MUTED, 10f, bold = true))
        val valuePaint = paint(COLOR_ACCENT, 16f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        ctx.canvas.drawText(section.value, right - 10f, boxTop + 21f, valuePaint)
        ctx.y = boxTop + boxH + 4f
    }

    // --- Table primitives ----------------------------------------------------

    private data class ColBound(val xStart: Float, val xEnd: Float, val align: DocumentAlign)

    private fun columnBounds(columns: List<DocumentColumn>): List<ColBound> {
        if (columns.isEmpty()) return emptyList()
        val usable = right - left
        val totalWeight = columns.sumOf { it.weight.coerceAtLeast(0.0001f).toDouble() }.toFloat()
        val result = ArrayList<ColBound>(columns.size)
        var x = left
        for (col in columns) {
            val w = usable * (col.weight.coerceAtLeast(0.0001f) / totalWeight)
            result.add(ColBound(x, x + w, col.align))
            x += w
        }
        return result
    }

    private fun drawTableHeader(canvas: Canvas, columns: List<DocumentColumn>, bounds: List<ColBound>, top: Float): Float {
        canvas.drawRect(left, top, right, top + HEADER_ROW_H, Paint().apply { color = COLOR_HEADER_BG })
        val baseline = top + 15f
        columns.forEachIndexed { i, col ->
            drawAligned(canvas, col.label, bounds[i], baseline, paint(COLOR_MUTED, 9f, bold = true))
        }
        val y = top + HEADER_ROW_H
        canvas.drawLine(left, y, right, y, linePaint(1f))
        return y
    }

    private fun drawTableRow(
        canvas: Canvas,
        columns: List<DocumentColumn>,
        bounds: List<ColBound>,
        row: DocumentRow,
        top: Float,
        zebra: Boolean,
    ): Float {
        if (zebra) canvas.drawRect(left, top, right, top + ROW_H, Paint().apply { color = COLOR_ZEBRA_BG })
        val baseline = top + 14f
        val cellPaint = paint(COLOR_TEXT, 10f)
        columns.indices.forEach { i ->
            val cb = bounds[i]
            val raw = row.cells.getOrNull(i).orEmpty()
            val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD
            drawAligned(canvas, truncate(raw, maxW, cellPaint), cb, baseline, cellPaint)
        }
        val y = top + ROW_H
        canvas.drawLine(left, y, right, y, linePaint(0.5f))
        return y
    }

    private fun drawAligned(canvas: Canvas, text: String, cb: ColBound, baseline: Float, paint: Paint) {
        when (cb.align) {
            DocumentAlign.START -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(text, cb.xStart + CELL_PAD, baseline, paint)
            }
            DocumentAlign.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, (cb.xStart + cb.xEnd) / 2f, baseline, paint)
            }
            DocumentAlign.END -> {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(text, cb.xEnd - CELL_PAD, baseline, paint)
            }
        }
    }

    // --- Footer / watermark / helpers ---------------------------------------

    private fun drawFooter(canvas: Canvas, footer: String) {
        val y = PAGE_HEIGHT - MARGIN + 8f
        canvas.drawLine(MARGIN, y - 16f, right, y - 16f, linePaint(1f))
        canvas.drawText(footer, MARGIN, y, paint(COLOR_MUTED, 9f))
    }

    private fun drawWatermark(canvas: Canvas, text: String) {
        val p = paint(COLOR_WATERMARK, 54f, bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.save()
        canvas.rotate(-45f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText(text, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, p)
        canvas.restore()
    }

    private fun linePaint(width: Float) = Paint().apply { color = COLOR_LINE; strokeWidth = width }

    private fun decodeScaled(bytes: ByteArray, maxSize: Int): Bitmap? {
        val src = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        } ?: return null
        val ratio = maxSize.toFloat() / maxOf(src.width, src.height)
        if (ratio >= 1f) return src
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun truncate(text: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, top: Float, rightX: Float, paint: Paint, lineHeight: Float): Float {
        paint.textAlign = Paint.Align.LEFT
        val maxW = rightX - x
        var y = top
        text.split("\n").forEach { para ->
            val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var line = StringBuilder()
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (paint.measureText(candidate) > maxW && line.isNotEmpty()) {
                    canvas.drawText(line.toString(), x, y, paint)
                    y += lineHeight
                    line = StringBuilder(w)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
            }
        }
        return y
    }
}

actual fun createDocumentPdfGenerator(): DocumentPdfGenerator = AndroidDocumentPdfGenerator()
