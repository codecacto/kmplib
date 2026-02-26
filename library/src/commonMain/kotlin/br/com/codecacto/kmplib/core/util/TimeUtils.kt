package br.com.codecacto.kmplib.core.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Obtém o timestamp atual em milissegundos.
 */
expect fun currentTimeMillis(): Long

/**
 * Obtém o Instant atual.
 */
fun currentInstant(): Instant = Instant.fromEpochMilliseconds(currentTimeMillis())

/**
 * Converte Instant para LocalDateTime no timezone do sistema.
 */
fun Instant.toSystemLocalDateTime(): LocalDateTime =
    this.toLocalDateTime(TimeZone.currentSystemDefault())

/**
 * Converte Instant para LocalDate no timezone do sistema.
 */
fun Instant.toSystemLocalDate(): LocalDate =
    this.toSystemLocalDateTime().date

/**
 * Formata uma data no formato brasileiro curto (dd/MM/yyyy).
 */
fun Instant.formatDateShort(): String {
    val localDateTime = this.toSystemLocalDateTime()
    val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
    val month = localDateTime.monthNumber.toString().padStart(2, '0')
    val year = localDateTime.year
    return "$day/$month/$year"
}

/**
 * Formata uma data no formato brasileiro longo (ex: "15 de janeiro de 2024").
 */
fun Instant.formatDateLong(): String {
    val localDateTime = this.toSystemLocalDateTime()
    val day = localDateTime.dayOfMonth
    val month = getMonthNamePtBr(localDateTime.monthNumber)
    val year = localDateTime.year
    return "$day de $month de $year"
}

/**
 * Formata data e hora no formato brasileiro (dd/MM/yyyy HH:mm).
 */
fun Instant.formatDateTime(): String {
    val localDateTime = this.toSystemLocalDateTime()
    val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
    val month = localDateTime.monthNumber.toString().padStart(2, '0')
    val year = localDateTime.year
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$day/$month/$year $hour:$minute"
}

/**
 * Retorna o nome do mês em português brasileiro.
 */
fun getMonthNamePtBr(month: Int): String = when (month) {
    1 -> "janeiro"
    2 -> "fevereiro"
    3 -> "março"
    4 -> "abril"
    5 -> "maio"
    6 -> "junho"
    7 -> "julho"
    8 -> "agosto"
    9 -> "setembro"
    10 -> "outubro"
    11 -> "novembro"
    12 -> "dezembro"
    else -> ""
}

/**
 * Utilitarios de data/hora para API baseada em timestamp (Long).
 */
object TimeUtils {
    /**
     * Formata um timestamp com um padrao simples (dd, MM, yyyy, HH, mm, ss).
     */
    fun formatTimestamp(timestampMillis: Long, pattern: String = "dd/MM/yyyy HH:mm"): String {
        val localDateTime = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        val replacements = mapOf(
            "yyyy" to localDateTime.year.toString().padStart(4, '0'),
            "MM" to localDateTime.monthNumber.toString().padStart(2, '0'),
            "dd" to localDateTime.dayOfMonth.toString().padStart(2, '0'),
            "HH" to localDateTime.hour.toString().padStart(2, '0'),
            "mm" to localDateTime.minute.toString().padStart(2, '0'),
            "ss" to localDateTime.second.toString().padStart(2, '0')
        )

        var result = pattern
        replacements.forEach { (token, value) ->
            result = result.replace(token, value)
        }
        return result
    }

    /**
     * Formata data brasileira curta (dd/MM/yyyy).
     */
    fun formatDateBrazilian(timestampMillis: Long): String =
        formatTimestamp(timestampMillis, "dd/MM/yyyy")

    /**
     * Formata data/hora brasileira (dd/MM/yyyy HH:mm).
     */
    fun formatDateTimeBrazilian(timestampMillis: Long): String =
        formatTimestamp(timestampMillis, "dd/MM/yyyy HH:mm")

