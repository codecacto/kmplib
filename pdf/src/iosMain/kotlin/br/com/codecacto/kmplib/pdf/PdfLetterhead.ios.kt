@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage

private const val TAG = "KmpLibPdfLetterhead"

/**
 * Imagem do timbrado decodificada pela **redução oficial da Apple** — ImageIO
 * `CGImageSourceCreateThumbnailAtIndex` com `kCGImageSourceThumbnailMaxPixelSize` (WWDC18, sessão
 * 219): decodifica já na medida pedida, nunca a imagem inteira, e `WithTransform` põe a foto em pé
 * pela orientação EXIF. O [CGImageRef] é nosso (+1) e sai em [release].
 */
internal class IosLetterheadImage private constructor(
    private val source: CGImageSourceRef,
    var image: CGImageRef,
) {
    val width: Int get() = CGImageGetWidth(image).toInt()
    val height: Int get() = CGImageGetHeight(image).toInt()

    /** Troca a imagem por uma reduzida a [maxPixels] no maior lado (só se for menor que a atual). */
    fun downsampleTo(maxPixels: Int) {
        if (maxPixels >= maxOf(width, height)) return
        val smaller = thumbnail(source, maxPixels) ?: return
        CFRelease(image)
        image = smaller
    }

    fun release() {
        CFRelease(image)
        CFRelease(source)
    }

    companion object {
        /** PNG/JPEG aceito → imagem com o maior lado ≤ [PdfLetterheadLayout.MAX_DECODE_PX]; senão `null`. */
        fun decode(bytes: ByteArray?): IosLetterheadImage? {
            if (bytes == null || bytes.isEmpty()) return null
            if (!PdfLetterheadLayout.isAcceptedImage(bytes)) {
                AppLogger.w(TAG, "Imagem do timbrado ignorada: não é PNG/JPEG ou passa de ${PdfLetterheadLayout.MAX_IMAGE_BYTES} bytes.")
                return null
            }
            val cfData = bytes.usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
            } ?: return null
            val source = CGImageSourceCreateWithData(cfData, null)
            CFRelease(cfData)
            if (source == null) {
                AppLogger.w(TAG, "Imagem do timbrado não reconhecida.")
                return null
            }
            val image = thumbnail(source, PdfLetterheadLayout.MAX_DECODE_PX)
            if (image == null) {
                AppLogger.w(TAG, "Imagem do timbrado não pôde ser decodificada.")
                CFRelease(source)
                return null
            }
            return IosLetterheadImage(source, image)
        }

        private fun thumbnail(source: CGImageSourceRef, maxPixels: Int): CGImageRef? {
            val options = CFDictionaryCreateMutable(null, 4, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
            memScoped {
                val value = alloc<IntVar>().apply { this.value = maxPixels.coerceAtLeast(1) }
                val number = CFNumberCreate(null, kCFNumberIntType, value.ptr)
                CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
                if (number != null) CFRelease(number)
            }
            val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
            CFRelease(options)
            return image
        }
    }
}

/** O timbrado pronto para desenhar em cada página do `UIGraphicsPDFRenderer`. */
internal class IosLetterhead(
    val frame: LetterheadPageFrame,
    private val headerImage: IosLetterheadImage?,
    private val footerImage: IosLetterheadImage?,
    private val pageWidth: Double,
    private val margin: Double,
    private val textColor: PdfColor,
    private val lineColor: PdfColor,
) {
    fun drawOn(canvas: IosPdfCanvas) {
        frame.header?.let { drawBand(canvas, it, frame.headerTop, headerImage) }
        frame.footer?.let { drawBand(canvas, it, frame.footerTop, footerImage) }
        frame.headerSeparatorY?.let { canvas.line(margin, it.toDouble(), pageWidth - margin, it.toDouble(), lineColor, 0.75) }
        frame.footerSeparatorY?.let { canvas.line(margin, it.toDouble(), pageWidth - margin, it.toDouble(), lineColor, 0.75) }
    }

    private fun drawBand(canvas: IosPdfCanvas, band: LetterheadBandLayout, top: Float, image: IosLetterheadImage?) {
        val rect = band.image
        if (rect != null && image != null) {
            // Contexto do UIGraphicsPDFRenderer é UIKit (topo-esquerda): `drawInRect` desenha em pé.
            UIImage.imageWithCGImage(image.image).drawInRect(
                CGRectMake(rect.x.toDouble(), (top + rect.y).toDouble(), rect.width.toDouble(), rect.height.toDouble()),
            )
        }
        val align = if (band.align == PdfLetterheadAlign.CENTER) PdfTextAlign.Center else PdfTextAlign.Left
        band.lines.forEachIndexed { i, text ->
            canvas.text(text, band.textAnchorX.toDouble(), (top + band.baselines[i]).toDouble(), band.fontSize.toDouble(), bold = false, color = textColor, align = align)
        }
    }

    fun release() {
        headerImage?.release()
        footerImage?.release()
    }
}

/**
 * Mede as faixas (texto pelo CoreText do [canvas]) e decodifica as imagens do [letterhead]. `null`
 * sem timbrado — o PDF segue o caminho de sempre.
 */
internal fun prepareIosLetterhead(
    letterhead: PdfLetterhead?,
    canvas: IosPdfCanvas,
    pageWidth: Double,
    pageHeight: Double,
    margin: Double,
    textColor: PdfColor,
    lineColor: PdfColor,
): IosLetterhead? {
    if (letterhead == null || letterhead.isEmpty) return null
    val width = (pageWidth - 2 * margin).toFloat()
    fun measurer(spec: LetterheadBandSpec): (String) -> Float =
        { canvas.measure(it, spec.fontSize.toDouble(), bold = false).toFloat() }

    val headerImage = IosLetterheadImage.decode(letterhead.headerImage)
    val footerImage = IosLetterheadImage.decode(letterhead.footerImage)
    val header = if (letterhead.hasHeader) {
        PdfLetterheadLayout.layoutBand(
            PdfLetterheadLayout.HEADER, letterhead.headerAlign, margin.toFloat(), width,
            headerImage?.width, headerImage?.height, letterhead.headerText, measurer(PdfLetterheadLayout.HEADER),
        )
    } else null
    val footer = if (letterhead.hasFooter) {
        PdfLetterheadLayout.layoutBand(
            PdfLetterheadLayout.FOOTER, letterhead.footerAlign, margin.toFloat(), width,
            footerImage?.width, footerImage?.height, letterhead.footerText, measurer(PdfLetterheadLayout.FOOTER),
        )
    } else null
    if (header == null && footer == null) {
        headerImage?.release(); footerImage?.release()
        return null
    }
    // Reduz cada imagem aos pixels que vão ao papel (≈ 216 dpi) — o PDF não carrega 1600 px à toa.
    fun shrink(image: IosLetterheadImage?, rect: LetterheadRect?): IosLetterheadImage? {
        if (image == null) return null
        if (rect == null) { image.release(); return null }
        val (w, h) = PdfLetterheadLayout.renderPixels(rect.width, rect.height, image.width, image.height)
        image.downsampleTo(maxOf(w, h))
        return image
    }
    return IosLetterhead(
        frame = PdfLetterheadLayout.pageFrame(pageHeight.toFloat(), margin.toFloat(), header, footer),
        headerImage = shrink(headerImage, header?.image),
        footerImage = shrink(footerImage, footer?.image),
        pageWidth = pageWidth,
        margin = margin,
        textColor = textColor,
        lineColor = lineColor,
    )
}
