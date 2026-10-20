package br.com.codecacto.kmplib.pdf.report

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2.309.0 (GAP-TB-M-05) — "Ver relatório completo" opcional: app sem portal passa `null`. */
class ReportSummaryActionsTest {

    @Test
    fun `pe do resumo some sem nenhum botao`() {
        assertFalse(reportSummaryShowsActions(hasFullReport = false, hasExportPdf = false))
    }

    @Test
    fun `um botao basta para desenhar o pe`() {
        assertTrue(reportSummaryShowsActions(hasFullReport = true, hasExportPdf = false))
        assertTrue(reportSummaryShowsActions(hasFullReport = false, hasExportPdf = true))
        assertTrue(reportSummaryShowsActions(hasFullReport = true, hasExportPdf = true))
    }
}
