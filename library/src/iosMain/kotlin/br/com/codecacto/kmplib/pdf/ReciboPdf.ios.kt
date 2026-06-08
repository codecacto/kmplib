@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeRotation
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetAlpha
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.NSParagraphStyle
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage

/**
 * Render do PDF do recibo no iOS via `UIGraphicsPDFRenderer`, seguindo a mesma spec
 * congelada do Android (`recibo-layout-spec.md`). Medidas em mm → pt (1mm = 2.83465pt).
 *
 * **Pendência (herda o item de prioridade alta do backlog — host macOS):** este `actual`
 * foi escrito para compilar/validar em macOS com o SDK iOS. No host Windows os targets
 * iOS ficam desabilitados; o fundador valida o build/visual no lado dele.
 */
// Conversão mm→pt via a constante compartilhada (commonMain) — paridade numérica exata.
private fun mm(value: Double): Double = mmToPt(value)

private fun rgb(r: Int, g: Int, b: Int): UIColor =
    UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)

actual fun generateReciboPdf(data: ReciboPdfData, watermark: Boolean): ByteArray {
    val pageW = mm(210.0)
    val pageH = mm(297.0)
    val contentX0 = mm(20.0)
    val contentX1 = mm(190.0)
    val contentW = mm(170.0)
    val colW = mm(81.0)
    val pagadorX = mm(109.0)

    val ink = rgb(0x1A, 0x1A, 0x1A)
    val inkMuted = rgb(0x5A, 0x5A, 0x5A)
    val inkFaint = rgb(0x8A, 0x8A, 0x8A)
    val accent = rgb(0x0B, 0x6E, 0x4F)
    val lineColor = rgb(0xD0, 0xD0, 0xD0)
    val signLineColor = rgb(0x3A, 0x3A, 0x3A)
    val watermarkColor = rgb(0xB0, 0xB0, 0xB0)

    fun font(size: Double, bold: Boolean): UIFont =
        if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)

    fun attrs(color: UIColor, size: Double, bold: Boolean): Map<Any?, *> =
        mapOf<Any?, Any?>(
            platform.UIKit.NSFontAttributeName to font(size, bold),
            platform.UIKit.NSForegroundColorAttributeName to color,
        )

    val format = UIGraphicsPDFRendererFormat()
    val bounds: CValue<CGRect> = CGRectMake(0.0, 0.0, pageW, pageH)
    val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

    val nsData = renderer.PDFDataWithActions { ctx ->
        ctx?.beginPage()
        val cg = ctx?.CGContext

        // `baselineY` é a coordenada de BASELINE da spec (idêntica à usada no Android).
        // O iOS `drawAtPoint` ancora o TOPO da caixa de texto, então convertemos a baseline
        // para topo usando o `ascent` da fonte, via o helper compartilhado
        // [textTopFromBaseline]. Isso elimina a divergência de offsets entre as plataformas.
        fun drawText(text: String, x: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
            val s = platform.Foundation.NSString.create(string = text)
            val ascent = font(size, bold).ascender
            val topY = textTopFromBaseline(baselineY, ascent)
            s.drawAtPoint(CGPointMakeCompat(x, topY), withAttributes = attrs(color, size, bold))
        }

        fun textWidth(text: String, size: Double, bold: Boolean): Double {
            val s = platform.Foundation.NSString.create(string = text)
            return s.sizeWithAttributes(attrs(color = ink, size = size, bold = bold)).useContents { width }
        }

        fun drawRight(text: String, rightX: Double, y: Double, color: UIColor, size: Double, bold: Boolean) {
            val w = textWidth(text, size, bold)
            drawText(text, rightX - w, y, color, size, bold)
        }

        fun drawCenter(text: String, centerX: Double, y: Double, color: UIColor, size: Double, bold: Boolean) {
            val w = textWidth(text, size, bold)
            drawText(text, centerX - w / 2.0, y, color, size, bold)
        }

        fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double, color: UIColor, width: Double) {
            cg ?: return
            CGContextSetStrokeColorWithColor(cg, color.CGColor)
            CGContextSetLineWidth(cg, width)
            CGContextMoveToPoint(cg, x1, y1)
            CGContextAddLineToPoint(cg, x2, y2)
            CGContextStrokePath(cg)
        }

        fun drawImage(bytes: ByteArray, boxX: Double, boxYTop: Double, boxW: Double, boxH: Double, anchorBottom: Boolean) {
            val nsd = bytes.toNSData()
            val img = UIImage.imageWithData(nsd) ?: return
            val (iw, ih) = img.size.useContents { width to height }
            if (iw <= 0.0 || ih <= 0.0) return
            val ratio = minOf(boxW / iw, boxH / ih)
            val w = iw * ratio; val h = ih * ratio
            val x = boxX + (boxW - w) / 2.0
            val y = if (anchorBottom) boxYTop + (boxH - h) else boxYTop
            img.drawInRect(CGRectMake(x, y, w, h))
        }

        // Marca d'água (§9) — abaixo do conteúdo
        if (watermark && data.watermarkText.isNotBlank()) {
            val cx = mm(105.0); val cy = mm(148.5)
            cg?.let {
                CGContextSaveGState(it)
                CGContextSetAlpha(it, 0.12)
                CGContextTranslateCTM(it, cx, cy)
                CGContextConcatCTM(it, CGAffineTransformMakeRotation(-0.7853981634)) // -45°
                val w = textWidth(data.watermarkText, 30.0, true)
                // Centraliza o bloco vertical sobre a origem (centro da página): a baseline
                // fica deslocada para baixo metade da altura de caixa-alta (capHeight ~0.7em).
                val baselineOffset = font(30.0, true).capHeight / 2.0
                drawText(data.watermarkText, -w / 2.0, baselineOffset, watermarkColor, 30.0, true)
                CGContextRestoreGState(it)
            }
        }

        // Cabeçalho (§2) — agora usa as MESMAS baselines (em mm) da spec/Android.
        data.logoBytes?.let { drawImage(it, contentX0, mm(20.0), mm(40.0), mm(20.0), anchorBottom = false) }
        drawRight("RECIBO", contentX1, mm(30.0), accent, 24.0, true)
        drawRight("Nº ${data.numeroRecibo}", contentX1, mm(36.0), inkFaint, 9.0, false)
        drawLine(contentX0, mm(48.0), contentX1, mm(48.0), lineColor, mm(0.5))

        // Partes (§3, §4) — baselines idênticas ao Android (rótulo 55mm; dados a partir de
        // ~69pt com line-height 13pt nome / 12pt muted).
        drawText("EMITENTE", contentX0, mm(55.0), accent, 8.0, true)
        drawText("PAGADOR", pagadorX, mm(55.0), accent, 8.0, true)
        val dataStart = mm(58.0) + 11.0
        var ye = dataStart; var yp = dataStart
        drawText(data.emitente.nome, contentX0, ye, ink, 11.0, true); ye += 13.0
        data.emitente.documento?.let { drawText(it, contentX0, ye, inkMuted, 9.0, false); ye += 12.0 }
        data.emitente.contato?.let { drawText(it, contentX0, ye, inkMuted, 9.0, false); ye += 12.0 }
        data.emitente.endereco?.let { drawText(it, contentX0, ye, inkMuted, 9.0, false); ye += 12.0 }
        drawText(data.pagador.nome, pagadorX, yp, ink, 11.0, true); yp += 13.0
        data.pagador.documento?.let { drawText(it, pagadorX, yp, inkMuted, 9.0, false); yp += 12.0 }
        val corpoTop = maxOf(maxOf(ye, yp) + mm(6.0), mm(78.0))
        drawLine(contentX0, corpoTop - mm(4.0), contentX1, corpoTop - mm(4.0), lineColor, mm(0.35))

        // Corpo (§5) — frase com NEGRITO inline (paridade weblib), composição compartilhada.
        val bodyWords = reciboBodyWords(reciboBodySegments(data))
        var y = corpoTop + 14.0
        run {
            val spaceW = textWidth(" ", 14.0, false)
            var cursorX = contentX0
            for (word in bodyWords) {
                val wordW = textWidth(word.text, 14.0, word.bold)
                val advance = if (word.spaceBefore && cursorX > contentX0) spaceW else 0.0
                if (cursorX + advance + wordW > contentX0 + contentW && cursorX > contentX0) {
                    y += 20.0
                    cursorX = contentX0
                    drawText(word.text, cursorX, y, ink, 14.0, word.bold)
                    cursorX += wordW
                } else {
                    cursorX += advance
                    drawText(word.text, cursorX, y, ink, 14.0, word.bold)
                    cursorX += wordW
                }
            }
        }

        // Valor em destaque (§5.1)
        y += mm(12.0)
        drawText(data.valorFormatado, contentX0, y, accent, 28.0, true)
        val ruleY = y + mm(8.0)
        drawLine(contentX0, ruleY, contentX1, ruleY, lineColor, mm(0.5))

        // Local e data (§6) — direita, baseline 11pt abaixo da régua + 8mm (igual Android)
        drawRight(data.localData, contentX1, ruleY + mm(8.0) + 11.0, ink, 11.0, false)

        // Assinaturas (§7) — ancoradas na base
        val baseY = mm(244.0)
        fun signColumn(centerX: Double, lineLen: Double, imgW: Double, imgH: Double, imgBytes: ByteArray?, nome: String, documento: String?) {
            imgBytes?.let { drawImage(it, centerX - imgW / 2.0, baseY - imgH, imgW, imgH, anchorBottom = true) }
            drawLine(centerX - lineLen / 2.0, baseY, centerX + lineLen / 2.0, baseY, signLineColor, mm(0.5))
            // Nome (baseline baseY+5mm) e documento (baseY+9mm) — idêntico ao Android.
            drawCenter(nome, centerX, baseY + mm(5.0), ink, 10.0, true)
            documento?.let { drawCenter(it, centerX, baseY + mm(9.0), inkMuted, 8.0, false) }
        }
        if (data.assinaturaPagadorBytes != null) {
            signColumn(contentX0 + colW / 2.0, mm(65.0), mm(55.0), mm(16.0), data.assinaturaEmitenteBytes, data.emitente.nome, data.emitente.documento)
            signColumn(pagadorX + colW / 2.0, mm(65.0), mm(55.0), mm(16.0), data.assinaturaPagadorBytes, data.pagador.nome, data.pagador.documento)
        } else {
            signColumn(mm(105.0), mm(80.0), mm(60.0), mm(18.0), data.assinaturaEmitenteBytes, data.emitente.nome, data.emitente.documento)
        }

        // Rodapé (§8) — baseline 272mm (igual Android)
        drawLine(contentX0, mm(268.0), contentX1, mm(268.0), lineColor, mm(0.35))
        drawText("Documento gerado eletronicamente.", contentX0, mm(272.0), inkFaint, 7.5, false)
        drawRight("Nº ${data.numeroRecibo} · ${data.dataHoraEmissao}", contentX1, mm(272.0), inkFaint, 7.5, false)
    }

    return nsData.toByteArray()
}

private fun CGPointMakeCompat(x: Double, y: Double): CValue<CGPoint> =
    platform.CoreGraphics.CGPointMake(x, y)

private fun ByteArray.toNSData(): NSData = this.usePinnedNSData()

private fun ByteArray.usePinnedNSData(): NSData = kotlinx.cinterop.memScoped {
    NSData.create(bytes = this@usePinnedNSData.toCValuesPtr(this), length = this@usePinnedNSData.size.toULong())
}

private fun ByteArray.toCValuesPtr(scope: kotlinx.cinterop.MemScope): kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>? {
    if (isEmpty()) return null
    val ptr = scope.allocArray<kotlinx.cinterop.ByteVar>(size)
    for (i in indices) ptr[i] = this[i]
    return ptr
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val bytes = ByteArray(length)
    if (length == 0) return bytes
    kotlinx.cinterop.memScoped {
        val buffer = allocArray<kotlinx.cinterop.ByteVar>(length)
        platform.posix.memcpy(buffer, this@toByteArray.bytes, this@toByteArray.length)
        for (i in 0 until length) bytes[i] = buffer[i]
    }
    return bytes
}
