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
 * Render nativo do PDF do relatório de horas extras via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context`. A4 a 72dpi (595 x 842 pt).
 * Visual coerente com [AndroidWorkReportPdfGenerator] (mesma paleta/medidas de cabeçalho, grade
 * de imagens e marca d'água). Reusa os mesmos helpers de imagem/escala/watermark.
 */
class AndroidHoursReportPdfGenerator : HoursReportPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_TOTAL_BG = 0xFFEFF6EF.toInt()
        const val COLOR_PENDING = 0xFFB26A00.toInt()
        const val COLOR_WATERMARK = 0x1F1A1A1A

        // Colunas da tabela de lançamentos (frações da largura útil).
        const val COL_DATE = 0.22f
        const val COL_TIME = 0.26f
        const val COL_DURATION = 0.18f
        const val COL_VALUE = 0.18f
        // Status = restante.
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: HoursReportPdfData): ByteArray {
        val doc = PdfDocument()
        val ctx = RenderCtx(doc)
        ctx.watermark = data.watermark
        ctx.watermarkText = data.watermarkText

        ctx.y = MARGIN
        ctx.y = drawHeader(ctx.canvas, data, ctx.y)
        ctx.y += 12f
        ctx.y = drawCompanyBlock(ctx.canvas, data, ctx.y)
        ctx.y += 16f

        // --- Lançamentos -----------------------------------------------------
        ctx.ensureSpace(60f)
        ctx.y = drawSectionTitle(ctx.canvas, "LANÇAMENTOS", ctx.y)
        ctx.y = drawTableHeader(ctx.canvas, ctx.y)
        if (data.entries.isEmpty()) {
            ctx.y = drawEmptyRow(ctx.canvas, "Nenhum lançamento no período.", ctx.y)
        } else {
            for (e in data.entries) {
                ctx.ensureSpace(26f)
                ctx.y = drawEntryRow(ctx.canvas, e, ctx.y)
            }
        }
        ctx.y += 14f

        // --- Totais ----------------------------------------------------------
        ctx.ensureSpace(90f)
        ctx.y = drawTotalsBlock(ctx.canvas, data, ctx.y)
        ctx.y += 16f

        // --- Comprovantes (imagens) ------------------------------------------
        if (data.attachments.isNotEmpty()) {
            ctx.ensureSpace(80f)
            ctx.y = drawSectionTitle(ctx.canvas, "COMPROVANTES", ctx.y)
            ctx.y = drawAttachmentGrid(ctx, data.attachments, ctx.y)
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

        fun ensureSpace(needed: Float) {
            if (y > PAGE_HEIGHT - MARGIN - needed) newPage()
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

    // --- Paints --------------------------------------------------------------

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    // --- Sections ------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, data: HoursReportPdfData, top: Float): Float {
        var textX = MARGIN
        var y = top

        val logo = data.company.logoBytes?.let { decodeScaled(it, maxSize = 64) }
        if (logo != null) {
            canvas.drawBitmap(logo, MARGIN, top, null)
            textX = MARGIN + logo.width + 14f
        }

        y += 16f
        canvas.drawText(data.company.name, textX, y, paint(COLOR_TEXT, 18f, bold = true))
        data.company.phone?.let {
            y += 14f
            canvas.drawText(it, textX, y, paint(COLOR_MUTED, 10f))
        }

        val titlePaint = paint(COLOR_TEXT, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val mutedRight = paint(COLOR_MUTED, 10f).apply { textAlign = Paint.Align.RIGHT }
        var ry = top + 16f
        canvas.drawText("Relatório de horas extras", right, ry, titlePaint)
        ry += 14f
        canvas.drawText(data.periodLabel, right, ry, mutedRight)
        ry += 14f
        canvas.drawText(data.generatedAtLabel, right, ry, mutedRight)

        val bottom = maxOf(y, ry) + 12f
        canvas.drawLine(MARGIN, bottom, right, bottom, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return bottom + 6f
    }

    private fun drawCompanyBlock(canvas: Canvas, data: HoursReportPdfData, top: Float): Float {
        val y = top + 14f
        val p = paint(COLOR_TEXT, 11f, bold = true)
        canvas.drawText(truncate(data.companyLabel, right - MARGIN, p), MARGIN, y, p)
        return y
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, top: Float): Float {
        val y = top + 12f
        canvas.drawText(title, MARGIN, y, paint(COLOR_TEXT, 12f, bold = true))
        return y + 8f
    }

    private fun drawEmptyRow(canvas: Canvas, text: String, top: Float): Float {
        val y = top + 14f
        canvas.drawText(text, MARGIN + 6f, y, paint(COLOR_MUTED, 10f))
        return y + 8f
    }

    // --- Tabela de lançamentos ----------------------------------------------

    private val usableWidth get() = right - MARGIN
    private val xDate get() = MARGIN
    private val xTime get() = MARGIN + usableWidth * COL_DATE
    private val xDuration get() = MARGIN + usableWidth * (COL_DATE + COL_TIME)
    private val xValue get() = MARGIN + usableWidth * (COL_DATE + COL_TIME + COL_DURATION)
    private val xStatus get() = MARGIN + usableWidth * (COL_DATE + COL_TIME + COL_DURATION + COL_VALUE)

    private fun drawTableHeader(canvas: Canvas, top: Float): Float {
        val y = top + 12f
        val h = paint(COLOR_MUTED, 9f, bold = true)
        canvas.drawText("Data", xDate, y, h)
        canvas.drawText("Entrada–Saída", xTime, y, h)
        canvas.drawText("Duração", xDuration, y, h)
        canvas.drawText("Valor", xValue, y, h)
        canvas.drawText("Status", xStatus, y, h)
        val ly = y + 5f
        canvas.drawLine(MARGIN, ly, right, ly, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return ly + 4f
    }

    private fun drawEntryRow(canvas: Canvas, e: HoursReportEntry, top: Float): Float {
        val y = top + 12f
        val cell = paint(COLOR_TEXT, 9.5f)
        canvas.drawText(truncate(e.date, xTime - xDate - 4f, cell), xDate, y, cell)
        canvas.drawText(
            truncate("${e.start}–${e.end}", xDuration - xTime - 4f, cell), xTime, y, cell,
        )
        canvas.drawText(truncate(e.durationLabel, xValue - xDuration - 4f, cell), xDuration, y, cell)
        canvas.drawText(truncate(e.valueLabel ?: "—", xStatus - xValue - 4f, cell), xValue, y, cell)
        canvas.drawText(truncate(e.statusLabel, right - xStatus, cell), xStatus, y, cell)
        val ly = y + 5f
        canvas.drawLine(MARGIN, ly, right, ly, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return ly + 3f
    }

    // --- Totais --------------------------------------------------------------

    private fun drawTotalsBlock(canvas: Canvas, data: HoursReportPdfData, top: Float): Float {
        var y = top + 14f
        canvas.drawText("TOTAIS", MARGIN, y, paint(COLOR_TEXT, 12f, bold = true))
        y += 6f

        // Total de horas.
        y = drawTotalRow(canvas, "Total de horas", data.totalHoursLabel, y, bold = true)

        // Destaque do pendente em caixa colorida.
        data.totalPendingLabel?.let { pending ->
            y += 6f
            val boxTop = y
            val boxBottom = y + 26f
            canvas.drawRect(
                RectF(MARGIN, boxTop, right, boxBottom),
                Paint().apply { color = COLOR_TOTAL_BG; isAntiAlias = true },
            )
            val labelP = paint(COLOR_PENDING, 11f, bold = true)
            canvas.drawText("A receber (pendente)", MARGIN + 8f, boxTop + 17f, labelP)
            val valP = paint(COLOR_PENDING, 13f, bold = true).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(pending, right - 8f, boxTop + 17f, valP)
            y = boxBottom + 4f
        }

        data.totalPaidLabel?.let { y = drawTotalRow(canvas, "Pago", it, y) }
        data.totalContestedLabel?.let { y = drawTotalRow(canvas, "Contestado", it, y) }
        return y
    }

    private fun drawTotalRow(
        canvas: Canvas,
        label: String,
        value: String,
        top: Float,
        bold: Boolean = false,
    ): Float {
        val y = top + 14f
        canvas.drawText(label, MARGIN, y, paint(COLOR_MUTED, 10f, bold = bold))
        val valP = paint(COLOR_TEXT, 10.5f, bold = bold).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(value, right, y, valP)
        return y + 2f
    }

    // --- Comprovantes (grade de imagens) ------------------------------------

    private fun drawAttachmentGrid(
        ctx: RenderCtx,
        attachments: List<HoursReportAttachment>,
        top: Float,
    ): Float {
        val cols = 2
        val gap = 12f
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val imgH = cellW * 0.66f
        val captionH = 16f
        val cellH = imgH + captionH

        var y = top
        var i = 0
        while (i < attachments.size) {
            ctx.ensureSpace(cellH + 8f)
            y = ctx.y
            for (c in 0 until cols) {
                val idx = i + c
                if (idx >= attachments.size) break
                val x = MARGIN + c * (cellW + gap)
                drawAttachmentCell(ctx.canvas, attachments[idx], x, y, cellW, imgH)
            }
            y += cellH + 10f
            ctx.y = y
            i += cols
        }
        return y
    }

    private fun drawAttachmentCell(
        canvas: Canvas,
        attachment: HoursReportAttachment,
        x: Float,
        y: Float,
        w: Float,
        imgH: Float,
    ) {
        val bmp = decodeScaled(attachment.imageBytes, maxSize = 600)
        val frame = RectF(x, y, x + w, y + imgH)
        canvas.drawRect(frame, Paint().apply { color = COLOR_HEADER_BG })
        if (bmp != null) {
            // Center-crop dentro do frame.
            val scale = maxOf(w / bmp.width, imgH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dst = RectF(
                x + (w - dw) / 2f,
                y + (imgH - dh) / 2f,
                x + (w - dw) / 2f + dw,
                y + (imgH - dh) / 2f + dh,
            )
            canvas.save()
            canvas.clipRect(frame)
            canvas.drawBitmap(bmp, null, dst, null)
            canvas.restore()
        }
        canvas.drawRect(frame, Paint().apply {
            style = Paint.Style.STROKE; color = COLOR_LINE; strokeWidth = 0.5f
        })
        attachment.caption?.let {
            val p = paint(COLOR_MUTED, 8.5f)
            canvas.drawText(truncate(it, w, p), x, y + imgH + 11f, p)
        }
    }

    // --- Primitives ----------------------------------------------------------

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
}

actual fun createHoursReportPdfGenerator(): HoursReportPdfGenerator =
    AndroidHoursReportPdfGenerator()
