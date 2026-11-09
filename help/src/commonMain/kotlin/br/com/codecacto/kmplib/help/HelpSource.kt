package br.com.codecacto.kmplib.help

import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.HttpLogLevel
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.core.network.handleApiCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Configuração da ajuda do produto, servida pelo apps-api central.
 *
 * @param projectSlug slug do projeto no Nexus (o `project=` das rotas).
 * @param perfil perfil de quem está logado (`cliente`, `profissional`…). Vídeos e fluxos são filtrados por
 *   ele; `null` não manda o parâmetro.
 * @param appsApiBaseUrl base pública do apps-api, sem barra final. Default de produção.
 * @param language tag BCP 47 do conteúdo. `null` (default) = o idioma em que a tela está
 *   ([appLanguageTag]), relido a cada chamada.
 */
data class HelpConfig(
    val projectSlug: String,
    val perfil: String? = null,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
    val language: String? = null,
) {
    internal fun languageTag(): String = language ?: appLanguageTag()

    /** Chave de cache e de ViewModel: a mesma para a mesma combinação de conteúdo. */
    internal fun contentKey(): String = "$appsApiBaseUrl|$projectSlug|${perfil.orEmpty()}|${languageTag()}"

    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}

/** Limites da pergunta no contrato de `POST /public/ajuda/perguntar`. */
const val HELP_QUESTION_MIN_LENGTH: Int = 3
const val HELP_QUESTION_MAX_LENGTH: Int = 300

/** Superfície fixa desta lib: a web usa a weblib, com `surface=web`. */
private const val SURFACE_APP = "app"

/** De onde vem o conteúdo de ajuda. Separado da tela para poder ser substituído em teste. */
interface HelpSource {
    suspend fun loadCatalog(): ApiResult<HelpCatalog>

    suspend fun ask(question: String): ApiResult<HelpAnswer>
}

/** Sem sessão de quem está logado, sem cookie e com log no máximo INFO: as rotas são públicas. */
internal val HelpHttpOptions: HttpClientOptions = HttpClientOptions(
    logLevel = HttpLogLevel.INFO,
    subscriptionBlock = null,
    accountDeletion = null,
)

/** Cliente próprio do módulo, um por processo: credencial do app nunca chega às rotas públicas da ajuda. */
private val helpHttpClient: HttpClient by lazy { createHttpClient(HelpHttpOptions) }

/** Implementação sobre as rotas públicas do apps-api. Nenhuma das duas chamadas lança, salvo cancelamento. */
class RestHelpSource internal constructor(
    private val config: HelpConfig,
    private val httpClient: HttpClient,
) : HelpSource {

    constructor(config: HelpConfig) : this(config, helpHttpClient)

    override suspend fun loadCatalog(): ApiResult<HelpCatalog> {
        val result = handleApiCall {
            httpClient.get(url(PATH_CATALOG)) {
                expectSuccess = true
                parameter("project", config.projectSlug)
                config.perfil?.let { parameter("perfil", it) }
                parameter("surface", SURFACE_APP)
                parameter("lang", config.languageTag())
            }.bodyAsText()
        }
        return parsed(result, ::parseHelpCatalog)
    }

    override suspend fun ask(question: String): ApiResult<HelpAnswer> {
        val body = HelpJson.encodeToString(
            HelpAskRequest.serializer(),
            HelpAskRequest(
                project = config.projectSlug,
                perfil = config.perfil,
                surface = SURFACE_APP,
                lang = config.languageTag(),
                pergunta = question.trim().take(HELP_QUESTION_MAX_LENGTH),
            ),
        )
        val result = handleApiCall {
            httpClient.post(url(PATH_ASK)) {
                expectSuccess = true
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
        }
        return parsed(result, ::parseHelpAnswer)
    }

    private fun url(path: String): String = config.appsApiBaseUrl.trimEnd('/') + path

    private fun <T : Any> parsed(result: ApiResult<String>, parse: (String) -> T?): ApiResult<T> = when (result) {
        is ApiResult.Success -> parse(result.data)?.let { ApiResult.Success(it) }
            ?: ApiResult.Error(code = -1, message = "Resposta inválida do servidor")
        is ApiResult.Error -> result
        ApiResult.Loading -> ApiResult.Loading
    }

    private companion object {
        const val PATH_CATALOG = "/public/ajuda"
        const val PATH_ASK = "/public/ajuda/perguntar"
    }
}
