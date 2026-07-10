package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes da LÓGICA PURA de layout da grade (janela derivada, posição por minuto, lanes) — par mobile
 * de `layout.test.ts` da weblib. Garante que o defeito do Influencer (balde-de-hora) NÃO se repete.
 */
class CalendarLayoutTest {

    private fun dt(hh: Int, mm: Int) = LocalDateTime(2026, 7, 9, hh, mm)
    private fun ev(id: String, sh: Int, sm: Int, eh: Int, em: Int, resourceId: String? = null) =
        ScheduleEvent(id = id, start = dt(sh, sm), end = dt(eh, em), resourceId = resourceId)

    // ── computeTimeWindow — janela derivada dos dados (não fixa 7–22) ──

    @Test
    fun `deriva janela de business hours com folga e snap`() {
        // 09:00–19:00 → 08:00–20:00 (padding 30 + snap 60)
        val w = computeTimeWindow(ComputeWindowOptions(businessRanges = listOf(WorkRange(540, 1140))))
        assertEquals(BusinessWindow(480, 1200), w)
    }

    @Test
    fun `usa fallback 08-20 sem business hours nem dados`() {
        assertEquals(BusinessWindow(480, 1200), computeTimeWindow())
    }

    @Test
    fun `EXPANDE para conter evento fora do expediente`() {
        val w = computeTimeWindow(
            ComputeWindowOptions(
                businessRanges = listOf(WorkRange(540, 1140)),
                events = listOf(ev("x", 7, 15, 7, 45)),
            ),
        )
        assertEquals(435, w.startMin) // cola no evento (07:15), sem snap
        assertEquals(1200, w.endMin)
    }

    @Test
    fun `expande o fim para combo que termina depois do fechamento`() {
        val w = computeTimeWindow(
            ComputeWindowOptions(
                businessRanges = listOf(WorkRange(540, 1140)),
                events = listOf(ev("x", 18, 30, 20, 15)),
            ),
        )
        assertEquals(1215, w.endMin) // 20:15
    }

    // ── positionInWindow — top/height por minuto real ──

    private val window = BusinessWindow(480, 1200) // 08:00–20:00

    @Test
    fun `top proporcional ao inicio e height a duracao`() {
        val r = toMinuteRange(dt(9, 0), dt(9, 30))
        val box = positionInWindow(r, window, 1.4f)
        assertEquals(84f, box.top, 0.001f)
        assertEquals(42f, box.height, 0.001f)
    }

    @Test
    fun `um 30min e um 90min tem alturas diferentes - bug do Influencer nao se repete`() {
        val short = positionInWindow(toMinuteRange(dt(9, 0), dt(9, 30)), window, 1.4f)
        val long = positionInWindow(toMinuteRange(dt(9, 0), dt(10, 30)), window, 1.4f)
        assertEquals(short.height * 3f, long.height, 0.01f)
    }

    @Test
    fun `09h30 NAO cai na linha 09h00`() {
        val at930 = positionInWindow(toMinuteRange(dt(9, 30), dt(10, 0)), window, 1.4f)
        val at900 = positionInWindow(toMinuteRange(dt(9, 0), dt(9, 30)), window, 1.4f)
        assertTrue(at930.top > at900.top)
        assertEquals(30 * 1.4f, at930.top - at900.top, 0.01f)
    }

    @Test
    fun `respeita a altura minima`() {
        val r = toMinuteRange(dt(9, 0), dt(9, 5))
        assertEquals(24f, positionInWindow(r, window, 1.4f, 24f).height, 0.001f)
    }

    // ── packLanes — sobreposição em lanes ──

    @Test
    fun `eventos sequenciais sem overlap ficam em 1 lane`() {
        val out = packEventLanes(listOf(ev("a", 9, 0, 9, 30), ev("b", 9, 30, 10, 0)))
        assertTrue(out.all { it.laneCount == 1 && it.laneIndex == 0 })
    }

    @Test
    fun `dois sobrepostos viram 2 lanes lado a lado`() {
        val out = packEventLanes(listOf(ev("a", 9, 0, 10, 0), ev("b", 9, 30, 10, 30)))
        val a = out.first { it.event.id == "a" }
        val b = out.first { it.event.id == "b" }
        assertEquals(2, a.laneCount)
        assertEquals(2, b.laneCount)
        assertEquals(setOf(0, 1), setOf(a.laneIndex, b.laneIndex))
    }

    @Test
    fun `tres concorrentes viram 3 lanes e um 4o disjunto abre novo cluster`() {
        val out = packEventLanes(
            listOf(
                ev("a", 9, 0, 10, 0),
                ev("b", 9, 15, 10, 0),
                ev("c", 9, 30, 10, 0),
                ev("d", 11, 0, 11, 30),
            ),
        )
        val abc = out.filter { it.event.id in listOf("a", "b", "c") }
        assertTrue(abc.all { it.laneCount == 3 })
        assertEquals(setOf(0, 1, 2), abc.map { it.laneIndex }.toSet())
        assertEquals(1, out.first { it.event.id == "d" }.laneCount)
    }

    @Test
    fun `reaproveita a lane livre quando o anterior ja terminou`() {
        val out = packEventLanes(
            listOf(ev("a", 9, 0, 9, 30), ev("b", 9, 15, 10, 0), ev("c", 9, 30, 10, 0)),
        )
        assertEquals(0, out.first { it.event.id == "a" }.laneIndex)
        assertEquals(0, out.first { it.event.id == "c" }.laneIndex) // herda lane 0 (a já terminou)
        assertEquals(1, out.first { it.event.id == "b" }.laneIndex)
    }

    @Test
    fun `deterministico - desempata por id`() {
        val out1 = packEventLanes(listOf(ev("b", 9, 0, 10, 0), ev("a", 9, 0, 10, 0)))
        val out2 = packEventLanes(listOf(ev("a", 9, 0, 10, 0), ev("b", 9, 0, 10, 0)))
        assertEquals(
            out1.map { "${it.event.id}:${it.laneIndex}" }.sorted(),
            out2.map { "${it.event.id}:${it.laneIndex}" }.sorted(),
        )
        assertEquals(0, out1.first { it.event.id == "a" }.laneIndex)
    }

    // ── offHoursRanges e hourTicks ──

    @Test
    fun `sem availableRanges nada e sombreado`() {
        assertEquals(emptyList(), offHoursRanges(window, null))
    }

    @Test
    fun `sombra antes, no almoco e depois do expediente`() {
        val off = offHoursRanges(window, listOf(WorkRange(540, 720), WorkRange(780, 1140)))
        assertEquals(
            listOf(MinuteRange(480, 540), MinuteRange(720, 780), MinuteRange(1140, 1200)),
            off,
        )
    }

    @Test
    fun `hourTicks - marcas de hora dentro da janela`() {
        assertEquals(listOf(480, 540, 600, 660), hourTicks(BusinessWindow(480, 660), 60))
    }
}
