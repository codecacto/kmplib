@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.core.ics

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class IcsCalendarTest {

    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private val options = IcsCalendarOptions(now = now)
    private val base = IcsEvent(
        uid = "lua-nova-2026-08-12@folhadeaxe.com.br",
        summary = "Lua Nova",
        start = IcsTime.Timed(Instant.parse("2026-08-12T17:36:35Z")),
    )
    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    private fun unfold(ics: String) = ics.replace("\r\n ", "")
    private fun linesOf(ics: String) = unfold(ics).split("\r\n")

    // ---------------------------------------------------------------------------------------
    // Paridade byte a byte com o buildIcs da weblib 0.224.0
    // ---------------------------------------------------------------------------------------

    private fun assertGolden(name: String, actual: String) =
        assertEquals(WEBLIB_GOLDEN.getValue(name), actual, "diverge da weblib no caso \"$name\"")

    @Test
    fun paridadeUtc() = assertGolden("utc", IcsCalendar.build(base, options))

    @Test
    fun paridadeDiaInteiro() =
        assertGolden("allDay", IcsCalendar.build(base.copy(start = IcsTime.AllDay(LocalDate(2026, 12, 31))), options))

    @Test
    fun paridadeSaoPauloComFim() = assertGolden(
        "saoPaulo",
        IcsCalendar.build(
            base.copy(timeZone = saoPaulo, end = IcsTime.Timed(Instant.parse("2026-08-12T18:36:35Z"))),
            options,
        ),
    )

    @Test
    fun paridadeLisboaComHorarioDeVerao() = assertGolden(
        "lisbon",
        IcsCalendar.build(
            base.copy(start = IcsTime.Timed(Instant.parse("2026-06-01T12:00:00Z")), timeZone = TimeZone.of("Europe/Lisbon")),
            options,
        ),
    )

    @Test
    fun paridadeNovaYorkSemanalAteData() = assertGolden(
        "newYorkWeekly",
        IcsCalendar.build(
            base.copy(
                start = IcsTime.Timed(Instant.parse("2026-03-01T14:00:00Z")),
                timeZone = TimeZone.of("America/New_York"),
                recurrence = IcsRecurrence(
                    IcsFrequency.WEEKLY,
                    byDay = listOf(IcsWeekday.MO, IcsWeekday.TH),
                    until = IcsTime.AllDay(LocalDate(2026, 12, 20)),
                ),
            ),
            options,
        ),
    )

    @Test
    fun paridadeCalendarioCompleto() = assertGolden(
        "full",
        IcsCalendar.build(
            listOf(
                base.copy(
                    description = "Oferenda; flores, velas\nÁgua ".repeat(4),
                    location = "Terreiro, Mirassol",
                    url = "https://FolhaDeAxe.com.br?d=2026-08-12",
                    categories = listOf("Lua", "Ritual, casa"),
                    status = IcsEventStatus.CONFIRMED,
                    sequence = 2,
                    alarms = listOf(
                        IcsAlarm(60),
                        IcsAlarm(1590, "Preparar"),
                        IcsAlarm(0),
                        IcsAlarm(-15),
                    ),
                    recurrence = IcsRecurrence(IcsFrequency.MONTHLY, interval = 2, count = 10),
                ),
                IcsEvent(
                    uid = "b",
                    summary = "Ciclo de 7 dias 🌙 — Á".repeat(3),
                    start = IcsTime.AllDay(LocalDate(2026, 1, 31)),
                    end = IcsTime.AllDay(LocalDate(2026, 2, 7)),
                    recurrence = IcsRecurrence(
                        IcsFrequency.MONTHLY,
                        byMonthDay = listOf(-1),
                        until = IcsTime.AllDay(LocalDate(2026, 12, 31)),
                    ),
                ),
                IcsEvent(
                    uid = "c",
                    summary = "Diário",
                    start = IcsTime.Timed(Instant.parse("2026-08-12T17:36:35Z")),
                    timeZone = saoPaulo,
                    recurrence = IcsRecurrence(IcsFrequency.DAILY, until = IcsTime.AllDay(LocalDate(2026, 8, 20))),
                ),
            ),
            options.copy(calendarName = "Folha de Axé"),
        ),
    )

    // ---------------------------------------------------------------------------------------
    // Estrutura RFC 5545
    // ---------------------------------------------------------------------------------------

    @Test
    fun cabecalhoComCrlfEmTodaLinhaEProdIdDaKmplib() {
        val ics = IcsCalendar.build(base, options)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//CodeCacto//kmplib//PT-BR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.replace("\r\n", "").none { it == '\r' || it == '\n' })
    }

    @Test
    fun umVTimezonePorFusoMesmoComVariosEventos() {
        val ics = IcsCalendar.build(
            listOf(
                base.copy(uid = "a", timeZone = saoPaulo),
                base.copy(uid = "b", timeZone = saoPaulo, start = IcsTime.Timed(Instant.parse("2027-01-01T00:00:00Z"))),
            ),
            options,
        )
        assertEquals(1, Regex("BEGIN:VTIMEZONE").findAll(ics).count())
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        val l = linesOf(ics)
        assertTrue(l.indexOf("BEGIN:VTIMEZONE") < l.indexOf("BEGIN:VEVENT"))
    }

    // ---------------------------------------------------------------------------------------
    // Texto e dobra
    // ---------------------------------------------------------------------------------------

    @Test
    fun escapaBarraPontoEVirgulaVirgulaQuebraERemoveControle() {
        assertEquals("a\\\\b\\;c\\,d\\ne\\nf", IcsCalendar.escapeText("a\\b;c,d\ne\r\nf\u0007"))
        assertEquals("a\\nb", IcsCalendar.escapeText("a\rb"))
        assertEquals("tab\tfica", IcsCalendar.escapeText("tab\tfica"))
    }

    @Test
    fun dobraEm75OctetosSemPartirAcentoNemEmoji() {
        val longo = "Á".repeat(50) + "🌙".repeat(20)
        val folded = IcsCalendar.foldLine("SUMMARY:$longo")
        val parts = folded.split("\r\n")
        assertTrue(parts.size > 1)
        parts.forEachIndexed { i, p ->
            assertTrue(p.encodeToByteArray().size <= 75, "linha $i com ${p.encodeToByteArray().size} octetos")
            if (i > 0) assertTrue(p.startsWith(" "))
            assertTrue(p.none { it == '�' })
            // Nenhuma parte termina num surrogate alto solto (emoji partido).
            assertTrue(p.isEmpty() || !p.last().isHighSurrogate())
        }
        assertEquals("SUMMARY:$longo", folded.replace("\r\n ", ""))
        assertEquals("CURTA:ok", IcsCalendar.foldLine("CURTA:ok"))
    }

    @Test
    fun duracaoDoLembrete() {
        assertEquals("-PT1H", IcsCalendar.formatDuration(60))
        assertEquals("-P1DT2H30M", IcsCalendar.formatDuration(1590))
        assertEquals("-P2D", IcsCalendar.formatDuration(2880))
        assertEquals("PT0S", IcsCalendar.formatDuration(0))
        assertEquals("PT15M", IcsCalendar.formatDuration(-15))
    }

    @Test
    fun urlGanhaBarraDeCaminhoEHostMinusculo() {
        val l = linesOf(IcsCalendar.build(base.copy(url = "HTTPS://Exemplo.COM"), options))
        assertTrue("URL:https://exemplo.com/" in l)
        val m = linesOf(IcsCalendar.build(base.copy(url = "mailto:contato@codecacto.com.br"), options))
        assertTrue("URL:mailto:contato@codecacto.com.br" in m)
    }

    // ---------------------------------------------------------------------------------------
    // RRULE
    // ---------------------------------------------------------------------------------------

    @Test
    fun semanalComByDayECount() {
        val l = linesOf(
            IcsCalendar.build(
                base.copy(
                    recurrence = IcsRecurrence(
                        IcsFrequency.WEEKLY,
                        interval = 2,
                        byDay = listOf(IcsWeekday.MO, IcsWeekday.TH),
                        count = 10,
                    ),
                ),
                options,
            ),
        )
        assertTrue("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=10;BYDAY=MO,TH" in l)
    }

    @Test
    fun untilComInstanteEmEventoDeDiaInteiroViraData() {
        val l = linesOf(
            IcsCalendar.build(
                base.copy(
                    start = IcsTime.AllDay(LocalDate(2026, 1, 1)),
                    recurrence = IcsRecurrence(IcsFrequency.YEARLY, until = IcsTime.Timed(Instant.parse("2030-05-04T22:00:00Z"))),
                ),
                options,
            ),
        )
        assertTrue("RRULE:FREQ=YEARLY;UNTIL=20300504" in l)
    }

    // ---------------------------------------------------------------------------------------
    // Validação na borda
    // ---------------------------------------------------------------------------------------

    @Test
    fun recusaEntradaInvalida() {
        fun falha(ev: IcsEvent, trecho: String) {
            val e = assertFailsWith<IllegalArgumentException> { IcsCalendar.build(ev, options) }
            assertTrue(e.message!!.contains(trecho), "mensagem \"${e.message}\" sem \"$trecho\"")
        }
        falha(base.copy(uid = " "), "uid")
        falha(base.copy(summary = ""), "summary")
        val dia = IcsTime.AllDay(LocalDate(2026, 8, 12))
        falha(base.copy(start = dia, end = dia), "DEPOIS")
        falha(base.copy(end = IcsTime.Timed(Instant.parse("2026-08-01T00:00:00Z"))), "antes")
        falha(base.copy(start = dia, end = IcsTime.Timed(now)), "dia inteiro")
        falha(base.copy(end = dia), "com hora")
        falha(base.copy(url = "/relativa"), "absoluta")
        falha(base.copy(url = "https://"), "absoluta")
        falha(base.copy(timeZone = FixedOffsetTimeZone(UtcOffset(hours = 3))), "IANA")
        falha(base.copy(sequence = -1), "sequence")
        falha(
            base.copy(recurrence = IcsRecurrence(IcsFrequency.DAILY, count = 2, until = IcsTime.AllDay(LocalDate(2026, 9, 1)))),
            "exclusivos",
        )
        falha(base.copy(recurrence = IcsRecurrence(IcsFrequency.DAILY, interval = 0)), "interval")
        falha(base.copy(recurrence = IcsRecurrence(IcsFrequency.DAILY, count = 0)), "count")
        falha(base.copy(recurrence = IcsRecurrence(IcsFrequency.MONTHLY, byMonthDay = listOf(0))), "byMonthDay")
        falha(base.copy(recurrence = IcsRecurrence(IcsFrequency.MONTHLY, byMonthDay = listOf(32))), "byMonthDay")
    }

    @Test
    fun utcComIdIanaEAceito() {
        val l = linesOf(IcsCalendar.build(base.copy(timeZone = TimeZone.of("UTC")), options))
        assertTrue("DTSTART;TZID=UTC:20260812T173635" in l)
    }
}
