package br.com.codecacto.kmplib.ui.calendar

/* ─────────────────────────── expediente semanal (lógica PURA) ───────────────────────────
 * Par mobile de `calendar/schedule.ts` da weblib — NOMES e semântica espelhados. Modelo + validação de
 * "faixas por dia da semana", domínio-AGNÓSTICO (é `WeekdaySchedule`, NÃO "horário de barbearia"): serve
 * expediente do estabelecimento, jornada de um profissional, disponibilidade de uma sala. Múltiplas
 * faixas por dia modelam o intervalo (ex.: 09:00–12:00 e 13:00–19:00 = almoço entre elas).
 *
 * Sem Compose — reusa a aritmética de `CalendarTime.kt` ([rangesOverlap]/[MinuteRange]) e o fim-de-dia de
 * `DayBoundaryTime.kt` ([parseDayMinute]/[DayTimeRole]). O **fim** de uma faixa aceita "24:00"
 * ([END_OF_DAY_MINUTE]); o **início** não (a API separa os papéis). Base do [AppWeeklyScheduleEditor].
 *
 * Diferença vs weblib (documentada): o `schedule.ts` da web usa `parseTimeOfDay`, que rejeita "24:00" —
 * a web ainda não expressa fim de dia. O mobile o expressa via os papéis de [DayTimeRole].
 */

/** Faixa "HH:mm"–"HH:mm" de um dia (o fim aceita "24:00"). */
data class TimeRange(val start: String = "", val end: String = "")

/** Faixas de um dia da semana (`weekday` 0=Domingo … 6=Sábado). Dias ausentes = fechado. */
data class WeekdaySchedule(val weekday: Int, val ranges: List<TimeRange> = emptyList())

/** Tipo de problema numa faixa. */
enum class RangeIssueKind {
    /** Faixa com início e/ou fim em branco. */
    Empty,

    /** Fim ≤ início (faixa nula/invertida). */
    Inverted,

    /** Duas faixas do mesmo dia se sobrepõem. */
    Overlap,
}

/** Problema encontrado na validação de um dia. */
data class RangeIssue(
    /** Índice da faixa problemática dentro do dia. */
    val index: Int,
    val kind: RangeIssueKind,
    /** Para [RangeIssueKind.Overlap]: índice da outra faixa que colide. */
    val with: Int? = null,
)

/** Presets de dias-alvo para a operação "copiar dia" (0=Dom … 6=Sáb). */
val ALL_WEEKDAYS: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6)

/** Dias úteis (segunda a sexta). */
val BUSINESS_WEEKDAYS: List<Int> = listOf(1, 2, 3, 4, 5)

/** Fim de semana (sábado e domingo). */
val WEEKEND_WEEKDAYS: List<Int> = listOf(0, 6)

/** Um alvo da operação "copiar dia" (ex.: dias úteis). */
data class WeekdayCopyTarget(val id: String, val label: String, val weekdays: List<Int>)

/**
 * Converte uma [TimeRange] em [MinuteRange], ou `null` se algum lado for inválido/vazio. O início é lido
 * como [DayTimeRole.Start] (rejeita "24:00") e o fim como [DayTimeRole.End] (aceita "24:00").
 */
fun parseRange(range: TimeRange): MinuteRange? {
    val startMin = parseDayMinute(range.start, DayTimeRole.Start) ?: return null
    val endMin = parseDayMinute(range.end, DayTimeRole.End) ?: return null
    return MinuteRange(startMin, endMin)
}

/** `true` se algum lado da faixa está em branco/não-preenchido. */
fun isRangeEmpty(range: TimeRange): Boolean = range.start.isBlank() || range.end.isBlank()

/**
 * Valida as faixas de UM dia e devolve TODOS os problemas (ordem estável):
 *  - [RangeIssueKind.Empty]    faixa com início e/ou fim em branco;
 *  - [RangeIssueKind.Inverted] fim ≤ início (faixa nula/invertida);
 *  - [RangeIssueKind.Overlap]  duas faixas do mesmo dia se sobrepõem (par reportado uma vez, `index < with`).
 * Fronteira **aberta**: 09:00–12:00 e 12:00–19:00 **não** sobrepõem (encostam).
 */
fun validateDayRanges(ranges: List<TimeRange>): List<RangeIssue> {
    val issues = ArrayList<RangeIssue>()
    val parsed: List<MinuteRange?> = ranges.map { if (isRangeEmpty(it)) null else parseRange(it) }

    ranges.forEachIndexed { i, r ->
        if (isRangeEmpty(r)) {
            issues.add(RangeIssue(index = i, kind = RangeIssueKind.Empty))
            return@forEachIndexed
        }
        val mr = parsed[i]
        if (mr != null && mr.endMin <= mr.startMin) {
            issues.add(RangeIssue(index = i, kind = RangeIssueKind.Inverted))
        }
    }

    for (i in parsed.indices) {
        val a = parsed[i]
        if (a == null || a.endMin <= a.startMin) continue
        for (j in (i + 1) until parsed.size) {
            val b = parsed[j]
            if (b == null || b.endMin <= b.startMin) continue
            if (rangesOverlap(a, b)) issues.add(RangeIssue(index = i, kind = RangeIssueKind.Overlap, with = j))
        }
    }

    return issues
}

/** `true` se o dia tem qualquer problema (vazio/invertido/sobreposto). */
fun dayHasIssues(ranges: List<TimeRange>): Boolean = validateDayRanges(ranges).isNotEmpty()

/** `true` se QUALQUER dia do expediente tem problema — trava o "salvar" no consumidor. */
fun scheduleHasIssues(schedule: List<WeekdaySchedule>): Boolean = schedule.any { dayHasIssues(it.ranges) }

/** Ordena e normaliza o expediente por `weekday`, descartando dias sem faixas (fechados). */
fun normalizeSchedule(schedule: List<WeekdaySchedule>): List<WeekdaySchedule> =
    schedule
        .filter { it.ranges.isNotEmpty() }
        .sortedBy { it.weekday }
        .map { WeekdaySchedule(it.weekday, it.ranges.map { r -> r.copy() }) }

/**
 * Copia as [ranges] de um dia para os [targets] (a operação "aplicar seg–sex"). Não muta a entrada. O
 * próprio dia de origem em [targets] é ignorado (idempotente). [ranges] vazio ⇒ fecha os alvos.
 */
fun applyRangesToWeekdays(
    schedule: List<WeekdaySchedule>,
    sourceWeekday: Int,
    ranges: List<TimeRange>,
    targets: List<Int>,
): List<WeekdaySchedule> {
    val byWeekday = LinkedHashMap<Int, List<TimeRange>>()
    for (d in schedule) byWeekday[d.weekday] = d.ranges

    for (wd in targets) {
        if (wd == sourceWeekday) continue
        if (ranges.isEmpty()) byWeekday.remove(wd) else byWeekday[wd] = ranges.map { it.copy() }
    }

    val out = ArrayList<WeekdaySchedule>()
    for (wd in 0..6) {
        val r = byWeekday[wd]
        if (!r.isNullOrEmpty()) out.add(WeekdaySchedule(wd, r.map { it.copy() }))
    }
    return out
}
