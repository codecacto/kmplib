package br.com.codecacto.kmplib.pdf

import kotlin.math.PI

/**
 * Render nativo do PDF do **recibo** no iOS via `UIGraphicsPDFRenderer` + CoreText (helper
 * [IosPdfCanvas]), seguindo `ReciboFacil/docs/design/recibo-layout-spec.md` com as MESMAS medidas
 * (mm→pt via [MM_TO_PT]), cores e baselines do renderer Android — **paridade Android=iOS**,
 * incluindo os trechos em **negrito inline** da frase do corpo (via [reciboBodyWords]).
 *
 * Não é mais stub — quita a dívida `kmplib-ios-pdf-stub-debt` para o recibo. As coordenadas são as
 * MESMAS do Android (baseline topo-esquerda), então nenhuma conversão de baseline *ad hoc* é
 * necessária. Validação visual em host macOS/CI.
 */
actual fun generateReciboPdf(data: ReciboPdfData, watermark: Boolean): ByteArray {
    val r = IosReciboPdfRenderer(data, watermark)
    return renderIosPdf(r.pageW, r.pageH) { r.render(this) }
}

private fun mm(value: Double): Double = value * MM_TO_PT

private class IosReciboPdfRenderer(
    private val data: ReciboPdfData,
    private val watermark: Boolean,
) {
    val pageW = mm(210.0)
    val pageH = mm(297.0)
    private val contentX0 = mm(20.0)
    private val contentX1 = mm(190.0)
    private val contentW = mm(170.0)
    private val colW = mm(81.0)
    private val pagadorX = mm(109.0)

    private val ink = PdfColor.argb(0xFF1A1A1A)
    private val inkMuted = PdfColor.argb(0xFF5A5A5A)
    private val inkFaint = PdfColor.argb(0xFF8A8A8A)
    private val accent = PdfColor.argb(0xFF0B6E4F)
    private val line = PdfColor.argb(0xFFD0D0D0)
    private val signLine = PdfColor.argb(0xFF3A3A3A)
    private val watermarkColor = PdfColor(r = 0.69, g = 0.69, b = 0.69, a = 0.12)

    fun render(c: IosPdfCanvas) {
        if (watermark) drawWatermark(c)
        drawHeader(c)
        val corpoTop = drawParties(c)
        val afterBody = drawBody(c, corpoTop)
        drawLocalData(c, afterBody)
        drawSignatures(c)
        drawFooter(c)
    }

    private fun drawHeader(c: IosPdfCanvas) {
        data.logoBytes?.let { c.image(it, contentX0, mm(20.0), mm(40.0), mm(20.0)) }
        c.text("RECIBO", contentX1, mm(30.0), 24.0, bold = true, color = accent, align = PdfTextAlign.Right)
        c.text("Nº ${data.numeroRecibo}", contentX1, mm(36.0), 9.0, bold = false, color = inkFaint, align = PdfTextAlign.Right)
        c.line(contentX0, mm(48.0), contentX1, mm(48.0), line, mm(0.5))
    }

    private fun drawParties(c: IosPdfCanvas): Double {
        c.text("EMITENTE", contentX0, mm(55.0), 8.0, bold = true, color = accent)
        c.text("PAGADOR", pagadorX, mm(55.0), 8.0, bold = true, color = accent)

        val dataStart = mm(58.0) + 11.0
        var ye = dataStart
        var yp = dataStart

        c.text(c.truncate(data.emitente.nome, colW, 11.0, true), contentX0, ye, 11.0, bold = true, color = ink)
        ye += 13.0
        data.emitente.documento?.let { c.text(it, contentX0, ye, 9.0, bold = false, color = inkMuted); ye += 12.0 }
        data.emitente.contato?.let { c.text(c.truncate(it, colW, 9.0, false), contentX0, ye, 9.0, bold = false, color = inkMuted); ye += 12.0 }
        data.emitente.endereco?.let { c.text(c.truncate(it, colW, 9.0, false), contentX0, ye, 9.0, bold = false, color = inkMuted); ye += 12.0 }

        c.text(c.truncate(data.pagador.nome, colW, 11.0, true), pagadorX, yp, 11.0, bold = true, color = ink)
        yp += 13.0
        data.pagador.documento?.let { c.text(it, pagadorX, yp, 9.0, bold = false, color = inkMuted); yp += 12.0 }

        val fim = maxOf(ye, yp)
        val corpoTop = maxOf(fim + mm(6.0), mm(78.0))
        c.line(contentX0, corpoTop - mm(4.0), contentX1, corpoTop - mm(4.0), line, mm(0.35))
        return corpoTop
    }

    private fun drawBody(c: IosPdfCanvas, top: Double): Double {
        val words = reciboBodyWords(reciboBodySegments(data))
        var y = top + 14.0
        y = drawWrappedRich(c, words, contentX0, y, contentW, 14.0, 20.0)

        y += mm(12.0)
        c.text(data.valorFormatado, contentX0, y, 28.0, bold = true, color = accent)
        val ruleY = y + mm(8.0)
        c.line(contentX0, ruleY, contentX1, ruleY, line, mm(0.5))
        return ruleY
    }

    private fun drawLocalData(c: IosPdfCanvas, afterRule: Double) {
        val y = afterRule + mm(8.0) + 11.0
        c.text(data.localData, contentX1, y, 11.0, bold = false, color = ink, align = PdfTextAlign.Right)
    }

    private fun drawSignatures(c: IosPdfCanvas) {
        val baseY = mm(244.0)
        val temPagador = data.assinaturaPagadorBytes != null
        if (temPagador) {
            val emitCenter = contentX0 + colW / 2.0
            val pagCenter = pagadorX + colW / 2.0
            drawSignatureColumn(c, emitCenter, baseY, mm(65.0), mm(55.0), mm(16.0), data.assinaturaEmitenteBytes, data.emitente.nome, data.emitente.documento)
            drawSignatureColumn(c, pagCenter, baseY, mm(65.0), mm(55.0), mm(16.0), data.assinaturaPagadorBytes, data.pagador.nome, data.pagador.documento)
        } else {
            val center = mm(105.0)
            drawSignatureColumn(c, center, baseY, mm(80.0), mm(60.0), mm(18.0), data.assinaturaEmitenteBytes, data.emitente.nome, data.emitente.documento)
        }
    }

    private fun drawSignatureColumn(
        c: IosPdfCanvas,
        centerX: Double,
        baseY: Double,
        lineLen: Double,
        imgW: Double,
        imgH: Double,
        imgBytes: ByteArray?,
        nome: String,
        documento: String?,
    ) {
        imgBytes?.let { c.image(it, centerX - imgW / 2.0, baseY - imgH, imgW, imgH) }
        c.line(centerX - lineLen / 2.0, baseY, centerX + lineLen / 2.0, baseY, signLine, mm(0.5))
        c.text(nome, centerX, baseY + mm(5.0), 10.0, bold = true, color = ink, align = PdfTextAlign.Center)
        documento?.let { c.text(it, centerX, baseY + mm(9.0), 8.0, bold = false, color = inkMuted, align = PdfTextAlign.Center) }
    }

    private fun drawFooter(c: IosPdfCanvas) {
        c.line(contentX0, mm(268.0), contentX1, mm(268.0), line, mm(0.35))
        val baseline = mm(272.0)
        c.text("Documento gerado eletronicamente.", contentX0, baseline, 7.5, bold = false, color = inkFaint)
        c.text("Nº ${data.numeroRecibo} · ${data.dataHoraEmissao}", contentX1, baseline, 7.5, bold = false, color = inkFaint, align = PdfTextAlign.Right)
    }

    private fun drawWatermark(c: IosPdfCanvas) {
        if (data.watermarkText.isBlank()) return
        val cx = mm(105.0)
        val cy = mm(148.5)
        c.save()
        c.translate(cx, cy)
        c.rotate(-45.0 * PI / 180.0)
        c.translate(-cx, -cy)
        c.text(data.watermarkText, cx, cy + 10.0, 30.0, bold = true, color = watermarkColor, align = PdfTextAlign.Center)
        c.restore()
    }

    /**
     * Desenha texto rico (negrito por palavra) com wrap por palavra na largura [maxWidth]. Cada
     * [ReciboWord] usa a fonte regular/bold do seu segmento. Retorna o y da última linha.
     */
    private fun drawWrappedRich(
        c: IosPdfCanvas,
        words: List<ReciboWord>,
        x: Double,
        startY: Double,
        maxWidth: Double,
        sizePt: Double,
        lineHeight: Double,
    ): Double {
        val spaceW = c.measure(" ", sizePt, false)
        var y = startY
        var cursorX = x
        for (word in words) {
            val wordW = c.measure(word.text, sizePt, word.bold)
            val advance = if (word.spaceBefore && cursorX > x) spaceW else 0.0
            if (cursorX + advance + wordW > x + maxWidth && cursorX > x) {
                y += lineHeight
                cursorX = x
                c.text(word.text, cursorX, y, sizePt, word.bold, ink)
                cursorX += wordW
            } else {
                cursorX += advance
                c.text(word.text, cursorX, y, sizePt, word.bold, ink)
                cursorX += wordW
            }
        }
        return y
    }
}
