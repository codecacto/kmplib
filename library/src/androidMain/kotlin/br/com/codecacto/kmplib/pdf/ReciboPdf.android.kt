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
 * Render nativo do PDF do recibo (Android) via `android.graphics.pdf.PdfDocument`,
 * seguindo `ReciboFacil/docs/design/recibo-layout-spec.md` à risca (medidas em mm
 * convertidas para pt; cores hex da spec; tipografia da tabela §10.2).
 *
 * O sistema de coordenadas do PdfDocument é em **pt** com origem no topo-esquerda —
 * idêntico ao da spec — então usamos [mm] para converter cada medida.
 */
actual fun generateReciboPdf(data: ReciboPdfData, watermark: Boolean): ByteArray =
    AndroidReciboPdfRenderer(data, watermark).render()

// Conversão mm→pt: delega à constante compartilhada (commonMain) para garantir paridade
// numérica exata entre Android, iOS e weblib.
private fun mm(value: Float): Float = (value.toDouble() * MM_TO_PT).toFloat()

private class AndroidReciboPdfRenderer(
    private val data: ReciboPdfData,
    private val watermark: Boolean,
) {
    // Página A4 em pt
    private val pageW = mm(210f)
    private val pageH = mm(297f)
    private val marginL = mm(20f)
    private val contentX0 = mm(20f)
    private val contentX1 = mm(190f)
    private val contentW = mm(170f)
    private val colW = mm(81f)
    private val gutter = mm(8f)
    private val pagadorX = mm(109f)

    // Paleta (contrato §0)
    private val ink = 0xFF1A1A1A.toInt()
    private val inkMuted = 0xFF5A5A5A.toInt()
    private val inkFaint = 0xFF8A8A8A.toInt()
    private val accent = 0xFF0B6E4F.toInt()
    private val line = 0xFFD0D0D0.toInt()
    private val signLine = 0xFF3A3A3A.toInt()
    private val watermarkColor = 0xFFB0B0B0.toInt()

    // Fontes (Inter quando fornecida; senão sans-serif da plataforma)
    private val regular: Typeface = loadTypeface(data.interRegularBytes, bold = false)
    private val bold: Typeface = loadTypeface(data.interBoldBytes, bold = true)

    fun render(): ByteArray {
        val doc = PdfDocument()
        // PageInfo usa Int; arredondamos a A4 (595 x 842).
        val info = PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        if (watermark) drawWatermark(canvas) // abaixo do conteúdo (§9)

        drawHeader(canvas)
        val corpoTop = drawParties(canvas)
        val afterBody = drawBody(canvas, corpoTop)
        drawLocalData(canvas, afterBody)
        drawSignatures(canvas)
        drawFooter(canvas)

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    // --- Cabeçalho (§2) -------------------------------------------------------
    private fun drawHeader(canvas: Canvas) {
        // Logo (40×20mm) topo-esquerda, *contain*
        data.logoBytes?.let { bytes ->
            decode(bytes)?.let { bmp ->
                val boxW = mm(40f); val boxH = mm(20f)
                val ratio = minOf(boxW / bmp.width, boxH / bmp.height)
                val w = bmp.width * ratio; val h = bmp.height * ratio
                canvas.drawBitmap(
                    bmp,
                    null,
                    RectF(contentX0, mm(20f), contentX0 + w, mm(20f) + h),
                    null,
                )
            }
        }
        // "RECIBO" 24pt Bold ACCENT, direita, baseline ~30mm
        canvas.drawText("RECIBO", contentX1, mm(30f), paint(accent, 24f, bold, right = true))
        // "Nº {num}" 9pt INK_FAINT direita baseline ~36mm
        canvas.drawText("Nº ${data.numeroRecibo}", contentX1, mm(36f), paint(inkFaint, 9f, regular, right = true))
        // Régua 48mm RULE LINE
        canvas.drawLine(contentX0, mm(48f), contentX1, mm(48f), strokePaint(line, mm(0.5f)))
    }

    // --- Blocos Emitente/Pagador (§3, §4). Retorna o y de início do corpo. -----
    private fun drawParties(canvas: Canvas): Float {
        // Rótulos de seção (8pt Bold ACCENT, baseline 55mm)
        canvas.drawText("EMITENTE", contentX0, mm(55f), paint(accent, 8f, bold).apply { letterSpacing = 0.06f })
        canvas.drawText("PAGADOR", pagadorX, mm(55f), paint(accent, 8f, bold).apply { letterSpacing = 0.06f })

        // y de dados começa ~58mm (após o rótulo). Line-height 13pt nome / 12pt muted.
        val dataStart = mm(58f) + 11f
        var ye = dataStart
        var yp = dataStart

        // Emitente
        canvas.drawText(truncate(data.emitente.nome, colW, bold, 11f), contentX0, ye, paint(ink, 11f, bold))
        ye += 13f
        data.emitente.documento?.let { canvas.drawText(it, contentX0, ye, paint(inkMuted, 9f, regular)); ye += 12f }
        data.emitente.contato?.let { canvas.drawText(truncate(it, colW, regular, 9f), contentX0, ye, paint(inkMuted, 9f, regular)); ye += 12f }
        data.emitente.endereco?.let { canvas.drawText(truncate(it, colW, regular, 9f), contentX0, ye, paint(inkMuted, 9f, regular)); ye += 12f }

        // Pagador
        canvas.drawText(truncate(data.pagador.nome, colW, bold, 11f), pagadorX, yp, paint(ink, 11f, bold))
        yp += 13f
        data.pagador.documento?.let { canvas.drawText(it, pagadorX, yp, paint(inkMuted, 9f, regular)); yp += 12f }

        val fim = maxOf(ye, yp)
        val corpoTop = maxOf(fim + mm(6f), mm(78f)) // mínimo y=78mm (§4)
        // Régua hairline 4mm acima do corpo (§4.1)
        canvas.drawLine(contentX0, corpoTop - mm(4f), contentX1, corpoTop - mm(4f), strokePaint(line, mm(0.35f)))
        return corpoTop
    }

    // --- Corpo + valor em destaque (§5). Retorna y após a régua pós-valor. -----
    private fun drawBody(canvas: Canvas, top: Float): Float {
        // Frase com trechos variáveis em NEGRITO inline (paridade com a weblib): a
        // composição vem do helper compartilhado `reciboBodySegments` (commonMain).
        val words = reciboBodyWords(reciboBodySegments(data))
        var y = top + 14f
        y = drawWrappedRich(canvas, words, contentX0, y, contentW, 14f, 20f)

        // Valor em destaque 28pt Bold ACCENT, 12mm acima (gap) → posição abaixo da frase
        y += mm(12f)
        canvas.drawText(data.valorFormatado, contentX0, y, paint(accent, 28f, bold))
        // Régua pós-valor 8mm abaixo do valor
        val ruleY = y + mm(8f)
        canvas.drawLine(contentX0, ruleY, contentX1, ruleY, strokePaint(line, mm(0.5f)))
        return ruleY
    }

    // --- Local e data (§6), 8mm abaixo da régua, à direita ---------------------
    private fun drawLocalData(canvas: Canvas, afterRule: Float) {
        val y = afterRule + mm(8f) + 11f
        canvas.drawText(data.localData, contentX1, y, paint(ink, 11f, regular, right = true))
    }

    // --- Assinatura(s) ancoradas na base (§7) ---------------------------------
    private fun drawSignatures(canvas: Canvas) {
        val baseY = mm(244f)
        val temPagador = data.assinaturaPagadorBytes != null
        if (temPagador) {
            // Duas colunas
            val emitCenter = contentX0 + colW / 2f
            val pagCenter = pagadorX + colW / 2f
            drawSignatureColumn(canvas, emitCenter, baseY, mm(65f), mm(55f), mm(16f),
                data.assinaturaEmitenteBytes, data.emitente.nome, data.emitente.documento)
            drawSignatureColumn(canvas, pagCenter, baseY, mm(65f), mm(55f), mm(16f),
                data.assinaturaPagadorBytes, data.pagador.nome, data.pagador.documento)
        } else {
            // Uma assinatura (emitente), centralizada em x=105
            val center = mm(105f)
            drawSignatureColumn(canvas, center, baseY, mm(80f), mm(60f), mm(18f),
                data.assinaturaEmitenteBytes, data.emitente.nome, data.emitente.documento)
        }
    }

    private fun drawSignatureColumn(
        canvas: Canvas,
        centerX: Float,
        baseY: Float,
        lineLen: Float,
        imgW: Float,
        imgH: Float,
        imgBytes: ByteArray?,
        nome: String,
        documento: String?,
    ) {
        // Imagem da assinatura acima da linha (base em baseY), *contain*
        imgBytes?.let { bytes ->
            decode(bytes)?.let { bmp ->
                val ratio = minOf(imgW / bmp.width, imgH / bmp.height)
                val w = bmp.width * ratio; val h = bmp.height * ratio
                val l = centerX - w / 2f
                val tImg = baseY - h
                canvas.drawBitmap(bmp, null, RectF(l, tImg, l + w, tImg + h), null)
            }
        }
        // Linha de assinatura
        canvas.drawLine(centerX - lineLen / 2f, baseY, centerX + lineLen / 2f, baseY, strokePaint(signLine, mm(0.5f)))
        // Nome (10pt Bold, centralizado, baseline ~249mm relativo: baseY + 5mm)
        canvas.drawText(nome, centerX, baseY + mm(5f), paint(ink, 10f, bold, center = true))
        // Documento (8pt muted, centralizado, baseY + 9mm)
        documento?.let { canvas.drawText(it, centerX, baseY + mm(9f), paint(inkMuted, 8f, regular, center = true)) }
    }

    // --- Rodapé (§8) ----------------------------------------------------------
    private fun drawFooter(canvas: Canvas) {
        canvas.drawLine(contentX0, mm(268f), contentX1, mm(268f), strokePaint(line, mm(0.35f)))
        val baseline = mm(272f)
        canvas.drawText("Documento gerado eletronicamente.", contentX0, baseline, paint(inkFaint, 7.5f, regular))
        canvas.drawText(
            "Nº ${data.numeroRecibo} · ${data.dataHoraEmissao}",
            contentX1, baseline, paint(inkFaint, 7.5f, regular, right = true),
        )
    }

    // --- Marca d'água (§9) ----------------------------------------------------
    private fun drawWatermark(canvas: Canvas) {
        if (data.watermarkText.isBlank()) return
        val cx = mm(105f); val cy = mm(148.5f)
        val p = paint(watermarkColor, 30f, bold, center = true).apply { alpha = (0.12f * 255).toInt() }
        canvas.save()
        canvas.rotate(-45f, cx, cy)
        // centraliza verticalmente ajustando baseline
        canvas.drawText(data.watermarkText, cx, cy + 10f, p)
        canvas.restore()
    }

    // --- Helpers --------------------------------------------------------------
    private fun loadTypeface(bytes: ByteArray?, bold: Boolean): Typeface {
        // Carregar uma fonte custom (Inter) sem arquivo exigiria Typeface.createFromFile.
        // Sem o TTF disponível como arquivo, usamos sans-serif (métricas aproximadas).
        // TODO(Inter): quando o app fornecer o TTF da Inter, embuti-la como recurso e
        //  carregar via Typeface.Builder para paridade exata de métricas com a weblib.
        return Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun paint(
        color: Int,
        sizePt: Float,
        tf: Typeface,
        bold: Boolean = false,
        right: Boolean = false,
        center: Boolean = false,
    ) = Paint().apply {
        isAntiAlias = true
        this.color = color
        textSize = sizePt
        typeface = tf
        textAlign = when {
            right -> Paint.Align.RIGHT
            center -> Paint.Align.CENTER
            else -> Paint.Align.LEFT
        }
    }

    private fun strokePaint(color: Int, width: Float) = Paint().apply {
        isAntiAlias = true
        this.color = color
        strokeWidth = width
    }

    private fun decode(bytes: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    private fun truncate(text: String, maxWidth: Float, tf: Typeface, sizePt: Float): String {
        val p = paint(ink, sizePt, tf)
        if (p.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && p.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    /**
     * Desenha texto rico (com negrito por palavra) com wrap por palavra na largura
     * [maxWidth]. Cada [ReciboWord] é desenhada com a fonte regular/bold do seu segmento
     * de origem, preservando o espaço entre palavras. Retorna o y após a última linha.
     */
    private fun drawWrappedRich(
        canvas: Canvas,
        words: List<ReciboWord>,
        x: Float,
        startY: Float,
        maxWidth: Float,
        sizePt: Float,
        lineHeight: Float,
    ): Float {
        val regularP = paint(ink, sizePt, regular)
        val boldP = paint(ink, sizePt, bold)
        val spaceW = regularP.measureText(" ")
        var y = startY
        var cursorX = x
        for (word in words) {
            val p = if (word.bold) boldP else regularP
            val wordW = p.measureText(word.text)
            val advance = if (word.spaceBefore && cursorX > x) spaceW else 0f
            if (cursorX + advance + wordW > x + maxWidth && cursorX > x) {
                // Quebra de linha
                y += lineHeight
                cursorX = x
                canvas.drawText(word.text, cursorX, y, p)
                cursorX += wordW
            } else {
                cursorX += advance
                canvas.drawText(word.text, cursorX, y, p)
                cursorX += wordW
            }
        }
        return y
    }
}
