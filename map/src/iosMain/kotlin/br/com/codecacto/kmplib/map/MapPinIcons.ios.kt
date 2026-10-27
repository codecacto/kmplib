package br.com.codecacto.kmplib.map

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGColorRetain
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetTextMatrix
import platform.CoreGraphics.CGContextSetTextPosition
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreText.CTLineCreateWithAttributedString
import platform.CoreText.CTLineDraw
import platform.CoreText.CTLineGetTypographicBounds
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSAttributedString
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.NSString
import platform.Foundation.addAttribute
import platform.Foundation.create
import platform.UIKit.UIBezierPath
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage

/**
 * Ícones do [NativeMap] sobre o Google Maps do iPhone (2.331.0): o MESMO desenho do Android
 * (`PinIcons` do `NativeMap.android.kt`) — gota de 32 × 42 pt com o texto curto ou o ponto no meio, e o
 * grupo como círculo com halo e o contador. Desenhados aqui (CoreGraphics + CoreText, que compilam
 * no servidor) para a ponte Swift só receber a `UIImage` pronta.
 *
 * Cache por aparência: a carteira tem milhares de pinos, mas poucas combinações de cor + texto.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object MapPinIcons {
    private const val MAX_CACHE = 256
    private val cache = LinkedHashMap<String, UIImage>()

    /** Âncora da gota: a ponta embaixo, no meio. */
    const val PIN_ANCHOR_X: Double = 0.5
    const val PIN_ANCHOR_Y: Double = 1.0

    fun pin(marker: ResolvedMarker): UIImage {
        val key = "p|${marker.color}|${marker.glyph}|${marker.glyphColor}"
        return cached(key) { drawPin(marker.color, marker.glyph, marker.glyphColor) }
    }

    fun cluster(cluster: ResolvedCluster): UIImage {
        val key = "c|${cluster.color}|${cluster.label}|${cluster.labelColor}"
        return cached(key) { drawCluster(cluster) }
    }

    private fun cached(key: String, draw: () -> UIImage): UIImage {
        cache[key]?.let { return it }
        val image = draw()
        if (cache.size >= MAX_CACHE) cache.remove(cache.keys.first())
        cache[key] = image
        return image
    }

    private fun render(width: Double, height: Double, block: (CGContextRef) -> Unit): UIImage {
        val format = UIGraphicsImageRendererFormat.preferredFormat()
        format.opaque = false
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format)
        return renderer.imageWithActions { _ ->
            UIGraphicsGetCurrentContext()?.let(block)
        }
    }

    private fun drawPin(color: Color, glyph: String?, glyphColor: Color): UIImage {
        val w = 32.0
        val h = 42.0
        val stroke = 2.0
        return render(w, h) { cg ->
            val r = w / 2 - stroke
            val cx = w / 2
            val cy = r + stroke
            val path = UIBezierPath.bezierPathWithArcCenter(
                center = CGPointMake(cx, cy),
                radius = r,
                startAngle = 0.0,
                endAngle = 2 * kotlin.math.PI,
                clockwise = true,
            )
            val tip = UIBezierPath()
            tip.moveToPoint(CGPointMake(cx - r * 0.62, cy + r * 0.78))
            tip.addLineToPoint(CGPointMake(cx, h - stroke))
            tip.addLineToPoint(CGPointMake(cx + r * 0.62, cy + r * 0.78))
            tip.closePath()
            path.appendPath(tip)
            color.toUIColor().setFill()
            path.fill()
            glyphColor.toUIColor().setStroke()
            path.lineWidth = stroke
            path.stroke()
            if (glyph != null) {
                drawCenteredText(cg, glyph, cx, cy, r, glyphColor)
            } else {
                glyphColor.toUIColor().setFill()
                val d = r * 0.32
                UIBezierPath.bezierPathWithOvalInRect(CGRectMake(cx - d, cy - d, 2 * d, 2 * d)).fill()
            }
        }
    }

    private fun drawCluster(cluster: ResolvedCluster): UIImage {
        val base = when (cluster.label.length) {
            1 -> 36.0
            2 -> 40.0
            else -> 46.0
        }
        val halo = 4.0
        val size = base + halo * 2
        return render(size, size) { cg ->
            val c = size / 2
            cluster.color.copy(alpha = 0.35f).toUIColor().setFill()
            UIBezierPath.bezierPathWithOvalInRect(CGRectMake(0.0, 0.0, size, size)).fill()
            cluster.color.toUIColor().setFill()
            UIBezierPath.bezierPathWithOvalInRect(CGRectMake(c - base / 2, c - base / 2, base, base)).fill()
            val ringR = base / 2 - 1.0
            val ring = UIBezierPath.bezierPathWithOvalInRect(CGRectMake(c - ringR, c - ringR, 2 * ringR, 2 * ringR))
            ring.lineWidth = 2.0
            cluster.labelColor.toUIColor().setStroke()
            ring.stroke()
            drawCenteredText(cg, cluster.label, c, c, base / 2, cluster.labelColor)
        }
    }

    /**
     * Texto em negrito centrado em ([cx], [cy]) — CoreText, como os PDFs do iOS da lib (as categorias
     * de desenho de texto do UIKit não chegam ao Kotlin/Native). Tamanho na mesma proporção do Android.
     */
    private fun drawCenteredText(cg: CGContextRef, text: String, cx: Double, cy: Double, radius: Double, color: Color) {
        val size = radius * when (text.length) {
            1 -> 1.05
            2 -> 0.9
            else -> 0.72
        }
        val font = UIFont.boldSystemFontOfSize(size)
        val attr = NSMutableAttributedString.create(string = text)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val range = NSMakeRange(0u, (text as NSString).length)
        attr.addAttribute("NSFont", value = font, range = range)
        // `CTForegroundColor` quer o CGColor como OBJETO (o `CGColorRef` cru derruba o processo).
        color.toUIColor().CGColor?.let { cgColor ->
            CFBridgingRelease(CGColorRetain(cgColor))?.let { attr.addAttribute("CTForegroundColor", value = it, range = range) }
        }
        val cfAttr = CFBridgingRetain(attr as NSAttributedString)
        val line = CTLineCreateWithAttributedString(cfAttr?.reinterpret()) ?: run {
            cfAttr?.let { CFRelease(it) }
            return
        }
        cfAttr?.let { CFRelease(it) }
        try {
            val width = CTLineGetTypographicBounds(line, null, null, null)
            // Centro vertical pela altura das maiúsculas (≈ 0,7 do tamanho) — o mesmo efeito do
            // `exactCenterY` do Android.
            val baseline = cy + font.capHeight / 2
            CGContextSaveGState(cg)
            CGContextSetTextMatrix(cg, CGAffineTransformMake(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))
            CGContextTranslateCTM(cg, cx - width / 2, baseline)
            CGContextScaleCTM(cg, 1.0, -1.0)
            CGContextSetTextPosition(cg, 0.0, 0.0)
            CTLineDraw(line, cg)
            CGContextRestoreGState(cg)
        } finally {
            CFRelease(line)
        }
    }
}
