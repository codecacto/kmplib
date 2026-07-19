@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFBridgingRetain
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGColorRef
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextSetTextMatrix
import platform.CoreGraphics.CGContextSetTextPosition
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGRectMake
import platform.CoreText.CTLineCreateWithAttributedString
import platform.CoreText.CTLineDraw
import platform.CoreText.CTLineGetTypographicBounds
import platform.CoreText.CTLineRef
import platform.Foundation.NSAttributedString
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.NSString
import platform.Foundation.addAttribute
import platform.Foundation.create
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage

/**
 * Alinhamento horizontal do texto no [IosPdfCanvas], espelhando `Paint.Align` do Android.
 */
internal enum class PdfTextAlign { Left, Center, Right }

/**
 * Cor RGBA (0..1) para o render de PDF no iOS. Convertida em `CGColor` pelo canvas.
 * Construída a partir do inteiro ARGB (`0xAARRGGBB`) usado pelos renderers Android — garante
 * **paridade de cor** exata entre as plataformas.
 */
internal data class PdfColor(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
    companion object {
        /** Converte um ARGB `0xAARRGGBB` (o mesmo literal usado no Android) em [PdfColor]. */
        fun argb(value: Long): PdfColor {
            val a = ((value shr 24) and 0xFF) / 255.0
            val r = ((value shr 16) and 0xFF) / 255.0
            val g = ((value shr 8) and 0xFF) / 255.0
            val b = (value and 0xFF) / 255.0
            return PdfColor(r, g, b, a)
        }
    }

    fun uiColor(): UIColor = UIColor(red = r, green = g, blue = b, alpha = a)

    fun cgColor(): CGColorRef? = uiColor().CGColor
}

/**
 * "Canvas" de PDF no iOS que desenha **texto via CoreText** (`CTLine`) e primitivas via
 * CoreGraphics/UIKit, dentro de um contexto do `UIGraphicsPDFRenderer`. É o núcleo que **quita a
 * dívida `kmplib-ios-pdf-stub-debt`** (ADR-0003 do Arroba Certa): abordagem **nativa/oficial** —
 * CoreText é exportado no Kotlin/Native (ao contrário das categorias de desenho de texto do UIKit
 * `NSString.drawAtPoint`) — nunca workaround (HTML→imagem/"Android-only").
 *
 * ### Cor/fonte do texto (padrão-ouro CoreText)
 * O texto é montado como `NSAttributedString` com os atributos que o CoreText lê:
 * - **`"NSFont"`** = valor de `kCTFontAttributeName` (uma `UIFont`, toll-free bridge → `CTFont`).
 * - **`"CTForegroundColor"`** = valor de `kCTForegroundColorAttributeName` (um `CGColor`).
 *
 * ### Sistema de coordenadas
 * O contexto do `UIGraphicsPDFRenderer` é orientado como UIKit (origem **topo-esquerda**, y↓),
 * **igual** ao `PdfDocument` do Android — então cada renderer usa as MESMAS coordenadas nas duas
 * plataformas (baseline de texto igual à do Android/spec). Para o CoreText desenhar upright, [text]
 * aplica a receita canônica: transladar até a baseline e inverter o eixo Y localmente
 * (`scale(1,-1)`) antes do `CTLineDraw`. Imagens usam `UIImage.drawInRect` (correto no contexto UIKit).
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito por construção para compilar/rodar em iOS; o
 * ambiente de build aqui é Linux (alvos Apple SKIPPED) — validação visual em macOS/CI.
 */
