package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.isSaved
import io.ktor.client.utils.unwrapCancellationException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlin.random.Random

/**
 * Política de **nova tentativa automática** do [createHttpClient] (2.252.0) — plugin oficial do Ktor
 * `HttpRequestRetry`, ligado por default.
 *
 * ## Por que existe
 *
 * Rede móvel pisca: troca de antena, CGNAT renovando a tradução, IPv6 da operadora derrubando a
 * conexão ociosa. O caso que originou (LocAki, out/2026): cliente num moto g15 na Vivo abria o app,
 * 12–15 GETs saíam juntos e um `java.net.SocketException: Connection reset` virava erro na tela —
 * com **todos os servidores respondendo 200** nos logs do Traefik. Pelo Wi-Fi, a mesma conta
 * carregava tudo. Sem política de nova tentativa, toda piscada de transporte vira falha para o
 * usuário.
 *
 * ## O que REPETE
 *
 * - Só métodos em [methods] — default **GET, HEAD e OPTIONS** (seguros, RFC 9110 §9.2.1). Repetir
 *   não muda nada no servidor.
 * - Falha de **transporte** (`IOException`): `Connection reset`, `unexpected end of stream`,
 *   conexão recusada, DNS que falhou na troca de rede, `ConnectTimeoutException`,
 *   `SocketTimeoutException` (no iOS, todo `NSURLErrorTimedOut` chega assim) e corpo de resposta
 *   interrompido no meio.
 * - Resposta de **gateway** em [retryOnStatus] — default **502, 503 e 504**. Se o servidor mandar
 *   `Retry-After` maior que [maxDelayMillis], **não** repete: ele está dizendo que demora, e a tela
 *   não pode ficar presa esperando.
 *
 * ## O que NÃO repete, nunca
 *
 * - **POST e PATCH** — não são idempotentes: repetir um `POST` cujo primeiro envio chegou ao
 *   servidor (e só a resposta se perdeu) duplica cobrança, registro, pedido. O construtor recusa
 *   [methods] com qualquer um dos dois.
 * - **PUT e DELETE** ficam FORA do default, embora a RFC os chame de idempotentes: o `DELETE`
 *   repetido depois de um primeiro que chegou volta 404 e a tela diz "erro" para uma exclusão que
 *   deu certo; e nem todo `PUT` dos nossos backends é substituição pura. Quem tem certeza do seu
 *   contrato acrescenta em [methods].
 * - **4xx** (inclusive 401/403/404/409/429) e os demais 5xx (500 é defeito do servidor, repetir só
 *   triplica o erro no GlitchTip).
 * - **Cancelamento** (`CancellationException`): a tela saiu, o `viewModelScope` foi cancelado —
 *   nada a repetir.
 * - **`HttpRequestTimeoutException`** (o teto total da requisição estourou) e falhas
 *   **permanentes** de transporte: certificado inválido/não confiável, cleartext proibido pela
 *   política de rede, ATS no iOS, URL inválida. Repetir não muda o resultado.
 *
 * ## O teto de tempo NÃO cresce
 *
 * O `HttpRequestRetry` é instalado **depois** do `HttpTimeout`, e por isso fica **dentro** dele: o
 * [HttpClientOptions.requestTimeoutMillis] (30 s) é o orçamento da chamada **inteira, com as
 * novas tentativas**. Uma conexão que trava no 1º envio e de novo no 2º termina no mesmo instante
 * em que terminaria sem a política — nunca em 90 s. As novas tentativas só aproveitam o tempo que
 * sobra quando a falha é rápida (o caso do `Connection reset`).
 *
 * ## Espera entre tentativas
 *
 * Exponencial com *equal jitter*: a n-ésima espera é sorteada entre metade e o todo de
 * `min(maxDelayMillis, baseDelayMillis × 2^(n-1))` — 200–400 ms e depois 400–800 ms com os
 * defaults. O jitter existe porque os 12–15 GETs da abertura caem JUNTOS na mesma piscada; sem ele,
 * voltariam juntos no mesmo milissegundo.
 *
 * Cada nova tentativa sai no log de requisição (`HttpClient`, nível aviso) com método, URL, número
 * da tentativa e o tipo da falha — nunca cabeçalho nem corpo.
 *
 * @property enabled liga o plugin. **Default `true`**. [Disabled] para desligar.
 * @property maxRetries quantas tentativas **a mais** (default 2 → até 3 envios).
 * @property baseDelayMillis base da espera exponencial (default 400 ms).
 * @property maxDelayMillis teto de cada espera (default 2 s); também o maior `Retry-After` aceito.
 * @property retryOnStatus status de resposta que repetem (default 502, 503, 504). Só 5xx.
 * @property methods métodos que repetem (default GET, HEAD, OPTIONS). POST/PATCH são recusados.
 */
