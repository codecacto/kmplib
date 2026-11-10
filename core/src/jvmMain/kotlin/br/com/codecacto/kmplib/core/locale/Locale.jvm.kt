package br.com.codecacto.kmplib.core.locale

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle

// Desktop (2.353.0): `Locale.getDefault()` é o idioma/região do usuário no SO (a JVM o lê do Windows na
// partida). Número, moeda e país vêm do `java.text`/`java.util` com o CLDR do JDK — o mesmo motor do
// Android. Data: esqueleto CLDR pela API oficial do JDK 19+ e, no JDK 17, pelos estilos localizados.

actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()

internal actual fun platformDecimalSeparator(): Char =
    DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

internal actual fun platformGroupingSeparator(): Char =
    DecimalFormatSymbols.getInstance(Locale.getDefault()).groupingSeparator

internal actual fun platformFormatNumber(
    value: Double,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    grouping: Boolean,
): String {
    val formato = NumberFormat.getNumberInstance(Locale.getDefault())
    formato.minimumFractionDigits = minFractionDigits
    formato.maximumFractionDigits = maxFractionDigits
    formato.isGroupingUsed = grouping
    // O default do java.text é HALF_EVEN (2,5 → 2), o do NSNumberFormatter também; as duas
    // plataformas passam a HALF_UP (2,5 → 3), que é o que a pessoa espera ler numa tela.
    (formato as? DecimalFormat)?.roundingMode = RoundingMode.HALF_UP
    return formato.format(value)
}

internal actual fun platformFormatPercent(fraction: Double, maxFractionDigits: Int): String {
    val formato = NumberFormat.getPercentInstance(Locale.getDefault())
    formato.maximumFractionDigits = maxFractionDigits
    (formato as? DecimalFormat)?.roundingMode = RoundingMode.HALF_UP
    return formato.format(fraction)
}

internal actual fun platformFormatEpochMillis(epochMillis: Long, skeleton: String, timeZoneId: String): String {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(platformDatePattern(skeleton), locale)
        .withZone(ZoneId.of(timeZoneId))
        .format(Instant.ofEpochMilli(epochMillis))
}

internal actual fun platformDatePattern(skeleton: String): String =
    bestPatternJdk19(Locale.getDefault(), skeleton) ?: styledPattern(Locale.getDefault(), skeleton)

/**
 * `DateTimeFormatterBuilder.getLocalizedDateTimePattern(template, chrono, locale)` — a API OFICIAL do
 * JDK (19+) que resolve um esqueleto CLDR no padrão da região, par do `getBestDateTimePattern` do
 * Android. Chamada por reflexão porque a lib compila com toolchain 17; em JRE 17 devolve `null`.
 */
private fun bestPatternJdk19(locale: Locale, skeleton: String): String? = runCatching {
    val metodo = DateTimeFormatterBuilder::class.java.getMethod(
        "getLocalizedDateTimePattern",
        String::class.java,
        java.time.chrono.Chronology::class.java,
        Locale::class.java,
    )
    metodo.invoke(null, canonicalTemplate(skeleton), IsoChronology.INSTANCE, locale) as? String
}.getOrNull()?.takeIf { it.isNotBlank() }

/** O esqueleto na ordem canônica que o JDK exige (ano → mês → dia → hora → minuto). */
internal fun canonicalTemplate(skeleton: String): String = buildString {
    for (letra in charArrayOf('y', 'M', 'd', 'j', 'H', 'h', 'm')) {
        val n = skeleton.count { it == letra }
        repeat(n) { append(letra) }
    }
}

/**
 * JRE 17: o padrão sai dos estilos localizados do CLDR (`FormatStyle`) — data curta para o esqueleto
 * numérico (com o ano forçado a 4 dígitos), média/longa para mês abreviado/por extenso, hora curta
 * para `jj`. Dia+mês sai da data curta sem o ano.
 */
