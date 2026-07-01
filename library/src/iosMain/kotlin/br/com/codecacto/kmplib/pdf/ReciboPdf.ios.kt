package br.com.codecacto.kmplib.pdf

/**
 * Implementação stub do gerador de PDF de recibo para iOS.
 *
 * TODO: Implementar quando as APIs de desenho de texto do UIKit estiverem
 *       disponíveis no Kotlin/Native 2.x, ou usar uma biblioteca de terceiros.
 *
 * As APIs NSString.drawAtPoint:withAttributes: e NSString.sizeWithAttributes:
 * não estão sendo exportadas corretamente no Kotlin/Native 2.3.0.
 */
actual fun generateReciboPdf(data: ReciboPdfData, watermark: Boolean): ByteArray {
    throw ReciboPdfNotSupportedException(
        "Geração de PDF de recibo ainda não suportada no iOS. " +
        "As APIs de desenho de texto do UIKit não estão disponíveis no Kotlin/Native 2.x."
    )
}
