package br.com.codecacto.kmplib.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Render nativo do PDF da OS via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context` (o logo é decodificado
 * direto dos bytes). Tamanho de página A4 a 72dpi (595 x 842 pt).
 */
class AndroidOsPdfGenerator : OsPdfGenerator {

    private companion object {
        // A4 em pontos (72dpi)
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f

        const val COLOR_TEXT = 0xFF1A1A1A.toInt()
        const val COLOR_MUTED = 0xFF6B6B6B.toInt()
        const val COLOR_LINE = 0xFFDDDDDD.toInt()
        const val COLOR_HEADER_BG = 0xFFF2F4F7.toInt()
        const val COLOR_TOTAL_BG = 0xFF1A1A1A.toInt()
        const val COLOR_TOTAL_TEXT = 0xFFFFFFFF.toInt()
        const val COLOR_WATERMARK = 0x14000000 // ~8% preto
    }

    override fun generate(data: OsPdfData): ByteArray {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        var y = MARGIN
        y = drawHeader(canvas, data, y)
        y += 18f
        y = drawClient(canvas, data, y)
        y = drawDescription(canvas, data, y)
        y += 8f
        y = drawItemsTable(canvas, data, y)
        y += 6f
        y = drawTotals(canvas, data, y)
        drawNotes(canvas, data, y + 16f)
        drawFooter(canvas, data)
        if (data.watermark) drawWatermark(canvas, data.watermarkText)

        doc.finishPage(page)

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    // --- Paints ---------------------------------------------------------------

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    // --- Sections -------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, data: OsPdfData, top: Float): Float {
        val right = PAGE_WIDTH - MARGIN
        var textX = MARGIN
        var y = top

        // Logo (opcional) à esquerda
        val logo = data.company.logoBytes?.let { decodeScaled(it, maxSize = 64) }
        if (logo != null) {
            canvas.drawBitmap(logo, MARGIN, top, null)
            textX = MARGIN + logo.width + 14f
        }

        val namePaint = paint(COLOR_TEXT, 18f, bold = true)
        y += 16f
        canvas.drawText(data.company.name, textX, y, namePaint)

        val mutedPaint = paint(COLOR_MUTED, 10f)
        listOfNotNull(data.company.phone, data.company.email, data.company.address)
            .forEach { line ->
                y += 14f
                canvas.drawText(line, textX, y, mutedPaint)
            }

        // Bloco título/numero/status à direita
        val titlePaint = paint(COLOR_TEXT, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val mutedRight = paint(COLOR_MUTED, 10f).apply { textAlign = Paint.Align.RIGHT }
        var ry = top + 16f
        canvas.drawText(data.title, right, ry, titlePaint)
        ry += 14f
        canvas.drawText("Nº ${data.number}", right, ry, mutedRight)
        data.status?.let {
            ry += 14f
            canvas.drawText(it, right, ry, mutedRight)
        }

        val bottom = maxOf(y, ry) + 12f
        val linePaint = Paint().apply { color = COLOR_LINE; strokeWidth = 1f }
        canvas.drawLine(MARGIN, bottom, right, bottom, linePaint)
        return bottom + 6f
    }

    private fun drawClient(canvas: Canvas, data: OsPdfData, top: Float): Float {
        val client = data.client ?: return top
        var y = top
        val labelPaint = paint(COLOR_MUTED, 9f, bold = true)
        canvas.drawText("CLIENTE", MARGIN, y, labelPaint)
        y += 14f
        canvas.drawText(client.name, MARGIN, y, paint(COLOR_TEXT, 12f, bold = true))
        listOfNotNull(client.phone, client.address).forEach { line ->
            y += 13f
            canvas.drawText(line, MARGIN, y, paint(COLOR_MUTED, 10f))
        }
        return y + 10f
    }

    private fun drawDescription(canvas: Canvas, data: OsPdfData, top: Float): Float {
        val desc = data.description?.takeIf { it.isNotBlank() } ?: return top
        var y = top + 4f
        canvas.drawText("DESCRIÇÃO", MARGIN, y, paint(COLOR_MUTED, 9f, bold = true))
        y += 14f
        y = drawWrappedText(canvas, desc, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, paint(COLOR_TEXT, 11f), 14f)
        return y + 8f
    }

    private fun drawItemsTable(canvas: Canvas, data: OsPdfData, top: Float): Float {
        if (data.items.isEmpty()) return top
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN
        // Colunas: descrição | qtd | unit | subtotal
        val colQty = right - 230f
        val colUnit = right - 150f
        val colSub = right
        var y = top

        // Cabeçalho da tabela
        val headerH = 22f
        canvas.drawRect(left, y, right, y + headerH, Paint().apply { color = COLOR_HEADER_BG })
        val hPaint = paint(COLOR_MUTED, 9f, bold = true)
        val hRight = paint(COLOR_MUTED, 9f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val baseline = y + 15f
        canvas.drawText("DESCRIÇÃO", left + 6f, baseline, hPaint)
        canvas.drawText("QTD", colQty, baseline, hRight)
        canvas.drawText("UNIT.", colUnit, baseline, hRight)
        canvas.drawText("SUBTOTAL", colSub - 6f, baseline, hRight)
        y += headerH

        val cellPaint = paint(COLOR_TEXT, 10f)
        val cellRight = paint(COLOR_TEXT, 10f).apply { textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f }

        data.items.forEach { item ->
            val rowH = 20f
            val b = y + 14f
            // descrição (truncada para caber até colQty)
            val descMax = colQty - (left + 6f) - 40f
            canvas.drawText(truncate(item.description, descMax, cellPaint), left + 6f, b, cellPaint)
            canvas.drawText(item.quantity.toString(), colQty, b, cellRight)
            canvas.drawText(OsPdfFormat.money(item.unitPrice), colUnit, b, cellRight)
            canvas.drawText(OsPdfFormat.money(item.subtotal), colSub - 6f, b, cellRight)
            y += rowH
            canvas.drawLine(left, y, right, y, linePaint)
        }
        return y
    }

    private fun drawTotals(canvas: Canvas, data: OsPdfData, top: Float): Float {
        val right = PAGE_WIDTH - MARGIN
        var y = top + 16f

        if (OsPdfFormat.isNonZero(data.discount)) {
            val labelPaint = paint(COLOR_MUTED, 11f).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("Desconto", right - 130f, y, labelPaint)
            canvas.drawText(
                "- ${OsPdfFormat.money(data.discount)}",
                right,
                y,
                paint(COLOR_TEXT, 11f).apply { textAlign = Paint.Align.RIGHT },
            )
            y += 18f
        }

        // Total destacado em caixa escura
        val boxH = 34f
        val boxLeft = right - 230f
        canvas.drawRoundRect(
            RectF(boxLeft, y, right, y + boxH), 6f, 6f,
            Paint().apply { color = COLOR_TOTAL_BG; isAntiAlias = true },
        )
        val cy = y + 22f
        canvas.drawText(
            "TOTAL",
            boxLeft + 12f, cy,
            paint(COLOR_TOTAL_TEXT, 11f, bold = true),
        )
        canvas.drawText(
            OsPdfFormat.money(data.total),
            right - 12f, cy,
            paint(COLOR_TOTAL_TEXT, 15f, bold = true).apply { textAlign = Paint.Align.RIGHT },
        )
        return y + boxH
    }

    private fun drawNotes(canvas: Canvas, data: OsPdfData, top: Float): Float {
        val notes = data.notes?.takeIf { it.isNotBlank() } ?: return top
        var y = top
        canvas.drawText("OBSERVAÇÕES", MARGIN, y, paint(COLOR_MUTED, 9f, bold = true))
        y += 14f
        y = drawWrappedText(canvas, notes, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, paint(COLOR_MUTED, 10f), 13f)
        return y
    }

    private fun drawFooter(canvas: Canvas, data: OsPdfData) {
        val footer = data.footer?.takeIf { it.isNotBlank() } ?: return
        val right = PAGE_WIDTH - MARGIN
        val y = PAGE_HEIGHT - MARGIN + 8f
        val linePaint = Paint().apply { color = COLOR_LINE; strokeWidth = 1f }
        canvas.drawLine(MARGIN, y - 16f, right, y - 16f, linePaint)
        canvas.drawText(footer, MARGIN, y, paint(COLOR_MUTED, 9f))
    }

    private fun drawWatermark(canvas: Canvas, text: String) {
        if (text.isBlank()) return
        val p = paint(COLOR_WATERMARK, 48f, bold = true).apply { textAlign = Paint.Align.CENTER }
        canvas.save()
        canvas.rotate(-30f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
        canvas.drawText(text, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, p)
        canvas.restore()
    }

    // --- Helpers --------------------------------------------------------------

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
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
    ): Float {
        var y = startY
        val words = text.split(' ')
        val line = StringBuilder()
        fun flush() {
            if (line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
                line.clear()
            }
        }
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth) {
                flush()
                line.append(word)
            } else {
                line.clear()
                line.append(candidate)
            }
        }
        flush()
        return y
    }
}

actual fun createOsPdfGenerator(): OsPdfGenerator = AndroidOsPdfGenerator()
