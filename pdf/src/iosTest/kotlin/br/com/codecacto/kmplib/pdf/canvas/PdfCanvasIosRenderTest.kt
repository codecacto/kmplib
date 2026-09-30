@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf.canvas

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Renderização de verdade no iOS (`UIGraphicsPDFRenderer` + CoreText), relida com o PDFKit. Roda no
 * simulador (`iosSimulatorArm64Test`, host macOS); no servidor Linux ela só COMPILA.
 */
class PdfCanvasIosRenderTest {

    @Test
    fun geraPaginasComAMedidaPedidaEOTextoPesquisavel() {
        val bytes = recordPdf {
            page(85.0, 55.0) {
                text("Colinha do Voto", 3.0, 3.0, PdfTextStyle(8.0, bold = true))
                textInBox("7", 30.0, 10.0, 6.0, 7.0, PdfTextStyle(14.0, bold = true, tabularNumbers = true))
                rect(30.0, 10.0, 6.0, 7.0, stroke = PdfStroke(0.75))
                line(0.0, 50.0, 85.0, 50.0, PdfStroke(0.5, dash = PdfDash.CutLine))
            }
            page(PdfPageSize.THERMAL_58) {
                textBlock("CELULAR NÃO ENTRA NA CABINE", 5.0, 5.0, 48.0, PdfTextStyle(9.0, bold = true))
            }
        }.toByteArray()

        val document = assertNotNull(PDFDocument(data = bytes.toNSData()))
        assertEquals(2uL, document.pageCount())

        val card = assertNotNull(document.pageAtIndex(0u)).boundsForBox(kPDFDisplayBoxMediaBox)
        card.useContents {
            assertTrue(abs(size.width - 85.0 * 72.0 / 25.4) < 0.5, "largura ${size.width}")
            assertTrue(abs(size.height - 55.0 * 72.0 / 25.4) < 0.5, "altura ${size.height}")
        }
        val roll = assertNotNull(document.pageAtIndex(1u)).boundsForBox(kPDFDisplayBoxMediaBox)
        roll.useContents { assertTrue(size.height < 297.0 * 72.0 / 25.4, "a bobina seguiu o conteúdo") }

        val text = document.string ?: ""
        assertTrue(text.contains("Colinha do Voto"), "texto embutido e pesquisável: '$text'")
        assertTrue(text.contains("CABINE"))
    }

    @Test
    fun medidorDaPlataformaDevolveMetricasPositivas() {
        val m = platformPdfTextMeasurer()
        val style = PdfTextStyle(12.0, tabularNumbers = true)
        assertTrue(m.widthPt("1111", style) > 0.0)
        // Algarismos tabulares: "1111" e "8888" ocupam o mesmo espaço.
        assertTrue(abs(m.widthPt("1111", style) - m.widthPt("8888", style)) < 0.01)
        val metrics = m.fontMetrics(style)
        assertTrue(metrics.ascentPt > 0.0 && metrics.descentPt > 0.0 && metrics.capHeightPt > 0.0)
    }
}

private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
