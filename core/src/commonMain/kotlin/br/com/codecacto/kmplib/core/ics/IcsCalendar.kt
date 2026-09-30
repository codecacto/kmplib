@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.core.ics

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Arquivo de agenda `.ics` — **RFC 5545 (iCalendar)**. Par mobile do `buildIcs` da weblib
 * (`@codecacto/weblib/utils`, ≥ 0.224.0): **a mesma entrada produz o mesmo texto** (exceto o
 * `PRODID` default, que diz quem gerou), e a suíte da lib confere isso byte a byte contra saídas
 * geradas pela weblib.
 *
 * Função **pura** (`commonMain`), sem rede e sem plataforma. Para entregar o arquivo à pessoa, use
 * `ShareHandler.shareIcs` (`kmplib-platform`).
 *
 * O que a RFC exige e o gerador garante, para o arquivo abrir igual no Google Agenda, no Calendário
 * da Apple e no Outlook:
 * - linhas terminadas em **CRLF** e **dobradas em 75 octetos** (bytes UTF-8, sem partir um acento no
 *   meio), continuação começando com um espaço (§3.1);
 * - texto com **escape** de `\`, `;`, `,` e quebra de linha (§3.3.11);
 * - `UID` e `DTSTAMP` em todo `VEVENT` (§3.6.1); `PRODID` e `VERSION` no calendário;
 * - evento de dia inteiro como `VALUE=DATE` com `DTEND` **exclusivo** (dia seguinte);
 * - hora com fuso ([IcsEvent.timeZone]) como `TZID` **com o `VTIMEZONE` correspondente** (§3.6.5 —
 *   "MUST"), gerado a partir da base IANA do próprio aparelho (a do `kotlinx-datetime`), sem tabela
 *   embarcada;
 * - `RRULE` com `UNTIL` em UTC quando o início tem hora (§3.3.10);
 * - `VALARM` `DISPLAY` com `TRIGGER` e `DESCRIPTION` (obrigatória nesse tipo).
 *
 * ```kotlin
 * val nova = MoonCalculator.nextPhase(PrincipalMoonPhase.NEW, Clock.System.now())
 * val ics = IcsCalendar.build(
 *     IcsEvent(
 *         uid = "lua-nova-${nova.instant}@folhadeaxe.com.br",
 *         summary = "Lua Nova",
 *         start = IcsTime.Timed(nova.instant),
 *         timeZone = TimeZone.currentSystemDefault(),
 *         alarms = listOf(IcsAlarm(minutesBefore = 60)),
 *     ),
 *     IcsCalendarOptions(calendarName = "Folha de Axé"),
 * )
 * getShareHandler().shareIcs(ics, fileName = "lua-nova.ics")
 * ```
 *
 * **Entrada inválida lança [IllegalArgumentException]** com a mensagem dizendo o campo e o `uid` —
 * é erro de programação (dado montado pelo app), não de usuário.
 */
object IcsCalendar {

    /** Tipo MIME do arquivo gerado (RFC 5545 §8.1). */
    const val MIME_TYPE: String = "text/calendar"

    /** `PRODID` default desta lib. A weblib usa `-//CodeCacto//weblib//PT-BR`. */
    const val DEFAULT_PROD_ID: String = "-//CodeCacto//kmplib//PT-BR"

    /** Monta o texto `.ics` de UM evento. */
    fun build(event: IcsEvent, options: IcsCalendarOptions = IcsCalendarOptions()): String =
        build(listOf(event), options)

    /** Monta o texto `.ics` de um ou mais eventos (um `VTIMEZONE` por fuso usado). */
    fun build(events: List<IcsEvent>, options: IcsCalendarOptions = IcsCalendarOptions()): String {
        val dtstamp = utcStamp(options.now.toEpochMilliseconds())

        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:${options.prodId}",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
        )
        options.calendarName?.let { if (it.isNotEmpty()) lines += "X-WR-CALNAME:${escapeText(it)}" }

        // Um VTIMEZONE por TZID usado, cobrindo do primeiro início ao último fim/UNTIL (mesma
        // janela da weblib — é o que mantém a paridade de saída).
        val ranges = LinkedHashMap<String, LongArray>()
        val zones = HashMap<String, TimeZone>()
        for (ev in events) {
            val tz = ev.timeZone ?: continue
            val start = ev.start as? IcsTime.Timed ?: continue
            val tzId = checkTimeZone(tz)
            val s = start.instant.toEpochMilliseconds()
            var e = (ev.end as? IcsTime.Timed)?.instant?.toEpochMilliseconds() ?: s
            when (val until = ev.recurrence?.until) {
                is IcsTime.Timed -> e = maxOf(e, until.instant.toEpochMilliseconds())
                is IcsTime.AllDay -> e = maxOf(
                    e,
                    LocalDateTime(until.date, LocalTime(23, 59, 59)).toInstant(TimeZone.UTC).toEpochMilliseconds(),
                )
                null -> if (ev.recurrence != null) e = maxOf(e, s + 2 * 366 * DAY) // sem fim: próximos anos
            }
            zones[tzId] = tz
            val cur = ranges[tzId]
            ranges[tzId] = if (cur == null) longArrayOf(s, e) else longArrayOf(minOf(cur[0], s), maxOf(cur[1], e))
        }
        for ((tzId, range) in ranges) {
            lines += buildVTimezone(tzId, zones.getValue(tzId), range[0] - HOUR, range[1] + HOUR)
        }

        for (ev in events) lines += eventLines(ev, dtstamp)
        lines += "END:VCALENDAR"
        return lines.joinToString(CRLF) { foldLine(it) } + CRLF
    }

    /** Escape de valor TEXT (RFC 5545 §3.3.11) + remoção dos caracteres de controle proibidos. */
    fun escapeText(value: String): String {
        val sb = StringBuilder(value.length + 8)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '\\' -> sb.append("\\\\")
                c == ';' -> sb.append("\\;")
                c == ',' -> sb.append("\\,")
                c == '\r' -> {
                    sb.append("\\n")
                    if (i + 1 < value.length && value[i + 1] == '\n') i++
                }
                c == '\n' -> sb.append("\\n")
                c.code in 0x00..0x08 || c.code in 0x0B..0x1F || c.code == 0x7F -> Unit
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /** Dobra uma linha em até 75 octetos (UTF-8), sem partir caractere; continuação com um espaço. */
    fun foldLine(line: String): String {
        if (utf8Length(line) <= 75) return line
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var bytes = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val high = line[i]
            val charCount = if (high.isHighSurrogate() && i + 1 < line.length && line[i + 1].isLowSurrogate()) 2 else 1
            val size = if (charCount == 2) 4 else utf8Size(high)
            if (bytes + size > limit) {
                out += current.toString()
                current.clear()
                bytes = 0
                limit = 74 // o espaço inicial da continuação ocupa 1 octeto
            }
            current.append(line, i, i + charCount)
            bytes += size
            i += charCount
        }
        out += current.toString()
        return out.joinToString("$CRLF ")
    }

    // -------------------------------------------------------------------------------------------
    // VEVENT
    // -------------------------------------------------------------------------------------------

    private fun eventLines(ev: IcsEvent, dtstamp: String): List<String> {
        val uid = ev.uid.trim()
        require(uid.isNotEmpty()) {
            "IcsCalendar: todo evento precisa de \"uid\" (estável, para a agenda atualizar em vez de duplicar)."
        }
        require(ev.summary.isNotBlank()) { "IcsCalendar: evento \"$uid\" sem \"summary\"." }
        val tzId = ev.timeZone?.let { checkTimeZone(it) }

        val lines = mutableListOf("BEGIN:VEVENT", "UID:${escapeText(uid)}", "DTSTAMP:$dtstamp")
        val allDay = ev.start is IcsTime.AllDay

        when (val start = ev.start) {
            is IcsTime.AllDay -> {
                val end = ev.end ?: IcsTime.AllDay(start.date.plus(DatePeriod(days = 1)))
                require(end is IcsTime.AllDay) {
                    "IcsCalendar: evento \"$uid\" de dia inteiro exige \"end\" também de dia inteiro (AllDay)."
                }
                require(end.date > start.date) {
                    "IcsCalendar: \"end\" deve ser DEPOIS de \"start\" (exclusivo) no evento \"$uid\"."
                }
                lines += "DTSTART;VALUE=DATE:${dateStamp(start.date)}"
                lines += "DTEND;VALUE=DATE:${dateStamp(end.date)}"
            }
            is IcsTime.Timed -> {
                val startMs = start.instant.toEpochMilliseconds()
                fun fmt(ms: Long, prop: String): String =
                    if (tzId != null) "$prop;TZID=$tzId:${localStamp(ms, ev.timeZone!!)}" else "$prop:${utcStamp(ms)}"
                lines += fmt(startMs, "DTSTART")
                ev.end?.let { end ->
                    require(end is IcsTime.Timed) { "IcsCalendar: evento \"$uid\" com hora exige \"end\" com hora (Timed)." }
                    val endMs = end.instant.toEpochMilliseconds()
                    require(endMs >= startMs) { "IcsCalendar: \"end\" antes de \"start\" no evento \"$uid\"." }
                    lines += fmt(endMs, "DTEND")
                }
            }
        }

        lines += "SUMMARY:${escapeText(ev.summary)}"
        ev.description?.takeIf { it.isNotEmpty() }?.let { lines += "DESCRIPTION:${escapeText(it)}" }
        ev.location?.takeIf { it.isNotEmpty() }?.let { lines += "LOCATION:${escapeText(it)}" }
        ev.url?.takeIf { it.isNotEmpty() }?.let { lines += "URL:${normalizeUrl(it, uid)}" }
        if (ev.categories.isNotEmpty()) lines += "CATEGORIES:${ev.categories.joinToString(",") { escapeText(it) }}"
        ev.status?.let { lines += "STATUS:${it.name}" }
        ev.sequence?.let {
            require(it >= 0) { "IcsCalendar: \"sequence\" deve ser inteiro ≥ 0 (evento \"$uid\")." }
            lines += "SEQUENCE:$it"
        }
        ev.recurrence?.let { lines += rrule(it, allDay, ev.timeZone, uid) }

        for (alarm in ev.alarms) {
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:${escapeText(alarm.description ?: ev.summary)}"
            lines += "TRIGGER:${formatDuration(alarm.minutesBefore)}"
            lines += "END:VALARM"
        }
        lines += "END:VEVENT"
        return lines
    }

    private fun rrule(rec: IcsRecurrence, allDay: Boolean, timeZone: TimeZone?, uid: String): String {
        require(rec.count == null || rec.until == null) {
            "IcsCalendar: \"count\" e \"until\" são mutuamente exclusivos (evento \"$uid\")."
        }
        val parts = mutableListOf("FREQ=${rec.frequency.name}")
        rec.interval?.let {
            require(it >= 1) { "IcsCalendar: \"interval\" deve ser inteiro ≥ 1 (evento \"$uid\")." }
            if (it > 1) parts += "INTERVAL=$it"
        }
        rec.count?.let {
            require(it >= 1) { "IcsCalendar: \"count\" deve ser inteiro ≥ 1 (evento \"$uid\")." }
            parts += "COUNT=$it"
        }
        when (val until = rec.until) {
            is IcsTime.AllDay -> parts += if (allDay) {
                "UNTIL=${dateStamp(until.date)}"
            } else {
                // Até o FIM daquele dia no fuso do evento, expresso em UTC.
                val nextDay = until.date.plus(DatePeriod(days = 1))
                val endOfDay = nextDay.atStartOfDayIn(timeZone ?: TimeZone.UTC).toEpochMilliseconds() - 1000
                "UNTIL=${utcStamp(endOfDay)}"
            }
            is IcsTime.Timed -> {
                val stamp = utcStamp(until.instant.toEpochMilliseconds())
                parts += if (allDay) "UNTIL=${stamp.substring(0, 8)}" else "UNTIL=$stamp"
            }
            null -> Unit
        }
        if (rec.byDay.isNotEmpty()) parts += "BYDAY=${rec.byDay.joinToString(",") { it.name }}"
        if (rec.byMonthDay.isNotEmpty()) {
            for (d in rec.byMonthDay) {
                require(d != 0 && d in -31..31) {
                    "IcsCalendar: \"byMonthDay\" fora de 1..31 / −31..−1 (evento \"$uid\")."
                }
            }
            parts += "BYMONTHDAY=${rec.byMonthDay.joinToString(",")}"
        }
        return "RRULE:${parts.joinToString(";")}"
    }

    // -------------------------------------------------------------------------------------------
    // VTIMEZONE a partir da base IANA do aparelho — mesmo algoritmo da weblib
    // -------------------------------------------------------------------------------------------

    private class Transition(val at: Long, val from: Long, val to: Long)

    private fun offsetMillis(ms: Long, tz: TimeZone): Long =
        tz.offsetAt(Instant.fromEpochMilliseconds(ms)).totalSeconds * 1000L

    private fun findTransitions(tz: TimeZone, startMs: Long, endMs: Long): List<Transition> {
        val out = mutableListOf<Transition>()
        var prevT = startMs
        var prevOffset = offsetMillis(prevT, tz)
        var t = startMs + DAY
        while (t <= endMs + DAY) {
            val offset = offsetMillis(t, tz)
            if (offset != prevOffset) {
                // Busca binária até o segundo.
                var lo = prevT
                var hi = t
                while (hi - lo > 1000) {
                    val mid = (lo + hi).floorDiv(2000L) * 1000L
                    if (mid <= lo) break
                    if (offsetMillis(mid, tz) == prevOffset) lo = mid else hi = mid
                }
                out += Transition(hi, prevOffset, offset)
                prevOffset = offset
            }
            prevT = t
            t += DAY
        }
        return out
    }

    private fun buildVTimezone(tzId: String, tz: TimeZone, fromMs: Long, toMs: Long): List<String> {
        // Uma folga de um ano para cada lado cobre a recorrência perto das bordas.
        val start = fromMs - 366 * DAY
        val end = toMs + 366 * DAY
        val transitions = findTransitions(tz, start, end)
        val initial = offsetMillis(start, tz)
        val lines = mutableListOf("BEGIN:VTIMEZONE", "TZID:$tzId")

        fun component(kind: String, localStart: String, from: Long, to: Long) {
            lines += "BEGIN:$kind"
            lines += "DTSTART:$localStart"
            lines += "TZOFFSETFROM:${formatOffset(from)}"
            lines += "TZOFFSETTO:${formatOffset(to)}"
            lines += "END:$kind"
        }

        // DTSTART de cada subcomponente = hora LOCAL (no deslocamento de ANTES) em que a regra começa.
        fun localAt(ms: Long, offset: Long) = utcStamp(ms + offset).dropLast(1)
        component("STANDARD", localAt(start, initial), initial, initial)
        transitions.forEachIndexed { i, tr ->
            // Horário de verão = subiu e, dentro de ~13 meses, volta ao deslocamento anterior.
            val back = transitions.drop(i + 1).firstOrNull { n -> n.to == tr.from && n.at - tr.at < 400 * DAY }
            val kind = if (tr.to > tr.from && back != null) "DAYLIGHT" else "STANDARD"
            component(kind, localAt(tr.at, tr.from), tr.from, tr.to)
        }
        lines += "END:VTIMEZONE"
        return lines
    }

    // -------------------------------------------------------------------------------------------
    // Formatação
    // -------------------------------------------------------------------------------------------

    private const val CRLF = "\r\n"
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    private fun pad(n: Int, width: Int = 2): String = kotlin.math.abs(n).toString().padStart(width, '0')

    private fun utcStamp(ms: Long): String {
        // Segundos inteiros, como `Date#getUTCSeconds` — os milissegundos não existem no formato.
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC)
        return "${pad(dt.year, 4)}${pad(dt.month.ordinal + 1)}${pad(dt.day)}" +
            "T${pad(dt.hour)}${pad(dt.minute)}${pad(dt.second)}Z"
    }

    private fun localStamp(ms: Long, tz: TimeZone): String {
        val dt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
        return "${pad(dt.year, 4)}${pad(dt.month.ordinal + 1)}${pad(dt.day)}" +
            "T${pad(dt.hour)}${pad(dt.minute)}${pad(dt.second)}"
    }

    private fun dateStamp(date: LocalDate): String =
        "${pad(date.year, 4)}${pad(date.month.ordinal + 1)}${pad(date.day)}"

    private fun formatOffset(ms: Long): String {
        val sign = if (ms < 0) "-" else "+"
        val total = kotlin.math.round(kotlin.math.abs(ms) / 1000.0).toLong()
        val h = (total / 3600).toInt()
        val m = ((total % 3600) / 60).toInt()
        val s = (total % 60).toInt()
        return "$sign${pad(h)}${pad(m)}${if (s != 0) pad(s) else ""}"
    }

    /** Duração do `TRIGGER`: minutos ANTES do início viram duração negativa. */
    internal fun formatDuration(minutesBefore: Int): String {
        val sign = if (minutesBefore > 0) "-" else ""
        var rest = kotlin.math.abs(minutesBefore.toLong())
        if (rest == 0L) return "PT0S"
        val days = rest / 1440
        rest %= 1440
        val hours = rest / 60
        val mins = rest % 60
        val sb = StringBuilder("${sign}P")
        if (days != 0L) sb.append("${days}D")
        if (hours != 0L || mins != 0L) sb.append('T')
        if (hours != 0L) sb.append("${hours}H")
        if (mins != 0L) sb.append("${mins}M")
        return sb.toString()
    }

    private fun checkTimeZone(tz: TimeZone): String {
        val id = tz.id
        // `TZID` é paramtext (RFC 5545 §3.2): sem `:`/`;`/`,`/aspas. Um deslocamento fixo
        // ("+03:00", "Z") não é fuso IANA e não tem regra para o VTIMEZONE descrever.
        require(
            id.isNotBlank() && id != "Z" && id[0] != '+' && id[0] != '-' &&
                id.none { it == ':' || it == ';' || it == ',' || it == '"' || it.isWhitespace() },
        ) { "IcsCalendar: \"timeZone\" deve ser um fuso IANA (ex.: \"America/Sao_Paulo\"), não \"$id\"." }
        return id
    }

    /**
     * Confere que a URL é absoluta e a escreve na forma canônica que o `URL#href` do navegador
     * produziria nos casos comuns (esquema e host em minúsculas; `/` como caminho vazio de http/https;
     * espaço vira `%20`) — é o que mantém a paridade com a weblib.
     */
    private fun normalizeUrl(url: String, uid: String): String {
        val trimmed = url.trim()
        val schemeEnd = trimmed.indexOf(':')
        val scheme = if (schemeEnd > 0) trimmed.substring(0, schemeEnd) else ""
        val validScheme = scheme.isNotEmpty() && scheme[0].isLetter() &&
            scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
        require(validScheme) { "IcsCalendar: \"url\" do evento \"$uid\" precisa ser absoluta." }
        val lowerScheme = scheme.lowercase()
        var rest = trimmed.substring(schemeEnd + 1).replace(" ", "%20")
        if (lowerScheme == "http" || lowerScheme == "https") {
            require(rest.startsWith("//") && rest.length > 2) {
                "IcsCalendar: \"url\" do evento \"$uid\" precisa ser absoluta."
            }
            rest = rest.substring(2)
            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, authorityEnd)
            require(authority.isNotEmpty()) { "IcsCalendar: \"url\" do evento \"$uid\" precisa ser absoluta." }
            val at = authority.lastIndexOf('@')
            val host = if (at >= 0) authority.substring(0, at + 1) + authority.substring(at + 1).lowercase() else authority.lowercase()
            var tail = rest.substring(authorityEnd)
            if (!tail.startsWith("/")) tail = "/$tail"
            return "$lowerScheme://$host$tail"
        }
        return "$lowerScheme:$rest"
    }

    private fun utf8Size(c: Char): Int = when {
        c.code < 0x80 -> 1
        c.code < 0x800 -> 2
        else -> 3 // surrogate solto também ocupa 3 (vira U+FFFD)
    }

    private fun utf8Length(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                n += 4; i += 2
            } else {
                n += utf8Size(c); i++
            }
        }
        return n
    }
}

