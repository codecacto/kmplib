package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel

/**
 * Factory multiplataforma de [HttpClient] Ktor — padrão-ouro do ecossistema para **não** repetir o
 * boilerplate de montar cliente HTTP por app (GAP-NS-M-03; promovido do `HttpClientFactory` local do
 * Números da Sorte, confirmado em ≥2 apps offline do arquétipo A).
 *
 * Usa o **engine oficial recomendado de cada plataforma** (OkHttp no Android, Darwin no iOS/K/N),
 * aplica timeouts sensatos e deixa **opcionais e opt-in** o logging (Ktor `Logging`) e a negociação
 * de conteúdo JSON (Ktor `ContentNegotiation`).
 *
 * **ContentNegotiation vem DESLIGADO por padrão** de propósito: os serviços centrais da kmplib
 * (`FeedbackService`/`ContactService`/`DeveloperInfoService`/`AppUpdateService` etc.) serializam
 * manualmente (Ktor core puro) e não exigem o plugin. Apps do arquétipo B/D que fazem REST de
 * domínio ligam `installJsonContentNegotiation = true`.
 *
 * Uso típico (app offline-first, só toques online no apps-api central):
 * ```kotlin
 * single<HttpClient> { createHttpClient() }
 * ```
 * App com REST de domínio + log em debug:
 * ```kotlin
 * createHttpClient(
 *     HttpClientOptions(enableLogging = BuildInfo.isDebug, installJsonContentNegotiation = true),
 * )
 * ```
 * Config avançada (interceptors/headers default) pelo bloco [configure]:
 * ```kotlin
 * createHttpClient { install(DefaultRequest) { header("X-App", "meu-app") } }
 * ```
 */
fun createHttpClient(
    options: HttpClientOptions = HttpClientOptions(),
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(createPlatformHttpClientEngine()) {
    // Deixa 4xx/5xx virarem resposta normal — quem trata é o `handleApiCall`/serviço chamador.
    expectSuccess = false

    install(HttpTimeout) {
        requestTimeoutMillis = options.requestTimeoutMillis
        connectTimeoutMillis = options.connectTimeoutMillis
        socketTimeoutMillis = options.socketTimeoutMillis
    }

    if (options.enableLogging) {
        install(Logging) {
            level = options.logLevel.toKtorLogLevel()
            logger = AppLoggerKtorLogger
        }
    }

    if (options.installJsonContentNegotiation) {
        install(ContentNegotiation) { json(options.json) }
    }

    configure()
}

/**
 * Opções do [createHttpClient]. Defaults servem apps offline-first (arquétipo A) que só fazem
 * toques online best-effort no apps-api central.
 *
 * @property requestTimeoutMillis timeout total da requisição (default 30s).
 * @property connectTimeoutMillis timeout de conexão (default 15s).
 * @property socketTimeoutMillis timeout de socket/leitura (default 30s).
 * @property enableLogging liga o plugin `Logging` do Ktor (default `false`; ligar só em debug).
 * @property logLevel nível de log quando [enableLogging] (default [HttpLogLevel.HEADERS]).
 * @property installJsonContentNegotiation instala `ContentNegotiation` JSON (default `false` —
 *   os serviços da kmplib usam Ktor core puro; ligar em apps que fazem REST de domínio tipado).
 * @property json [Json] usado pelo `ContentNegotiation` (default [DefaultHttpClientJson]).
 */
data class HttpClientOptions(
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
    val enableLogging: Boolean = false,
    val logLevel: HttpLogLevel = HttpLogLevel.HEADERS,
    val installJsonContentNegotiation: Boolean = false,
    val json: Json = DefaultHttpClientJson,
) {
    companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 30_000
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 15_000
        const val DEFAULT_SOCKET_TIMEOUT_MILLIS: Long = 30_000
    }
}

/** Nível de log do factory, mapeado 1:1 para o `LogLevel` do Ktor (sem vazar o tipo do Ktor na API). */
enum class HttpLogLevel { NONE, INFO, HEADERS, BODY, ALL }

/**
 * [Json] tolerante padrão para o factory (e reutilizável por serviços): ignora campos desconhecidos,
 * é leniente, não codifica defaults e omite nulos — o mesmo shape adotado pelos serviços da lib.
 */
val DefaultHttpClientJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun HttpLogLevel.toKtorLogLevel(): KtorLogLevel = when (this) {
    HttpLogLevel.NONE -> KtorLogLevel.NONE
    HttpLogLevel.INFO -> KtorLogLevel.INFO
    HttpLogLevel.HEADERS -> KtorLogLevel.HEADERS
    HttpLogLevel.BODY -> KtorLogLevel.BODY
    HttpLogLevel.ALL -> KtorLogLevel.ALL
}

private val AppLoggerKtorLogger: Logger = object : Logger {
    override fun log(message: String) = AppLogger.d("HttpClient", message)
}

/**
 * Engine Ktor da plataforma (OkHttp no Android, Darwin no iOS). Retorna uma **instância** de engine
 * — passar a instância ao construtor `HttpClient(engine) { ... }` habilita o overload de config
 * star-projected, permitindo montar toda a configuração em `commonMain`.
 */
internal expect fun createPlatformHttpClientEngine(): HttpClientEngine
