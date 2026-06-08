package br.com.codecacto.kmplib.ads.custom

import kotlinx.serialization.Serializable

/**
 * Anuncio "house" (proprio do app) carregado do Firestore.
 *
 * Schema esperado em cada documento da colecao (default `custom_ads`):
 * ```json
 * {
 *   "appId": "meu-app",
 *   "imageUrl": "https://.../banner.png",
 *   "targetUrl": "https://meusite.com/produto",
 *   "format": "banner",
 *   "placementId": "home_top",
 *   "active": true,
 *   "priority": 10,
 *   "startsAt": 1735689600000,
 *   "endsAt": 1738368000000,
 *   "title": "",
 *   "ctaLabel": "Saiba mais"
 * }
 * ```
 *
 * @property appId identificador do app dono do anuncio. Quando o consumidor
 *   passa `CustomAdConfig.appId`, so anuncios com este `appId` aparecem.
 * @property format "banner" para [CustomBannerAd] retangular, "interstitial" para [CustomInterstitialAd].
 * @property placementId agrupa anuncios por slot (ex: "home_top", "settings_bottom").
 * @property priority maior = exibido primeiro quando ha varios ativos.
 * @property weight peso pra rotacao ponderada entre anuncios do mesmo placement.
 *   Quando ha varios anuncios ativos no mesmo slot, o composable sorteia um
 *   por peso. Default 100; valor 0 = nunca sai mas continua na lista.
 * @property startsAt timestamp millis (UTC) a partir do qual o anuncio fica ativo. null = sempre.
 * @property endsAt timestamp millis (UTC) ate o qual o anuncio fica ativo. null = sem expiracao.
 */
@Serializable
data class CustomAd(
    val id: String = "",
    val appId: String = "",
    val imageUrl: String = "",
    val targetUrl: String = "",
    val format: String = FORMAT_BANNER,
    val placementId: String = "default",
    val active: Boolean = true,
    val priority: Int = 0,
    val weight: Int = 100,
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val title: String = "",
    val ctaLabel: String = "",
    /** Millis UTC. Setado pelo admin; nao usado pela lib em runtime. */
    val createdAt: Long = 0L,
    /** Millis UTC. Setado pelo admin; nao usado pela lib em runtime. */
    val updatedAt: Long = 0L,
) {
    companion object {
        const val FORMAT_BANNER = "banner"
        const val FORMAT_INTERSTITIAL = "interstitial"
    }
}