/** Início/fim/limite de um evento: **com hora** (um instante) ou **de dia inteiro** (uma data). */
sealed interface IcsTime {
    /** Com hora. Escrito em UTC (`Z`), ou na hora local com `TZID` quando o evento tem [IcsEvent.timeZone]. */
    data class Timed(val instant: Instant) : IcsTime

    /** Dia inteiro (`VALUE=DATE`). Como fim, é **exclusivo**: o evento de um dia só termina no dia seguinte. */
    data class AllDay(val date: LocalDate) : IcsTime
}

/** Dia da semana do `BYDAY` (RFC 5545). */
enum class IcsWeekday { MO, TU, WE, TH, FR, SA, SU }

/** Frequência do `RRULE`. */
enum class IcsFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** `STATUS` do evento. */
enum class IcsEventStatus { CONFIRMED, TENTATIVE, CANCELLED }

/**
 * Recorrência básica (`RRULE`). [count] e [until] são **mutuamente exclusivos** (RFC 5545).
 *
 * @param interval a cada N períodos (default 1, omitido do texto).
 * @param count número total de ocorrências.
 * @param until última data/instante (inclusive). [IcsTime.AllDay] = até o fim daquele dia.
 * @param byDay dias da semana (`WEEKLY`: quais dias).
 * @param byMonthDay dias do mês (1..31, ou −1 = último).
 */
