package br.com.codecacto.kmplib.pdf

/**
 * Implementação stub do gerador de PDF de relatório tabular para iOS.
 *
 * TODO: Implementar quando as APIs de desenho de texto do UIKit estiverem
 *       disponíveis no Kotlin/Native 2.x, ou usar uma biblioteca de terceiros.
 */
private class IosTableReportPdfGenerator : TableReportPdfGenerator {
    override fun generate(data: TableReportPdfData): ByteArray {
        throw OsPdfNotSupportedException(
            "Geração de PDF de relatório tabular ainda não suportada no iOS. " +
            "As APIs de desenho de texto do UIKit não estão disponíveis no Kotlin/Native 2.x."
        )
    }
}

actual fun createTableReportPdfGenerator(): TableReportPdfGenerator = IosTableReportPdfGenerator()
