package br.com.codecacto.kmplib.bible

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Os tempos do ano litúrgico (calendário romano geral). */
enum class LiturgicalSeason {
    /** Advento: do 1º domingo do Advento até 24/12. */
    ADVENT,

    /** Natal: de 25/12 até o Batismo do Senhor. */
    CHRISTMAS,

    /** Tempo Comum (as duas partes do ano). */
    ORDINARY_TIME,

    /** Quaresma: da Quarta-feira de Cinzas até a Quinta-feira Santa (antes da Missa da Ceia). */
    LENT,

    /** Tríduo Pascal: Quinta-feira Santa (Ceia) até o Domingo de Páscoa. */
    PASCHAL_TRIDUUM,

    /** Tempo Pascal: do Domingo de Páscoa até Pentecostes. */
    EASTER,
}

/** Cor litúrgica do tempo (a das celebrações do tempo; festas próprias à parte). */
enum class LiturgicalColor { PURPLE, WHITE, GREEN, RED }

/**
 * Cálculo **puro** da Páscoa e dos tempos litúrgicos (rito romano), sem rede e sem tabela.
 *
 * Duas escolhas que mudam por conferência episcopal são parâmetros, com o default do **Brasil**
 * (CNBB), que é o mesmo dos EUA e de Portugal:
 * - [epiphanyOnSunday]: a Epifania vai para o domingo entre 2 e 8 de janeiro (senão, 6/1).
 * - [ascensionOnSunday]: a Ascensão vai para o 7º domingo da Páscoa (senão, quinta-feira, Páscoa + 39).
 */
class LiturgicalCalendar(
    val epiphanyOnSunday: Boolean = true,
    val ascensionOnSunday: Boolean = true,
) {

    /** Domingo de Páscoa (gregoriano). */
    fun easter(year: Int): LocalDate = easterSunday(year)

    /** Quarta-feira de Cinzas (Páscoa − 46). */
    fun ashWednesday(year: Int): LocalDate = easter(year).minus(DatePeriod(days = 46))

    /** Domingo de Ramos (Páscoa − 7). */
    fun palmSunday(year: Int): LocalDate = easter(year).minus(DatePeriod(days = 7))

    /** Quinta-feira Santa (Páscoa − 3). */
    fun holyThursday(year: Int): LocalDate = easter(year).minus(DatePeriod(days = 3))

    /** Sexta-feira Santa (Páscoa − 2). */
    fun goodFriday(year: Int): LocalDate = easter(year).minus(DatePeriod(days = 2))

    /** Ascensão do Senhor (domingo, Páscoa + 42, ou quinta, Páscoa + 39 — ver [ascensionOnSunday]). */
    fun ascension(year: Int): LocalDate = easter(year).plus(DatePeriod(days = if (ascensionOnSunday) 42 else 39))

    /** Pentecostes (Páscoa + 49). */
    fun pentecost(year: Int): LocalDate = easter(year).plus(DatePeriod(days = 49))

    /** Santíssima Trindade (Pentecostes + 7). */
    fun trinitySunday(year: Int): LocalDate = pentecost(year).plus(DatePeriod(days = 7))

    /** Corpus Christi (quinta-feira, Páscoa + 60). */
    fun corpusChristi(year: Int): LocalDate = easter(year).plus(DatePeriod(days = 60))

    /** 1º domingo do Advento: o 4º domingo antes do Natal (entre 27/11 e 3/12). */
    fun firstSundayOfAdvent(year: Int): LocalDate {
        val christmas = LocalDate(year, 12, 25)
        val back = (christmas.dayOfWeek.isoDayNumber % 7).let { if (it == 0) 7 else it }
        return christmas.minus(DatePeriod(days = back + 21))
    }

    /** Epifania do Senhor (ver [epiphanyOnSunday]). */
    fun epiphany(year: Int): LocalDate {
        if (!epiphanyOnSunday) return LocalDate(year, 1, 6)
        var d = LocalDate(year, 1, 2)
        while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plus(DatePeriod(days = 1))
        return d
    }

    /**
     * Batismo do Senhor — último dia do tempo do Natal: o domingo depois da Epifania; com a Epifania no
     * domingo 7 ou 8 de janeiro, é a segunda-feira seguinte (norma romana).
     */
    fun baptismOfTheLord(year: Int): LocalDate {
        val epiphany = epiphany(year)
        if (epiphanyOnSunday && epiphany.dayOfMonth >= 7) return epiphany.plus(DatePeriod(days = 1))
        var d = epiphany.plus(DatePeriod(days = 1))
        while (d.dayOfWeek != DayOfWeek.SUNDAY) d = d.plus(DatePeriod(days = 1))
        return d
    }

    /** O tempo litúrgico de [date]. */
    fun seasonOf(date: LocalDate): LiturgicalSeason {
        val y = date.year
        val advent = firstSundayOfAdvent(y)
        return when {
            date >= LocalDate(y, 12, 25) -> LiturgicalSeason.CHRISTMAS
            date >= advent -> LiturgicalSeason.ADVENT
            date <= baptismOfTheLord(y) -> LiturgicalSeason.CHRISTMAS
            date < ashWednesday(y) -> LiturgicalSeason.ORDINARY_TIME
            date < holyThursday(y) -> LiturgicalSeason.LENT
            date <= easter(y) -> LiturgicalSeason.PASCHAL_TRIDUUM
            date <= pentecost(y) -> LiturgicalSeason.EASTER
            else -> LiturgicalSeason.ORDINARY_TIME
        }
    }

    /** A cor do tempo de [date] (Pentecostes e Sexta-feira Santa: vermelho; Domingo de Ramos: vermelho). */
    fun colorOf(date: LocalDate): LiturgicalColor {
        val y = date.year
        if (date == pentecost(y) || date == goodFriday(y) || date == palmSunday(y)) return LiturgicalColor.RED
        return when (seasonOf(date)) {
            LiturgicalSeason.ADVENT, LiturgicalSeason.LENT -> LiturgicalColor.PURPLE
            LiturgicalSeason.CHRISTMAS, LiturgicalSeason.EASTER, LiturgicalSeason.PASCHAL_TRIDUUM -> LiturgicalColor.WHITE
            LiturgicalSeason.ORDINARY_TIME -> LiturgicalColor.GREEN
        }
    }

    companion object {
        /**
         * Domingo de Páscoa gregoriano — algoritmo de Meeus/Jones/Butcher (*Astronomical Algorithms*,
         * cap. 8), válido para qualquer ano do calendário gregoriano (≥ 1583).
         */
        fun easterSunday(year: Int): LocalDate {
            require(year >= 1583) { "calendário gregoriano começa em 1583" }
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = ((h + l - 7 * m + 114) % 31) + 1
            return LocalDate(year, month, day)
        }
    }
}
