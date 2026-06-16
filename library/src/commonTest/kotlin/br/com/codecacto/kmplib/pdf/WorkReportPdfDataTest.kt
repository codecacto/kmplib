package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkReportPdfDataTest {

    @Test
    fun `nome de arquivo default sanitiza e adiciona pdf`() {
        val name = defaultWorkReportPdfFileName("Residência Família Silva")
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.startsWith("relatorio-obra-"))
        // Sem espaços/acentos crus no nome de arquivo.
        assertTrue(name.none { it == ' ' })
    }

    @Test
    fun `nome de arquivo vazio mantem o prefixo do template`() {
        // Espelha o comportamento de defaultFinanceReportPdfFileName: só "_" é trimado.
        assertEquals("relatorio-obra-.pdf", defaultWorkReportPdfFileName("   "))
        // Conteúdo totalmente inválido cai no fallback puro.
        assertEquals("relatorio-obra-.pdf", defaultWorkReportPdfFileName("@@@"))
    }

    @Test
    fun `company equals considera logoBytes`() {
        val a = WorkReportPdfCompany("X", logoBytes = byteArrayOf(1, 2, 3))
        val b = WorkReportPdfCompany("X", logoBytes = byteArrayOf(1, 2, 3))
        val c = WorkReportPdfCompany("X", logoBytes = byteArrayOf(9))
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `photo equals considera imageBytes e campos`() {
        val a = WorkReportPhoto(byteArrayOf(1, 2), stageName = "Fundação", caption = "Sapata", takenAtLabel = "07/06")
        val b = WorkReportPhoto(byteArrayOf(1, 2), stageName = "Fundação", caption = "Sapata", takenAtLabel = "07/06")
        val c = WorkReportPhoto(byteArrayOf(1, 2), stageName = "Alvenaria", caption = "Sapata", takenAtLabel = "07/06")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun `data defaults sao seguros`() {
        val data = WorkReportPdfData(
            company = WorkReportPdfCompany("Construtora X"),
            workName = "Obra 1",
            generatedAtLabel = "Emitido em 07/06/2026",
        )
        assertEquals(0, data.overallProgress)
        assertEquals(null, data.clientName)
        assertTrue(data.stages.isEmpty())
        assertTrue(data.photos.isEmpty())
        assertTrue(data.diaryEntries.isEmpty())
        assertTrue(!data.watermark)
    }

    @Test
    fun `shape final C-5 expoe os novos campos`() {
        val data = WorkReportPdfData(
            company = WorkReportPdfCompany("Construtora X", phone = "(11) 99999-0000"),
            workName = "Residência Silva",
            clientName = "Família Silva",
            address = "Rua A, 100",
            periodLabel = "01/05 a 31/05/2026",
            generatedAtLabel = "Emitido em 07/06/2026",
            overallProgress = 62,
            stages = listOf(
                WorkReportPdfStage(name = "Fundação", statusLabel = "Concluída", progress = 100, note = "Sem ocorrências"),
            ),
            photos = listOf(
                WorkReportPhoto(byteArrayOf(1), stageName = "Fundação", caption = "Sapata", takenAtLabel = "07/06"),
            ),
            diaryEntries = listOf(
                WorkReportPdfDiaryEntry(
                    dateLabel = "07/06/2026",
                    weatherLabel = "Ensolarado",
                    crewLabel = "5 pedreiros",
                    author = "João",
                    text = "Concretagem da laje.",
                ),
            ),
            watermark = true,
            watermarkText = "Feito com MinhaObra",
        )

        assertEquals("Família Silva", data.clientName)
        assertEquals(62, data.overallProgress)

        val stage = data.stages.single()
        assertEquals(100, stage.progress)
        assertEquals("Sem ocorrências", stage.note)

        val photo = data.photos.single()
        assertEquals("Fundação", photo.stageName)

        val diary = data.diaryEntries.single()
        assertEquals("João", diary.author)
        assertEquals("Concretagem da laje.", diary.text)
        assertEquals("Ensolarado", diary.weatherLabel)
        assertEquals("5 pedreiros", diary.crewLabel)
    }
}
