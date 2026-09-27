package br.com.codecacto.kmplib.core.locale

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * # Número e data no formato da REGIÃO do aparelho
 *
 * O idioma da tela é do `compose-resources`; **como se escreve um número ou uma data** é da região
 * que a pessoa configurou no sistema — e as duas coisas não andam juntas: um brasileiro em Lisboa lê
 * a tela em pt-BR e escreve `27/09/2026`; um americano lê `9/27/2026`, e `03/04` para ele é 4 de
 * março. Montar `"dd/MM/yyyy"` à mão erra metade do mundo.
 *
 * Tudo aqui vem das **APIs oficiais de cada plataforma**, que carregam o CLDR do sistema:
 * - **Android:** `java.text.DecimalFormat`/`NumberFormat` e `android.text.format.DateFormat
 *   .getBestDateTimePattern` (esqueleto CLDR → padrão da região);
 * - **iOS:** `NSNumberFormatter` e `NSDateFormatter.setLocalizedDateFormatFromTemplate`.
 *
 * **Os ESQUELETOS** (`"ddMMyyyy"`, `"jjmm"`) dizem *quais campos* entram; a plataforma decide a ordem,
 * os separadores e o relógio de 12/24 h (`j` = hora no formato que a região prefere). Ver
 * [DateSkeletons].
 *
 * Formatação para o **servidor** (ISO, ponto decimal) não passa por aqui: isto é só para a tela.
 */
object RegionalFormat {

    /** Separador decimal da região (`,` no BR e em PT, `.` nos EUA). */
    fun decimalSeparator(): Char = platformDecimalSeparator()

    /** Separador de milhar da região (`.` no BR, `,` nos EUA, espaço fino em PT/FR). */
    fun groupingSeparator(): Char = platformGroupingSeparator()

    /**
     * Número decimal para a tela: `1234.5` → `1.234,5` (BR) · `1,234.5` (US).
     *
     * @param minFractionDigits casas decimais sempre mostradas (`2` → `3,00`).
     * @param maxFractionDigits teto de casas; arredonda **meio para cima** (HALF_UP em ambas as plataformas).
     * @param grouping `false` tira o separador de milhar (ano, código, porta).
     */
    fun formatNumber(
        value: Double,
        minFractionDigits: Int = 0,
        maxFractionDigits: Int = 2,
        grouping: Boolean = true,
    ): String {
        require(minFractionDigits >= 0 && maxFractionDigits >= minFractionDigits) {
            "casas decimais inválidas: min=$minFractionDigits max=$maxFractionDigits"
        }
        return platformFormatNumber(value, minFractionDigits, maxFractionDigits, grouping)
    }

    /** Inteiro com o separador de milhar da região: `12345` → `12.345` (BR) · `12,345` (US). */
    fun formatInteger(value: Long, grouping: Boolean = true): String =
        platformFormatNumber(value.toDouble(), 0, 0, grouping)

    /**
     * Percentual para a tela a partir de uma **fração**: `0.125` → `12,5%` (BR) · `12.5%` (US) ·
     * `12,5 %` (FR). A posição e o espaço do `%` também são da região.
     */
    fun formatPercent(fraction: Double, maxFractionDigits: Int = 0): String =
        platformFormatPercent(fraction, maxFractionDigits)

    /**
     * Data de calendário (sem fuso — é a data escolhida) no formato da região.
     * [DateSkeletons.SHORT] → `27/09/2026` (BR) · `09/27/2026` (US).
     */
    fun formatDate(date: LocalDate, skeleton: String = DateSkeletons.SHORT): String =
        // Meio-dia UTC formatado em UTC: nenhum fuso do mundo empurra a data para o dia vizinho.
        platformFormatEpochMillis(date.toEpochDays() * MILLIS_PER_DAY + NOON_MILLIS, skeleton, UTC_ID)

