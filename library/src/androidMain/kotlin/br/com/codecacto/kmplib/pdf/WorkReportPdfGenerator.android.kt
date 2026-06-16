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
 * Render nativo do PDF do relatório de obra via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context`. A4 a 72dpi (595 x 842 pt).
 * Visual coerente com [AndroidFinanceReportPdfGenerator] (mesma paleta/medidas de cabeçalho).
 */
class AndroidWorkReportPdfGenerator : WorkReportPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_BAR_TRACK = 0xFFE6E8EB.toInt()
        const val COLOR_BAR_FILL = 0xFF2E7D32.toInt()
        const val COLOR_WATERMARK = 0x1F1A1A1A
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: WorkReportPdfData): ByteArray {
        val doc = PdfDocument()
        val ctx = RenderCtx(doc)
        ctx.watermark = data.watermark
        ctx.watermarkText = data.watermarkText

        ctx.y = MARGIN
        ctx.y = drawHeader(ctx.canvas, data, ctx.y)
        ctx.y += 12f
        ctx.y = drawWorkBlock(ctx.canvas, data, ctx.y)
        ctx.y += 16f

        // --- Etapas ----------------------------------------------------------
        ctx.ensureSpace(60f)
        ctx.y = drawSectionTitle(ctx.canvas, "ETAPAS", ctx.y)
        if (data.stages.isEmpty()) {
            ctx.y = drawEmptyRow(ctx.canvas, "Nenhuma etapa cadastrada.", ctx.y)
        } else {
            for (s in data.stages) {
                ctx.ensureSpace(34f)
                ctx.y = drawStageRow(ctx.canvas, s, ctx.y)
            }
        }
        ctx.y += 16f

        // --- Registro fotográfico -------------------------------------------
        if (data.photos.isNotEmpty()) {
            ctx.ensureSpace(80f)
            ctx.y = drawSectionTitle(ctx.canvas, "REGISTRO FOTOGRÁFICO", ctx.y)
            ctx.y = drawPhotoGrid(ctx, data.photos, ctx.y)
            ctx.y += 16f
        }

        // --- Diário de obra --------------------------------------------------
        if (data.diaryEntries.isNotEmpty()) {
            ctx.ensureSpace(60f)
            ctx.y = drawSectionTitle(ctx.canvas, "DIÁRIO DE OBRA", ctx.y)
            for (d in data.diaryEntries) {
                ctx.ensureSpace(50f)
                ctx.y = drawDiaryBlock(ctx.canvas, d, ctx.y)
            }
        }

        ctx.finish()

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** Estado mutável da renderização paginada (espelha o loop do FinanceReport). */
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

    private fun drawHeader(canvas: Canvas, data: WorkReportPdfData, top: Float): Float {
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
        canvas.drawText("Acompanhamento de obra", right, ry, titlePaint)
        data.periodLabel?.let { ry += 14f; canvas.drawText(it, right, ry, mutedRight) }
        ry += 14f
        canvas.drawText(data.generatedAtLabel, right, ry, mutedRight)

        val bottom = maxOf(y, ry) + 12f
        canvas.drawLine(MARGIN, bottom, right, bottom, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return bottom + 6f
    }

    private fun drawWorkBlock(canvas: Canvas, data: WorkReportPdfData, top: Float): Float {
        var y = top + 14f
        canvas.drawText(data.workName, MARGIN, y, paint(COLOR_TEXT, 15f, bold = true))
        data.clientName?.let {
            y += 14f
            canvas.drawText("Cliente: $it", MARGIN, y, paint(COLOR_MUTED, 10f))
        }
        data.address?.let {
            y += 14f
            canvas.drawText(it, MARGIN, y, paint(COLOR_MUTED, 10f))
        }
        y += 18f
        canvas.drawText("Progresso geral", MARGIN, y, paint(COLOR_MUTED, 9.5f, bold = true))
        y += 6f
        val frac = (data.overallProgress / 100f).coerceIn(0f, 1f)
        y = drawProgressBar(canvas, MARGIN, y, right - MARGIN, frac, 10f)
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

    private fun drawStageRow(canvas: Canvas, s: WorkReportPdfStage, top: Float): Float {
        var y = top + 12f
        val clamped = s.progress.coerceIn(0, 100)
        val frac = clamped / 100f
        val pct = "$clamped%"
        val namePaint = paint(COLOR_TEXT, 10.5f, bold = true)
        canvas.drawText(truncate(s.name, right - MARGIN - 120f, namePaint), MARGIN, y, namePaint)
        val rightPaint = paint(COLOR_MUTED, 9.5f).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("${s.statusLabel}  ·  $pct", right, y, rightPaint)
        y += 6f
        y = drawProgressBar(canvas, MARGIN, y, right - MARGIN, frac, 7f)
        s.note?.takeIf { it.isNotBlank() }?.let {
            val notePaint = paint(COLOR_MUTED, 8.5f)
            y = drawWrappedText(canvas, it, MARGIN, y + 11f, right - MARGIN, notePaint, 11f)
        }
        y += 8f
        canvas.drawLine(MARGIN, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return y
    }

    private fun drawPhotoGrid(ctx: RenderCtx, photos: List<WorkReportPhoto>, top: Float): Float {
        val cols = 2
        val gap = 12f
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val imgH = cellW * 0.66f
        val captionH = 38f
        val cellH = imgH + captionH

        var y = top
        var i = 0
        while (i < photos.size) {
            ctx.ensureSpace(cellH + 8f)
            y = ctx.y
            for (c in 0 until cols) {
                val idx = i + c
                if (idx >= photos.size) break
                val x = MARGIN + c * (cellW + gap)
                drawPhotoCell(ctx.canvas, photos[idx], x, y, cellW, imgH, captionH)
            }
            y += cellH + 10f
            ctx.y = y
            i += cols
        }
        return y
    }

    private fun drawPhotoCell(
        canvas: Canvas,
        photo: WorkReportPhoto,
        x: Float,
        y: Float,
        w: Float,
        imgH: Float,
        captionH: Float,
    ) {
        val bmp = decodeScaled(photo.imageBytes, maxSize = 600)
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
        var ty = y + imgH + 11f
        photo.stageName?.takeIf { it.isNotBlank() }?.let {
            val p = paint(COLOR_TEXT, 8.5f, bold = true)
            canvas.drawText(truncate(it, w, p), x, ty, p)
            ty += 11f
        }
        photo.caption?.let {
            val p = paint(COLOR_TEXT, 8.5f)
            canvas.drawText(truncate(it, w, p), x, ty, p)
            ty += 11f
        }
        photo.takenAtLabel?.let {
            val p = paint(COLOR_MUTED, 8f)
            canvas.drawText(truncate(it, w, p), x, ty, p)
        }
    }

    private fun drawDiaryBlock(canvas: Canvas, d: WorkReportPdfDiaryEntry, top: Float): Float {
        var y = top + 12f
        val header = buildString {
            append(d.dateLabel)
            d.weatherLabel?.let { append("  ·  $it") }
            d.crewLabel?.let { append("  ·  $it") }
            d.author?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
        }
        canvas.drawText(header, MARGIN, y, paint(COLOR_TEXT, 10f, bold = true))
        y += 4f
        val bodyPaint = paint(COLOR_MUTED, 9.5f)
        y = drawWrappedText(canvas, d.text, MARGIN, y + 10f, right - MARGIN, bodyPaint, 13f)
        y += 8f
        canvas.drawLine(MARGIN, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return y
    }

    // --- Primitives ----------------------------------------------------------

    private fun drawProgressBar(
        canvas: Canvas,
        x: Float,
        top: Float,
        rightX: Float,
        progress: Float,
        height: Float,
    ): Float {
        val frac = progress.coerceIn(0f, 1f)
        val w = rightX - x
        val track = RectF(x, top, x + w, top + height)
        val radius = height / 2f
        canvas.drawRoundRect(track, radius, radius, Paint().apply { color = COLOR_BAR_TRACK; isAntiAlias = true })
        if (frac > 0f) {
            val fill = RectF(x, top, x + w * frac, top + height)
            canvas.drawRoundRect(fill, radius, radius, Paint().apply { color = COLOR_BAR_FILL; isAntiAlias = true })
        }
        return top + height
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

actual fun createWorkReportPdfGenerator(): WorkReportPdfGenerator =
    AndroidWorkReportPdfGenerator()
