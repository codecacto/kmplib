package br.com.codecacto.kmplib.ui.calendar

import kotlinx.datetime.LocalDateTime

/* ─────────────────────────── helpers de tempo (sem fuso, sem Compose) ───────────────────────────
 * Núcleo PURO e testável do calendário mobile — espelha `calendar/time.ts` da weblib. Aqui mora a
 * aritmética de HORA/MINUTO da grade. Filosofia "horário de parede local" (igual ao `itemTime` do
 * Influencer): posiciona pelo relógio local do salão (D-20), sem conversão de fuso — o que evita o bug
 * clássico de um agendamento "pular de hora". Toda a aritmética é em `LocalDateTime` (parede local);
 * NUNCA `Instant` (typealias de `kotlin.time.Instant` no kotlinx-datetime 0.7.x, raiz do incidente R8).
 */

/** Minutos em um dia. */
const val MINUTES_IN_DAY: Int = 1440

/** Converte minutos-do-dia (0..1439+) para "HH:mm". Minutos ≥1440 espelham no dia (mod 24h). */
fun formatTimeOfDay(minutes: Int): String {
    val total = if (minutes < 0) 0 else minutes
    val h = (total / 60) % 24
    val m = total % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/** Converte "HH:mm" para minutos-do-dia (0..1439). `null` se inválido. */
fun parseTimeOfDay(value: String): Int? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").find(value.trim()) ?: return null
    val h = match.groupValues[1].toIntOrNull() ?: return null
    val m = match.groupValues[2].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** Máscara progressiva de hora "HH:mm" (aceita parcial), a partir dos dígitos digitados. */
fun maskTime(value: String): String {
    val d = value.filter { it.isDigit() }.take(4)
    return if (d.length <= 2) d else "${d.substring(0, 2)}:${d.substring(2)}"
}

/** Minutos-do-dia de um `LocalDateTime` (componentes de parede). */
fun minutesOfDay(dateTime: LocalDateTime): Int = dateTime.hour * 60 + dateTime.minute

/**
 * "Minutos de parede" absolutos desde a época (dias × 1440 + hora do dia) — base para diferença de
 * duração SEM fuso. Puro `LocalDate`/`LocalDateTime`, nunca `Instant`.
 */
private fun LocalDateTime.wallMinutes(): Long =
    date.toEpochDays() * MINUTES_IN_DAY + hour * 60 + minute

/** Duração em minutos entre dois datetimes (parede local). `0` se fim ≤ início. */
fun durationMinutes(start: LocalDateTime, end: LocalDateTime): Int {
    val diff = end.wallMinutes() - start.wallMinutes()
    return if (diff < 0) 0 else diff.toInt()
}

/**
 * Faixa de minutos-do-dia de um par início→fim, **relativa ao dia do início**. O fim é
 * `startMin + duração`, então um evento que cruza a meia-noite tem `endMin > 1440` (posiciona
 * corretamente, sem "voltar" ao topo).
 */
fun toMinuteRange(start: LocalDateTime, end: LocalDateTime): MinuteRange {
    val startMin = minutesOfDay(start)
    return MinuteRange(startMin, startMin + durationMinutes(start, end))
}

/** Duas faixas se sobrepõem (fronteira aberta: fim == início NÃO sobrepõe). */
fun rangesOverlap(a: MinuteRange, b: MinuteRange): Boolean =
    a.startMin < b.endMin && b.startMin < a.endMin

/** Arredonda `min` para baixo ao múltiplo de `step`. */
fun floorToStep(min: Int, step: Int): Int {
    if (step <= 0) return min
    return (min / step) * step - (if (min < 0 && min % step != 0) step else 0)
}

/** Arredonda `min` para cima ao múltiplo de `step`. */
fun ceilToStep(min: Int, step: Int): Int {
    if (step <= 0) return min
    val floored = floorToStep(min, step)
    return if (floored == min) min else floored + step
}

/**
 * Coage uma string ISO ("yyyy-MM-ddTHH:mm[:ss][offset/Z]" ou "yyyy-MM-dd") para `LocalDateTime`
 * **parede local** — ignora offset/`Z` (mesma filosofia do `parseLocalDateTime` da weblib). Útil no
 * boundary de API do app; o grid em si já recebe `LocalDateTime`. `null` se inválida.
 */
fun parseCalendarDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank()) return null
    val match = Regex(
        """^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?""",
    ).find(value.trim()) ?: return null
    val g = match.groupValues
    val year = g[1].toIntOrNull() ?: return null
    val month = g[2].toIntOrNull() ?: return null
    val day = g[3].toIntOrNull() ?: return null
    val hour = g[4].toIntOrNull() ?: 0
    val minute = g[5].toIntOrNull() ?: 0
    val second = g[6].toIntOrNull() ?: 0
    return try {
        LocalDateTime(year, month, day, hour, minute, second)
    } catch (_: IllegalArgumentException) {
        null
    }
}
