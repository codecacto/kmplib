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
 * Render nativo do PDF do relatório financeiro via `android.graphics.pdf.PdfDocument`.
 * Sem dependências externas e sem necessidade de `Context` (o logo é decodificado direto
 * dos bytes). Tamanho de página A4 a 72dpi (595 x 842 pt). Visual coerente com o recibo
 * de OS ([AndroidOsPdfGenerator]): mesma paleta, mesmas medidas de cabeçalho e tabelas.
 */
class AndroidFinanceReportPdfGenerator : FinanceReportPdfGenerator {

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
    }

    override fun generate(data: FinanceReportPdfData): ByteArray {
        val doc = PdfDocument()

        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        var y = MARGIN
        y = drawHeader(canvas, data, y)
        y += 16f

        // --- Seção R-A: Recebimentos -----------------------------------------
        y = drawSectionTitle(canvas, "RECEBIMENTOS", y)
        if (data.receipts.isEmpty()) {
            y = drawEmptyRow(canvas, "Nenhum recebimento no período.", y)
        } else {
            y = drawReceiptsHeader(canvas, y)
            for (r in data.receipts) {
                if (y > PAGE_HEIGHT - MARGIN - 60f) {
                    doc.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN
                    y = drawReceiptsHeader(canvas, y)
                }
                y = drawReceiptRow(canvas, r, y)
            }
        }
        y = drawTotalBox(canvas, "TOTAL RECEBIDO", data.totalReceived, y + 6f)
        y += 24f

        // --- Seção R-B: Contas a receber -------------------------------------
        if (y > PAGE_HEIGHT - MARGIN - 120f) {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }
        y = drawSectionTitle(canvas, "CONTAS A RECEBER", y)
        if (data.receivables.isEmpty()) {
            y = drawEmptyRow(canvas, "Nenhuma conta a receber.", y)
        } else {
            y = drawReceivablesHeader(canvas, y)
            for (r in data.receivables) {
                if (y > PAGE_HEIGHT - MARGIN - 60f) {
                    doc.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN
                    y = drawReceivablesHeader(canvas, y)
                }
                y = drawReceivableRow(canvas, r, y)
            }
        }
        drawTotalBox(canvas, "TOTAL A RECEBER", data.totalReceivable, y + 6f)

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

    private fun drawHeader(canvas: Canvas, data: FinanceReportPdfData, top: Float): Float {
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
        data.company.phone?.let { line ->
            y += 14f
            canvas.drawText(line, textX, y, mutedPaint)
        }

        // Bloco título/período/emissão à direita
        val titlePaint = paint(COLOR_TEXT, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val mutedRight = paint(COLOR_MUTED, 10f).apply { textAlign = Paint.Align.RIGHT }
        var ry = top + 16f
        canvas.drawText("Relatório financeiro", right, ry, titlePaint)
        ry += 14f
        canvas.drawText(data.periodLabel, right, ry, mutedRight)
        ry += 14f
        canvas.drawText(data.generatedAtLabel, right, ry, mutedRight)

        val bottom = maxOf(y, ry) + 12f
        val linePaint = Paint().apply { color = COLOR_LINE; strokeWidth = 1f }
        canvas.drawLine(MARGIN, bottom, right, bottom, linePaint)
        return bottom + 6f
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

    // --- R-A: Recebimentos (nº | cliente | pago em | status | recebido) -------

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    private fun drawReceiptsHeader(canvas: Canvas, top: Float): Float {
        val colNum = left + 6f
        val colClient = left + 36f
        val colPaid = right - 200f
        val colStatus = right - 120f
        val colAmount = right - 6f
        var y = top

        val headerH = 20f
        canvas.drawRect(left, y, right, y + headerH, Paint().apply { color = COLOR_HEADER_BG })
        val h = paint(COLOR_MUTED, 8.5f, bold = true)
        val hRight = paint(COLOR_MUTED, 8.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val baseline = y + 14f
        canvas.drawText("Nº", colNum, baseline, h)
        canvas.drawText("CLIENTE", colClient, baseline, h)
        canvas.drawText("PAGO EM", colPaid, baseline, h)
        canvas.drawText("STATUS", colStatus, baseline, h)
        canvas.drawText("RECEBIDO", colAmount, baseline, hRight)
        y += headerH
        return y
    }

    private fun drawReceiptRow(canvas: Canvas, r: FinanceReportReceipt, top: Float): Float {
        val colNum = left + 6f
        val colClient = left + 36f
        val colPaid = right - 200f
        val colStatus = right - 120f
        val colAmount = right - 6f
        val rowH = 18f
        val b = top + 13f

        val cell = paint(COLOR_TEXT, 9.5f)
        val cellRight = paint(COLOR_TEXT, 9.5f).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(r.number.toString(), colNum, b, cell)
        canvas.drawText(truncate(r.clientName, colPaid - colClient - 6f, cell), colClient, b, cell)
        canvas.drawText(truncate(r.paidAtLabel, colStatus - colPaid - 6f, cell), colPaid, b, cell)
        canvas.drawText(truncate(r.paymentStatusLabel, colAmount - 60f - colStatus - 6f, cell), colStatus, b, cell)
        canvas.drawText(OsPdfFormat.money(r.amountReceived), colAmount, b, cellRight)

        val y = top + rowH
        canvas.drawLine(left, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return y
    }

    // --- R-B: Contas a receber (nº | cliente | status | total | receb. | saldo)

    private fun drawReceivablesHeader(canvas: Canvas, top: Float): Float {
        val colNum = left + 6f
        val colClient = left + 36f
        val colStatus = right - 250f
        val colTotal = right - 168f
        val colRecv = right - 88f
        val colBalance = right - 6f
        var y = top

        val headerH = 20f
        canvas.drawRect(left, y, right, y + headerH, Paint().apply { color = COLOR_HEADER_BG })
        val h = paint(COLOR_MUTED, 8.5f, bold = true)
        val hRight = paint(COLOR_MUTED, 8.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val baseline = y + 14f
        canvas.drawText("Nº", colNum, baseline, h)
        canvas.drawText("CLIENTE", colClient, baseline, h)
        canvas.drawText("STATUS", colStatus, baseline, h)
        canvas.drawText("TOTAL", colTotal, baseline, hRight)
        canvas.drawText("RECEB.", colRecv, baseline, hRight)
        canvas.drawText("SALDO", colBalance, baseline, hRight)
        y += headerH
        return y
    }

    private fun drawReceivableRow(canvas: Canvas, r: FinanceReportReceivable, top: Float): Float {
        val colNum = left + 6f
        val colClient = left + 36f
        val colStatus = right - 250f
        val colTotal = right - 168f
        val colRecv = right - 88f
        val colBalance = right - 6f
        val rowH = 18f
        val b = top + 13f

        val cell = paint(COLOR_TEXT, 9.5f)
        val cellRight = paint(COLOR_TEXT, 9.5f).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(r.number.toString(), colNum, b, cell)
        canvas.drawText(truncate(r.clientName, colStatus - colClient - 6f, cell), colClient, b, cell)
        canvas.drawText(truncate(r.statusLabel, colTotal - 60f - colStatus - 6f, cell), colStatus, b, cell)
        canvas.drawText(OsPdfFormat.money(r.total), colTotal, b, cellRight)
        canvas.drawText(OsPdfFormat.money(r.amountReceived), colRecv, b, cellRight)
        canvas.drawText(OsPdfFormat.money(r.balance), colBalance, b, cellRight)

        val y = top + rowH
        canvas.drawLine(left, y, right, y, Paint().apply { color = COLOR_LINE; strokeWidth = 0.5f })
        return y
    }

    private fun drawTotalBox(canvas: Canvas, label: String, total: String, top: Float): Float {
        val r = PAGE_WIDTH - MARGIN
        val y = top + 12f
        val boxH = 30f
        val boxLeft = r - 240f
        canvas.drawRoundRect(
            RectF(boxLeft, y, r, y + boxH), 6f, 6f,
            Paint().apply { color = COLOR_TOTAL_BG; isAntiAlias = true },
        )
        val cy = y + 20f
        canvas.drawText(label, boxLeft + 12f, cy, paint(COLOR_TOTAL_TEXT, 10f, bold = true))
        canvas.drawText(
            OsPdfFormat.money(total),
            r - 12f, cy,
            paint(COLOR_TOTAL_TEXT, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT },
        )
        return y + boxH
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
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }
}

actual fun createFinanceReportPdfGenerator(): FinanceReportPdfGenerator =
    AndroidFinanceReportPdfGenerator()
