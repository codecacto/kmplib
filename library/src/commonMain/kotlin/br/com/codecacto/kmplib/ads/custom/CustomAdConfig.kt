package br.com.codecacto.kmplib.ads.custom

/**
 * Configuracao do [CustomAdManager].
 *
 * @property collection nome da colecao no Firestore. Default: `custom_ads`.
 * @property appId se definido, o manager so observa anuncios desse `appId`
 *   (filtro client-side). Quando null, nao filtra por app.
 * @property placementId se definido, o manager so observa anuncios desse placement
 *   (filtro client-side). Quando null, observa todos e o filtro fica nos composables.
 * @property onImpression callback opcional disparado quando um anuncio aparece na tela.
 * @property onClick callback opcional disparado quando o usuario clica no anuncio
 *   (chamado antes de abrir a URL).
 */
data class CustomAdConfig(
    val collection: String = "custom_ads",
    val appId: String? = null,
    val placementId: String? = null,
    val onImpression: ((CustomAd) -> Unit)? = null,
    val onClick: ((CustomAd) -> Unit)? = null
)
