package br.com.codecacto.kmplib.pdf

/**
 * Implementação iOS do [HoursReportPdfGenerator].
 *
 * PENDÊNCIA (herda o item do backlog da kmplib — publicação iOS a partir de host macOS): o render
 * real deve usar `UIGraphicsPDFRenderer` (UIKit), desenhando o mesmo layout lógico do Android.
 * Até lá, [generate] lança [OsPdfNotSupportedException] — o consumidor deve tratar com fallback.
 */
class IosHoursReportPdfGenerator : HoursReportPdfGenerator {
    override fun generate(data: HoursReportPdfData): ByteArray {
        // TODO(iOS/macOS): implementar com UIGraphicsPDFRenderer (UIKit).
        throw OsPdfNotSupportedException(
            "Geração de PDF do relatório de horas ainda não implementada no iOS (requer render " +
                "nativo UIGraphicsPDFRenderer em host macOS). Use um fallback de texto via ShareHandler.",
        )
    }
}

actual fun createHoursReportPdfGenerator(): HoursReportPdfGenerator = IosHoursReportPdfGenerator()
