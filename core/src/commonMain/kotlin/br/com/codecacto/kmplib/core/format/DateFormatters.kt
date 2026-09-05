package br.com.codecacto.kmplib.core.format

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
 * Formata epoch millis como **"HH:mm"** (24h, zero-padded) no [timeZone] pedido.
 *
 * Existe porque a lib já dava a data ([formatDateBrFromMillis]), a data com a hora
 * ([formatDateTimeBrFromMillis]) e a hora a partir de hora+minuto ([formatTime]) — mas nada que
 * desse **só a hora** a partir de um instante. Toda tela que escreve "concluído às 09:12" tinha de
 * repetir as mesmas quatro linhas de conversão.
 *
 * ⚠️ **O fuso é decisão do chamador.** O default é o do aparelho, que é o certo para "o que aconteceu
 * agora"; quando a hora pertence a um lugar (o fuso da casa, da obra, da quadra), passe o fuso de lá —
 * senão o app de quem viajou mostra a hora do destino para um fato que aconteceu em casa.
 *
 * Mesma convenção dos irmãos deste arquivo: `millis <= 0` devolve `"-"` (ausência de valor, não
 * "01/01/1970").
 */
fun formatTimeBrFromMillis(
    millis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (millis <= 0L) return "-"
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
    return formatTime(dt.hour, dt.minute)
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

/**
 * Nomes completos dos dias da semana em pt-BR, capitalizados e com hifen ("Segunda-Feira" ...
 * "Sabado" -> "Sábado", "Domingo"). Fonte canonica para exibicao pt-BR na lib e nos apps-espelho.
 */
private val WEEKDAY_NAMES_BR: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "Segunda-Feira",
    DayOfWeek.TUESDAY to "Terça-Feira",
    DayOfWeek.WEDNESDAY to "Quarta-Feira",
    DayOfWeek.THURSDAY to "Quinta-Feira",
    DayOfWeek.FRIDAY to "Sexta-Feira",
    DayOfWeek.SATURDAY to "Sábado",
    DayOfWeek.SUNDAY to "Domingo",
)

/** Aceita "yyyy-MM-dd" ou um ISO datetime "yyyy-MM-ddTHH:mm[:ss]" (usa a parte de data). null se invalido. */
private fun parseIsoLocalDate(iso: String): LocalDate? {
    val trimmed = iso.trim()
    runCatching { LocalDate.parse(trimmed) }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(trimmed) }.getOrNull()?.let { return it.date }
    return null
}

/**
 * Nome completo do dia da semana em pt-BR, capitalizado ("Sexta-Feira", "Sábado", "Domingo"),
 * a partir de um ISO "yyyy-MM-dd" (ou ISO datetime). Retorna null se a data for invalida.
 */
fun weekdayNameBr(isoDate: String): String? =
    parseIsoLocalDate(isoDate)?.let { WEEKDAY_NAMES_BR.getValue(it.dayOfWeek) }

/**
 * Data com dia da semana para listas (ex.: cartao de ponto): "Sexta-Feira, 17/07"
 * (dia da semana pt-BR capitalizado + "dd/MM", sem ano). A partir de ISO "yyyy-MM-dd"
 * (ou ISO datetime). Retorna a entrada original se nao for uma data valida.
 */
fun formatDateWeekdayBr(isoDate: String): String {
    val date = parseIsoLocalDate(isoDate) ?: return isoDate
    val name = WEEKDAY_NAMES_BR.getValue(date.dayOfWeek)
    val dd = date.dayOfMonth.toString().padStart(2, '0')
    val mm = date.monthNumber.toString().padStart(2, '0')
    return "$name, $dd/$mm"
}

/**
 * Extrai "HH:mm" (24h, zero-padded) de um timestamp ISO. Aceita datetime local sem offset
 * ("2026-07-17T08:29:00" / "2026-07-17T08:29") e tambem instantes com offset/"Z"
 * ("2026-07-17T08:29:00Z", "...-03:00"), convertidos para [timeZone] (default: fuso do sistema).
 * Retorna a entrada original se nao for parseavel.
 */
fun formatTimeFromIso(
    iso: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val trimmed = iso.trim()
    runCatching { LocalDateTime.parse(trimmed) }.getOrNull()?.let {
        return formatTime(it.hour, it.minute)
    }
    runCatching { Instant.parse(trimmed).toLocalDateTime(timeZone) }.getOrNull()?.let {
        return formatTime(it.hour, it.minute)
    }
    return iso
}
