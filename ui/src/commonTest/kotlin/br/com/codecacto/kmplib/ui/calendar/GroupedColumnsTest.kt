package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Colunas AGRUPADAS do `AppTimeGridScheduler` (2.322.0, GAP-VIT-T03): dia → profissional. */
class GroupedColumnsTest {

    private val seg = LocalDate(2026, 10, 12)
    private val ter = LocalDate(2026, 10, 13)
    private val rafael = ScheduleResource(id = "rafael", label = "Dr. Rafael Costa", availableRanges = listOf(WorkRange(480, 1080)))
    private val helena = ScheduleResource(id = "helena", label = "Dra. Helena Prado")

    private fun grupos() = scheduleDayGroups(
        days = listOf(seg, ter),
        resources = listOf(rafael, helena),
        today = seg,
        dayLabel = { "D${it.day}" },
        shortLabel = { r -> r.label.split(' ').drop(1).joinToString("") { it.take(1) } },
    )

    private fun evento(id: String, dia: LocalDate, hora: Int, recurso: String?) = ScheduleEvent(
        id = id,
        start = LocalDateTime(dia.year, dia.month, dia.day, hora, 0),
        end = LocalDateTime(dia.year, dia.month, dia.day, hora, 30),
        resourceId = recurso,
    )

    @Test
    fun `id composto vai e volta`() {
        val id = groupedColumnId("2026-10-12", "rafael")
        assertEquals("2026-10-12::rafael", id)
        assertEquals(GroupedColumnKey("2026-10-12", "rafael"), parseGroupedColumnId(id))
        assertNull(parseGroupedColumnId("rafael"))
        assertNull(parseGroupedColumnId("::rafael"))
        assertNull(parseGroupedColumnId("2026-10-12::"))
    }

    @Test
    fun `semana agrupada tem um grupo por dia e uma sub-coluna por recurso`() {
        val g = grupos()
        assertEquals(listOf("2026-10-12", "2026-10-13"), g.map { it.id })
        assertEquals(listOf(true, false), g.map { it.isCurrent })
        assertEquals(listOf("2026-10-12::rafael", "2026-10-12::helena"), g[0].columns.map { it.id })
        // Rótulo curto à vista; nome completo para o leitor de tela; expediente copiado.
        assertEquals("RC", g[0].columns[0].label)
        assertEquals("Dr. Rafael Costa", g[0].columns[0].accessibilityLabel)
        assertEquals(listOf(WorkRange(480, 1080)), g[1].columns[0].availableRanges)
    }

    @Test
    fun `eventos vao para o dia e o profissional certos e o removido vira orfao`() {
        val d = distributeGroupedEvents(
            events = listOf(
                evento("a", seg, 9, "rafael"),
                evento("b", ter, 10, "helena"),
                evento("c", LocalDate(2026, 10, 20), 9, "rafael"), // fora da semana → silêncio
                evento("d", seg, 11, null), // sem profissional → silêncio
                evento("e", ter, 8, "sumiu"), // profissional removido → órfão
            ),
            groups = grupos(),
        )
        assertEquals(listOf("a"), d.byColumn.getValue("2026-10-12::rafael").map { it.id })
        assertEquals(listOf("b"), d.byColumn.getValue("2026-10-13::helena").map { it.id })
        assertTrue(d.byColumn.getValue("2026-10-13::rafael").isEmpty())
        assertEquals(listOf("e"), d.orphans.map { it.id })
    }

    @Test
    fun `linha do agora so nas sub-colunas de hoje`() {
        val g = grupos()
        assertEquals(listOf("2026-10-12::rafael", "2026-10-12::helena"), groupedNowLineColumnIds(g, hasNow = true))
        assertTrue(groupedNowLineColumnIds(g, hasNow = false).isEmpty())
        assertTrue(groupedNowLineColumnIds(g.map { it.copy(isCurrent = false) }, hasNow = true).isEmpty())
    }

    @Test
    fun `a primeira sub-coluna de cada grupo recebe a divisoria forte`() {
        assertEquals(setOf("2026-10-12::rafael", "2026-10-13::rafael"), groupStartColumnIds(grupos()))
    }

    @Test
    fun `pista de horario livre funciona por sub-coluna`() {
        // A regra é por coluna: o expediente da sub-coluna (copiado do recurso) limita as pistas.
        val col = grupos()[0].columns[0]
        val livres = freeSlotRanges(
            window = BusinessWindow(420, 1140),
            stepMin = 60,
            availableRanges = col.availableRanges,
            occupied = listOf(MinuteRange(540, 570)),
        )
        assertEquals(listOf(480, 600, 660, 720, 780, 840, 900, 960, 1020), livres.map { it.startMin })
    }
}