internal fun styledPattern(locale: Locale, skeleton: String): String {
    fun data(style: FormatStyle) =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(style, null, IsoChronology.INSTANCE, locale)
    fun hora() = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
    val temData = skeleton.any { it == 'd' || it == 'M' || it == 'y' }
    val temHora = skeleton.any { it == 'j' || it == 'H' || it == 'h' }
    val temAno = 'y' in skeleton
    val meses = skeleton.count { it == 'M' }
    val parteData = when {
        !temData -> null
        meses >= 4 -> data(FormatStyle.LONG)
        meses == 3 -> data(FormatStyle.MEDIUM)
        else -> numericDate(data(FormatStyle.SHORT), temAno)
    }
    val parteHora = if (temHora) hora() else null
    return listOfNotNull(parteData, parteHora).joinToString(" ").ifBlank { "dd/MM/yyyy" }
}

/** Data curta com dia e mês de 2 dígitos e ano de 4 (ou sem ano, tirando o separador junto). */
private fun numericDate(shortPattern: String, comAno: Boolean): String {
    var p = shortPattern
        .replace(Regex("d+"), "dd")
        .replace(Regex("M+"), "MM")
    p = if (comAno) {
        p.replace(Regex("[yu]+"), "yyyy")
    } else {
        p.replace(Regex("""[^dM'\s]*[yu]+[^dM'\s]*"""), "").trim { !it.isLetter() }
    }
    return p
}

internal actual fun platformCountries(languageTag: String): List<Country> {
    val idioma = Locale.forLanguageTag(languageTag)
    val collator = Collator.getInstance(idioma).apply { strength = Collator.PRIMARY }
    return Locale.getISOCountries()
        .map { codigo -> Country(codigo, regionLocale(codigo).getDisplayCountry(idioma)) }
        .filter { it.name.isNotBlank() && it.name != it.code }
        .sortedWith { a, b -> collator.compare(a.name, b.name) }
}

internal actual fun platformCountryName(code: String, languageTag: String): String? {
    if (code !in Locale.getISOCountries()) return null
    return regionLocale(code).getDisplayCountry(Locale.forLanguageTag(languageTag))
}

/** `Locale` só com a região — pelo `Builder`, já que o construtor `Locale(String, String)` saiu de moda. */
private fun regionLocale(code: String): Locale = Locale.Builder().setRegion(code).build()

// ---- Moeda (2.303.0) -------------------------------------------------------------------------------
// `java.util.Currency` no Android é servido pelo ICU do sistema (CLDR); a escala do valor, porém, é a
// da ISO 4217 (`Money.fractionDigitsOf`) e é imposta aqui pelo min/max de casas.

internal actual fun platformFormatCurrency(minorUnits: Long, scale: Int, shownDigits: Int, code: String): String? {
    val moeda = currencyOrNull(code) ?: return null
    val formato = NumberFormat.getCurrencyInstance(Locale.getDefault())
    formato.currency = moeda
    // Depois do `currency`: trocar a moeda redefine as casas para o default dela.
    formato.minimumFractionDigits = shownDigits
    formato.maximumFractionDigits = shownDigits
    // Exato: o BigDecimal carrega a escala; nenhum Double no caminho. Quando shownDigits < scale o valor
    // é inteiro (formatMoney garante), então o arredondamento nunca muda o número.
    (formato as? DecimalFormat)?.roundingMode = RoundingMode.UNNECESSARY
    return formato.format(BigDecimal.valueOf(minorUnits, scale))
}

internal actual fun platformCurrencySymbol(code: String): String? =
    currencyOrNull(code)?.getSymbol(Locale.getDefault())

internal actual fun platformCurrencyForRegion(region: String): String? =
    try {
        Currency.getInstance(regionLocale(region))?.currencyCode
    } catch (_: IllegalArgumentException) {
        null // região que o sistema não conhece
    }

internal actual fun platformKnownCurrencyCodes(): Set<String> = knownCurrencyCodes

private val knownCurrencyCodes: Set<String> by lazy {
    Currency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode }
}

internal actual fun platformCurrencyName(code: String, languageTag: String): String? =
    currencyOrNull(code)?.getDisplayName(Locale.forLanguageTag(languageTag))

internal actual fun platformRegionCodes(): List<String> = Locale.getISOCountries().toList()

private fun currencyOrNull(code: String): Currency? =
    try {
        Currency.getInstance(code)
    } catch (_: IllegalArgumentException) {
        null
    }
