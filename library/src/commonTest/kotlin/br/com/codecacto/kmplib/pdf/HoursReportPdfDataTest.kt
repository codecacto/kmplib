package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HoursReportPdfDataTest {

    @Test
    fun `nome de arquivo default sanitiza e adiciona pdf`() {
        val name = defaultHoursReportPdfFileName("01/05 a 31/05/2026")
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.startsWith("relatorio-horas-"))
        // Sem espaços/barras crus no nome de arquivo.
        assertTrue(name.none { it == ' ' || it == '/' })
    }

    @Test
    fun `nome de arquivo vazio mantem o prefixo do template`() {
        // Espelha defaultWorkReportPdfFileName: só "_" é trimado.
        assertEquals("relatorio-horas-.pdf", defaultHoursReportPdfFileName("   "))
        assertEquals("relatorio-horas-.pdf", defaultHoursReportPdfFileName("@@@"))
    }

    @Test
    fun `company equals e hashCode consideram logoBytes`() {
        val a = HoursReportPdfCompany("X", logoBytes = byteArrayOf(1, 2, 3))
        val b = HoursReportPdfCompany("X", logoBytes = byteArrayOf(1, 2, 3))
        val c = HoursReportPdfCompany("X", logoBytes = byteArrayOf(9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun `attachment equals considera imageBytes e caption`() {
        val a = HoursReportAttachment(byteArrayOf(1, 2), caption = "Holerite")
        val b = HoursReportAttachment(byteArrayOf(1, 2), caption = "Holerite")
        val c = HoursReportAttachment(byteArrayOf(1, 2), caption = "Outro")
        val d = HoursReportAttachment(byteArrayOf(9), caption = "Holerite")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
        assertTrue(a != d)
    }

    @Test
    fun `entry guarda os campos formatados`() {
        val e = HoursReportEntry(
            date = "07/06/2026",
            start = "08:00",
            end = "12:30",
            durationLabel = "4h30",
            valueLabel = "R$ 90,00",
            statusLabel = "Pendente",
        )
        assertEquals("07/06/2026", e.date)
        assertEquals("08:00", e.start)
        assertEquals("12:30", e.end)
        assertEquals("4h30", e.durationLabel)
        assertEquals("R$ 90,00", e.valueLabel)
        assertEquals("Pendente", e.statusLabel)
    }

    @Test
    fun `entry value opcional default null`() {
        val e = HoursReportEntry(
            date = "07/06/2026",
            start = "08:00",
            end = "12:30",
            durationLabel = "4h30",
            statusLabel = "Pendente",
        )
        assertNull(e.valueLabel)
    }

    @Test
    fun `data defaults sao seguros e shape congelado`() {
        val data = HoursReportPdfData(
            company = HoursReportPdfCompany("João da Silva"),
            periodLabel = "01/05 a 31/05/2026",
            companyLabel = "Empresa: Construtora X",
            generatedAtLabel = "Emitido em 07/06/2026",
            totalHoursLabel = "32h30",
        )
        assertTrue(data.entries.isEmpty())
        assertTrue(data.attachments.isEmpty())
        assertNull(data.totalPendingLabel)
        assertNull(data.totalPaidLabel)
        assertNull(data.totalContestedLabel)
        assertTrue(!data.watermark)
        assertNull(data.watermarkText)
    }
}
