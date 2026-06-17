package br.com.codecacto.kmplib.ads.custom

import io.ktor.client.HttpClient

/**
 * Configuracao do [CustomAdManager].
 *
 * A partir da kmplib 2.37.0 a fonte padrao dos house ads e o backend central **apps-api** (REST,
 * [RestCustomAdSource]), nao mais o Firestore. Para isso, informe [httpClient] + [appId] (o mesmo
 * `HttpClient` ja usado por feedback/developer). Quando [httpClient] e [appId] sao fornecidos, o
 * [CustomAdManager.initialize] constroi sozinho um [RestCustomAdSource]; voce pode tambem passar um
 * `source` explicito no `initialize` (ex.: fake nos testes ou o legado `CustomAdRepository`).
 *
 * @property appId slug do app no catalogo central. Vai no path `/public/ads/app/{appId}` (REST) —
 *   OBRIGATORIO para a fonte REST. Tambem usado como filtro client-side no caminho legado.
 * @property httpClient `HttpClient` (Ktor) do app, usado pela fonte REST. Quando null (e sem
 *   `source` explicito no `initialize`), o manager nao tem fonte REST e voce DEVE passar um
 *   `source` no `initialize`.
 * @property appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 * @property collection LEGADO — nome da colecao no Firestore quando ainda se usa o
 *   `CustomAdRepository`. Ignorado pela fonte REST.
 * @property placementId se definido, o manager so busca anuncios desse placement (vai como
 *   `?placement=` no REST / filtro client-side no Firestore). Quando null, o filtro por placement
 *   fica nos composables.
 * @property onImpression callback opcional disparado quando um anuncio aparece na tela.
 * @property onClick callback opcional disparado quando o usuario clica no anuncio
 *   (chamado antes de abrir a URL).
 */
data class CustomAdConfig(
    val appId: String? = null,
    val httpClient: HttpClient? = null,
    val appsApiBaseUrl: String = RestCustomAdSource.DEFAULT_APPS_API_BASE_URL,
    val collection: String = "custom_ads",
    val placementId: String? = null,
    val onImpression: ((CustomAd) -> Unit)? = null,
    val onClick: ((CustomAd) -> Unit)? = null
)
