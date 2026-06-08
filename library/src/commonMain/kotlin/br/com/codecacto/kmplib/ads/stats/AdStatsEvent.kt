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
    APP_OPEN,
}

/** Qual backend exibiu o anuncio. */
enum class AdProviderTag {
    ADMOB,
    CUSTOM,
}

/**
 * Identifica unicamente um "stat bucket" no Firestore.
 *
 * Custom ads sao agregados por `adId` (cada anuncio tem suas proprias metricas).
 * AdMob nao tem adId individual visivel pro app — agregamos por
 * `provider+appId+format` (todos os impressions de banner do app X virao
 * num bucket so).
 *
 * Doc ID resultante:
 *   `custom__<appId>__banner__<adId>__<YYYY-MM-DD>`
 *   `admob__<appId>__banner__all__<YYYY-MM-DD>`
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
