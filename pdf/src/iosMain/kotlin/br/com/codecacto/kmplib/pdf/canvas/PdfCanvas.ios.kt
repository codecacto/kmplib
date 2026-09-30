@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf.canvas

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextAddPath
import platform.CoreGraphics.CGContextClipToRect
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineDash
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextSetTextMatrix
import platform.CoreGraphics.CGContextSetTextPosition
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGPathCreateWithRect
import platform.CoreGraphics.CGPathCreateWithRoundedRect
import platform.CoreGraphics.CGPathRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreText.CTFontCreateWithFontDescriptor
import platform.CoreText.CTFontDescriptorRef
import platform.CoreText.CTFontManagerCreateFontDescriptorFromData
import platform.CoreText.CTLineCreateWithAttributedString
import platform.CoreText.CTLineDraw
import platform.CoreText.CTLineGetTypographicBounds
import platform.CoreText.CTLineRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSAttributedString
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.NSNumber
import platform.Foundation.addAttribute
import platform.Foundation.create
import platform.Foundation.length
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontDescriptorFeatureSettingsAttribute
import platform.UIKit.UIFontFeatureSelectorIdentifierKey
import platform.UIKit.UIFontFeatureTypeIdentifierKey
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIFontWeightRegular
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage
import br.com.codecacto.kmplib.pdf.coreTextForegroundColor

/**
 * iOS: `UIGraphicsPDFRenderer` (UIKit) com texto por **CoreText** (`CTLine`) — a pilha oficial da
 * Apple para gerar PDF. Uma página por `beginPageWithBounds`, cada uma com a sua medida (bobina de
 * altura variável no mesmo documento que uma A4). As fontes são embutidas pelo próprio CoreGraphics.
 *
 * O contexto do renderer já vem orientado como UIKit (origem topo-esquerda, y para baixo), igual ao
 * `PdfDocument` do Android — as coordenadas gravadas entram direto. Só o texto precisa da inversão
 * local do eixo Y, porque o CoreText desenha no sistema do Quartz.
 */
internal actual fun platformPdfTextMeasurer(): PdfTextMeasurer = IosPdfTextMeasurer()

internal actual fun renderPdfPages(pages: List<RecordedPdfPage>): ByteArray {
    val first = pages.first()
    val renderer = UIGraphicsPDFRenderer(
        bounds = CGRectMake(0.0, 0.0, first.widthPt, first.heightPt),
        format = UIGraphicsPDFRendererFormat(),
    )
    val fonts = IosFontCache()
    val data: NSData = renderer.PDFDataWithActions { context ->
        if (context == null) return@PDFDataWithActions
        pages.forEach { page ->
            context.beginPageWithBounds(CGRectMake(0.0, 0.0, page.widthPt, page.heightPt), emptyMap<Any?, Any?>())
            val cg = UIGraphicsGetCurrentContext() ?: return@forEach
            page.ops.forEach { op -> draw(cg, op, fonts) }
        }
    }
    return data.toByteArray()
}

private fun draw(cg: CGContextRef, op: PdfOp, fonts: IosFontCache) {
    when (op) {
        is PdfOp.Text -> drawText(cg, op, fonts)
        is PdfOp.Line -> {
            CGContextSaveGState(cg)
            applyStroke(cg, op.stroke)
            CGContextMoveToPoint(cg, op.x1, op.y1)
            CGContextAddLineToPoint(cg, op.x2, op.y2)
            CGContextStrokePath(cg)
            CGContextRestoreGState(cg)
        }
        is PdfOp.Rect -> {
            val rect = CGRectMake(op.x, op.y, op.width, op.height)
            val path = if (op.radius > 0.0) {
                CGPathCreateWithRoundedRect(rect, op.radius, op.radius, null)
            } else {
                CGPathCreateWithRect(rect, null)
            } ?: return
            try {
                op.fill?.let { fill ->
                    CGContextSaveGState(cg)
                    CGContextSetFillColorWithColor(cg, fill.uiColor().CGColor)
                    CGContextAddPath(cg, path)
                    CGContextFillPath(cg)
                    CGContextRestoreGState(cg)
                }
                op.stroke?.let { stroke ->
                    CGContextSaveGState(cg)
                    applyStroke(cg, stroke)
                    CGContextAddPath(cg, path)
                    CGContextStrokePath(cg)
                    CGContextRestoreGState(cg)
                }
            } finally {
                CGPathRelease(path)
            }
        }
        is PdfOp.Image -> {
            // Imagem que não decodifica deixa a caixa em branco (contrato do `image()`).
            val image = UIImage.imageWithData(op.bytes.toNSData()) ?: return
            val (w, h) = image.size.useContents { width to height }
            if (w <= 0.0 || h <= 0.0) return
            val r = imageDrawRect(w, h, op.x, op.y, op.width, op.height, op.fit)
            CGContextSaveGState(cg)
            CGContextClipToRect(cg, CGRectMake(op.x, op.y, op.width, op.height))
            image.drawInRect(CGRectMake(r[0], r[1], r[2], r[3]))
            CGContextRestoreGState(cg)
        }
    }
}

