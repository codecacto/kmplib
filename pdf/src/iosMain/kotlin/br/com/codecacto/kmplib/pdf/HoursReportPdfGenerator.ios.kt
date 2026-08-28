package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF do relatório de horas extras no iOS (`UIGraphicsPDFRenderer` + CoreText),
 * paridade com [AndroidHoursReportPdfGenerator]: cabeçalho + tabela de lançamentos paginada + bloco
 * de totais (com destaque de pendente) + grade de comprovantes (imagens center-crop) + marca d'água.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS)** — espelha o par Android; build iOS não roda em Linux.
 */
private class IosHoursReportPdfGenerator : HoursReportPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_TOTAL_BG = PdfColor.argb(0xFFEFF6EF)
        val COLOR_PENDING = PdfColor.argb(0xFFB26A00)

        const val COL_DATE = 0.22
        const val COL_TIME = 0.26
        const val COL_DURATION = 0.18
        const val COL_VALUE = 0.18
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN
    private val usableWidth get() = right - MARGIN
    private val xDate get() = MARGIN
    private val xTime get() = MARGIN + usableWidth * COL_DATE
    private val xDuration get() = MARGIN + usableWidth * (COL_DATE + COL_TIME)
    private val xValue get() = MARGIN + usableWidth * (COL_DATE + COL_TIME + COL_DURATION)
    private val xStatus get() = MARGIN + usableWidth * (COL_DATE + COL_TIME + COL_DURATION + COL_VALUE)

    override fun generate(data: HoursReportPdfData): ByteArray {
        val watermark = if (data.watermark) data.watermarkText else null
        return renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark) {
            var y = MARGIN
            y = drawHeader(canvas, data, y)
            y += 12.0
            y = drawCompanyBlock(canvas, data, y)
            y += 16.0

            y = ensureSpace(this, y, 60.0)
            y = drawSectionTitle(canvas, "LANÇAMENTOS", y)
            y = drawTableHeader(canvas, y)
            if (data.entries.isEmpty()) {
                y = drawEmptyRow(canvas, "Nenhum lançamento no período.", y)
            } else {
                for (e in data.entries) {
                    y = ensureSpace(this, y, 26.0)
                    y = drawEntryRow(canvas, e, y)
                }
            }
            y += 14.0

            y = ensureSpace(this, y, 90.0)
            y = drawTotalsBlock(canvas, data, y)
            y += 16.0

            if (data.attachments.isNotEmpty()) {
                y = ensureSpace(this, y, 80.0)
                y = drawSectionTitle(canvas, "COMPROVANTES", y)
                drawAttachmentGrid(this, data.attachments, y)
            }
        }
    }

    private fun ensureSpace(flow: IosPageFlow, y: Double, needed: Double): Double =
        if (y <= PAGE_HEIGHT - MARGIN - needed) y else { flow.newPage(); MARGIN }

    private fun drawHeader(c: IosPdfCanvas, data: HoursReportPdfData, top: Double): Double {
        var textX = MARGIN
        var y = top
        data.company.logoBytes?.let {
            c.image(it, MARGIN, top, 64.0, 64.0)
            textX = MARGIN + 64.0 + 14.0
        }
        y += 16.0
        c.text(data.company.name, textX, y, 18.0, bold = true, color = COLOR_TEXT)
        data.company.phone?.let {
            y += 14.0
            c.text(it, textX, y, 10.0, bold = false, color = COLOR_MUTED)
        }

        var ry = top + 16.0
        c.text("Relatório de horas extras", right, ry, 14.0, bold = true, color = COLOR_TEXT, align = PdfTextAlign.Right)
        ry += 14.0
        c.text(data.periodLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        ry += 14.0
        c.text(data.generatedAtLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
    }

    private fun drawCompanyBlock(c: IosPdfCanvas, data: HoursReportPdfData, top: Double): Double {
        val y = top + 14.0
        c.text(c.truncate(data.companyLabel, right - MARGIN, 11.0, true), MARGIN, y, 11.0, bold = true, color = COLOR_TEXT)
        return y
    }

    private fun drawSectionTitle(c: IosPdfCanvas, title: String, top: Double): Double {
        val y = top + 12.0
        c.text(title, MARGIN, y, 12.0, bold = true, color = COLOR_TEXT)
        return y + 8.0
    }

    private fun drawEmptyRow(c: IosPdfCanvas, text: String, top: Double): Double {
        val y = top + 14.0
        c.text(text, MARGIN + 6.0, y, 10.0, bold = false, color = COLOR_MUTED)
        return y + 8.0
    }

    private fun drawTableHeader(c: IosPdfCanvas, top: Double): Double {
        val y = top + 12.0
        c.text("Data", xDate, y, 9.0, bold = true, color = COLOR_MUTED)
        c.text("Entrada–Saída", xTime, y, 9.0, bold = true, color = COLOR_MUTED)
        c.text("Duração", xDuration, y, 9.0, bold = true, color = COLOR_MUTED)
        c.text("Valor", xValue, y, 9.0, bold = true, color = COLOR_MUTED)
        c.text("Status", xStatus, y, 9.0, bold = true, color = COLOR_MUTED)
        val ly = y + 5.0
        c.line(MARGIN, ly, right, ly, COLOR_LINE, 1.0)
        return ly + 4.0
    }

    private fun drawEntryRow(c: IosPdfCanvas, e: HoursReportEntry, top: Double): Double {
        val y = top + 12.0
        c.text(c.truncate(e.date, xTime - xDate - 4.0, 9.5, false), xDate, y, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate("${e.start}–${e.end}", xDuration - xTime - 4.0, 9.5, false), xTime, y, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(e.durationLabel, xValue - xDuration - 4.0, 9.5, false), xDuration, y, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(e.valueLabel ?: "—", xStatus - xValue - 4.0, 9.5, false), xValue, y, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(e.statusLabel, right - xStatus, 9.5, false), xStatus, y, 9.5, bold = false, color = COLOR_TEXT)
        val ly = y + 5.0
        c.line(MARGIN, ly, right, ly, COLOR_LINE, 0.5)
        return ly + 3.0
    }

    private fun drawTotalsBlock(c: IosPdfCanvas, data: HoursReportPdfData, top: Double): Double {
        var y = top + 14.0
        c.text("TOTAIS", MARGIN, y, 12.0, bold = true, color = COLOR_TEXT)
        y += 6.0
        y = drawTotalRow(c, "Total de horas", data.totalHoursLabel, y, bold = true)

        data.totalPendingLabel?.let { pending ->
            y += 6.0
            val boxTop = y
            val boxBottom = y + 26.0
            c.fillRect(MARGIN, boxTop, right - MARGIN, boxBottom - boxTop, COLOR_TOTAL_BG)
            c.text("A receber (pendente)", MARGIN + 8.0, boxTop + 17.0, 11.0, bold = true, color = COLOR_PENDING)
            c.text(pending, right - 8.0, boxTop + 17.0, 13.0, bold = true, color = COLOR_PENDING, align = PdfTextAlign.Right)
            y = boxBottom + 4.0
        }

        data.totalPaidLabel?.let { y = drawTotalRow(c, "Pago", it, y) }
        data.totalContestedLabel?.let { y = drawTotalRow(c, "Contestado", it, y) }
        return y
    }

    private fun drawTotalRow(c: IosPdfCanvas, label: String, value: String, top: Double, bold: Boolean = false): Double {
        val y = top + 14.0
        c.text(label, MARGIN, y, 10.0, bold = bold, color = COLOR_MUTED)
        c.text(value, right, y, 10.5, bold = bold, color = COLOR_TEXT, align = PdfTextAlign.Right)
        return y + 2.0
    }

    private fun drawAttachmentGrid(flow: IosPageFlow, attachments: List<HoursReportAttachment>, top: Double): Double {
        val cols = 2
        val gap = 12.0
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val imgH = cellW * 0.66
        val captionH = 16.0
        val cellH = imgH + captionH

        var y = top
        var i = 0
        while (i < attachments.size) {
            y = if (y <= PAGE_HEIGHT - MARGIN - (cellH + 8.0)) y else { flow.newPage(); MARGIN }
            for (col in 0 until cols) {
                val idx = i + col
                if (idx >= attachments.size) break
                val x = MARGIN + col * (cellW + gap)
                drawAttachmentCell(flow.canvas, attachments[idx], x, y, cellW, imgH)
            }
            y += cellH + 10.0
            i += cols
        }
        return y
    }

    private fun drawAttachmentCell(c: IosPdfCanvas, attachment: HoursReportAttachment, x: Double, y: Double, w: Double, imgH: Double) {
        c.fillRect(x, y, w, imgH, COLOR_HEADER_BG)
        c.imageCrop(attachment.imageBytes, x, y, w, imgH)
        c.strokeRect(x, y, w, imgH, COLOR_LINE, 0.5)
        attachment.caption?.let {
            c.text(c.truncate(it, w, 8.5, false), x, y + imgH + 11.0, 8.5, bold = false, color = COLOR_MUTED)
        }
    }
}

actual fun createHoursReportPdfGenerator(): HoursReportPdfGenerator = IosHoursReportPdfGenerator()
