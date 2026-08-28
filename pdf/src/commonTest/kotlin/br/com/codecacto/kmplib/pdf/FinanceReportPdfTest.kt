package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinanceReportPdfTest {

    // --- defaultFinanceReportPdfFileName -------------------------------------

    @Test
    fun defaultFileName_sanitizesAndAddsExtension() {
        assertEquals(
            "relatorio-financeiro-Junho_2026.pdf",
            defaultFinanceReportPdfFileName("Junho/2026"),
        )
    }

    @Test
    fun defaultFileName_handlesSpecialChars() {
        val name = defaultFinanceReportPdfFileName("01/06 a 30/06")
        assertTrue(name.endsWith(".pdf"))
        assertFalse(name.contains('/'))
        assertFalse(name.contains(' '))
    }

    @Test
    fun defaultFileName_emptyPeriod_stillValid() {
        val name = defaultFinanceReportPdfFileName("")
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.startsWith("relatorio-financeiro"))
        // sem caracteres inválidos
        assertFalse(name.contains(' '))
    }

    // --- Modelo: paridade de shape e logo no equals --------------------------

    @Test
    fun company_equalsConsidersLogoBytes() {
        val a = FinanceReportPdfCompany(name = "ACME", logoBytes = byteArrayOf(1, 2, 3))
        val b = FinanceReportPdfCompany(name = "ACME", logoBytes = byteArrayOf(1, 2, 3))
        val c = FinanceReportPdfCompany(name = "ACME", logoBytes = byteArrayOf(9, 9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }

    @Test
    fun model_holdsBothSectionsAndTotals() {
        val data = sampleData()
        assertEquals(2, data.receipts.size)
        assertEquals("520.00", data.totalReceived)
        assertEquals(1, data.receivables.size)
        assertEquals("300.00", data.totalReceivable)
        // campos das linhas preservados
        assertEquals(7, data.receipts.first().number)
        assertEquals("João Silva", data.receipts.first().clientName)
        assertEquals("Pago", data.receipts.first().paymentStatusLabel)
        assertEquals("300.00", data.receivables.first().balance)
    }

    @Test
    fun model_defaultsEmptyLists() {
        val data = FinanceReportPdfData(
            company = FinanceReportPdfCompany(name = "ACME"),
            periodLabel = "Junho/2026",
            generatedAtLabel = "Emitido em 07/06/2026",
            totalReceived = "0.00",
            totalReceivable = "0.00",
        )
        assertTrue(data.receipts.isEmpty())
        assertTrue(data.receivables.isEmpty())
    }

    private fun sampleData() = FinanceReportPdfData(
        company = FinanceReportPdfCompany(name = "ACME Serviços", phone = "+5511999990000"),
        periodLabel = "Junho/2026",
        generatedAtLabel = "Emitido em 07/06/2026",
        receipts = listOf(
            FinanceReportReceipt(7, "João Silva", "05/06/2026", "Pago", "320.00"),
            FinanceReportReceipt(8, "Maria Souza", "06/06/2026", "Pago (PIX)", "200.00"),
        ),
        totalReceived = "520.00",
        receivables = listOf(
            FinanceReportReceivable(9, "Carlos Lima", "Parcial", "500.00", "200.00", "300.00"),
        ),
        totalReceivable = "300.00",
    )
}