data class IcsRecurrence(
    val frequency: IcsFrequency,
    val interval: Int? = null,
    val count: Int? = null,
    val until: IcsTime? = null,
    val byDay: List<IcsWeekday> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
)

/**
 * Lembrete (`VALARM` `DISPLAY`).
 *
 * @param minutesBefore minutos ANTES do início (0 = na hora; negativo = depois do início).
 * @param description texto do lembrete. Default: o `summary` do evento.
 */
data class IcsAlarm(
    val minutesBefore: Int,
    val description: String? = null,
)

/**
 * Um evento (`VEVENT`).
 *
 * @param uid identificador ÚNICO e ESTÁVEL (ex.: `"ciclo-42@folhadeaxe.com.br"`). Reexportar o mesmo
 *   evento com o mesmo `uid` (e [sequence] maior) ATUALIZA na agenda em vez de duplicar.
 * @param summary título.
 * @param start [IcsTime.Timed] (com hora) ou [IcsTime.AllDay] (dia inteiro).
 * @param end fim EXCLUSIVO, do mesmo tipo do [start]. Default: dia inteiro → 1 dia; com hora → sem duração.
 * @param timeZone fuso **IANA** para escrever a hora LOCAL com `TZID` (a recorrência atravessa o
 *   horário de verão certo). Sem ele, a hora vai em UTC (`Z`) — também válido, e a agenda converte.
 *   Deslocamento fixo (`+03:00`) é recusado.
 * @param url link absoluto (`https://…`).
 * @param sequence revisão do evento (0, 1, 2…) — aumente ao reenviar uma alteração.
 */
data class IcsEvent(
    val uid: String,
    val summary: String,
    val start: IcsTime,
    val end: IcsTime? = null,
    val timeZone: TimeZone? = null,
    val description: String? = null,
    val location: String? = null,
    val url: String? = null,
    val categories: List<String> = emptyList(),
    val status: IcsEventStatus? = null,
    val sequence: Int? = null,
    val alarms: List<IcsAlarm> = emptyList(),
    val recurrence: IcsRecurrence? = null,
)

/**
 * Opções do calendário.
 *
 * @param prodId `PRODID` — quem gerou. Default [IcsCalendar.DEFAULT_PROD_ID].
 * @param calendarName nome do calendário (`X-WR-CALNAME`, lido por Google/Apple ao ASSINAR).
 * @param now `DTSTAMP` — instante de geração. Default: agora (passe fixo em teste).
 */
data class IcsCalendarOptions(
    val prodId: String = IcsCalendar.DEFAULT_PROD_ID,
    val calendarName: String? = null,
    val now: Instant = Clock.System.now(),
)
