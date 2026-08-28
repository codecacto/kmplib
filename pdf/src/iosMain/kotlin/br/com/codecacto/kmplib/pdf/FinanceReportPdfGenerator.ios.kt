package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF do relatório financeiro no iOS (`UIGraphicsPDFRenderer` + CoreText), paridade
 * com [AndroidFinanceReportPdfGenerator]: cabeçalho + seção Recebimentos + seção Contas a receber,
 * cada uma com cabeçalho de tabela repetido por página e caixa de total destacada. Sem marca d'água
 * (igual ao Android).
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS)** — espelha o par Android; build iOS não roda em Linux.
 */
private class IosFinanceReportPdfGenerator : FinanceReportPdfGenerator {

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
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: FinanceReportPdfData): ByteArray =
        renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark = null) {
            var y = MARGIN
            y = drawHeader(canvas, data, y)
            y += 16.0

            // Recebimentos.
            y = drawSectionTitle(canvas, "RECEBIMENTOS", y)
            if (data.receipts.isEmpty()) {
                y = drawEmptyRow(canvas, "Nenhum recebimento no período.", y)
            } else {
                y = drawReceiptsHeader(canvas, y)
                for (r in data.receipts) {
                    if (y > PAGE_HEIGHT - MARGIN - 60.0) {
                        newPage()
                        y = drawReceiptsHeader(canvas, MARGIN)
                    }
                    y = drawReceiptRow(canvas, r, y)
                }
            }
            y = drawTotalBox(canvas, "TOTAL RECEBIDO", data.totalReceived, y + 6.0)
            y += 24.0

            // Contas a receber.
            if (y > PAGE_HEIGHT - MARGIN - 120.0) {
                newPage()
                y = MARGIN
            }
            y = drawSectionTitle(canvas, "CONTAS A RECEBER", y)
            if (data.receivables.isEmpty()) {
                y = drawEmptyRow(canvas, "Nenhuma conta a receber.", y)
            } else {
                y = drawReceivablesHeader(canvas, y)
                for (r in data.receivables) {
                    if (y > PAGE_HEIGHT - MARGIN - 60.0) {
                        newPage()
                        y = drawReceivablesHeader(canvas, MARGIN)
                    }
                    y = drawReceivableRow(canvas, r, y)
                }
            }
            drawTotalBox(canvas, "TOTAL A RECEBER", data.totalReceivable, y + 6.0)
        }

    private fun drawHeader(c: IosPdfCanvas, data: FinanceReportPdfData, top: Double): Double {
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
        c.text("Relatório financeiro", right, ry, 14.0, bold = true, color = COLOR_TEXT, align = PdfTextAlign.Right)
        ry += 14.0
        c.text(data.periodLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        ry += 14.0
        c.text(data.generatedAtLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
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

    private fun drawReceiptsHeader(c: IosPdfCanvas, top: Double): Double {
        val colNum = left + 6.0
        val colClient = left + 36.0
        val colPaid = right - 200.0
        val colStatus = right - 120.0
        val colAmount = right - 6.0
        val headerH = 20.0
        c.fillRect(left, top, right - left, headerH, COLOR_HEADER_BG)
        val baseline = top + 14.0
        c.text("Nº", colNum, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("CLIENTE", colClient, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("PAGO EM", colPaid, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("STATUS", colStatus, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("RECEBIDO", colAmount, baseline, 8.5, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        return top + headerH
    }

    private fun drawReceiptRow(c: IosPdfCanvas, r: FinanceReportReceipt, top: Double): Double {
        val colNum = left + 6.0
        val colClient = left + 36.0
        val colPaid = right - 200.0
        val colStatus = right - 120.0
        val colAmount = right - 6.0
        val rowH = 18.0
        val b = top + 13.0
        c.text(r.number.toString(), colNum, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(r.clientName, colPaid - colClient - 6.0, 9.5, false), colClient, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(r.paidAtLabel, colStatus - colPaid - 6.0, 9.5, false), colPaid, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(r.paymentStatusLabel, colAmount - 60.0 - colStatus - 6.0, 9.5, false), colStatus, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(OsPdfFormat.money(r.amountReceived), colAmount, b, 9.5, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
        val y = top + rowH
        c.line(left, y, right, y, COLOR_LINE, 0.5)
        return y
    }

    private fun drawReceivablesHeader(c: IosPdfCanvas, top: Double): Double {
        val colNum = left + 6.0
        val colClient = left + 36.0
        val colStatus = right - 250.0
        val colTotal = right - 168.0
        val colRecv = right - 88.0
        val colBalance = right - 6.0
        val headerH = 20.0
        c.fillRect(left, top, right - left, headerH, COLOR_HEADER_BG)
        val baseline = top + 14.0
        c.text("Nº", colNum, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("CLIENTE", colClient, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("STATUS", colStatus, baseline, 8.5, bold = true, color = COLOR_MUTED)
        c.text("TOTAL", colTotal, baseline, 8.5, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        c.text("RECEB.", colRecv, baseline, 8.5, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        c.text("SALDO", colBalance, baseline, 8.5, bold = true, color = COLOR_MUTED, align = PdfTextAlign.Right)
        return top + headerH
    }

    private fun drawReceivableRow(c: IosPdfCanvas, r: FinanceReportReceivable, top: Double): Double {
        val colNum = left + 6.0
        val colClient = left + 36.0
        val colStatus = right - 250.0
        val colTotal = right - 168.0
        val colRecv = right - 88.0
        val colBalance = right - 6.0
        val rowH = 18.0
        val b = top + 13.0
        c.text(r.number.toString(), colNum, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(r.clientName, colStatus - colClient - 6.0, 9.5, false), colClient, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(c.truncate(r.statusLabel, colTotal - 60.0 - colStatus - 6.0, 9.5, false), colStatus, b, 9.5, bold = false, color = COLOR_TEXT)
        c.text(OsPdfFormat.money(r.total), colTotal, b, 9.5, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
        c.text(OsPdfFormat.money(r.amountReceived), colRecv, b, 9.5, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
        c.text(OsPdfFormat.money(r.balance), colBalance, b, 9.5, bold = false, color = COLOR_TEXT, align = PdfTextAlign.Right)
        val y = top + rowH
        c.line(left, y, right, y, COLOR_LINE, 0.5)
        return y
    }

    private fun drawTotalBox(c: IosPdfCanvas, label: String, total: String, top: Double): Double {
        val r = PAGE_WIDTH - MARGIN
        val y = top + 12.0
        val boxH = 30.0
        val boxLeft = r - 240.0
        c.fillRoundRect(boxLeft, y, r - boxLeft, boxH, 6.0, COLOR_TOTAL_BG)
        val cy = y + 20.0
        c.text(label, boxLeft + 12.0, cy, 10.0, bold = true, color = COLOR_TOTAL_TEXT)
        c.text(OsPdfFormat.money(total), r - 12.0, cy, 14.0, bold = true, color = COLOR_TOTAL_TEXT, align = PdfTextAlign.Right)
        return y + boxH
    }
}

actual fun createFinanceReportPdfGenerator(): FinanceReportPdfGenerator = IosFinanceReportPdfGenerator()