private fun applyStroke(cg: CGContextRef, stroke: PdfStroke) {
    CGContextSetStrokeColorWithColor(cg, stroke.color.uiColor().CGColor)
    CGContextSetLineWidth(cg, stroke.widthPt)
    CGContextSetLineCap(cg, CGLineCap.kCGLineCapButt)
    val dash = stroke.dash
    if (dash != null) {
        memScoped {
            val lengths = allocArray<CGFloatVar>(2)
            lengths[0] = dash.onMm.mmToPoints()
            lengths[1] = dash.offMm.mmToPoints()
            CGContextSetLineDash(cg, dash.phaseMm.mmToPoints(), lengths, 2u)
        }
    } else {
        CGContextSetLineDash(cg, 0.0, null, 0u)
    }
}

private fun drawText(cg: CGContextRef, op: PdfOp.Text, fonts: IosFontCache) {
    val line = fonts.makeLine(op.text, op.style) ?: return
    try {
        CGContextSaveGState(cg)
        // Matriz de texto identidade, translada até a linha de base e inverte o Y localmente: o
        // CoreText desenha "de pé" num contexto UIKit (topo-esquerda).
        CGContextSetTextMatrix(cg, CGAffineTransformMake(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))
        CGContextTranslateCTM(cg, op.xPt, op.baselinePt)
        CGContextScaleCTM(cg, 1.0, -1.0)
        CGContextSetTextPosition(cg, 0.0, 0.0)
        CTLineDraw(line, cg)
        CGContextRestoreGState(cg)
    } finally {
        CFRelease(line)
    }
}

private class IosPdfTextMeasurer : PdfTextMeasurer {
    private val fonts = IosFontCache()
    private val metrics = HashMap<PdfTextStyle, PdfFontMetricsPt>()

    override fun widthPt(text: String, style: PdfTextStyle): Double {
        if (text.isEmpty()) return 0.0
        val line = fonts.makeLine(text, style) ?: return 0.0
        return try {
            CTLineGetTypographicBounds(line, null, null, null)
        } finally {
            CFRelease(line)
        }
    }

    override fun fontMetrics(style: PdfTextStyle): PdfFontMetricsPt = metrics.getOrPut(style) {
        val font = fonts.font(style)
        PdfFontMetricsPt(
            ascentPt = font.ascender,
            descentPt = -font.descender,
            capHeightPt = font.capHeight,
        )
    }
}

/** `UIFont` por estilo, dentro de UM documento. */
private class IosFontCache {
    private val fonts = HashMap<PdfTextStyle, UIFont>()

    fun font(style: PdfTextStyle): UIFont = fonts.getOrPut(style) { resolveFont(style) }

