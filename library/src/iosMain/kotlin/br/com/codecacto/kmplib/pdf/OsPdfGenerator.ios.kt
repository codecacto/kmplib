package br.com.codecacto.kmplib.pdf

/**
 * Implementação stub do gerador de PDF de OS para iOS.
 *
 * TODO: Implementar quando as APIs de desenho de texto do UIKit estiverem
 *       disponíveis no Kotlin/Native 2.x, ou usar uma biblioteca de terceiros.
 */
private class IosOsPdfGenerator : OsPdfGenerator {
    override fun generate(data: OsPdfData): ByteArray {
        throw OsPdfNotSupportedException(
            "Geração de PDF de OS ainda não suportada no iOS. " +
            "As APIs de desenho de texto do UIKit não estão disponíveis no Kotlin/Native 2.x. " +
            "Cheque PlatformCapabilities.pdfGeneration antes de oferecer a feature."
        )
    }
}

actual fun createOsPdfGenerator(): OsPdfGenerator = IosOsPdfGenerator()
