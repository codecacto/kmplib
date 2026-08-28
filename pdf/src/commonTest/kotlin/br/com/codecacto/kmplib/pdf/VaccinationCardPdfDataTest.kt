package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VaccinationCardPdfDataTest {

    @Test
    fun `nome de arquivo default sanitiza e adiciona pdf`() {
        val name = defaultVaccinationCardPdfFileName("João da Silva")
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.startsWith("carteira-vacinacao-"))
        assertTrue(name.none { it == ' ' })
    }

    @Test
    fun `nome de arquivo invalido cai no fallback do template`() {
        assertEquals("carteira-vacinacao-.pdf", defaultVaccinationCardPdfFileName("   "))
        assertEquals("carteira-vacinacao-.pdf", defaultVaccinationCardPdfFileName("@@@"))
    }

    @Test
    fun `data equals considera logoBytes`() {
        val base = { logo: ByteArray? ->
            VaccinationCardPdfData(
                holder = VaccinationHolder("João"),
                generatedAtLabel = "Gerado em 14/06/2026",
                logoBytes = logo,
            )
        }
        val a = base(byteArrayOf(1, 2, 3))
        val b = base(byteArrayOf(1, 2, 3))
        val c = base(byteArrayOf(9))
        val d = base(null)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
        assertTrue(a != d)
    }

    @Test
    fun `defaults sao seguros`() {
        val data = VaccinationCardPdfData(
            holder = VaccinationHolder("Rex"),
            generatedAtLabel = "Gerado em 14/06/2026",
        )
        assertEquals("Carteira de Vacinação", data.title)
        assertTrue(data.columns.isEmpty())
        assertTrue(data.items.isEmpty())
        assertEquals(null, data.notice)
        assertEquals(null, data.footer)
        assertTrue(!data.watermark)
    }

    @Test
    fun `coluna tem alinhamento e peso default`() {
        val col = VaccinationColumn("Vacina")
        assertEquals(VaccinationAlign.START, col.align)
        assertEquals(1f, col.weight)
    }

    @Test
    fun `shape de carteira humana monta corretamente`() {
        val data = VaccinationCardPdfData(
            holder = VaccinationHolder(
                name = "João",
                lines = listOf(
                    VaccinationHolderLine("Nascimento", "12/03/2022"),
                    VaccinationHolderLine("Sexo", "Masculino"),
                ),
            ),
            subtitle = "MinhasVacinas",
            columns = listOf(
                VaccinationColumn("Vacina", weight = 2f),
                VaccinationColumn("Dose"),
                VaccinationColumn("Aplicada", VaccinationAlign.CENTER),
                VaccinationColumn("Status", VaccinationAlign.END),
            ),
            items = listOf(
                VaccinationItem(cells = listOf("BCG", "1ª", "10/03/2022", "tomada"), statusColorArgb = 0xFF10B981.toInt()),
                VaccinationItem(cells = listOf("Penta", "3ª", "—", "atrasada"), statusColorArgb = 0xFFDC3545.toInt()),
            ),
            generatedAtLabel = "Gerado em 14/06/2026",
        )
        assertEquals(2, data.holder.lines.size)
        assertEquals(4, data.columns.size)
        assertEquals(2f, data.columns.first().weight)
        assertEquals(VaccinationAlign.END, data.columns.last().align)
        assertEquals(2, data.items.size)
        assertEquals(0xFF10B981.toInt(), data.items.first().statusColorArgb)
    }

    @Test
    fun `shape de comprovante rebanho carrega aviso nao-oficial`() {
        val notice = "Documento organizacional de controle interno. Não substitui o atestado oficial."
        val data = VaccinationCardPdfData(
            holder = VaccinationHolder(
                name = "Lote Pasto 3",
                lines = listOf(VaccinationHolderLine("Animais", "42")),
            ),
            title = "Comprovante de Vacinação (Rebanho)",
            columns = listOf(
                VaccinationColumn("Brinco"),
                VaccinationColumn("Vacina"),
                VaccinationColumn("Aplicada", VaccinationAlign.CENTER),
            ),
            items = listOf(VaccinationItem(cells = listOf("BR-014", "Brucelose", "14/06/2026"))),
            notice = notice,
            generatedAtLabel = "Gerado em 14/06/2026",
            footer = "Lote/lab/validade registrados no app.",
        )
        assertEquals(notice, data.notice)
        assertEquals("Comprovante de Vacinação (Rebanho)", data.title)
        assertTrue(data.footer != null)
    }

    @Test
    fun `item muted e sem status sao validos`() {
        val item = VaccinationItem(cells = listOf("Aftosa", "—", "Legado"), muted = true)
        assertTrue(item.muted)
        assertEquals(null, item.statusColorArgb)
    }
}
