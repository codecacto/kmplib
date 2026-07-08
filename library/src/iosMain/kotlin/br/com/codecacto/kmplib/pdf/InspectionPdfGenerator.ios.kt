package br.com.codecacto.kmplib.pdf

/**
 * Implementação stub do gerador de PDF de vistoria para iOS.
 *
 * Débito conhecido dos PDFs iOS da kmplib (memória `kmplib-ios-pdf-stub-debt`): as APIs de desenho
 * de texto do UIKit (`NSString.drawAtPoint`/`sizeWithAttributes`) não estão disponíveis no
 * Kotlin/Native 2.x. Não bloqueia — nenhum app builda iOS no servidor. Restaurar via
 * `UIGraphicsPDFRenderer` (com o MESMO layout lógico do Android, incluindo `drawBitmap` das
 * fotos-prova/assinaturas) quando as APIs estiverem disponíveis ou em host macOS.
 */
private class IosInspectionPdfGenerator : InspectionPdfGenerator {
    override fun generate(data: InspectionPdfData): ByteArray {
        throw OsPdfNotSupportedException(
            "Geração de PDF de vistoria ainda não suportada no iOS. " +
                "As APIs de desenho de texto do UIKit não estão disponíveis no Kotlin/Native 2.x. " +
            "Cheque PlatformCapabilities.pdfGeneration antes de oferecer a feature."
        )
    }
}

actual fun createInspectionPdfGenerator(): InspectionPdfGenerator = IosInspectionPdfGenerator()