    /**
     * Instante (epoch millis) no formato da região, no fuso [timeZone] (default: o do aparelho).
     * [DateSkeletons.SHORT_DATE_TIME] → `27/09/2026 14:05` (BR) · `09/27/2026, 2:05 PM` (US).
     */
    fun formatDateTime(
        epochMillis: Long,
        skeleton: String = DateSkeletons.SHORT_DATE_TIME,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = platformFormatEpochMillis(epochMillis, skeleton, timeZone.id)

    /** Só a hora de um instante, com o relógio de 12/24 h da região: `14:05` · `2:05 PM`. */
    fun formatTime(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = platformFormatEpochMillis(epochMillis, DateSkeletons.TIME, timeZone.id)

    /**
     * O **padrão** que a região usa para [skeleton] — `dd/MM/y` (BR), `MM/dd/y` (US), `dd.MM.y` (DE).
     * Serve para montar o placeholder de formato ([datePlaceholder]) ou validar digitação.
     */
    fun datePattern(skeleton: String = DateSkeletons.SHORT): String = platformDatePattern(skeleton)

    /**
     * Placeholder de **formato** do campo de data, na ordem da região e com a inicial de "ano" no
     * idioma da tela: `dd/mm/aaaa` (BR), `mm/dd/yyyy` (US), `dd.mm.yyyy` (DE).
     *
     * @param yearLetter a inicial de "ano" no idioma da tela (`a` em pt/es, `y` em en). O
     *   `AppDatePicker` do `kmplib-ui` já passa a do idioma certo.
     */
    fun datePlaceholder(yearLetter: Char, skeleton: String = DateSkeletons.SHORT): String =
        placeholderFromDatePattern(datePattern(skeleton), yearLetter)

    private const val MILLIS_PER_DAY: Long = 86_400_000L
    private const val NOON_MILLIS: Long = 43_200_000L
    private const val UTC_ID: String = "UTC"
}

/**
 * Esqueletos CLDR prontos (UTS #35). Não são padrões: a plataforma reordena e troca separadores.
 */
object DateSkeletons {
    /** Data numérica com ano de 4 dígitos: `27/09/2026`. */
    const val SHORT: String = "ddMMyyyy"

    /** Data com mês abreviado: `27 de set. de 2026` · `Sep 27, 2026`. */
    const val MEDIUM: String = "dMMMy"

    /** Data com mês por extenso: `27 de setembro de 2026` · `September 27, 2026`. */
    const val LONG: String = "dMMMMy"

    /** Dia e mês, sem ano: `27/09` · `9/27`. */
    const val DAY_MONTH: String = "ddMM"

    /** Hora e minuto no relógio da região: `14:05` · `2:05 PM`. */
    const val TIME: String = "jjmm"

    /** Data numérica + hora: `27/09/2026 14:05`. */
    const val SHORT_DATE_TIME: String = "ddMMyyyyjjmm"
}

/**
 * Converte um padrão de data da região (`dd/MM/y`, `M/d/yy`, `y-MM-dd`) no placeholder de FORMATO
 * que a pessoa lê: dia vira `dd`, mês `mm`, ano 4 × [yearLetter]; separadores e texto entre aspas
 * ficam como estão (as aspas saem).
 *
 * Pura — é a parte com regra, e a que os testes cobrem sem aparelho.
 */
fun placeholderFromDatePattern(pattern: String, yearLetter: Char): String {
    val out = StringBuilder()
    var i = 0
    var entreAspas = false
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\'') {
            entreAspas = !entreAspas
            i++
            continue
        }
        if (entreAspas || !c.isLetter()) {
            out.append(c)
            i++
            continue
        }
        var j = i
        while (j < pattern.length && pattern[j] == c) j++
        out.append(
            when (c) {
                'd' -> "dd"
                'M', 'L' -> "mm"
                'y', 'u', 'Y' -> yearLetter.toString().repeat(4)
                else -> pattern.substring(i, j)
            },
        )
        i = j
    }
    return out.toString()
}

internal expect fun platformDecimalSeparator(): Char

internal expect fun platformGroupingSeparator(): Char

internal expect fun platformFormatNumber(
    value: Double,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    grouping: Boolean,
): String

internal expect fun platformFormatPercent(fraction: Double, maxFractionDigits: Int): String

internal expect fun platformFormatEpochMillis(epochMillis: Long, skeleton: String, timeZoneId: String): String

internal expect fun platformDatePattern(skeleton: String): String