data class HttpRetryPolicy(
    val enabled: Boolean = true,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    val retryOnStatus: Set<Int> = DEFAULT_RETRY_ON_STATUS,
    val methods: Set<HttpMethod> = DEFAULT_METHODS,
) {
    init {
        require(maxRetries >= 0) { "maxRetries não pode ser negativo" }
        require(baseDelayMillis > 0) { "baseDelayMillis precisa ser positivo" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis não pode ser menor que baseDelayMillis" }
        require(retryOnStatus.all { it in 500..599 }) {
            "retryOnStatus só aceita 5xx — 4xx é resposta definitiva do servidor"
        }
        require(HttpMethod.Post !in methods && HttpMethod.Patch !in methods) {
            "POST e PATCH não se repetem automaticamente: duplicariam cobrança/registro"
        }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 2
        const val DEFAULT_BASE_DELAY_MILLIS: Long = 400
        const val DEFAULT_MAX_DELAY_MILLIS: Long = 2_000
        val DEFAULT_RETRY_ON_STATUS: Set<Int> = setOf(502, 503, 504)
        val DEFAULT_METHODS: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

        /** Sem nova tentativa — o comportamento de antes da 2.252.0. */
        val Disabled: HttpRetryPolicy = HttpRetryPolicy(enabled = false)
    }
}

/** A resposta [status] a um [method] merece nova tentativa? */
internal fun HttpRetryPolicy.shouldRetryResponse(
    method: HttpMethod,
    status: Int,
    retryAfterHeader: String?,
): Boolean {
    if (method !in methods || status !in retryOnStatus) return false
    // Retry-After maior que o nosso teto = o servidor avisa que vai demorar; não prender a tela.
    val retryAfterMillis = retryAfterHeader.retryAfterMillisOrNull() ?: return true
    return retryAfterMillis <= maxDelayMillis
}

/** A exceção [cause] num envio de [method] merece nova tentativa? */
internal fun HttpRetryPolicy.shouldRetryException(method: HttpMethod, cause: Throwable): Boolean =
    method in methods && isTransientTransportFailure(cause)

/**
 * Falha de transporte que uma nova tentativa pode resolver. Cancelamento — inclusive o do teto da
 * requisição, que chega como `CancellationException` com o `HttpRequestTimeoutException` dentro —
 * nunca é.
 */
internal fun isTransientTransportFailure(cause: Throwable): Boolean {
    if (cause is CancellationException) return false
    if (cause.unwrapCancellationException() is HttpRequestTimeoutException) return false
    if (cause is HttpRequestTimeoutException) return false
    if (cause !is IOException) return false
    return !isPermanentTransportFailure(cause)
}

/**
 * Falha de transporte que **não** passa sozinha (certificado, política de cleartext/ATS, URL
 * inválida). Lida pela plataforma, porque só ela conhece os tipos/códigos do engine.
 */
internal expect fun isPermanentTransportFailure(cause: Throwable): Boolean

/**
 * Espera antes da tentativa [retry] (1 = a primeira nova tentativa): exponencial com *equal
 * jitter*, nunca abaixo de um `Retry-After` aceito (≤ [HttpRetryPolicy.maxDelayMillis]).
 */
internal fun HttpRetryPolicy.retryDelayMillis(
    retry: Int,
    retryAfterHeader: String?,
    random: Random,
): Long {
    val exponent = (retry - 1).coerceIn(0, 30)
    val ceiling = (baseDelayMillis shl exponent).let { if (it <= 0) maxDelayMillis else it }
        .coerceAtMost(maxDelayMillis)
    val half = ceiling / 2
    val jittered = half + random.nextLong(ceiling - half + 1)
    val retryAfter = retryAfterHeader.retryAfterMillisOrNull()?.coerceAtMost(maxDelayMillis) ?: 0L
    return maxOf(jittered, retryAfter)
}

/** `Retry-After` em segundos (a forma de data HTTP é ignorada — cai na espera exponencial). */
private fun String?.retryAfterMillisOrNull(): Long? =
    this?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let { it * 1_000 }

/**
 * Instala o `HttpRequestRetry` com a [policy]. Precisa vir **depois** do `HttpTimeout` (ver
 * "O teto de tempo NÃO cresce" em [HttpRetryPolicy]).
 */
internal fun HttpClientConfig<*>.installRetry(
    policy: HttpRetryPolicy,
    logRetries: Boolean,
    random: Random = Random.Default,
    delay: (suspend (Long) -> Unit)? = null,
) {
    if (!policy.enabled || policy.maxRetries == 0) return
    installRetryPlugin(policy, logRetries, random, delay)
    // DEPOIS do retry = dentro dele: o corpo é lido enquanto ainda dá para repetir.
    install(ReadBodyInsideRetry) { methods = policy.methods }
}

private fun HttpClientConfig<*>.installRetryPlugin(
    policy: HttpRetryPolicy,
    logRetries: Boolean,
    random: Random,
    delay: (suspend (Long) -> Unit)?,
) {
    install(HttpRequestRetry) {
        maxRetries = policy.maxRetries
        retryIf { request, response ->
            policy.shouldRetryResponse(
                method = request.method,
                status = response.status.value,
                retryAfterHeader = response.headers[HttpHeaders.RetryAfter],
            )
        }
        retryOnExceptionIf { request, cause -> policy.shouldRetryException(request.method, cause) }
        delayMillis(respectRetryAfterHeader = false) { retry ->
            policy.retryDelayMillis(retry, response?.headers?.get(HttpHeaders.RetryAfter), random)
        }
        if (delay != null) delay(delay)
        modifyRequest { request ->
            if (logRetries) {
                val motivo = cause?.let { it.describeForLog() } ?: "HTTP ${response?.status?.value}"
                AppLogger.w(
                    RETRY_LOG_TAG,
                    "Nova tentativa $retryCount/${policy.maxRetries}: ${request.method.value} " +
                        "${redactHttpLogMessage(request.url.buildString())} — $motivo",
                )
            }
        }
    }
}

internal const val RETRY_LOG_TAG: String = "HttpClient"

/**
 * Lê o corpo INTEIRO **dentro** da janela da nova tentativa (2.252.4).
 *
 * O `HttpRequestRetry` decide com os cabeçalhos: uma vez que o `200` chega, a chamada sai do plugin e
 * o corpo é lido depois, no `HttpStatement` — fora do alcance da nova tentativa. Corpo cortado no
 * meio (conexão móvel que cai, stream HTTP/2 resetado) virava `ApiResult.Error(-1, "unexpected end
 * of stream")` com o `RESPONSE: 200` já no log — o defeito do LocAki depois de cadastrar/excluir.
 * Provado em `RestRepositoryOkHttpTest` (OkHttp real, servidor que corta o corpo).
 *
 * Instalado DEPOIS do `HttpRequestRetry` (fica dentro dele): para os métodos que repetem e só quando a
 * resposta já seria guardada em memória pelo próprio Ktor (`isSaved` — o `SaveBodyPlugin` já mantém o
 * corpo inteiro; `prepareGet { execute { } }` e downloads em fluxo NÃO são tocados), faz o mesmo
 * `HttpClientCall.save()` que o Ktor faria logo depois, só que antes da decisão de repetir. Uma falha
 * de leitura vira `IOException` e entra na política normal.
 */
internal class ReadBodyInsideRetryConfig {
    var methods: Set<HttpMethod> = HttpRetryPolicy.DEFAULT_METHODS
}

internal val ReadBodyInsideRetry = createClientPlugin("KmplibReadBodyInsideRetry", ::ReadBodyInsideRetryConfig) {
    val methods = pluginConfig.methods
    on(Send) { request ->
        val call = proceed(request)
        if (call.request.method !in methods || !call.response.isSaved) return@on call
        try {
            call.save()
        } catch (e: IllegalStateException) {
            // `SavedHttpCall` confere o Content-Length: corpo que fechou "limpo" mas curto é corte.
            if (e.message?.startsWith("Content-Length mismatch") == true) {
                throw IOException("Corpo da resposta incompleto", e)
            }
            throw e
        }
    }
}

/** Tipo + mensagem curta da falha; nada de cabeçalho nem corpo (não estão na exceção). */
private fun Throwable.describeForLog(): String {
    val tipo = this::class.simpleName ?: "IOException"
    val msg = message?.let(::redactHttpLogMessage)?.take(MAX_LOG_MESSAGE)?.replace('\n', ' ')
    return if (msg.isNullOrBlank()) tipo else "$tipo: $msg"
}

private const val MAX_LOG_MESSAGE = 160