    /** `CTLine` do texto com fonte e cor; quem chama faz `CFRelease`. */
    fun makeLine(text: String, style: PdfTextStyle): CTLineRef? {
        val font = font(style)
        val attr = NSMutableAttributedString.create(string = text)
        val range = NSMakeRange(0u, attr.length)
        // "NSFont"/"CTForegroundColor" são os valores de kCTFontAttributeName e
        // kCTForegroundColorAttributeName — as strings evitam o bridging das constantes CFString.
        attr.addAttribute("NSFont", value = font, range = range)
        // Cor como OBJETO ObjC: o `CGColorRef` cru vira embrulho Kotlin e derruba o CoreText.
        style.color.uiColor().coreTextForegroundColor()?.let {
            attr.addAttribute("CTForegroundColor", value = it, range = range)
        }
        if (needsSyntheticBold(style)) {
            // Negrito sintético (família sem o arquivo negrito): contorno + preenchimento, a -3 % do
            // corpo — kCTStrokeWidthAttributeName negativo. Não muda a largura medida.
            attr.addAttribute("NSStrokeWidth", value = NSNumber(double = -3.0), range = range)
        }
        val cfAttr = CFBridgingRetain(attr as NSAttributedString)
        val line = CTLineCreateWithAttributedString(cfAttr?.reinterpret())
        cfAttr?.let { CFRelease(it) }
        return line
    }

    private fun needsSyntheticBold(style: PdfTextStyle): Boolean =
        style.bold && style.font.kind == PdfFontFamily.Kind.Custom && style.font.bold == null
}

private fun resolveFont(style: PdfTextStyle): UIFont {
    val size = style.sizePt
    val weight = if (style.bold) UIFontWeightBold else UIFontWeightRegular
    return when (style.font.kind) {
        PdfFontFamily.Kind.SansSerif ->
            if (style.tabularNumbers) {
                UIFont.monospacedDigitSystemFontOfSize(size, weight)
            } else {
                UIFont.systemFontOfSize(size, weight)
            }
        PdfFontFamily.Kind.Monospace -> UIFont.monospacedSystemFontOfSize(size, weight)
        PdfFontFamily.Kind.Custom -> {
            val faces = loadedDescriptors(style.font)
            val descriptor = if (style.bold && faces.bold != null) faces.bold else faces.regular
            val ctFont = CTFontCreateWithFontDescriptor(descriptor, size, null)
                ?: throw IllegalArgumentException("a fonte '${style.font.name}' não pôde ser carregada")
            val base = CFBridgingRelease(ctFont) as UIFont
            if (style.tabularNumbers) withTabularNumbers(base, size) else base
        }
    }
}

/** Algarismos de largura fixa: kNumberSpacingType (6) / kMonospacedNumbersSelector (0). */
private fun withTabularNumbers(font: UIFont, size: Double): UIFont {
    val feature = mapOf<Any?, Any?>(
        UIFontFeatureTypeIdentifierKey to NSNumber(int = 6),
        UIFontFeatureSelectorIdentifierKey to NSNumber(int = 0),
    )
    val descriptor = font.fontDescriptor.fontDescriptorByAddingAttributes(
        mapOf<Any?, Any?>(UIFontDescriptorFeatureSettingsAttribute to listOf(feature)),
    )
    return UIFont.fontWithDescriptor(descriptor, size)
}

private class IosLoadedFaces(val regular: CTFontDescriptorRef, val bold: CTFontDescriptorRef?)

/**
 * Descritores CoreText da família, criados uma vez por instância (`CTFontManagerCreateFontDescriptorFromData`
 * — fonte em memória, sem registrar no processo). Ficam retidos enquanto a família existir.
 */
private fun loadedDescriptors(family: PdfFontFamily): IosLoadedFaces {
    (family.platformCache as? IosLoadedFaces)?.let { return it }
    val faces = IosLoadedFaces(
        regular = descriptorFrom(family.regular!!, family.name),
        bold = family.bold?.let { descriptorFrom(it, family.name + " (negrito)") },
    )
    family.platformCache = faces
    return faces
}

private fun descriptorFrom(bytes: ByteArray, name: String): CTFontDescriptorRef {
    val cfData = CFBridgingRetain(bytes.toNSData())
    try {
        val data: CFDataRef? = cfData?.reinterpret()
        return CTFontManagerCreateFontDescriptorFromData(data)
            ?: throw IllegalArgumentException("a fonte '$name' não é um TTF/OTF válido")
    } finally {
        cfData?.let { CFRelease(it) }
    }
}

private fun PdfColor.uiColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    bytes?.let { src -> out.usePinned { platform.posix.memcpy(it.addressOf(0), src, len.toULong()) } }
    return out
}
