package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableReportPdfDataTest {

    @Test
    fun `nome de arquivo default sanitiza e adiciona pdf`() {
        val name = defaultTablePdfFileName("Relatório de Frequência")
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.startsWith("relatorio-"))
        // Sem espaços/acentos crus no nome de arquivo.
        assertTrue(name.none { it == ' ' })
    }

    @Test
    fun `nome de arquivo vazio ou invalido cai no fallback`() {
        assertEquals("relatorio-.pdf", defaultTablePdfFileName("   "))
        assertEquals("relatorio-.pdf", defaultTablePdfFileName("@@@"))
    }

    @Test
    fun `nome de arquivo ja com extensao nao duplica pdf`() {
        assertTrue(defaultTablePdfFileName("lista.pdf").endsWith(".pdf"))
        assertEquals(1, defaultTablePdfFileName("lista.pdf").count { it == '.' })
    }

    @Test
    fun `company equals e hashCode consideram logoBytes`() {
        val a = TableReportPdfCompany("X", logoBytes = byteArrayOf(1, 2, 3))
        val b = TableReportPdfCompany("X", logoBytes = byteArrayOf(1, 2, 3))
        val c = TableReportPdfCompany("X", logoBytes = byteArrayOf(9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun `company sem logo e considerado igual quando ambos nulos`() {
        val a = TableReportPdfCompany("X", phone = "(11) 99999-0000")
        val b = TableReportPdfCompany("X", phone = "(11) 99999-0000")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `defaults do modelo sao seguros e sem dinheiro`() {
        val data = TableReportPdfData(
            company = TableReportPdfCompany("Prof. Ana"),
            title = "Relatório de Frequência",
            columns = listOf(TableReportColumn("Aluno")),
        )
        assertEquals(null, data.subtitle)
        assertEquals(null, data.summary)
        assertEquals(null, data.footer)
        assertTrue(data.rows.isEmpty())
        assertTrue(!data.watermark)
        assertEquals("Sem registros.", data.emptyText)
        assertEquals("Gerado com kmplib", data.watermarkText)
    }

    @Test
    fun `coluna tem alinhamento START e peso 1 por padrao`() {
        val col = TableReportColumn("Aluno")
        assertEquals(TableReportAlign.START, col.align)
        assertEquals(1f, col.weight)
    }

    @Test
    fun `shape de relatorio de frequencia preserva colunas e linhas`() {
        val data = TableReportPdfData(
            company = TableReportPdfCompany("Prof. Ana", phone = "(11) 99999-0000"),
            title = "Relatório de Frequência",
            subtitle = "Turma 3º A — Maio/2026",
            columns = listOf(
                TableReportColumn("Aluno", weight = 3f),
                TableReportColumn("Presenças", TableReportAlign.END),
                TableReportColumn("Total", TableReportAlign.END),
                TableReportColumn("Frequência", TableReportAlign.END),
            ),
            rows = listOf(
                TableReportRow(listOf("João da Silva", "18", "20", "90%")),
                TableReportRow(listOf("Maria Souza", "20", "20", "100%")),
            ),
            summary = "Frequência média da turma: 95%",
            footer = "Emitido em 14/06/2026",
            watermark = true,
            watermarkText = "Feito com Chamada Fácil",
        )

        assertEquals(4, data.columns.size)
        assertEquals(3f, data.columns.first().weight)
        assertEquals(TableReportAlign.END, data.columns[1].align)
        assertEquals(2, data.rows.size)
        assertEquals(listOf("João da Silva", "18", "20", "90%"), data.rows.first().cells)
        assertEquals("Turma 3º A — Maio/2026", data.subtitle)
        assertEquals("Frequência média da turma: 95%", data.summary)
        assertTrue(data.watermark)
        // Nenhum campo monetário/total existe neste modelo (template de tabela puro).
    }
}
