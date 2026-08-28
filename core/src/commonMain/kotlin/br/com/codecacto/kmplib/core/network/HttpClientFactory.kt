package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
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
 * aplica timeouts sensatos, **pede a resposta comprimida** (`ContentEncoding`, ligado por default
 * desde 2.162.0 — ver [HttpClientOptions]), loga a requisição e deixa **opt-in** a negociação de
 * conteúdo JSON (Ktor `ContentNegotiation`).
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

    // Pede a resposta comprimida e a descomprime — ver o bloco "gzip" em [HttpClientOptions].
    // Fica DEPOIS do ContentNegotiation de propósito: o corpo chega ao desserializador já em
    // texto claro, e nenhum chamador precisa saber que houve compressão.
    if (options.installContentEncoding) {
        install(ContentEncoding) {
            // `mode` fica no default (`DecompressResponse`): o plugin manda `Accept-Encoding` e
            // descomprime a RESPOSTA, e não comprime o corpo que sobe. Comprimir requisição
            // (`Mode.All`) exigiria que todo servidor nosso aceitasse `Content-Encoding` no POST —
            // e o corpo que o app manda é pequeno; o volume está na descida.
            gzip()
            deflate()
        }
    }

    configure()
}

/**
 * Opções do [createHttpClient]. Defaults servem apps offline-first (arquétipo A) que só fazem
 * toques online best-effort no apps-api central.
 *
 * ## Log de requisição vem LIGADO (2.117.0) — e por que só no nível `INFO`
 *
 * **Regra da fábrica (fundador, ago/2026): todo projeto loga requisição.** O caso que originou a
 * regra: o app do NeuroCoreX apontava para um host inexistente (`api.` em vez de `api-`), o login
 * ficava girando até o timeout e terminava em "erro de conexão" — e o **logcat não mostrava uma
 * linha sequer**. Sem log de rede, "não funciona" e "está apontando para o lugar errado" são
 * indistinguíveis de fora, e a investigação começa do zero toda vez.
 *
 * O nível é `INFO` de propósito, e isto **não** é conservadorismo:
 * - `HEADERS` imprimiria o `Authorization` — o token de acesso inteiro no logcat, que em aparelho
 *   com depuração ligada é credencial exposta;
 * - `BODY` imprimiria o corpo do `POST /auth/login` — ou seja, **a senha em claro** — e, num produto
 *   de saúde, as respostas da avaliação.
 *
 * `INFO` dá o que a investigação precisa (método, URL, status, tempo) e nada que não deveria estar
 * ali. Quem precisa de mais em depuração local sobe para `BODY` explicitamente, ciente do que isso
 * imprime.
 *
 * ## gzip vem LIGADO (2.162.0) — o app precisa PEDIR a compressão
 *
 * O servidor comprimir não basta: sem `Accept-Encoding` na requisição, ele responde em texto
 * cru — e o `ktor-client-core` **não** traz o plugin que manda esse cabeçalho (ele mora no
 * artefato `ktor-client-encoding`, que nenhum app declarava). Medido no Cidade Conectada, em
 * produção, rota a rota:
 *
 * | rota | sem gzip | com gzip |
 * |---|---:|---:|
 * | `/v1/categories` | 26.847 B | 8.172 B |
 * | `/v1/feed?size=20` | 15.065 B | 4.815 B |
 * | `/v1/properties?size=6` | 9.308 B | 1.997 B |
 * | leque da tela Início (20 rotas) | ~108.000 B | ~34.000 B |
 *
 * **−69% de todo o tráfego JSON do app**, sem tocar em uma linha de tela. É o maior ganho por
 * esforço que a camada de rede tem, e por isso é **default** e não opção a lembrar: o buraco era
 * igual em todo app da fábrica justamente porque dependia de alguém lembrar.
 *
 * Detalhe que engana quem for medir: **OkHttp (Android) e NSURLSession (iOS) já pediam gzip
 * sozinhos** quando ninguém definia o cabeçalho — parte do ganho pode já estar acontecendo em
 * produção nesses engines. O plugin torna o comportamento **determinístico e independente do
 * engine** (CIO/Js não fazem isso), o faz aparecer no log de requisição e vale para qualquer
 * `Accept-Encoding` que o engine deixe de mandar. Não há risco de descompressão dupla: quando o
 * cliente define o cabeçalho, as duas plataformas param de descomprimir por conta própria; e
 * quando descomprimem, removem o `Content-Encoding` junto — que é o que o plugin lê para decidir.
 *
 * Desligar (`installContentEncoding = false`) só faz sentido para falar com um servidor que
 * responde `Content-Encoding` mentiroso — não é o caso de nada nosso.
 *
 * @property requestTimeoutMillis timeout total da requisição (default 30s).
 * @property connectTimeoutMillis timeout de conexão (default 15s).
 * @property socketTimeoutMillis timeout de socket/leitura (default 30s).
 * @property enableLogging liga o plugin `Logging` do Ktor. **Default `true`** — ver o bloco abaixo.
 * @property logLevel nível de log quando [enableLogging]. **Default [HttpLogLevel.INFO]** — método,
 *   URL, status e tempo. Nunca headers nem corpo por default: ver o bloco abaixo.
 * @property installContentEncoding instala `ContentEncoding` (gzip + deflate) na requisição e
 *   descomprime a resposta. **Default `true`** — ver o bloco acima.
 * @property installJsonContentNegotiation instala `ContentNegotiation` JSON (default `false` —
 *   os serviços da kmplib usam Ktor core puro; ligar em apps que fazem REST de domínio tipado).
 * @property json [Json] usado pelo `ContentNegotiation` (default [DefaultHttpClientJson]).
 */
data class HttpClientOptions(
    val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
    val enableLogging: Boolean = true,
    val logLevel: HttpLogLevel = HttpLogLevel.INFO,
    val installContentEncoding: Boolean = true,
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
