@file:OptIn(ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.KmpLibUiInternalApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
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
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIGraphicsImageRendererFormatRangeStandard
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.drawInRect
import platform.posix.memcpy

/**
 * Bytes de imagem (o que o `PHPicker` e o `AVCapturePhoto` entregam) → [PickedImage], pela
 * **redução oficial da Apple**: ImageIO `CGImageSourceCreateThumbnailAtIndex` com
 * `kCGImageSourceThumbnailMaxPixelSize` (WWDC18 *Image and Graphics Best Practices*, sessão 219).
 *
 * Por que ImageIO e não `UIImage(data:)` + desenhar (o caminho até a 2.277.x): o `UIImage` decodifica
 * a foto **inteira** para depois reduzir — 48 MP são ~190 MB de memória por um instante, e com o
 * teto configurável (até 4096 px) isso deixa de ser raro. O ImageIO decodifica já na medida pedida.
 *
 * - `kCGImageSourceCreateThumbnailWithTransform` aplica a orientação EXIF **aos pixels**: o JPEG que
 *   sai está em pé e sem tag de rotação.
 * - `kCGImageSourceCreateThumbnailFromImageAlways` ignora a miniatura embutida no arquivo (que pode
 *   ter 160 px) e reduz a partir da imagem cheia.
 * - O JPEG é recodificado a partir do `CGImage` sem metadados: **EXIF/GPS não viajam**, e HEIC vira
 *   JPEG.
 * - A imagem nunca é ampliada (a miniatura não passa da medida original).
 *
 * @return `null` quando os bytes não são uma imagem legível — o log já nomeou o motivo.
 */
@KmpLibUiInternalApi
fun NSData.toPickedImage(
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
    jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY,
): PickedImage? {
    val teto = coercePickedImageMaxDimension(maxDimension)
    val qualidade = coercePickedImageJpegQuality(jpegQuality)
    if (length.toLong() == 0L) {
        AppLogger.w(TAG, "Foto vazia.")
        return null
    }

    // Cópia para um CFData próprio: a ponte toll-free (`CFBridgingRetain`) devolve um ponteiro
    // opaco que exigiria `reinterpret`, e a cópia é barata perto da decodificação.
    val cfData = CFDataCreate(null, bytes?.reinterpret(), length.toLong()) ?: run {
        AppLogger.w(TAG, "Não foi possível ler os bytes da foto.")
        return null
    }
    val fonte = CGImageSourceCreateWithData(cfData, null)
    CFRelease(cfData)
    if (fonte == null) {
        AppLogger.w(TAG, "Bytes não reconhecidos como imagem.")
        return null
    }

    val opcoes = CFDictionaryCreateMutable(
        null,
        4,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )
    CFDictionarySetValue(opcoes, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
    CFDictionarySetValue(opcoes, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
    CFDictionarySetValue(opcoes, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
    memScoped {
        val valor = alloc<IntVar>().apply { value = teto }
        val numero = CFNumberCreate(null, kCFNumberIntType, valor.ptr)
        CFDictionarySetValue(opcoes, kCGImageSourceThumbnailMaxPixelSize, numero)
        // O dicionário reteve o número (callbacks kCFType); a nossa referência sai aqui.
        if (numero != null) CFRelease(numero)
    }

    val miniatura = CGImageSourceCreateThumbnailAtIndex(fonte, 0u, opcoes)
    CFRelease(opcoes)
    CFRelease(fonte)
    if (miniatura == null) {
        AppLogger.w(TAG, "A foto não pôde ser decodificada.")
        return null
    }

    return try {
        val largura = CGImageGetWidth(miniatura).toInt()
        val altura = CGImageGetHeight(miniatura).toInt()
        val jpeg = UIImageJPEGRepresentation(UIImage.imageWithCGImage(miniatura), qualidade / 100.0)
        if (jpeg == null || largura <= 0 || altura <= 0) {
            AppLogger.w(TAG, "Não foi possível codificar a foto em JPEG.")
            null
        } else {
            PickedImage(bytes = jpeg.toByteArray(), widthPx = largura, heightPx = altura)
        }
    } finally {
        // `Create` = referência +1, e o Kotlin/Native não gerencia tipo Core Foundation.
        CFRelease(miniatura)
    }
}

/**
 * `UIImage` → [PickedImage]: o caminho de quem já tem a imagem decodificada (a câmera do
 * `UIImagePickerController` entrega `UIImage`, não bytes).
 *
 * ⚠️ A `UIImage` de uma foto de iPhone quase nunca está em pé: os pixels vêm deitados e a orientação
 * vem à parte, em `imageOrientation`. `image.size` já é a medida **exibida**, mas
 * `UIImageJPEGRepresentation` grava os **pixels crus mais a tag** — então a imagem é **sempre
 * redesenhada** (o desenho aplica a orientação) antes de codificar, e o que sai não tem tag de
 * rotação: medida devolvida e medida contida nos bytes são a mesma coisa.
 *
 * O redesenho usa `UIGraphicsImageRenderer` (2.278.0) — o substituto oficial do
 * `UIGraphicsBeginImageContextWithOptions`, depreciado no iOS 17 — com escala 1 (a medida é em
 * pixels) e faixa de cor padrão (a faixa estendida dobra a memória do contexto e o JPEG não a usa).
 */
@KmpLibUiInternalApi
fun UIImage.toPickedImage(
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
    jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY,
): PickedImage? {
    val teto = coercePickedImageMaxDimension(maxDimension)
    val qualidade = coercePickedImageJpegQuality(jpegQuality)

    // `size` está em PONTOS; `scale` os converte em pixels.
    val (larguraPt, alturaPt) = size.useContents { width to height }
    val larguraPx = (larguraPt * scale).toInt()
    val alturaPx = (alturaPt * scale).toInt()
    if (larguraPx <= 0 || alturaPx <= 0) {
        AppLogger.w(TAG, "Foto sem medida utilizável.")
        return null
    }

    val (largura, altura) = scaledImageSize(larguraPx, alturaPx, teto)
    val formato = UIGraphicsImageRendererFormat.defaultFormat().apply {
        scale = 1.0
        preferredRange = UIGraphicsImageRendererFormatRangeStandard
    }
    val renderizador = UIGraphicsImageRenderer(
        size = CGSizeMake(largura.toDouble(), altura.toDouble()),
        format = formato,
    )
    val original = this
    val normalizada = renderizador.imageWithActions { _ ->
        original.drawInRect(CGRectMake(0.0, 0.0, largura.toDouble(), altura.toDouble()))
    }

    val jpeg = UIImageJPEGRepresentation(normalizada, qualidade / 100.0)
    if (jpeg == null) {
        AppLogger.w(TAG, "Não foi possível codificar a foto em JPEG.")
        return null
    }
    return PickedImage(bytes = jpeg.toByteArray(), widthPx = largura, heightPx = altura)
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}

private const val TAG = "KmpLibImageEncoder"
