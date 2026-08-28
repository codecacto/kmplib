package br.com.codecacto.kmplib.ads.stats

/** Tipo de evento agregado pelo [AdStats]. */
enum class AdEventType {
    IMPRESSION,
    CLICK,
}

/** Formato do anuncio. */
enum class AdFormat {
    BANNER,
    INTERSTITIAL,
}

/** Qual backend exibiu o anuncio. Hoje so existem house ads (CUSTOM). */
enum class AdProviderTag {
    CUSTOM,
}

/**
 * Identifica unicamente um "stat bucket" no agregado por dia.
 *
 * House ads (CUSTOM) sao agregados por `adId` (cada anuncio tem suas proprias metricas).
 *
 * Doc ID resultante:
 *   `custom__<appId>__banner__<adId>__<YYYY-MM-DD>`
 */
internal fun buildStatKey(
    provider: AdProviderTag,
    appId: String,
    format: AdFormat,
    adId: String?,
    day: String,
): String {
    val safeAppId = appId.ifBlank { "unknown" }
    val safeAdId = adId?.ifBlank { null } ?: "all"
    val providerStr = provider.name.lowercase()
    val formatStr = format.name.lowercase()
    return "${providerStr}__${safeAppId}__${formatStr}__${safeAdId}__${day}"
}
