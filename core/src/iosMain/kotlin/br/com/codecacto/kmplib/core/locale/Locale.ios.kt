package br.com.codecacto.kmplib.core.locale

import platform.Foundation.ISOCountryCodes
import platform.Foundation.ISOCurrencyCodes
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterPercentStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.decimalSeparator
import platform.Foundation.groupingSeparator
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.localizedStandardCompare
import platform.Foundation.localeIdentifierFromComponents
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.localizedStringForCurrencyCode
import platform.Foundation.numberWithDouble
import platform.Foundation.preferredLanguages
import platform.Foundation.timeZoneWithName

// Idioma: `preferredLanguages` (a lista de Ajustes → Geral → Idioma e Região, já filtrada pelo
// idioma por app de Ajustes → <App> → Idioma) — a mesma fonte do compose-resources.
// Formato: `currentLocale`, que no iOS carrega a REGIÃO e as preferências de formato (calendário,
// relógio de 24 h) que a pessoa configurou — separadas do idioma, como deve ser.

actual fun deviceLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.takeIf { it.isNotBlank() }
        ?: FactoryLocales.PT_BR

internal actual fun platformDecimalSeparator(): Char =
    NSLocale.currentLocale.decimalSeparator.firstOrNull() ?: '.'

internal actual fun platformGroupingSeparator(): Char =
    NSLocale.currentLocale.groupingSeparator.firstOrNull() ?: ','

internal actual fun platformFormatNumber(
    value: Double,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    grouping: Boolean,
): String {
    val formato = NSNumberFormatter()
    formato.locale = NSLocale.currentLocale
    formato.numberStyle = NSNumberFormatterDecimalStyle
    formato.minimumFractionDigits = minFractionDigits.toULong()
    formato.maximumFractionDigits = maxFractionDigits.toULong()
    formato.usesGroupingSeparator = grouping
    // Mesmo arredondamento do Android (HALF_UP) — o default do Foundation é o do banqueiro.
    formato.roundingMode = NSNumberFormatterRoundHalfUp
    return formato.stringFromNumber(NSNumber.numberWithDouble(value)) ?: value.toString()
}

internal actual fun platformFormatPercent(fraction: Double, maxFractionDigits: Int): String {
    val formato = NSNumberFormatter()
    formato.locale = NSLocale.currentLocale
    formato.numberStyle = NSNumberFormatterPercentStyle
    formato.maximumFractionDigits = maxFractionDigits.toULong()
    formato.roundingMode = NSNumberFormatterRoundHalfUp
    return formato.stringFromNumber(NSNumber.numberWithDouble(fraction)) ?: "${fraction * 100}%"
}

internal actual fun platformFormatEpochMillis(epochMillis: Long, skeleton: String, timeZoneId: String): String {
    val formato = NSDateFormatter()
    formato.locale = NSLocale.currentLocale
    NSTimeZone.timeZoneWithName(timeZoneId)?.let { formato.timeZone = it }
    formato.setLocalizedDateFormatFromTemplate(skeleton)
    return formato.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}

internal actual fun platformDatePattern(skeleton: String): String =
    NSDateFormatter.dateFormatFromTemplate(skeleton, 0u, NSLocale.currentLocale) ?: "dd/MM/yyyy"

internal actual fun platformCountries(languageTag: String): List<Country> {
    val idioma = localeFor(languageTag)
    return NSLocale.ISOCountryCodes
        .mapNotNull { it as? String }
        .mapNotNull { codigo ->
            idioma.localizedStringForCountryCode(codigo)?.let { nome -> Country(codigo, nome) }
        }
        // `localizedStandardCompare` é a ordenação "como o Finder" do locale ATUAL do aparelho — acento
        // e caixa tratados como a pessoa espera (Áustria junto de Austrália). Pode diferir de
        // [languageTag] só na colação fina de alguns idiomas; os nomes em si saem em [languageTag].
        .sortedWith { a, b -> (a.name as NSString).localizedStandardCompare(b.name).toInt() }
}

internal actual fun platformCountryName(code: String, languageTag: String): String? {
    val conhecidos = NSLocale.ISOCountryCodes.mapNotNull { it as? String }
    if (code !in conhecidos) return null
    return localeFor(languageTag).localizedStringForCountryCode(code)
}

/** `NSLocale` do idioma pedido; a tag BCP 47 (`pt-BR`) serve de identificador (`pt_BR` também). */
private fun localeFor(languageTag: String): NSLocale =
    NSLocale.localeWithLocaleIdentifier(languageTag.replace('-', '_'))

// ---- Moeda (2.303.0) -------------------------------------------------------------------------------
// NSNumberFormatter de estilo moeda no `currentLocale` (região e preferências de formato do aparelho);
// a escala do valor é a da ISO 4217 (`Money.fractionDigitsOf`) e é imposta pelo min/max de casas.

internal actual fun platformFormatCurrency(minorUnits: Long, scale: Int, shownDigits: Int, code: String): String? {
    if (code !in knownCurrencyCodes) return null
    val formato = currencyFormatter(code)
    // Depois do `currencyCode`: trocar a moeda redefine as casas para o default dela.
    formato.minimumFractionDigits = shownDigits.toULong()
    formato.maximumFractionDigits = shownDigits.toULong()
    return formato.stringFromNumber(exactDecimal(minorUnits, scale))
}

internal actual fun platformCurrencySymbol(code: String): String? {
    if (code !in knownCurrencyCodes) return null
    return currencyFormatter(code).currencySymbol
}

internal actual fun platformCurrencyForRegion(region: String): String? {
    if (region !in NSLocale.ISOCountryCodes.mapNotNull { it as? String }) return null
    // Locale só com a região (idioma neutro): a moeda de um locale vem da REGIÃO dele.
    val id = NSLocale.localeIdentifierFromComponents(mapOf<Any?, Any?>(NSLocaleCountryCode to region))
    return NSLocale.localeWithLocaleIdentifier(id).objectForKey(NSLocaleCurrencyCode) as? String
}

internal actual fun platformKnownCurrencyCodes(): Set<String> = knownCurrencyCodes

private val knownCurrencyCodes: Set<String> by lazy {
    NSLocale.ISOCurrencyCodes.mapNotNull { it as? String }.toSet()
}

internal actual fun platformCurrencyName(code: String, languageTag: String): String? =
    localeFor(languageTag).localizedStringForCurrencyCode(code)

internal actual fun platformRegionCodes(): List<String> = NSLocale.ISOCountryCodes.mapNotNull { it as? String }

private fun currencyFormatter(code: String): NSNumberFormatter {
    val formato = NSNumberFormatter()
    formato.locale = NSLocale.currentLocale
    formato.numberStyle = NSNumberFormatterCurrencyStyle
    formato.currencyCode = code
    return formato
}

/** `minorUnits × 10^-scale` exato (mantissa inteira + expoente), sem passar por Double. */
private fun exactDecimal(minorUnits: Long, scale: Int): NSDecimalNumber {
    val negativo = minorUnits < 0
    // |Long.MIN_VALUE| não cabe em Long, mas cabe em ULong.
    val modulo: ULong = if (negativo) (-(minorUnits + 1)).toULong() + 1u else minorUnits.toULong()
    return NSDecimalNumber(mantissa = modulo, exponent = (-scale).toShort(), isNegative = negativo)
}
