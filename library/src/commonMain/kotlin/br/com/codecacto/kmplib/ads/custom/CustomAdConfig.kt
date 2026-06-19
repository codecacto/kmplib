package br.com.codecacto.kmplib.ads.custom

import io.ktor.client.HttpClient

/**
 * Configuracao do [CustomAdManager].
 *
 * A partir da kmplib 2.38.0 os house ads sao identificados por **projeto + superficie**: informe o
 * [projectSlug] do app e a [surface] (default "app"); o [CustomAdManager.initialize] constroi sozinho
 * um [RestCustomAdSource] que chama `GET {base}/public/ads?project={projectSlug}&surface={surface}`.
 * Voce pode tambem passar um `source` explicito no `initialize` (ex.: fake nos testes).
 *
 * @property projectSlug slug do projeto no catalogo central. Vai como `?project=` no REST —
 *   OBRIGATORIO para a fonte REST.
 * @property surface superficie/contexto onde os anuncios aparecem (ex.: "app", "home"). Default
 *   "app". Vai como `?surface=` no REST.
 * @property httpClient `HttpClient` (Ktor) do app, usado pela fonte REST (o mesmo de
 *   feedback/developer). Quando null (e sem `source` explicito no `initialize`), nenhum anuncio e
 *   carregado.
 * @property appsApiBaseUrl base URL do apps-api (sem barra final). Default de producao.
 * @property onImpression callback opcional disparado quando um anuncio aparece na tela.
 * @property onClick callback opcional disparado quando o usuario clica no anuncio
 *   (chamado antes de abrir a URL).
 */
data class CustomAdConfig(
    val projectSlug: String? = null,
    val surface: String = DEFAULT_SURFACE,
    val httpClient: HttpClient? = null,
    val appsApiBaseUrl: String = RestCustomAdSource.DEFAULT_APPS_API_BASE_URL,
    val onImpression: ((CustomAd) -> Unit)? = null,
    val onClick: ((CustomAd) -> Unit)? = null,
) {
    companion object {
        const val DEFAULT_SURFACE = "app"
    }
}
