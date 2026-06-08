package br.com.codecacto.kmplib.ads.router

import kotlinx.serialization.Serializable

/**
 * Configuracao remota que decide qual provider mostrar pra um app.
 *
 * Documento no Firestore em `app_ad_configs/{appId}`:
 * ```json
 * {
 *   "banner": "custom",
 *   "interstitial": "admob",
 *   "appOpen": "admob",
 *   "version": 1
 * }
 * ```
 *
 * `version` e bump-ado a cada save no admin — util pra debug e pra forcar
 * invalidacao de cache se algum dia houver.
 *
 * `appOpen` so suporta ADMOB ou OFF hoje (nao existe `CustomAppOpenAd`).
 * Se o admin gravar `custom`, o tratamento e equivalente a OFF.
 */
@Serializable
data class AdRouting(
    val banner: AdProvider = AdProvider.OFF,
    val interstitial: AdProvider = AdProvider.OFF,
    val appOpen: AdProvider = AdProvider.OFF,
    val version: Long = 0L,
    /** Millis UTC do ultimo save no admin. Nao usado pela lib em runtime, mas
     * declarado pra evitar falha de desserializacao quando o doc tem o campo. */
    val updatedAt: Long = 0L,
) {
    companion object {
        /** Default conservador — admin precisa explicitamente habilitar cada formato. */
        val OFF = AdRouting(
            banner = AdProvider.OFF,
            interstitial = AdProvider.OFF,
            appOpen = AdProvider.OFF,
        )

        /** Atalho pra apps que so usam Custom Ads. App open continua off
         * porque Custom Ads nao tem variante app_open. */
        val ALL_CUSTOM = AdRouting(
            banner = AdProvider.CUSTOM,
            interstitial = AdProvider.CUSTOM,
            appOpen = AdProvider.OFF,
        )

        /** Atalho pra apps que so usam AdMob. */
        val ALL_ADMOB = AdRouting(
            banner = AdProvider.ADMOB,
            interstitial = AdProvider.ADMOB,
            appOpen = AdProvider.ADMOB,
        )
    }
}
