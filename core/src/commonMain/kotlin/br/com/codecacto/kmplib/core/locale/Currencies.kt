package br.com.codecacto.kmplib.core.locale

import br.com.codecacto.kmplib.core.currency.isIsoCurrencyCodeFormat
import br.com.codecacto.kmplib.core.text.foldForSearch

/**
 * Uma moeda: código **ISO 4217** (`BRL`, `USD`) + nome no idioma pedido + símbolo na região do aparelho.
 * O código vai ao servidor; nome e símbolo são só de tela.
 */
data class CurrencyInfo(val code: String, val name: String, val symbol: String)

/**
 * Moeda usada quando a região é desconhecida (`null`, `"419"`, região sem moeda própria como a
 * Antártida): **USD**, a moeda de referência internacional. De propósito NÃO é BRL — assumir Brasil
 * para quem não disse onde está é o erro que o `deviceRegion()` existe para evitar.
 */
const val FALLBACK_CURRENCY: String = "USD"

/**
 * As moedas que aparecem primeiro no seletor, depois da moeda da região do aparelho (ver
 * [Currencies.ordered]).
 */
val COMMON_CURRENCIES: List<String> = listOf("BRL", "USD", "EUR", "GBP")

/**
 * Moeda padrão da região [region] (ISO 3166-1 alfa-2, normalmente `deviceRegion()` do
 * `kmplib-platform`): BR → BRL, US → USD, PT/DE/FR/ES… → EUR, GB → GBP, JP → JPY.
 *
 * Fonte: o CLDR do sistema — Android `Currency.getInstance(Locale com a região)`, iOS
 * `NSLocale.currencyCode` de um locale com a região. Região `null`, malformada, desconhecida ou sem
 * moeda → [FALLBACK_CURRENCY] (USD), nunca BRL. Aceita minúsculas.
 */
fun defaultCurrencyForRegion(region: String?): String {
    val r = region?.trim()?.uppercase() ?: return FALLBACK_CURRENCY
    if (!isIsoCountryCode(r)) return FALLBACK_CURRENCY
    return platformCurrencyForRegion(r)?.takeIf { isIsoCurrencyCodeFormat(it) } ?: FALLBACK_CURRENCY
}

/**
 * `true` quando [code] é um código ISO 4217 **bem formado** (3 letras maiúsculas — `"brl"` NÃO vale:
 * normalize antes, se quiser aceitar) **e conhecido pela plataforma** (Android
 * `Currency.getAvailableCurrencies`, iOS `NSLocale.ISOCurrencyCodes`). Para validar o que chega do
 * formulário ou do servidor antes de gravar.
 */
fun isValidCurrencyCode(code: String?): Boolean =
    isIsoCurrencyCodeFormat(code) && code!! in platformKnownCurrencyCodes()

/**
 * # Moedas com nome traduzido — o seletor de moeda de um app global
 *
 * A lista é a das moedas **em circulação**: a moeda de cada país que o sistema conhece (sem moedas
 * históricas como o cruzeiro, nem códigos técnicos como `XAU`/`XDR`). Nomes e símbolos vêm do CLDR
 * do sistema (Android `Currency.getDisplayName`/`getSymbol`, iOS `localizedStringForCurrencyCode`/
 * `NSNumberFormatter.currencySymbol`).
 *
 * **Idioma padrão = o da TELA** ([appLanguageTag]), como em [Countries].
 */
object Currencies {

    /** Todas as moedas em circulação, com o nome em [languageTag], em ordem alfabética do nome. */
    fun all(languageTag: String = appLanguageTag()): List<CurrencyInfo> {
        val codigos = platformRegionCodes()
            .mapNotNull { platformCurrencyForRegion(it) }
            .filter { isIsoCurrencyCodeFormat(it) }
            .toSet()
        return codigos
            .mapNotNull { info(it, languageTag) }
            .sortedWith(compareBy({ foldForSearch(it.name) }, { it.code }))
    }

    /** A moeda [code] com nome e símbolo, ou `null` se a plataforma não a conhece. Aceita minúsculas. */
    fun info(code: String, languageTag: String = appLanguageTag()): CurrencyInfo? {
        val c = code.trim().uppercase()
        if (!isValidCurrencyCode(c)) return null
        // Inicial maiúscula: o CLDR do Android escreve "real brasileiro", o do iOS "Real brasileiro".
        val nome = platformCurrencyName(c, languageTag)?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercaseChar() } ?: c
        return CurrencyInfo(c, nome, RegionalFormat.currencySymbol(c))
    }

    /** Nome da moeda em [languageTag] (`"BRL"` → "Real brasileiro"), ou `null` se desconhecida. */
    fun name(code: String, languageTag: String = appLanguageTag()): String? =
        info(code, languageTag)?.name?.takeIf { it != code.trim().uppercase() }

    /**
     * Os códigos que vão no TOPO do seletor: a moeda da região [region] e depois
     * [COMMON_CURRENCIES] (BRL, USD, EUR, GBP), sem repetir. Região desconhecida → só os comuns.
     */
    fun preferredCodes(region: String?): List<String> {
        val daRegiao = region?.trim()?.uppercase()?.takeIf { isIsoCountryCode(it) }
            ?.let { platformCurrencyForRegion(it) }
            ?.takeIf { isIsoCurrencyCodeFormat(it) }
        return (listOfNotNull(daRegiao) + COMMON_CURRENCIES).distinct()
    }

    /**
     * A lista do seletor: [preferredCodes] primeiro (nessa ordem), depois o resto em ordem alfabética.
     * Pura sobre [currencies] — o teste não precisa de aparelho.
     */
    fun ordered(
        region: String?,
        currencies: List<CurrencyInfo> = all(),
        preferred: List<String> = preferredCodes(region),
    ): List<CurrencyInfo> {
        val porCodigo = currencies.associateBy { it.code }
        val topo = preferred.mapNotNull { porCodigo[it] }
        val codigosTopo = topo.map { it.code }.toSet()
        return topo + currencies.filter { it.code !in codigosTopo }
    }

    /**
     * Busca por pedaço do nome, do código ou do símbolo, ignorando acento e caixa — "real" acha o real
     * brasileiro, "eur" acha o euro, "iene" acha o iene; palavras separadas se somam.
     */
    fun search(query: String, currencies: List<CurrencyInfo> = all()): List<CurrencyInfo> {
        val termos = foldForSearch(query).split(' ').filter { it.isNotEmpty() }
        if (termos.isEmpty()) return currencies
        return currencies.filter { m ->
            val alvo = foldForSearch(m.name) + " " + m.code.lowercase() + " " + foldForSearch(m.symbol)
            termos.all { alvo.contains(it) }
        }
    }
}

internal expect fun platformCurrencyForRegion(region: String): String?

internal expect fun platformKnownCurrencyCodes(): Set<String>

internal expect fun platformCurrencyName(code: String, languageTag: String): String?

/** Códigos ISO 3166-1 alfa-2 que a plataforma conhece (a mesma fonte de [Countries]). */
internal expect fun platformRegionCodes(): List<String>
