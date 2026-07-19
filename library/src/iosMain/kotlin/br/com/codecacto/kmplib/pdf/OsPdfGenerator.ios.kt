package br.com.codecacto.kmplib.pdf

import kotlin.math.PI

/**
 * Render nativo do PDF da OS/orçamento/**recibo** no iOS via `UIGraphicsPDFRenderer` + CoreText
 * (helper [IosPdfCanvas]), com o **MESMO layout lógico e as MESMAS cores** do renderer Android
 * ([AndroidOsPdfGenerator]) — paridade Android=iOS. Este é o gerador consumido pelo **recibo do
 * Arroba Certa** (`OsPdfData`: empresa=fazenda, cliente=comprador, itens=discriminação do cálculo
 * de arroba) e é o **gate da Onda 1** do projeto (ADR-0003).
 *
 * Não é mais stub — quita a dívida `kmplib-ios-pdf-stub-debt` para este gerador. Validação visual
 * em host macOS/CI (iOS não compila em Linux).
 */
private class IosOsPdfGenerator : OsPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_TOTAL_BG = PdfColor.argb(0xFF1A1A1A)
        val COLOR_TOTAL_TEXT = PdfColor.argb(0xFFFFFFFF)
        val COLOR_WATERMARK = PdfColor.argb(0x14000000) // ~8% preto
    }

    override fun generate(data: OsPdfData): ByteArray = renderIosPdf(PAGE_WIDTH, PAGE_HEIGHT) {
        var y = MARGIN
        y = drawHeader(this, data, y)
        y += 18.0
        y = drawClient(this, data, y)
        y = drawDescription(this, data, y)
        y += 8.0
        y = drawItemsTable(this, data, y)
        y += 6.0
        y = drawTotals(this, data, y)
        drawNotes(this, data, y + 16.0)
        drawFooter(this, data)
        if (data.watermark) drawWatermark(this, data.watermarkText)
    }

    private fun drawHeader(c: IosPdfCanvas, data: OsPdfData, top: Double): Double {
        val right = PAGE_WIDTH - MARGIN
        var textX = MARGIN
        var y = top

        data.company.logoBytes?.let { bytes ->
            c.image(bytes, MARGIN, top, 64.0, 64.0)
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
        ry += 14.0
        c.text("Nº ${data.number}", right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        data.status?.let {
            ry += 14.0
            c.text(it, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        }

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
    }

    private fun drawClient(c: IosPdfCanvas, data: OsPdfData, top: Double): Double {
        val client = data.client ?: return top
        var y = top
        c.text("CLIENTE", MARGIN, y, 9.0, bold = true, color = COLOR_MUTED)
        y += 14.0
        c.text(client.name, MARGIN, y, 12.0, bold = true, color = COLOR_TEXT)
        listOfNotNull(client.phone, client.address).forEach { line ->
            y += 13.0
            c.text(line, MARGIN, y, 10.0, bold = false, color = COLOR_MUTED)
        }
        return y + 10.0
    }

    private fun drawDescription(c: IosPdfCanvas, data: OsPdfData, top: Double): Double {
        val desc = data.description?.takeIf { it.isNotBlank() } ?: return top
        var y = top + 4.0
        c.text("DESCRIÇÃO", MARGIN, y, 9.0, bold = true, color = COLOR_MUTED)
        y += 14.0
        y = c.wrappedText(desc, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, 11.0, bold = false, color = COLOR_TEXT, lineHeight = 14.0)
        return y + 8.0
    }

    private fun drawItemsTable(c: IosPdfCanvas, data: OsPdfData, top: Double): Double {
        if (data.items.isEmpty()) return top
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN
        val colQty = right - 230.0
        val colUnit = right - 150.0
        val colSub = right
        var y = top

        val headerH = 22.0
        c.fillRect(left, y, right - left, headerH, COLOR_HEADER_BG)
        val baseline = y + 15.0
        c.text("DESCRIÇÃO", left + 6.0, baseline, 9.0, bold = true, color = COLOR_MUTED)
        c.text("QTD", colQty, baseline, 9.0, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        c.text("UNIT.", colUnit, baseline, 9.0, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        c.text("SUBTOTAL", colSub - 6.0, baseline, 9.0, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        y += headerH

        data.items.forEach { item ->
            val rowH = 20.0
            val b = y + 14.0
            val descMax = colQty - (left + 6.0) - 40.0
            c.text(c.truncate(item.description, descMax, 10.0, false), left + 6.0, b, 10.0, bold = false, color = COLOR_TEXT)
            c.text(item.quantity.toString(), colQty, b, 10.0, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
            c.text(OsPdfFormat.money(item.unitPrice), colUnit, b, 10.0, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
            c.text(OsPdfFormat.money(item.subtotal), colSub - 6.0, b, 10.0, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
            y += rowH
            c.line(left, y, right, y, COLOR_LINE, 0.5)
        }
        return y
    }

    private fun drawTotals(c: IosPdfCanvas, data: OsPdfData, top: Double): Double {
        val right = PAGE_WIDTH - MARGIN
        var y = top + 16.0

        if (OsPdfFormat.isNonZero(data.discount)) {
            c.text("Desconto", right - 130.0, y, 11.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
            c.text("- ${OsPdfFormat.money(data.discount)}", right, y, 11.0, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
            y += 18.0
        }

        val boxH = 34.0
        val boxLeft = right - 230.0
        c.fillRoundRect(boxLeft, y, right - boxLeft, boxH, 6.0, COLOR_TOTAL_BG)
        val cy = y + 22.0
        c.text("TOTAL", boxLeft + 12.0, cy, 11.0, bold = true, color = COLOR_TOTAL_TEXT)
        c.text(OsPdfFormat.money(data.total), right - 12.0, cy, 15.0, bold = true, color = COLOR_TOTAL_TEXT, align = PdfTextAlign.Right)
        return y + boxH
    }

    private fun drawNotes(c: IosPdfCanvas, data: OsPdfData, top: Double): Double {
        val notes = data.notes?.takeIf { it.isNotBlank() } ?: return top
        var y = top
        c.text("OBSERVAÇÕES", MARGIN, y, 9.0, bold = true, color = COLOR_MUTED)
        y += 14.0
        y = c.wrappedText(notes, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, 10.0, bold = false, color = COLOR_MUTED, lineHeight = 13.0)
        return y
    }

    private fun drawFooter(c: IosPdfCanvas, data: OsPdfData) {
        val footer = data.footer?.takeIf { it.isNotBlank() } ?: return
        val right = PAGE_WIDTH - MARGIN
        val y = PAGE_HEIGHT - MARGIN + 8.0
        c.line(MARGIN, y - 16.0, right, y - 16.0, COLOR_LINE, 1.0)
        c.text(footer, MARGIN, y, 9.0, bold = false, color = COLOR_MUTED)
    }

    private fun drawWatermark(c: IosPdfCanvas, text: String) {
        if (text.isBlank()) return
        val cx = PAGE_WIDTH / 2.0
        val cy = PAGE_HEIGHT / 2.0
        c.save()
        c.translate(cx, cy)
        c.rotate(-30.0 * PI / 180.0)
        c.translate(-cx, -cy)
        c.text(text, cx, cy, 48.0, bold = true, color = COLOR_WATERMARK, align = PdfTextAlign.Center)
        c.restore()
    }
}

actual fun createOsPdfGenerator(): OsPdfGenerator = IosOsPdfGenerator()