    /**
     * Retorna tempo relativo simples (ex: "há 5 minutos").
     */
    fun getRelativeTime(timestampMillis: Long, nowMillis: Long = currentTimeMillis()): String {
        val diff = nowMillis - timestampMillis
        val isFuture = diff < 0
        val absSeconds = kotlin.math.abs(diff) / 1000

        val value: Long
        val unit: String

        when {
            absSeconds < 60 -> {
                value = absSeconds
                unit = if (value == 1L) "segundo" else "segundos"
            }
            absSeconds < 3600 -> {
                value = absSeconds / 60
                unit = if (value == 1L) "minuto" else "minutos"
            }
            absSeconds < 86400 -> {
                value = absSeconds / 3600
                unit = if (value == 1L) "hora" else "horas"
            }
            else -> {
                value = absSeconds / 86400
                unit = if (value == 1L) "dia" else "dias"
            }
        }

        return if (isFuture) {
            "em $value $unit"
        } else {
            "há $value $unit"
        }
    }

    /**
     * Verifica se o timestamp e hoje.
     */
    fun isToday(timestampMillis: Long, nowMillis: Long = currentTimeMillis()): Boolean {
        val date = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val today = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return date == today
    }

    /**
     * Verifica se o timestamp e ontem.
     */
    fun isYesterday(timestampMillis: Long, nowMillis: Long = currentTimeMillis()): Boolean {
        val date = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val today = Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        // Calculate yesterday by subtracting one day in milliseconds
        val yesterdayMillis = nowMillis - (24 * 60 * 60 * 1000)
        val yesterday = Instant.fromEpochMilliseconds(yesterdayMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return date == yesterday
    }

    /**
     * Retorna o inicio do dia (00:00:00.000) para o timestamp.
     */
    fun startOfDay(timestampMillis: Long): Long {
        val date = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val start = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0, 0)
        return start.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    /**
     * Retorna o fim do dia (23:59:59.999) para o timestamp.
     */
    fun endOfDay(timestampMillis: Long): Long {
        val date = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val end = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 23, 59, 59)
        return end.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 999
    }

    /**
     * Adiciona ou subtrai dias de um timestamp.
     */
    fun addDays(timestampMillis: Long, days: Int): Long {
        val instant = Instant.fromEpochMilliseconds(timestampMillis)
        // Add days directly to instant (days * 24 hours * 60 minutes * 60 seconds * 1000 milliseconds)
        val daysInMillis = days.toLong() * 24 * 60 * 60 * 1000
        return timestampMillis + daysInMillis
    }

    /**
     * Define hora/minuto/segundo em um timestamp mantendo a data.
     */
    fun setTime(timestampMillis: Long, hour: Int, minute: Int, second: Int = 0): Long {
        val date = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dateTime = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute, second)
        return dateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    /**
     * Parseia datas com padroes conhecidos.
     * Suporta: "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm", "dd/MM/yyyy".
     */
    fun parseDate(value: String, pattern: String): Long {
        return when (pattern) {
            "yyyy-MM-dd HH:mm" -> {
                val match = Regex("""(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})""")
                    .matchEntire(value)
                    ?: throw IllegalArgumentException("Data invalida: $value")
                val (year, month, day, hour, minute) = match.destructured
                toInstant(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
            }
            "dd/MM/yyyy HH:mm" -> {
                val match = Regex("""(\d{2})/(\d{2})/(\d{4}) (\d{2}):(\d{2})""")
                    .matchEntire(value)
                    ?: throw IllegalArgumentException("Data invalida: $value")
                val (day, month, year, hour, minute) = match.destructured
                toInstant(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
            }
            "dd/MM/yyyy" -> {
                val match = Regex("""(\d{2})/(\d{2})/(\d{4})""")
                    .matchEntire(value)
                    ?: throw IllegalArgumentException("Data invalida: $value")
                val (day, month, year) = match.destructured
                toInstant(year.toInt(), month.toInt(), day.toInt(), 0, 0)
            }
            else -> throw IllegalArgumentException("Formato nao suportado: $pattern")
        }
    }

    private fun toInstant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Long {
        val dateTime = LocalDateTime(year, month, day, hour, minute, second)
        return dateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }
}




