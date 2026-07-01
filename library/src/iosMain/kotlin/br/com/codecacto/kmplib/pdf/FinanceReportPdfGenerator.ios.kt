package br.com.codecacto.kmplib.pdf

/**
 * Implementação stub do gerador de PDF de relatório financeiro para iOS.
 *
 * TODO: Implementar quando as APIs de desenho de texto do UIKit estiverem
 *       disponíveis no Kotlin/Native 2.x, ou usar uma biblioteca de terceiros.
 */
private class IosFinanceReportPdfGenerator : FinanceReportPdfGenerator {
    override fun generate(data: FinanceReportPdfData): ByteArray {
        throw OsPdfNotSupportedException(
            "Geração de PDF de relatório financeiro ainda não suportada no iOS. " +
            "As APIs de desenho de texto do UIKit não estão disponíveis no Kotlin/Native 2.x."
        )
    }
}

actual fun createFinanceReportPdfGenerator(): FinanceReportPdfGenerator = IosFinanceReportPdfGenerator()
