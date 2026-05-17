package br.com.codecacto.kmplib.core.format

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Converte ISO "yyyy-MM-dd" para "dd/MM/yyyy" (pt-BR e es-ES). Retorna a entrada se nao for ISO valido.
 */
fun formatDateBr(isoDate: String): String {
    val parts = isoDate.split("-")
    return if (parts.size == 3 && parts[0].length == 4) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate
}

/**
 * Converte "dd/MM/yyyy" para ISO "yyyy-MM-dd", validando contra LocalDate. Retorna null se invalido.
 */
fun parseDateBrToIso(brDate: String): String? {
    val parts = brDate.split("/")
    if (parts.size != 3) return null
    return runCatching {
        val iso = "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
        LocalDate.parse(iso)
        iso
    }.getOrNull()
}

/**
 * Converte ISO "yyyy-MM-dd" para "MM/dd/yyyy" (en-US).
 */
fun formatDateUS(isoDate: String): String {
    val parts = isoDate.split("-")
    return if (parts.size == 3 && parts[0].length == 4) "${parts[1]}/${parts[2]}/${parts[0]}" else isoDate
}

/**
 * Converte "MM/dd/yyyy" (en-US) para ISO. Retorna null se invalido.
 */
fun parseDateUSToIso(usDate: String): String? {
    val parts = usDate.split("/")
    if (parts.size != 3) return null
    return runCatching {
        val iso = "${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}"
        LocalDate.parse(iso)
        iso
    }.getOrNull()
}

/**
 * Formata epoch millis como "dd/MM/yyyy" (pt-BR / es-ES).
 */
fun formatDateBrFromMillis(
    millis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (millis <= 0L) return "-"
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
    return "${dt.dayOfMonth.toString().padStart(2, '0')}/" +
        "${dt.monthNumber.toString().padStart(2, '0')}/" +
        "${dt.year}"
}

/**
 * Formata epoch millis como "dd/MM/yyyy HH:mm" (pt-BR / es-ES).
 */
fun formatDateTimeBrFromMillis(
    millis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (millis <= 0L) return "-"
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
    return "${dt.dayOfMonth.toString().padStart(2, '0')}/" +
        "${dt.monthNumber.toString().padStart(2, '0')}/" +
        "${dt.year} " +
        "${dt.hour.toString().padStart(2, '0')}:" +
        "${dt.minute.toString().padStart(2, '0')}"
}

/**
 * Formata epoch millis como ISO "yyyy-MM-dd".
 */
fun formatIsoDateFromMillis(
    millis: Long,
    timeZone: TimeZone = TimeZone.UTC,
): String {
    if (millis <= 0L) return "-"
    return Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date.toString()
}

/**
 * "HH:mm" zero-padded.
 */
fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * Converte data ISO "yyyy-MM-dd" para epoch millis no inicio do dia. null se invalido.
 */
fun parseIsoDateToMillis(value: String, timeZone: TimeZone = TimeZone.UTC): Long? = runCatching {
    LocalDate.parse(value.trim()).atStartOfDayIn(timeZone).toEpochMilliseconds()
}.getOrNull()
