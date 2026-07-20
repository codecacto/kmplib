package br.com.codecacto.kmplib.pdf

/**
 * Render nativo do PDF de vistoria no iOS (`UIGraphicsPDFRenderer` + CoreText), paridade com
 * [AndroidInspectionPdfGenerator]: cabeçalho + veículo + identificação (modo Laudo) + itens por seção
 * com foto-prova embarcada (grade de miniaturas center-crop) + assinaturas embarcadas + marca d'água.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS)** — espelha o par Android; build iOS não roda em Linux.
 */
private class IosInspectionPdfGenerator : InspectionPdfGenerator {

    private companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 40.0

        val COLOR_TEXT = PdfColor.argb(0xFF1A1A1A)
        val COLOR_MUTED = PdfColor.argb(0xFF6B6B6B)
        val COLOR_LINE = PdfColor.argb(0xFFDDDDDD)
        val COLOR_HEADER_BG = PdfColor.argb(0xFFF2F4F7)
    }

    private val left get() = MARGIN
    private val right get() = PAGE_WIDTH - MARGIN

    override fun generate(data: InspectionPdfData): ByteArray {
        val watermark = if (data.watermark) data.watermarkText else null
        return renderIosPdfPaged(PAGE_WIDTH, PAGE_HEIGHT, watermark) {
            var y = MARGIN
            y = drawHeader(canvas, data, y)
            y += 12.0
            y = drawVehicleBlock(canvas, data, y)
            y += 12.0

            data.thirdParty?.let { tp ->
                y = ensureSpace(this, y, 70.0)
                y = drawSectionTitle(canvas, "IDENTIFICAÇÃO", y)
                y = drawThirdParty(canvas, tp, y)
                y += 12.0
            }

            if (data.sections.isEmpty()) {
                y = ensureSpace(this, y, 40.0)
                y = drawSectionTitle(canvas, "VISTORIA", y)
                y = drawEmptyRow(canvas, "Nenhum item vistoriado.", y)
            } else {
                for (section in data.sections) {
                    y = ensureSpace(this, y, 50.0)
                    y = drawSectionTitle(canvas, section.title.uppercase(), y)
                    if (section.items.isEmpty()) {
                        y = drawEmptyRow(canvas, "Sem itens.", y)
                    } else {
                        for (item in section.items) {
                            y = drawItem(this, item, y)
                        }
                    }
                    y += 8.0
                }
            }

            val signatures = data.signatures
            if (signatures.isNotEmpty()) {
                y = ensureSpace(this, y, 120.0)
                y += 8.0
                y = drawSectionTitle(canvas, "ASSINATURAS", y)
                drawSignatures(this, signatures, y)
            }
        }
    }

    private fun ensureSpace(flow: IosPageFlow, y: Double, needed: Double): Double =
        if (y <= PAGE_HEIGHT - MARGIN - needed) y else { flow.newPage(); MARGIN }

    private fun drawHeader(c: IosPdfCanvas, data: InspectionPdfData, top: Double): Double {
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
        c.text(data.title, right, ry, 14.0, bold = true, color = COLOR_TEXT, align = PdfTextAlign.Right)
        data.contextLabel?.takeIf { it.isNotBlank() }?.let { ry += 14.0; c.text(it, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right) }
        ry += 14.0
        c.text(data.generatedAtLabel, right, ry, 10.0, bold = false, color = COLOR_MUTED, align = PdfTextAlign.Right)

        val bottom = maxOf(y, ry) + 12.0
        c.line(MARGIN, bottom, right, bottom, COLOR_LINE, 1.0)
        return bottom + 6.0
    }

    private fun drawVehicleBlock(c: IosPdfCanvas, data: InspectionPdfData, top: Double): Double {
        var y = top + 14.0
        c.text(data.vehicle.nickname.ifBlank { "Veículo" }, MARGIN, y, 15.0, bold = true, color = COLOR_TEXT)
        val details = buildString {
            append(data.vehicle.plate)
            data.vehicle.typeLabel?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
        }
        y += 14.0
        c.text(details, MARGIN, y, 10.5, bold = false, color = COLOR_MUTED)
        return y + 4.0
    }

    private fun drawThirdParty(c: IosPdfCanvas, tp: InspectionPdfThirdParty, top: Double): Double {
        var y = top + 12.0
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
            c.text("$label:", MARGIN, y, 9.5, bold = true, color = COLOR_MUTED)
            c.text(c.truncate(value, right - MARGIN - 90.0, 10.0, false), MARGIN + 84.0, y, 10.0, bold = false, color = COLOR_TEXT)
            y += 15.0
        }
        return y
    }

    private fun drawSectionTitle(c: IosPdfCanvas, title: String, top: Double): Double {
        val y = top + 14.0
        c.text(title, MARGIN, y, 12.0, bold = true, color = COLOR_TEXT)
        c.line(MARGIN, y + 5.0, right, y + 5.0, COLOR_LINE, 0.75)
        return y + 12.0
    }

    private fun drawEmptyRow(c: IosPdfCanvas, text: String, top: Double): Double {
        val y = top + 12.0
        c.text(text, MARGIN + 6.0, y, 10.0, bold = false, color = COLOR_MUTED)
        return y + 6.0
    }

    private fun drawItem(flow: IosPageFlow, item: InspectionPdfItem, top: Double): Double {
        // Espelha o Android: `ensureSpace(30f)` e depois `y = ctx.y.coerceAtLeast(top)`.
        val afterEnsure = ensureSpace(flow, top, 30.0)
        var yy = maxOf(afterEnsure, top)
        yy += 13.0

        val c = flow.canvas
        val statusColor = item.statusColorArgb?.let { PdfColor.argb(it.toLong() and 0xFFFFFFFFL) } ?: COLOR_TEXT
        val statusWidth = c.measure(item.statusLabel, 10.5, true) + 8.0
        c.text(c.truncate(item.text, right - MARGIN - statusWidth, 10.5, false), MARGIN, yy, 10.5, bold = false, color = COLOR_TEXT)
        c.text(item.statusLabel, right, yy, 10.5, bold = true, color = statusColor, align = PdfTextAlign.Right)

        item.observation?.takeIf { it.isNotBlank() }?.let {
            yy = c.wrappedText(it, MARGIN, yy + 11.0, right - MARGIN - MARGIN, 8.5, bold = false, color = COLOR_MUTED, lineHeight = 11.0)
        }

        var cursor = yy + 6.0
        if (item.photos.isNotEmpty()) {
            cursor = drawItemPhotos(flow, item.photos, cursor)
        }
        flow.canvas.line(MARGIN, cursor, right, cursor, COLOR_LINE, 0.5)
        return cursor + 4.0
    }

    private fun drawItemPhotos(flow: IosPageFlow, photos: List<ByteArray>, top: Double): Double {
        val cols = 4
        val gap = 8.0
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val cellH = cellW * 0.72

        var y = top + 2.0
        var i = 0
        while (i < photos.size) {
            y = if (y <= PAGE_HEIGHT - MARGIN - (cellH + 8.0)) y else { flow.newPage(); MARGIN }
            for (col in 0 until cols) {
                val idx = i + col
                if (idx >= photos.size) break
                val x = MARGIN + col * (cellW + gap)
                drawPhotoCell(flow.canvas, photos[idx], x, y, cellW, cellH)
            }
            y += cellH + 6.0
            i += cols
        }
        return y
    }

    private fun drawPhotoCell(c: IosPdfCanvas, bytes: ByteArray, x: Double, y: Double, w: Double, h: Double) {
        c.fillRect(x, y, w, h, COLOR_HEADER_BG)
        c.imageCrop(bytes, x, y, w, h)
        c.strokeRect(x, y, w, h, COLOR_LINE, 0.5)
    }

    private fun drawSignatures(flow: IosPageFlow, signatures: List<InspectionPdfSignature>, top: Double): Double {
        val cols = 2
        val gap = 24.0
        val cellW = (right - MARGIN - gap * (cols - 1)) / cols
        val imgH = 70.0
        val cellH = imgH + 34.0

        var y = top + 6.0
        var i = 0
        while (i < signatures.size) {
            y = if (y <= PAGE_HEIGHT - MARGIN - (cellH + 8.0)) y else { flow.newPage(); MARGIN }
            for (col in 0 until cols) {
                val idx = i + col
                if (idx >= signatures.size) break
                val x = MARGIN + col * (cellW + gap)
                drawSignatureCell(flow.canvas, signatures[idx], x, y, cellW, imgH)
            }
            y += cellH + 10.0
            i += cols
        }
        return y
    }

    private fun drawSignatureCell(c: IosPdfCanvas, sig: InspectionPdfSignature, x: Double, y: Double, w: Double, imgH: Double) {
        // A assinatura é *contain* dentro da caixa (proporção preservada), ancorada acima da linha.
        c.image(sig.pngBytes, x, y, w, imgH)
        val lineY = y + imgH + 2.0
        c.line(x, lineY, x + w, lineY, COLOR_TEXT, 0.75)
        var ty = lineY + 12.0
        c.text(c.truncate(sig.label, w, 9.0, true), x, ty, 9.0, bold = true, color = COLOR_TEXT)
        sig.name?.takeIf { it.isNotBlank() }?.let {
            ty += 11.0
            c.text(c.truncate(it, w, 8.5, false), x, ty, 8.5, bold = false, color = COLOR_MUTED)
        }
    }
}

actual fun createInspectionPdfGenerator(): InspectionPdfGenerator = IosInspectionPdfGenerator()
