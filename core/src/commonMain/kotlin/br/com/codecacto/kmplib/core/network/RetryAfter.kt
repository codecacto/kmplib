package br.com.codecacto.kmplib.core.network

import io.ktor.http.fromHttpToGmtDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Lê o cabeçalho **`Retry-After`** (RFC 9110 §10.2.3) e devolve **quantos segundos esperar**
 * (2.366.0).
 *
 * O cabeçalho vem em uma de duas formas, e as duas valem:
 * - **delta-seconds** — `Retry-After: 120` (só algarismos; é o que a backlib manda);
 * - **HTTP-date** — `Retry-After: Sun, 15 Nov 2026 14:05:00 GMT` (IMF-fixdate, lido pelo próprio
 *   Ktor, `fromHttpToGmtDate`) e as duas formas obsoletas que a RFC 9110 §5.6.7 manda o destinatário
 *   aceitar e que o Ktor não lê: RFC 850 (`Sunday, 15-Nov-26 14:05:00 GMT`, ano de 2 dígitos que
 *   passe de 50 anos à frente é do século anterior) e asctime (`Sun Nov 15 14:05:00 2026`).
 *
 * Data no passado (ou igual a agora) vira `0`: o servidor já liberou. Segundos que sobram de uma
 * data (fração) são arredondados **para cima** — dizer "pode tentar" um instante antes do
 * desbloqueio faz a pessoa levar o mesmo 429 de novo.
 *
 * @param value o valor do cabeçalho, como chegou (`null` = ausente).
 * @param nowEpochMillis o "agora" contra o qual uma data é medida (parâmetro para o teste).
 * @return segundos (≥ 0), ou `null` quando ausente, em branco, negativo, fracionário ("1.5"),
 *   grande demais para `Long` ou ilegível — nesse caso o servidor **não disse**, e a tela usa a
 *   frase sem prazo.
 */
fun parseRetryAfterSeconds(
    value: String?,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): Long? {
    val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (v.all { it in '0'..'9' }) return v.toLongOrNull()
    val dateMillis = imfFixdateMillis(v) ?: obsoleteHttpDateMillis(v, nowEpochMillis) ?: return null
    val diff = dateMillis - nowEpochMillis
    return if (diff <= 0) 0L else (diff + 999) / 1_000
}

private fun imfFixdateMillis(v: String): Long? =
    if (IMF_FIXDATE.matches(v)) runCatching { v.fromHttpToGmtDate().timestamp }.getOrNull() else null

private val IMF_FIXDATE = Regex("""[A-Za-z]{3}, \d{2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2} GMT""")
private val RFC_850 = Regex("""[A-Za-z]{6,9}, (\d{2})-([A-Za-z]{3})-(\d{2}) (\d{2}):(\d{2}):(\d{2}) GMT""")
private val ASCTIME = Regex("""[A-Za-z]{3} ([A-Za-z]{3}) ([ \d]\d) (\d{2}):(\d{2}):(\d{2}) (\d{4})""")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun obsoleteHttpDateMillis(v: String, nowEpochMillis: Long): Long? {
    RFC_850.matchEntire(v)?.let { m ->
        val (dd, mon, yy, hh, mi, ss) = m.destructured
        val nowYear = Instant.fromEpochMilliseconds(nowEpochMillis).toLocalDateTime(TimeZone.UTC).year
        var year = nowYear - nowYear % 100 + yy.toInt()
        if (year > nowYear + 50) year -= 100
        return utcMillis(year, mon, dd.toInt(), hh.toInt(), mi.toInt(), ss.toInt())
    }
    ASCTIME.matchEntire(v)?.let { m ->
        val (mon, d, hh, mi, ss, yyyy) = m.destructured
        return utcMillis(yyyy.toInt(), mon, d.trim().toInt(), hh.toInt(), mi.toInt(), ss.toInt())
    }
    return null
}

private fun utcMillis(year: Int, month: String, day: Int, hour: Int, minute: Int, second: Int): Long? {
    val m = MONTHS.indexOfFirst { it.equals(month, ignoreCase = true) }.takeIf { it >= 0 } ?: return null
    return runCatching {
        LocalDateTime(year, m + 1, day, hour, minute, second).toInstant(TimeZone.UTC).toEpochMilliseconds()
    }.getOrNull()
}

/**
 * Minutos inteiros para mostrar a quem espera, a partir de [seconds] do `Retry-After` (2.366.0):
 * arredonda **para cima** e nunca dá menos de 1 — "tente de novo em 0 minutos" não existe, e
 * 30 segundos lidos como "1 minuto" erra para o lado seguro.
 */
fun retryAfterMinutesForDisplay(seconds: Long): Long =
    if (seconds <= 60) 1L else (seconds + 59) / 60
