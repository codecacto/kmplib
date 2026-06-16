package br.com.codecacto.kmplib.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Render nativo do PDF de tabela genérico via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context`. A4 a 72dpi (595 x 842 pt).
 * Visual coerente com os demais geradores (mesma paleta/medidas de cabeçalho), porém
 * usando APENAS tokens/cores neutras — sem nenhum bloco monetário ("TOTAL R$").
 *
 * Recursos:
 *  - Cabeçalho do documento (logo opcional + empresa; título/subtítulo à direita).
 *  - Linha de cabeçalho da tabela (negrito, fundo neutro) com colunas ponderadas.
 *  - Linhas de dados com **zebra** (alternância) e strokes leves; alinhamento por coluna.
 *  - **Paginação automática** quando as linhas estouram a altura útil da página
 *    (a linha de cabeçalho da tabela é repetida no topo de cada nova página).
 */
class AndroidTableReportPdfGenerator : TableReportPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        // Tokens neutros (sem cor financeira).
        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_ZEBRA_BG = 0xFFFAFBFC.toInt()
        const val COLOR_WATERMARK = 0x1F1A1A1A

        const val ROW_H = 20f
        const val HEADER_ROW_H = 22f
        const val CELL_PAD = 6f
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: TableReportPdfData): ByteArray {
        val doc = PdfDocument()
        val ctx = RenderCtx(doc)
        ctx.watermark = data.watermark
        ctx.watermarkText = data.watermarkText

        // Larguras (x de início) de cada coluna, derivadas dos pesos.
        val bounds = columnBounds(data.columns)

        ctx.y = MARGIN
        ctx.y = drawHeader(ctx.canvas, data, ctx.y)
        ctx.y += 14f

        // Linha de cabeçalho da tabela (repetida no topo de cada página).
        ctx.y = drawTableHeader(ctx.canvas, data.columns, bounds, ctx.y)

        if (data.rows.isEmpty()) {
            ctx.ensureSpace(ROW_H, data, bounds)
            val b = ctx.y + 14f
            ctx.canvas.drawText(data.emptyText, left + CELL_PAD, b, paint(COLOR_MUTED, 10f))
            ctx.y += ROW_H
        } else {
            data.rows.forEachIndexed { index, row ->
                if (ctx.ensureSpace(ROW_H, data, bounds)) {
                    // Página nova: o cabeçalho da tabela já foi redesenhado em ensureSpace.
                }
                ctx.y = drawRow(ctx.canvas, data.columns, bounds, row, ctx.y, zebra = index % 2 == 1)
            }
        }

        // Resumo textual (sem dinheiro).
        data.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            ctx.ensureSpace(40f, data, bounds)
            ctx.y += 16f
            ctx.canvas.drawText("RESUMO", left, ctx.y, paint(COLOR_MUTED, 9f, bold = true))
            ctx.y += 14f
            ctx.y = drawWrappedText(ctx.canvas, summary, left, ctx.y, right, paint(COLOR_TEXT, 10.5f), 13f)
        }

        // Rodapé (texto livre, ancorado ao pé da página corrente).
        data.footer?.takeIf { it.isNotBlank() }?.let { footer ->
            drawFooter(ctx.canvas, footer)
        }

        ctx.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** Estado mutável da renderização paginada (espelha o loop do WorkReport). */
    private inner class RenderCtx(val doc: PdfDocument) {
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = MARGIN
        var watermarkText: String? = null
        var watermark = false

        /**
         * Garante espaço para [needed] pontos. Se não couber, abre nova página e
         * redesenha o cabeçalho da tabela no topo. Retorna `true` se trocou de página.
         */
        fun ensureSpace(needed: Float, data: TableReportPdfData, bounds: List<ColBound>): Boolean {
            if (y <= PAGE_HEIGHT - MARGIN - needed) return false
            newPage()
            y = drawTableHeader(canvas, data.columns, bounds, y)
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
            val text = watermarkText
            if (watermark && !text.isNullOrBlank()) drawWatermark(canvas, text)
        }
    }

    /** Limites horizontais (xStart..xEnd) de uma coluna + seu alinhamento. */
    private data class ColBound(val xStart: Float, val xEnd: Float, val align: TableReportAlign)

    private fun columnBounds(columns: List<TableReportColumn>): List<ColBound> {
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

    // --- Paints --------------------------------------------------------------

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = size
        typeface = if (bold) {
            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        } else {
            android.graphics.Typeface.DEFAULT
        }
    }

    // --- Sections ------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, data: TableReportPdfData, top: Float): Float {
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

        // Título/subtítulo à direita.
        val titlePaint = paint(COLOR_TEXT, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val mutedRight = paint(COLOR_MUTED, 10f).apply { textAlign = Paint.Align.RIGHT }
        var ry = top + 16f
        canvas.drawText(data.title, right, ry, titlePaint)
        data.subtitle?.takeIf { it.isNotBlank() }?.let {
            ry += 14f
            canvas.drawText(it, right, ry, mutedRight)
        }

        val bottom = maxOf(y, ry) + 12f
        canvas.drawLine(MARGIN, bottom, right, bottom, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return bottom + 6f
    }

    private fun drawTableHeader(
        canvas: Canvas,
        columns: List<TableReportColumn>,
        bounds: List<ColBound>,
        top: Float,
    ): Float {
        canvas.drawRect(left, top, right, top + HEADER_ROW_H, Paint().apply { color = COLOR_HEADER_BG })
        val baseline = top + 15f
        columns.forEachIndexed { i, col ->
            val cb = bounds[i]
            drawAligned(canvas, col.label, cb, baseline, paint(COLOR_MUTED, 9f, bold = true))
        }
        val y = top + HEADER_ROW_H
        canvas.drawLine(left, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return y
    }

    private fun drawRow(
        canvas: Canvas,
        columns: List<TableReportColumn>,
        bounds: List<ColBound>,
        row: TableReportRow,
        top: Float,
        zebra: Boolean,
    ): Float {
        if (zebra) {
            canvas.drawRect(left, top, right, top + ROW_H, Paint().apply { color = COLOR_ZEBRA_BG })
        }
        val baseline = top + 14f
        val cellPaint = paint(COLOR_TEXT, 10f)
        columns.indices.forEach { i ->
            val cb = bounds[i]
            val raw = row.cells.getOrNull(i).orEmpty()
            val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD
            val text = truncate(raw, maxW, cellPaint)
            drawAligned(canvas, text, cb, baseline, cellPaint)
        }
        val y = top + ROW_H
        canvas.drawLine(left, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return y
    }

    /** Desenha [text] dentro dos limites [cb] respeitando o alinhamento da coluna. */
    private fun drawAligned(canvas: Canvas, text: String, cb: ColBound, baseline: Float, paint: Paint) {
        when (cb.align) {
            TableReportAlign.START -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(text, cb.xStart + CELL_PAD, baseline, paint)
            }
            TableReportAlign.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, (cb.xStart + cb.xEnd) / 2f, baseline, paint)
            }
            TableReportAlign.END -> {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(text, cb.xEnd - CELL_PAD, baseline, paint)
            }
        }
    }

    private fun drawFooter(canvas: Canvas, footer: String) {
        val y = PAGE_HEIGHT - MARGIN + 8f
        canvas.drawLine(MARGIN, y - 16f, right, y - 16f, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        canvas.drawText(footer, MARGIN, y, paint(COLOR_MUTED, 9f))
    }

    private fun drawWatermark(canvas: Canvas, text: String) {
        val p = paint(COLOR_WATERMARK, 54f, bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.save()
        canvas.rotate(-45f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText(text, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, p)
        canvas.restore()
    }

    // --- Helpers -------------------------------------------------------------

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

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        rightX: Float,
        paint: Paint,
        lineHeight: Float,
    ): Float {
        paint.textAlign = Paint.Align.LEFT
        val maxW = rightX - x
        var y = top
        val words = text.split(Regex("\\s+"))
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
        return y
    }
}

actual fun createTableReportPdfGenerator(): TableReportPdfGenerator =
    AndroidTableReportPdfGenerator()
