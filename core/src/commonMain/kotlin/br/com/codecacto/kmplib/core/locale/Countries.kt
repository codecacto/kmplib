package br.com.codecacto.kmplib.core.locale

import br.com.codecacto.kmplib.core.text.foldForSearch

/**
 * Um país: código **ISO 3166-1 alfa-2** (`BR`, `PT`, `US`) + o nome no idioma pedido.
 *
 * O código é o que vai para o servidor e para o banco; o nome é só de tela — muda com o idioma.
 */
data class Country(val code: String, val name: String) {
    /** A bandeira em emoji (indicadores regionais Unicode), ou `""` se o código não for 2 letras. */
    val flag: String get() = countryFlagEmoji(code)
}

/**
 * # Países com nome traduzido — o seletor de país de um app global
 *
 * A lista e os nomes vêm do **CLDR embarcado no sistema**, não de uma tabela nossa que envelheceria
 * (país muda de nome — "Turquia" virou "Türkiye" na ONU em 2022 —, e o sistema acompanha):
 * - **Android:** `Locale.getISOCountries()` + `Locale.getDisplayCountry(locale)`, ordenados por
 *   `java.text.Collator` do idioma;
 * - **iOS:** `NSLocale.ISOCountryCodes` + `NSLocale.localizedStringForCountryCode`, ordenados por
 *   `localizedStandardCompare` (colação do locale do aparelho).
 *
 * **Ordem alfabética DO IDIOMA**, não a do código Unicode: "Áustria" fica junto de "Austrália", não
 * depois de "Zâmbia"; em espanhol "Ñ" vem depois de "N".
 *
 * **Idioma padrão = o da TELA** ([appLanguageTag]), não o do aparelho: num aparelho em francês a tela
 * está em pt-BR, e o seletor tem de estar também.
 */
object Countries {

    /**
     * Todos os países que a plataforma conhece, com o nome em [languageTag] e na ordem alfabética
     * desse idioma. Códigos que não são país (`AA`, `ZZ`, regiões numéricas) não entram.
     */
    fun all(languageTag: String = appLanguageTag()): List<Country> =
        platformCountries(languageTag)
            .filter { isIsoCountryCode(it.code) && it.name.isNotBlank() }

    /**
     * Nome do país [code] em [languageTag] (`"PT"` → "Portugal"), ou `null` se [code] não for um
     * código ISO 3166-1 alfa-2 que a plataforma conheça. Aceita minúsculas.
     */
    fun name(code: String, languageTag: String = appLanguageTag()): String? {
        val normalizado = code.trim().uppercase()
        if (!isIsoCountryCode(normalizado)) return null
        return platformCountryName(normalizado, languageTag)
            ?.takeIf { it.isNotBlank() && !it.equals(normalizado, ignoreCase = true) }
    }

    /** Busca por pedaço do nome ou pelo código, ignorando acento e caixa ("bras" acha Brasil). */
    fun search(query: String, countries: List<Country> = all()): List<Country> {
        val termos = foldForSearch(query).split(' ').filter { it.isNotEmpty() }
        if (termos.isEmpty()) return countries
        return countries.filter { pais ->
            val alvo = foldForSearch(pais.name) + " " + pais.code.lowercase()
            termos.all { alvo.contains(it) }
        }
    }
}

/** `true` para exatamente duas letras maiúsculas A–Z. */
fun isIsoCountryCode(code: String): Boolean = code.length == 2 && code.all { it in 'A'..'Z' }

/**
 * Bandeira em emoji a partir do código ISO: cada letra vira o "indicador regional" Unicode
 * correspondente (U+1F1E6 + deslocamento) e o sistema desenha a bandeira. `""` para código inválido.
 */
fun countryFlagEmoji(code: String): String {
    val c = code.trim().uppercase()
    if (!isIsoCountryCode(c)) return ""
    return buildString {
        c.forEach { letra ->
            val ponto = REGIONAL_INDICATOR_A + (letra - 'A')
            // Fora do plano básico: par substituto UTF-16, escrito à mão (sem Character.toChars no common).
            val v = ponto - 0x10000
            append(((v ushr 10) + 0xD800).toChar())
            append(((v and 0x3FF) + 0xDC00).toChar())
        }
    }
}

private const val REGIONAL_INDICATOR_A: Int = 0x1F1E6

internal expect fun platformCountries(languageTag: String): List<Country>

internal expect fun platformCountryName(code: String, languageTag: String): String?
