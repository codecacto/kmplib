package br.com.codecacto.kmplib.firebase.ads

/**
 * IDs de ad units para Android e iOS.
 */
data class AdUnitIds(
    val android: String,
    val ios: String
)

/**
 * Configuração completa de ads para o app.
 *
 * @param banner IDs do banner ad
 * @param interstitial IDs do interstitial ad
 * @param appOpen IDs do app open ad (opcional)
 * @param testMode Se true, usa IDs de teste
 * @param remoteConfigKey Chave no Firebase Remote Config para habilitar/desabilitar ads
 */
data class AdConfig(
    val banner: AdUnitIds,
    val interstitial: AdUnitIds,
    val appOpen: AdUnitIds? = null,
    val testMode: Boolean = false,
    val remoteConfigKey: String = "admob_enabled"
)
