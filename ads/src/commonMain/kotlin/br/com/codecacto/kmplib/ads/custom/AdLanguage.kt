package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.core.locale.FactoryLocales

/**
 * O idioma do anúncio: o da TELA do app, não o do aparelho sozinho (2.345.0).
 *
 * O app segue o idioma do aparelho, mas só quando tem aquela tradução: app só em português num aparelho
 * em inglês abre em português, e anúncio em inglês ali seria mentira. Por isso o idioma do aparelho só
 * vale se [supported] o declara. Qualquer outro caso é pt-BR, a pasta `values` de todo app da fábrica.
 *
 * Casa como o `compose-resources` ([FactoryLocales.match]): `en-US`→`en`, `es-MX`→`es`, `pt-PT`→`pt-PT`,
 * `pt-BR`/`pt`→`pt-BR`. A lista declarada passa pela mesma régua, então `en-US` nela vale como `en`.
 */
internal fun resolveAdLanguage(deviceLanguageTag: String?, supported: Set<String>): String =
    FactoryLocales.match(deviceLanguageTag, supported.map { FactoryLocales.match(it) })

/**
 * O valor de `?lang=` para [language]. Em pt-BR é `null`: a requisição fica idêntica à de um app sem
 * idioma, e é o contrato do `/public/ads` (sem `lang` = pt-BR).
 */
internal fun adLanguageQueryValue(language: String): String? =
    language.takeUnless { it == FactoryLocales.PT_BR }
