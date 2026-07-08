package br.com.codecacto.kmplib.pdf

/**
 * Implementação stub do gerador de PDF de carteira de vacinação para iOS.
 *
 * TODO: Implementar quando as APIs de desenho de texto do UIKit estiverem
 *       disponíveis no Kotlin/Native 2.x, ou usar uma biblioteca de terceiros.
 */
private class IosVaccinationCardPdfGenerator : VaccinationCardPdfGenerator {
    override fun generate(data: VaccinationCardPdfData): ByteArray {
        throw OsPdfNotSupportedException(
            "Geração de PDF de carteira de vacinação ainda não suportada no iOS. " +
            "As APIs de desenho de texto do UIKit não estão disponíveis no Kotlin/Native 2.x. " +
            "Cheque PlatformCapabilities.pdfGeneration antes de oferecer a feature."
        )
    }
}

actual fun createVaccinationCardPdfGenerator(): VaccinationCardPdfGenerator = IosVaccinationCardPdfGenerator()
