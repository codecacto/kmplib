package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.core.locale.FactoryLocales
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
 * @property supportedLanguages idiomas que o APP traduz (2.345.0): `en`, `es`, `pt-PT`, `pt-BR`. O anúncio
 *   segue o idioma do aparelho só se ele estiver aqui; senão, pt-BR. Default só pt-BR: o app não manda
 *   `lang` e a requisição fica como antes. App global declara os idiomas que tem, e fora do pt-BR
 *   recebe só arte daquele idioma (sem arte, sem anúncio).
 * @property onImpression callback opcional disparado quando um anuncio aparece na tela.
 * @property onClick callback opcional disparado quando o usuario clica no anuncio
 *   (chamado antes de abrir a URL).
 */
data class CustomAdConfig(
    val projectSlug: String? = null,
    val surface: String = DEFAULT_SURFACE,
    val httpClient: HttpClient? = null,
    val appsApiBaseUrl: String = RestCustomAdSource.DEFAULT_APPS_API_BASE_URL,
    val supportedLanguages: Set<String> = DEFAULT_SUPPORTED_LANGUAGES,
    val onImpression: ((CustomAd) -> Unit)? = null,
    val onClick: ((CustomAd) -> Unit)? = null,
) {
    companion object {
        const val DEFAULT_SURFACE = "app"
        val DEFAULT_SUPPORTED_LANGUAGES: Set<String> = setOf(FactoryLocales.PT_BR)
    }
}
