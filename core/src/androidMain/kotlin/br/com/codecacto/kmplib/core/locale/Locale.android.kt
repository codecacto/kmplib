package br.com.codecacto.kmplib.core.locale

import android.text.format.DateFormat
import java.math.RoundingMode
import java.text.Collator
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// `Locale.getDefault()` é o idioma/região que o sistema aplicou a ESTE processo — desde o Android 13,
// o idioma por app (Configurações → Idioma do app) já chega aqui. Nada de cache: a pessoa pode trocar
// o idioma com o processo vivo, e a Activity é recriada, mas objetos de longa vida continuariam velhos.

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
    val formato = SimpleDateFormat(platformDatePattern(skeleton), locale)
    formato.timeZone = TimeZone.getTimeZone(timeZoneId)
    return formato.format(Date(epochMillis))
}

internal actual fun platformDatePattern(skeleton: String): String =
    // `getBestDateTimePattern` é a API oficial (API 18+) do esqueleto CLDR → padrão da região.
    // Em teste de JVM (android.jar de mentira) ela devolve `null`; o fallback só existe para isso.
    DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton) ?: fallbackPattern(skeleton)

/** Padrão neutro (dd/MM/yyyy HH:mm) para quando a plataforma não responde — nunca em aparelho real. */
private fun fallbackPattern(skeleton: String): String {
    val temData = skeleton.any { it == 'd' || it == 'M' || it == 'y' }
    val temHora = skeleton.any { it == 'j' || it == 'H' || it == 'h' }
    val data = buildList {
        if ('d' in skeleton) add("dd")
        if ('M' in skeleton) add("MM")
        if ('y' in skeleton) add("yyyy")
    }.joinToString("/")
    return when {
        temData && temHora -> "$data HH:mm"
        temHora -> "HH:mm"
        else -> data
    }
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
