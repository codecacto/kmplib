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
 * Render nativo do PDF de vistoria via `android.graphics.pdf.PdfDocument`, com **imagens embarcadas
 * de verdade** (foto-prova por item + assinaturas), usando a MESMA técnica de `Canvas.drawBitmap`
 * do [AndroidWorkReportPdfGenerator]. Sem dependências externas e sem necessidade de `Context`.
 * A4 a 72dpi (595 x 842 pt); visual coerente com os demais geradores do módulo.
 */
class AndroidInspectionPdfGenerator : InspectionPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_WATERMARK = 0x1F1A1A1A
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: InspectionPdfData): ByteArray {
        val doc = PdfDocument()
        val ctx = RenderCtx(doc)
        ctx.watermark = data.watermark
        ctx.watermarkText = data.watermarkText

        ctx.y = MARGIN
        ctx.y = drawHeader(ctx.canvas, data, ctx.y)
        ctx.y += 12f
        ctx.y = drawVehicleBlock(ctx.canvas, data, ctx.y)
        ctx.y += 12f

        // --- Terceiro (modo Laudo) ------------------------------------------
        data.thirdParty?.let { tp ->
            ctx.ensureSpace(70f)
            ctx.y = drawSectionTitle(ctx.canvas, "IDENTIFICAÇÃO", ctx.y)
            ctx.y = drawThirdParty(ctx.canvas, tp, ctx.y)
            ctx.y += 12f
        }

        // --- Itens agrupados por seção --------------------------------------
        if (data.sections.isEmpty()) {
            ctx.ensureSpace(40f)
            ctx.y = drawSectionTitle(ctx.canvas, "VISTORIA", ctx.y)
            ctx.y = drawEmptyRow(ctx.canvas, "Nenhum item vistoriado.", ctx.y)
        } else {
            for (section in data.sections) {
                ctx.ensureSpace(50f)
                ctx.y = drawSectionTitle(ctx.canvas, section.title.uppercase(), ctx.y)
                if (section.items.isEmpty()) {
                    ctx.y = drawEmptyRow(ctx.canvas, "Sem itens.", ctx.y)
                } else {
                    for (item in section.items) {
                        ctx.y = drawItem(ctx, item, ctx.y)
                    }
                }
                ctx.y += 8f
            }
        }

        // --- Assinaturas embarcadas (modo Laudo) ----------------------------
        val signatures = data.signatures
        if (signatures.isNotEmpty()) {
            ctx.ensureSpace(120f)
            ctx.y += 8f
            ctx.y = drawSectionTitle(ctx.canvas, "ASSINATURAS", ctx.y)
            ctx.y = drawSignatures(ctx, signatures, ctx.y)
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

    private fun drawHeader(canvas: Canvas, data: InspectionPdfData, top: Float): Float {
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
        canvas.drawText(data.title, right, ry, titlePaint)
        data.contextLabel?.takeIf { it.isNotBlank() }?.let { ry += 14f; canvas.drawText(it, right, ry, mutedRight) }
        ry += 14f
        canvas.drawText(data.generatedAtLabel, right, ry, mutedRight)

        val bottom = maxOf(y, ry) + 12f
        canvas.drawLine(MARGIN, bottom, right, bottom, Paint().apply { color = COLOR_LINE; strokeWidth = 1f })
        return bottom + 6f
    }

    private fun drawVehicleBlock(canvas: Canvas, data: InspectionPdfData, top: Float): Float {
        var y = top + 14f
        canvas.drawText(data.vehicle.nickname.ifBlank { "Veículo" }, MARGIN, y, paint(COLOR_TEXT, 15f, bold = true))
        val details = buildString {
            append(data.vehicle.plate)
            data.vehicle.typeLabel?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
        }
        y += 14f
        canvas.drawText(details, MARGIN, y, paint(COLOR_MUTED, 10.5f))
        return y + 4f
    }

    private fun drawThirdParty(canvas: Canvas, tp: InspectionPdfThirdParty, top: Float): Float {
        var y = top + 12f
        val rows = buildList {
            add("Responsável" to buildString {
                append(tp.responsibleName)
                tp.responsibleDocument?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
            })
            tp.clientName?.takeIf { it.isNotBlank() }?.let { name ->
                add("Cliente" to buildString {
                    append(name)
                    tp.clientDocument?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                })
            }
        }
        for ((label, value) in rows) {
            canvas.drawText("$label:", MARGIN, y, paint(COLOR_MUTED, 9.5f, bold = true))
            canvas.drawText(truncate(value, right - MARGIN - 90f, paint(COLOR_TEXT, 10f)), MARGIN + 84f, y, paint(COLOR_TEXT, 10f))
            y += 15f
        }
        return y
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, top: Float): Float {
        val y = top + 14f
        canvas.drawText(title, MARGIN, y, paint(COLOR_TEXT, 12f, bold = true))
        canvas.drawLine(MARGIN, y + 5f, right, y + 5f, Paint().apply { color = COLOR_LINE; strokeWidth = 0.75f })
        return y + 12f
    }

    private fun drawEmptyRow(canvas: Canvas, text: String, top: Float): Float {
        val y = top + 12f
        canvas.drawText(text, MARGIN + 6f, y, paint(COLOR_MUTED, 10f))
        return y + 6f
    }

    private fun drawItem(ctx: RenderCtx, item: InspectionPdfItem, top: Float): Float {
        // Reserva espaço para: linha do item (+observação) + eventuais fotos.
        ctx.ensureSpace(30f)
        var y = ctx.y.coerceAtLeast(top)
        y += 13f

        val namePaint = paint(COLOR_TEXT, 10.5f, bold = false)
        val statusPaint = paint(item.statusColorArgb ?: COLOR_TEXT, 10.5f, bold = true).apply {
            textAlign = Paint.Align.RIGHT
        }
        val statusWidth = statusPaint.measureText(item.statusLabel) + 8f
        ctx.canvas.drawText(truncate(item.text, right - MARGIN - statusWidth, namePaint), MARGIN, y, namePaint)
        ctx.canvas.drawText(item.statusLabel, right, y, statusPaint)

        item.observation?.takeIf { it.isNotBlank() }?.let {
            val notePaint = paint(COLOR_MUTED, 8.5f)
            y = drawWrappedText(ctx.canvas, it, MARGIN, y + 11f, right - MARGIN, notePaint, 11f)
        }

        ctx.y = y + 6f
        // Fotos-prova embarcadas do item (grade de miniaturas).
        if (item.photos.isNotEmpty()) {
            ctx.y = drawItemPhotos(ctx, item.photos, ctx.y)
        }
        ctx.canvas.drawLine(MARGIN, ctx.y, right, ctx.y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        ctx.y += 4f
        return ctx.y
    }

    private fun drawItemPhotos(ctx: RenderCtx, photos: List<ByteArray>, top: Float): Float {
        val cols = 4
        val gap = 8f
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val cellH = cellW * 0.72f

        var y = top + 2f
        var i = 0
        while (i < photos.size) {
            ctx.ensureSpace(cellH + 8f)
            y = ctx.y
            for (c in 0 until cols) {
                val idx = i + c
                if (idx >= photos.size) break
                val x = MARGIN + c * (cellW + gap)
                drawPhotoCell(ctx.canvas, photos[idx], x, y, cellW, cellH)
            }
            y += cellH + 6f
            ctx.y = y
            i += cols
        }
        return y
    }

    private fun drawPhotoCell(canvas: Canvas, bytes: ByteArray, x: Float, y: Float, w: Float, h: Float) {
        val frame = RectF(x, y, x + w, y + h)
        canvas.drawRect(frame, Paint().apply { color = COLOR_HEADER_BG })
        val bmp = decodeScaled(bytes, maxSize = 400)
        if (bmp != null) {
            val scale = maxOf(w / bmp.width, h / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dst = RectF(
                x + (w - dw) / 2f,
                y + (h - dh) / 2f,
                x + (w - dw) / 2f + dw,
                y + (h - dh) / 2f + dh,
            )
            canvas.save()
            canvas.clipRect(frame)
            canvas.drawBitmap(bmp, null, dst, null)
            canvas.restore()
        }
        canvas.drawRect(frame, Paint().apply {
            style = Paint.Style.STROKE; color = COLOR_LINE; strokeWidth = 0.5f
        })
    }

    private fun drawSignatures(ctx: RenderCtx, signatures: List<InspectionPdfSignature>, top: Float): Float {
        val cols = 2
        val gap = 24f
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val imgH = 70f
        val cellH = imgH + 34f

        var y = top + 6f
        var i = 0
        while (i < signatures.size) {
            ctx.ensureSpace(cellH + 8f)
            y = ctx.y
            for (c in 0 until cols) {
                val idx = i + c
                if (idx >= signatures.size) break
                val x = MARGIN + c * (cellW + gap)
                drawSignatureCell(ctx.canvas, signatures[idx], x, y, cellW, imgH)
            }
            y += cellH + 10f
            ctx.y = y
            i += cols
        }
        return y
    }

    private fun drawSignatureCell(
        canvas: Canvas,
        sig: InspectionPdfSignature,
        x: Float,
        y: Float,
        w: Float,
        imgH: Float,
    ) {
        val bmp = decodeScaled(sig.pngBytes, maxSize = 400)
        if (bmp != null) {
            // A assinatura ocupa a largura da célula, ancorada acima da linha (proporção preservada).
            val scale = minOf(w / bmp.width, imgH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dst = RectF(
                x + (w - dw) / 2f,
                y + (imgH - dh),
                x + (w - dw) / 2f + dw,
                y + imgH,
            )
            canvas.drawBitmap(bmp, null, dst, null)
        }
        // Linha da assinatura.
        val lineY = y + imgH + 2f
        canvas.drawLine(x, lineY, x + w, lineY, Paint().apply { color = COLOR_TEXT; strokeWidth = 0.75f })
        // Rótulo + nome sob a linha.
        var ty = lineY + 12f
        canvas.drawText(truncate(sig.label, w, paint(COLOR_TEXT, 9f, bold = true)), x, ty, paint(COLOR_TEXT, 9f, bold = true))
        sig.name?.takeIf { it.isNotBlank() }?.let {
            ty += 11f
            canvas.drawText(truncate(it, w, paint(COLOR_MUTED, 8.5f)), x, ty, paint(COLOR_MUTED, 8.5f))
        }
    }

    // --- Primitives ----------------------------------------------------------

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

actual fun createInspectionPdfGenerator(): InspectionPdfGenerator =
    AndroidInspectionPdfGenerator()
