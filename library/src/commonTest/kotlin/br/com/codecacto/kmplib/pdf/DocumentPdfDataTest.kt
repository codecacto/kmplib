package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentPdfDataTest {

    @Test
    fun defaultFileName_sanitizesAndAddsPdf() {
        assertEquals("Contrato_de_Parceria.pdf", defaultDocumentPdfFileName("Contrato de Parceria"))
        // Acentos não são ASCII → viram separador (a sanitização é só A-Za-z0-9._-).
        assertEquals("Relat_rio.pdf", defaultDocumentPdfFileName("Relatório"))
    }

    @Test
    fun defaultFileName_keepsExistingPdfExtension() {
        assertEquals("doc.pdf", defaultDocumentPdfFileName("doc.pdf"))
    }

    @Test
    fun defaultFileName_emptyFallsBack() {
        assertEquals("documento.pdf", defaultDocumentPdfFileName("   "))
    }

    @Test
    fun company_equalsHandlesLogoBytes() {
        val a = DocumentPdfCompany(name = "X", logoBytes = byteArrayOf(1, 2, 3))
        val b = DocumentPdfCompany(name = "X", logoBytes = byteArrayOf(1, 2, 3))
        val c = DocumentPdfCompany(name = "X", logoBytes = byteArrayOf(9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun sections_modelComposesContractShape() {
        // Espelha o uso do PDF de contrato do Influencer.
        val data = DocumentPdfData(
            company = DocumentPdfCompany(name = "Agência"),
            title = "Contrato de Parceria",
            subtitle = "Recorrente",
            headerInfo = listOf(DocumentInfoRow("Contratada", "Ana")),
            sections = listOf(
                DocumentSection.Table(
                    title = "Termos",
                    columns = listOf(DocumentColumn("Item", weight = 2f), DocumentColumn("Valor", DocumentAlign.END)),
                    rows = listOf(DocumentRow(listOf("Stories / mês", "10"))),
                ),
                DocumentSection.Total(label = "Valor mensal", value = "R$ 1.500,00"),
            ),
            footer = "Emitido em 14/06/2026",
        )
        assertEquals(2, data.sections.size)
        val table = data.sections[0] as DocumentSection.Table
        assertEquals("Termos", table.title)
        assertEquals(DocumentAlign.END, table.columns[1].align)
        val total = data.sections[1] as DocumentSection.Total
        assertEquals("R$ 1.500,00", total.value)
    }

    @Test
    fun cards_defaultEmptyText() {
        val cards = DocumentSection.Cards(cards = emptyList())
        assertEquals("Sem indicadores.", cards.emptyText)
    }
}
