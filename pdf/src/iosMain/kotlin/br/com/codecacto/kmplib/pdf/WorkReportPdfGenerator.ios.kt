package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF do relatório de obra no iOS (`UIGraphicsPDFRenderer` + CoreText), paridade
 * com [AndroidWorkReportPdfGenerator]: cabeçalho + progresso geral (barra) + etapas (barra por etapa)
 * + registro fotográfico (fotos center-crop) + diário de obra + marca d'água.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS)** — espelha o par Android; build iOS não roda em Linux.
 */
private class IosWorkReportPdfGenerator : WorkReportPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
        val COLOR_BAR_TRACK = PdfColor.argb(0xFFE6E8EB)
        val COLOR_BAR_FILL = PdfColor.argb(0xFF2E7D32)
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: WorkReportPdfData): ByteArray {
        val watermark = if (data.watermark) data.watermarkText else null
        return renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark) {
            var y = MARGIN
            y = drawHeader(canvas, data, y)
            y += 12.0
            y = drawWorkBlock(canvas, data, y)
            y += 16.0

            y = ensureSpace(this, y, 60.0)
            y = drawSectionTitle(canvas, "ETAPAS", y)
            if (data.stages.isEmpty()) {
                y = drawEmptyRow(canvas, "Nenhuma etapa cadastrada.", y)
            } else {
                for (s in data.stages) {
                    y = ensureSpace(this, y, 34.0)
                    y = drawStageRow(canvas, s, y)
                }
            }
            y += 16.0

            if (data.photos.isNotEmpty()) {
                y = ensureSpace(this, y, 80.0)
                y = drawSectionTitle(canvas, "REGISTRO FOTOGRÁFICO", y)
                y = drawPhotoGrid(this, data.photos, y)
                y += 16.0
            }

            if (data.diaryEntries.isNotEmpty()) {
                y = ensureSpace(this, y, 60.0)
                y = drawSectionTitle(canvas, "DIÁRIO DE OBRA", y)
                for (d in data.diaryEntries) {
                    y = ensureSpace(this, y, 50.0)
                    y = drawDiaryBlock(canvas, d, y)
                }
            }
        }
    }

    private fun ensureSpace(flow: IosPageFlow, y: Double, needed: Double): Double =
        if (y <= PAGE_HEIGHT - MARGIN - needed) y else { flow.newPage(); MARGIN }

    private fun drawHeader(c: IosPdfCanvas, data: WorkReportPdfData, top: Double): Double {
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
        c.text("Acompanhamento de obra", right, ry, 14.0, bold = true, color = COLOR_TEXT, align = PdfTextAlign.Right)
        data.periodLabel?.let { ry += 14.0; c.text(it, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right) }
        ry += 14.0
        c.text(data.generatedAtLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
    }

    private fun drawWorkBlock(c: IosPdfCanvas, data: WorkReportPdfData, top: Double): Double {
        var y = top + 14.0
        c.text(data.workName, MARGIN, y, 15.0, bold = true, color = COLOR_TEXT)
        data.clientName?.let {
            y += 14.0
            c.text("Cliente: $it", MARGIN, y, 10.0, bold = false, color = COLOR_MUTED)
        }
        data.address?.let {
            y += 14.0
            c.text(it, MARGIN, y, 10.0, bold = false, color = COLOR_MUTED)
        }
        y += 18.0
        c.text("Progresso geral", MARGIN, y, 9.5, bold = true, color = COLOR_MUTED)
        y += 6.0
        val frac = (data.overallProgress / 100.0).coerceIn(0.0, 1.0)
        return drawProgressBar(c, MARGIN, y, right - MARGIN, frac, 10.0)
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

    private fun drawStageRow(c: IosPdfCanvas, s: WorkReportPdfStage, top: Double): Double {
        var y = top + 12.0
        val clamped = s.progress.coerceIn(0, 100)
        val frac = clamped / 100.0
        c.text(c.truncate(s.name, right - MARGIN - 120.0, 10.5, true), MARGIN, y, 10.5, bold = true, color = COLOR_TEXT)
        c.text("${s.statusLabel}  ·  $clamped%", right, y, 9.5, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)
        y += 6.0
        y = drawProgressBar(c, MARGIN, y, right - MARGIN, frac, 7.0)
        s.note?.takeIf { it.isNotBlank() }?.let {
            y = c.wrappedText(it, MARGIN, y + 11.0, right - MARGIN - MARGIN, 8.5, bold = false, color = COLOR_MUTED, lineHeight = 11.0)
        }
        y += 8.0
        c.line(MARGIN, y, right, y, COLOR_LINE, 0.5)
        return y
    }

    private fun drawPhotoGrid(flow: IosPageFlow, photos: List<WorkReportPhoto>, top: Double): Double {
        val cols = 2
        val gap = 12.0
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val imgH = cellW * 0.66
        val captionH = 38.0
        val cellH = imgH + captionH

        var y = top
        var i = 0
        while (i < photos.size) {
            y = if (y <= PAGE_HEIGHT - MARGIN - (cellH + 8.0)) y else { flow.newPage(); MARGIN }
            for (col in 0 until cols) {
                val idx = i + col
                if (idx >= photos.size) break
                val x = MARGIN + col * (cellW + gap)
                drawPhotoCell(flow.canvas, photos[idx], x, y, cellW, imgH)
            }
            y += cellH + 10.0
            i += cols
        }
        return y
    }

    private fun drawPhotoCell(c: IosPdfCanvas, photo: WorkReportPhoto, x: Double, y: Double, w: Double, imgH: Double) {
        c.fillRect(x, y, w, imgH, COLOR_HEADER_BG)
        c.imageCrop(photo.imageBytes, x, y, w, imgH)
        c.strokeRect(x, y, w, imgH, COLOR_LINE, 0.5)
        var ty = y + imgH + 11.0
        photo.stageName?.takeIf { it.isNotBlank() }?.let {
            c.text(c.truncate(it, w, 8.5, true), x, ty, 8.5, bold = true, color = COLOR_TEXT)
            ty += 11.0
        }
        photo.caption?.let {
            c.text(c.truncate(it, w, 8.5, false), x, ty, 8.5, bold = false, color = COLOR_TEXT)
            ty += 11.0
        }
        photo.takenAtLabel?.let {
            c.text(c.truncate(it, w, 8.0, false), x, ty, 8.0, bold = false, color = COLOR_MUTED)
        }
    }

    private fun drawDiaryBlock(c: IosPdfCanvas, d: WorkReportPdfDiaryEntry, top: Double): Double {
        var y = top + 12.0
        val header = buildString {
            append(d.dateLabel)
            d.weatherLabel?.let { append("  ·  $it") }
            d.crewLabel?.let { append("  ·  $it") }
            d.author?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
        }
        c.text(header, MARGIN, y, 10.0, bold = true, color = COLOR_TEXT)
        y += 4.0
        y = c.wrappedText(d.text, MARGIN, y + 10.0, right - MARGIN - MARGIN, 9.5, bold = false, color = COLOR_MUTED, lineHeight = 13.0)
        y += 8.0
        c.line(MARGIN, y, right, y, COLOR_LINE, 0.5)
        return y
    }

    private fun drawProgressBar(c: IosPdfCanvas, x: Double, top: Double, rightX: Double, progress: Double, height: Double): Double {
        val frac = progress.coerceIn(0.0, 1.0)
        val w = rightX - x
        val radius = height / 2.0
        c.fillRoundRect(x, top, w, height, radius, COLOR_BAR_TRACK)
        if (frac > 0.0) {
            c.fillRoundRect(x, top, w * frac, height, radius, COLOR_BAR_FILL)
        }
        return top + height
    }
}

actual fun createWorkReportPdfGenerator(): WorkReportPdfGenerator = IosWorkReportPdfGenerator()