internal class IosPdfCanvas(
    val cg: CGContextRef,
    val pageWidth: Double,
    val pageHeight: Double,
) {

    /** Desenha [text] com a **baseline** em ([x], [baselineY]) (coordenadas topo-esquerda). */
    fun text(
        text: String,
        x: Double,
        baselineY: Double,
        size: Double,
        bold: Boolean,
        color: PdfColor,
        align: PdfTextAlign = PdfTextAlign.Left,
    ) {
        if (text.isEmpty()) return
        val line = makeLine(text, size, bold, color) ?: return
        try {
            val width = CTLineGetTypographicBounds(line, null, null, null)
            val drawX = when (align) {
                PdfTextAlign.Left -> x
                PdfTextAlign.Right -> x - width
                PdfTextAlign.Center -> x - width / 2.0
            }
            CGContextSaveGState(cg)
            // Text matrix identidade; então translada até a baseline e inverte o Y localmente
            // para o CoreText desenhar upright num contexto UIKit (top-left).
            CGContextSetTextMatrix(cg, CGAffineTransformMake(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))
            CGContextTranslateCTM(cg, drawX, baselineY)
            CGContextScaleCTM(cg, 1.0, -1.0)
            CGContextSetTextPosition(cg, 0.0, 0.0)
            CTLineDraw(line, cg)
            CGContextRestoreGState(cg)
        } finally {
            CFRelease(line)
        }
    }

    /** Largura do texto no tamanho/peso dados (pt). 0 para texto vazio. */
    fun measure(text: String, size: Double, bold: Boolean): Double {
        if (text.isEmpty()) return 0.0
        val line = makeLine(text, size, bold, PdfColor(0.0, 0.0, 0.0)) ?: return 0.0
        return try {
            CTLineGetTypographicBounds(line, null, null, null)
        } finally {
            CFRelease(line)
        }
    }

    /** Ascent da fonte no tamanho dado (pt, positivo). */
    fun ascent(size: Double, bold: Boolean): Double = memScoped {
        val line = makeLine("Mg", size, bold, PdfColor(0.0, 0.0, 0.0)) ?: return 0.0
        val a = alloc<CGFloatVar>()
        CTLineGetTypographicBounds(line, a.ptr, null, null)
        val result = a.value
        CFRelease(line)
        result
    }

    /** Trunca [text] com "…" para caber em [maxWidth] no tamanho/peso dados. */
    fun truncate(text: String, maxWidth: Double, size: Double, bold: Boolean): String {
        if (measure(text, size, bold) <= maxWidth) return text
        var end = text.length
        while (end > 0 && measure(text.substring(0, end) + "…", size, bold) > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    fun line(x0: Double, y0: Double, x1: Double, y1: Double, color: PdfColor, width: Double) {
        color.cgColor()?.let { CGContextSetStrokeColorWithColor(cg, it) }
        CGContextSetLineWidth(cg, width)
        CGContextMoveToPoint(cg, x0, y0)
        CGContextAddLineToPoint(cg, x1, y1)
        CGContextStrokePath(cg)
    }

    fun fillRect(x: Double, y: Double, w: Double, h: Double, color: PdfColor) {
        color.cgColor()?.let { CGContextSetFillColorWithColor(cg, it) }
        CGContextFillRect(cg, CGRectMake(x, y, w, h))
    }

    fun fillRoundRect(x: Double, y: Double, w: Double, h: Double, radius: Double, color: PdfColor) {
        val path = UIBezierPath.bezierPathWithRoundedRect(
            rect = CGRectMake(x, y, w, h),
            cornerRadius = radius,
        )
        color.uiColor().setFill()
        path.fill()
    }

    /**
     * Desenha a imagem [bytes] (PNG/JPEG) *contain* na caixa ([x],[y],[boxW],[boxH]), ancorada no
     * topo-esquerda (igual ao `drawBitmap` do Android). No-op se os bytes forem inválidos.
     */
    fun image(bytes: ByteArray, x: Double, y: Double, boxW: Double, boxH: Double) {
        val image = decodeImage(bytes) ?: return
        val (iw, ih) = image.size.useContents { width to height }
        if (iw <= 0.0 || ih <= 0.0) return
        val ratio = minOf(boxW / iw, boxH / ih)
        image.drawInRect(CGRectMake(x, y, iw * ratio, ih * ratio))
    }

    fun save() = CGContextSaveGState(cg)
    fun restore() = CGContextRestoreGState(cg)
    fun translate(dx: Double, dy: Double) = CGContextTranslateCTM(cg, dx, dy)
    fun rotate(radians: Double) = CGContextRotateCTM(cg, radians)

    /**
     * Desenha [text] com quebra por palavra dentro de [maxWidth] a partir da baseline
     * ([x],[startY]), avançando [lineHeight] por linha. Retorna o y da última baseline.
     */
    fun wrappedText(
        text: String,
        x: Double,
        startY: Double,
        maxWidth: Double,
        size: Double,
        bold: Boolean,
        color: PdfColor,
        lineHeight: Double,
    ): Double {
        var y = startY
        val words = text.split(' ')
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                text(current.toString(), x, y, size, bold, color)
                y += lineHeight
                current.clear()
            }
        }
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measure(candidate, size, bold) > maxWidth) {
                flush()
                current.append(word)
            } else {
                current.clear()
                current.append(candidate)
            }
        }
        flush()
        return y
    }

    // --- Interno -------------------------------------------------------------

    /**
     * Monta um `CTLine` do [text] com fonte/cor via atributos que o CoreText lê. `"NSFont"` é o
     * valor textual de `kCTFontAttributeName` e `"CTForegroundColor"` o de
     * `kCTForegroundColorAttributeName` — usar as strings evita bridging de constantes CFString.
     * O chamador deve `CFRelease` o resultado.
     */
    private fun makeLine(text: String, size: Double, bold: Boolean, color: PdfColor): CTLineRef? {
        val font: UIFont = if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
        val attr = NSMutableAttributedString(string = text)
        val range = NSMakeRange(0u, (text as NSString).length)
        attr.addAttribute("NSFont", value = font, range = range)
        color.cgColor()?.let { attr.addAttribute("CTForegroundColor", value = it, range = range) }
        val cfAttr = CFBridgingRetain(attr as NSAttributedString)
        val line = CTLineCreateWithAttributedString(cfAttr?.reinterpret())
        cfAttr?.let { CFRelease(it) }
        return line
    }

    private fun decodeImage(bytes: ByteArray): UIImage? {
        if (bytes.isEmpty()) return null
        return try {
            UIImage(data = bytes.toNSData())
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Renderiza um PDF de página única de [pageWidth]×[pageHeight] pt via `UIGraphicsPDFRenderer`,
 * executando [draw] sobre o [IosPdfCanvas] da página. Retorna os bytes do PDF.
 */
internal fun renderIosPdf(
    pageWidth: Double,
    pageHeight: Double,
    draw: IosPdfCanvas.() -> Unit,
): ByteArray {
    val bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight)
    val format = UIGraphicsPDFRendererFormat()
    val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)
    val data: NSData = renderer.PDFDataWithActions { context ->
        context?.beginPage()
        val cg = UIGraphicsGetCurrentContext()
        if (cg != null) {
            IosPdfCanvas(cg, pageWidth, pageHeight).draw()
        }
    }
    return data.toByteArray()
}

// --- NSData <-> ByteArray (file-private; espelha ShareHandler.ios/PdfRasterizer.ios) ---

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    val len = this.length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    this.bytes?.let { src ->
        out.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), src, len.toULong())
        }
    }
    return out
}
