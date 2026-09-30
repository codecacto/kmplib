package br.com.codecacto.kmplib.platform.print

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintRulesTest {

    @Test
    fun milimetrosViramMilesimosDePolegada() {
        assertEquals(2283, mmToMils(58.0))
        assertEquals(3150, mmToMils(80.0))
        assertEquals(8268, mmToMils(210.0))
        assertEquals(11693, mmToMils(297.0))
    }

    @Test
    fun pontosViramMilesimosDePolegada() {
        assertEquals(1000, pointsToMils(72.0))
        assertEquals(8264, pointsToMils(595.0))
    }

    @Test
    fun folhaUsaAPropriaMedida() {
        assertEquals(85.0 to 55.0, resolvedPaperSizeMm(PrintPaper.sheet(85.0, 55.0, "Cartão"), 999.0))
    }

    @Test
    fun bobinaTiraOComprimentoDaPaginaDoPdf() {
        val (w, h) = resolvedPaperSizeMm(PrintPaper.THERMAL_58, 72.0)!!
        assertEquals(58.0, w)
        assertEquals(25.4, h, 1e-9)
    }

    @Test
    fun bobinaSemPaginaNaoResolve() {
        assertNull(resolvedPaperSizeMm(PrintPaper.THERMAL_80, null))
    }

    @Test
    fun idDoPapelCustomizadoEEstavelEUnico() {
        assertEquals("kmplib_580x1420", customMediaId(PrintPaper.THERMAL_58, 142.0))
        assertEquals("kmplib_800x1420", customMediaId(PrintPaper.THERMAL_80, 142.0))
    }

    @Test
    fun reconheceAssinaturaDePdf() {
        assertTrue(isPdf("%PDF-1.7\n".encodeToByteArray()))
        assertFalse(isPdf("<html>".encodeToByteArray()))
        assertFalse(isPdf(ByteArray(0)))
        assertFalse(isPdf("%PDF".encodeToByteArray()))
    }

    @Test
    fun nomeDoTrabalhoSemQuebraENuncaVazio() {
        assertEquals("Colinha do Voto", sanitizePrintJobName("  Colinha\ndo\tVoto "))
        assertEquals("Documento", sanitizePrintJobName("   "))
        assertEquals(120, sanitizePrintJobName("x".repeat(300)).length)
    }

    @Test
    fun resultadoPeloEstadoDoTrabalho() {
        assertEquals(PrintResult.Sent, printResultFor(PrintJobPhase.QUEUED))
        assertEquals(PrintResult.Sent, printResultFor(PrintJobPhase.STARTED))
        assertEquals(PrintResult.Sent, printResultFor(PrintJobPhase.BLOCKED))
        assertEquals(PrintResult.Sent, printResultFor(PrintJobPhase.COMPLETED))
        assertEquals(PrintResult.Cancelled, printResultFor(PrintJobPhase.CANCELED))
        assertEquals(PrintResult.Cancelled, printResultFor(PrintJobPhase.CREATED))
        assertEquals(PrintResult.Cancelled, printResultFor(null))
        assertEquals(PrintResult.Failed("sem papel"), printResultFor(PrintJobPhase.FAILED, "sem papel"))
        assertTrue(printResultFor(PrintJobPhase.FAILED) is PrintResult.Failed)
    }

    @Test
    fun papeisProntosEValidacao() {
        assertTrue(PrintPaper.THERMAL_58.isRoll)
        assertFalse(PrintPaper.A4.isRoll)
        assertEquals(PrintPaper(215.9, 279.4, "Letter"), PrintPaper.LETTER)
        assertEquals(148.0, PrintPaper.A5.widthMm)
        assertEquals(PrintPaper(80.0, null, "80 mm"), PrintPaper.roll(80.0, "80 mm"))
        assertFailsWith<IllegalArgumentException> { PrintPaper(0.0, 10.0, "x") }
        assertFailsWith<IllegalArgumentException> { PrintPaper(10.0, -1.0, "x") }
        assertFailsWith<IllegalArgumentException> { PrintPaper(10.0, 10.0, " ") }
    }
}
