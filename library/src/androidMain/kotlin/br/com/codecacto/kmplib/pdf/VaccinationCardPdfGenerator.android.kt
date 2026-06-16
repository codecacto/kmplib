package br.com.codecacto.kmplib.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Render nativo do PDF da carteira de vacinação via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context`. A4 a 72dpi (595 x 842 pt).
 *
 * Layout (paridade com o `actual` iOS em `VaccinationCardPdfGenerator.ios.kt`):
 *  - Cabeçalho: logo opcional + título + nome do titular à esquerda; emissão/subtítulo à direita.
 *  - Bloco do titular: linhas rótulo/valor de identificação.
 *  - Caixa de aviso ([VaccinationCardPdfData.notice]) opcional — usada pelo comprovante do Rebanho.
 *  - Tabela de itens: colunas ponderadas + alinhamento; ponto de status colorido opcional por linha;
 *    paginação automática (repete o cabeçalho da tabela no topo de cada página).
 *  - Rodapé: data de geração + footer opcional. Marca d'água -45° quando `watermark=true`.
 *
 * Visual coerente com [AndroidTableReportPdfGenerator]/[AndroidWorkReportPdfGenerator]
 * (mesma paleta neutra e medidas de cabeçalho).
 */
class AndroidVaccinationCardPdfGenerator : VaccinationCardPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_ZEBRA_BG = 0xFFFAFBFC.toInt()
        const val COLOR_NOTICE_BG = 0xFFFFF6E5.toInt()
        const val COLOR_NOTICE_BORDER = 0xFFE0B84D.toInt()
        const val COLOR_NOTICE_TEXT = 0xFF7A5A12.toInt()
        const val COLOR_WATERMARK = 0x1F1A1A1A

        const val ROW_H = 22f
        const val HEADER_ROW_H = 22f
        const val CELL_PAD = 6f
        const val STATUS_DOT_R = 3f
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: VaccinationCardPdfData): ByteArray {
        val doc = PdfDocument()
        val ctx = RenderCtx(doc)
        ctx.watermark = data.watermark
        ctx.watermarkText = data.watermarkText

        val bounds = columnBounds(data.columns)

        ctx.y = MARGIN
        ctx.y = drawHeader(ctx.canvas, data, ctx.y)
        ctx.y += 12f
        ctx.y = drawHolderBlock(ctx.canvas, data.holder, ctx.y)

        // Caixa de aviso (comprovante não-oficial do Rebanho), antes da tabela.
        data.notice?.takeIf { it.isNotBlank() }?.let { notice ->
            ctx.y += 10f
            ctx.ensureSpace(48f, data, bounds)
            ctx.y = drawNotice(ctx.canvas, notice, ctx.y)
        }

        ctx.y += 14f

        if (data.columns.isNotEmpty()) {
            ctx.y = drawTableHeader(ctx.canvas, data.columns, bounds, ctx.y)
            if (data.items.isEmpty()) {
                ctx.ensureSpace(ROW_H, data, bounds)
                ctx.canvas.drawText(data.emptyText, left + CELL_PAD, ctx.y + 14f, paint(COLOR_MUTED, 10f))
                ctx.y += ROW_H
            } else {
                data.items.forEachIndexed { index, item ->
                    ctx.ensureSpace(ROW_H, data, bounds)
                    ctx.y = drawItemRow(ctx.canvas, data.columns, bounds, item, ctx.y, zebra = index % 2 == 1)
                }
            }
        }

        data.footer?.takeIf { it.isNotBlank() }?.let { footer ->
            ctx.ensureSpace(30f, data, bounds)
            ctx.y += 14f
            ctx.y = drawWrappedText(ctx.canvas, footer, left, ctx.y, right, paint(COLOR_MUTED, 9f), 12f)
        }

        // Data de geração ancorada ao pé da página corrente.
        drawGeneratedAt(ctx.canvas, data.generatedAtLabel)

        ctx.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private inner class RenderCtx(val doc: PdfDocument) {
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        var y = MARGIN
        var watermarkText: String? = null
        var watermark = false

        /** Garante espaço; em página nova redesenha o cabeçalho da tabela. Retorna true se trocou. */
        fun ensureSpace(needed: Float, data: VaccinationCardPdfData, bounds: List<ColBound>): Boolean {
            if (y <= PAGE_HEIGHT - MARGIN - needed) return false
            newPage()
            if (data.columns.isNotEmpty()) y = drawTableHeader(canvas, data.columns, bounds, y)
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

    private data class ColBound(val xStart: Float, val xEnd: Float, val align: VaccinationAlign)

    private fun columnBounds(columns: List<VaccinationColumn>): List<ColBound> {
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

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun drawHeader(canvas: Canvas, data: VaccinationCardPdfData, top: Float): Float {
        var textX = MARGIN
        var y = top

        val logo = data.logoBytes?.let { decodeScaled(it, maxSize = 64) }
        if (logo != null) {
            canvas.drawBitmap(logo, MARGIN, top, null)
            textX = MARGIN + logo.width + 14f
        }

        y += 16f
        canvas.drawText(data.title, textX, y, paint(COLOR_TEXT, 18f, bold = true))
        y += 16f
        canvas.drawText(data.holder.name, textX, y, paint(COLOR_TEXT, 13f, bold = true))

        val mutedRight = paint(COLOR_MUTED, 10f).apply { textAlign = Paint.Align.RIGHT }
        var ry = top + 16f
        data.subtitle?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, right, ry, mutedRight)
            ry += 14f
        }
        canvas.drawText(data.generatedAtLabel, right, ry, mutedRight)

        val bottom = maxOf(y, ry) + 12f
        canvas.drawLine(MARGIN, bottom, right, bottom, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return bottom + 6f
    }

    private fun drawHolderBlock(canvas: Canvas, holder: VaccinationHolder, top: Float): Float {
        if (holder.lines.isEmpty()) return top
        var y = top
        val labelPaint = paint(COLOR_MUTED, 9.5f, bold = true)
        val valuePaint = paint(COLOR_TEXT, 10.5f)
        for (line in holder.lines) {
            y += 14f
            canvas.drawText("${line.label}:", MARGIN, y, labelPaint)
            canvas.drawText(line.value, MARGIN + 110f, y, valuePaint)
        }
        return y + 4f
    }

    private fun drawNotice(canvas: Canvas, notice: String, top: Float): Float {
        val pad = 8f
        val textPaint = paint(COLOR_NOTICE_TEXT, 9.5f, bold = true)
        // Mede a altura necessária do texto com wrap.
        val measured = measureWrappedHeight(notice, right - MARGIN - 2 * pad, textPaint, 12f)
        val boxTop = top
        val boxBottom = boxTop + measured + 2 * pad
        val rect = RectF(left, boxTop, right, boxBottom)
        canvas.drawRoundRect(rect, 6f, 6f, Paint().apply { color = COLOR_NOTICE_BG; isAntiAlias = true })
        canvas.drawRoundRect(rect, 6f, 6f, Paint().apply {
            style = Paint.Style.STROKE; color = COLOR_NOTICE_BORDER; strokeWidth = 1f; isAntiAlias = true
        })
        drawWrappedText(canvas, notice, left + pad, boxTop + pad + 10f, right - pad, textPaint, 12f)
        return boxBottom
    }

    private fun drawTableHeader(
        canvas: Canvas,
        columns: List<VaccinationColumn>,
        bounds: List<ColBound>,
        top: Float,
    ): Float {
        canvas.drawRect(left, top, right, top + HEADER_ROW_H, Paint().apply { color = COLOR_HEADER_BG })
        val baseline = top + 15f
        columns.forEachIndexed { i, col ->
            drawAligned(canvas, col.label, bounds[i], baseline, paint(COLOR_MUTED, 9f, bold = true), indent = 0f)
        }
        val y = top + HEADER_ROW_H
        canvas.drawLine(left, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return y
    }

    private fun drawItemRow(
        canvas: Canvas,
        columns: List<VaccinationColumn>,
        bounds: List<ColBound>,
        item: VaccinationItem,
        top: Float,
        zebra: Boolean,
    ): Float {
        if (zebra) {
            canvas.drawRect(left, top, right, top + ROW_H, Paint().apply { color = COLOR_ZEBRA_BG })
        }
        val baseline = top + 15f
        val textColor = if (item.muted) COLOR_MUTED else COLOR_TEXT
        val cellPaint = paint(textColor, 10f)

        // Ponto de status colorido antes da 1ª célula (se houver) — desloca a primeira coluna.
        var firstIndent = 0f
        item.statusColorArgb?.let { argb ->
            val cb = bounds.firstOrNull() ?: return@let
            val cx = cb.xStart + CELL_PAD + STATUS_DOT_R
            val cy = top + ROW_H / 2f
            canvas.drawCircle(cx, cy, STATUS_DOT_R, Paint().apply { color = argb; isAntiAlias = true })
            firstIndent = STATUS_DOT_R * 2 + 4f
        }

        columns.indices.forEach { i ->
            val cb = bounds[i]
            val raw = item.cells.getOrNull(i).orEmpty()
            val indent = if (i == 0) firstIndent else 0f
            val maxW = (cb.xEnd - cb.xStart) - 2 * CELL_PAD - indent
            val text = truncate(raw, maxW, cellPaint)
            drawAligned(canvas, text, cb, baseline, cellPaint, indent)
        }
        val y = top + ROW_H
        canvas.drawLine(left, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return y
    }

    private fun drawAligned(
        canvas: Canvas,
        text: String,
        cb: ColBound,
        baseline: Float,
        paint: Paint,
        indent: Float,
    ) {
        when (cb.align) {
            VaccinationAlign.START -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(text, cb.xStart + CELL_PAD + indent, baseline, paint)
            }
            VaccinationAlign.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, (cb.xStart + cb.xEnd) / 2f, baseline, paint)
            }
            VaccinationAlign.END -> {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(text, cb.xEnd - CELL_PAD, baseline, paint)
            }
        }
    }

    private fun drawGeneratedAt(canvas: Canvas, label: String) {
        val y = PAGE_HEIGHT - MARGIN + 8f
        canvas.drawLine(MARGIN, y - 16f, right, y - 16f, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        canvas.drawText(label, MARGIN, y, paint(COLOR_MUTED, 9f))
    }

    private fun drawWatermark(canvas: Canvas, text: String) {
        val p = paint(COLOR_WATERMARK, 54f, bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.save()
        canvas.rotate(-45f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText(text, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, p)
        canvas.restore()
    }

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

    /** Mede a altura (pt) que [text] ocupará com wrap em [maxW], sem desenhar. */
    private fun measureWrappedHeight(text: String, maxW: Float, paint: Paint, lineHeight: Float): Float {
        if (maxW <= 0f) return lineHeight
        var lines = 0
        val words = text.split(Regex("\\s+"))
        var line = StringBuilder()
        for (w in words) {
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(candidate) > maxW && line.isNotEmpty()) {
                lines++
                line = StringBuilder(w)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) lines++
        return (lines.coerceAtLeast(1)) * lineHeight
    }
}

actual fun createVaccinationCardPdfGenerator(): VaccinationCardPdfGenerator =
    AndroidVaccinationCardPdfGenerator()
