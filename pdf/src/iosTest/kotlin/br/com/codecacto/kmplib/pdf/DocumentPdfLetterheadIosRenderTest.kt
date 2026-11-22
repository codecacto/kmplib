@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 2.377.0 — o timbrado no PDF de documento do iOS: cabeçalho e rodapé em TODA página. Roda no
 * simulador (Mac); relê com o PDFKit e confere o texto do timbrado em cada página.
 */
class DocumentPdfLetterheadIosRenderTest {

    @Test
    fun timbradoSaiEmTodaPagina() {
        val rows = List(120) { DocumentRow(listOf("Linha $it", "R$ $it,00")) }
        val data = DocumentPdfData(
            company = DocumentPdfCompany("Empresa padrao"),
            title = "Relatorio",
            sections = listOf(
                DocumentSection.Table(columns = listOf(DocumentColumn("Item"), DocumentColumn("Valor")), rows = rows),
            ),
            footer = "Gerado em 22/11/2026",
            letterhead = PdfLetterhead(
                headerText = "Clinica Royal CRM 1234",
                replacesDefaultHeader = true,
                footerText = "Rua das Flores 100",
                // Imagem inválida: é ignorada, o PDF sai igual.
                headerImage = byteArrayOf(1, 2, 3, 4),
            ),
        )
        val bytes = generateDocumentPdfBytes(data)
        val document = assertNotNull(PDFDocument(data = bytes.toNSData()))
        val pages = document.pageCount().toInt()
        assertTrue(pages >= 2, "120 linhas paginam")
        for (i in 0 until pages) {
            val text = document.pageAtIndex(i.toULong())?.string ?: ""
            assertTrue(text.contains("Clinica Royal"), "cabeçalho na página ${i + 1}: '$text'")
            assertTrue(text.contains("Rua das Flores"), "rodapé na página ${i + 1}")
        }
        val first = document.pageAtIndex(0u)?.string ?: ""
        assertTrue(!first.contains("Empresa padrao"), "o timbrado substitui o bloco da empresa")
    }
}

private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
