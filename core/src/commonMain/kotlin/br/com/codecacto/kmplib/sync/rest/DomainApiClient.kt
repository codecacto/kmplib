package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.firebase.auth.IAuthRepository
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.monetization.entitlement.parseQuotaExceeded
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cliente HTTP do **backend REST-CRUD de domínio** de um app (`/v1/...`) — a base de rede da camada
 * offline-first REST-CRUD ([OfflineFirstRestRepository]/[RestCrudSyncEngine]/[RestUploadQueue]).
 *
 * Promovido do padrão que a Onda 3 do onboarding replicava app a app (piloto MinhasHoras): a
 * [SyncEngine][br.com.codecacto.kmplib.sync.SyncEngine] `/pull`+`/push` (que continua para backends
 * com esse protocolo) **não encaixa** num backend REST-CRUD comum (`GET/POST/PUT/PATCH/DELETE` por
 * recurso). Este cliente é a peça de transporte desse cenário.
 *
 * ### Padrão-ouro (idêntico aos demais serviços REST da lib — `AdminApiEntitlementRepository`/`RestSyncPort`)
 * - **`ktor-client-core` puro** (SEM `ContentNegotiation`): os repositórios (de)serializam o payload
 *   com kotlinx-json. Use o [createHttpClient][br.com.codecacto.kmplib.core.network.createHttpClient]
 *   da lib (`expectSuccess=false`, para 4xx/5xx virarem resposta classificável aqui).
 * - **Bearer Firebase** (`Authorization: Bearer <ID token>`) via [DomainTokenProvider].
 *
 * ### Resiliência (memória `app-baseline-resilience-ux`)
 *  - **401 → refresh de token** (`token(forceRefresh = true)`) e **1 retry** automático.
 *  - **402 → [DomainResult.Quota]** (paywall) — extrai o [QuotaExceeded] do corpo (`error.details`).
 *  - **429 → [DomainResult.Error]** amigável de rate-limit (semântica distinta da cota do plano).
 *  - Erros de rede/transporte **nunca lançam** para a UI: viram [DomainResult.Error] com
 *    `code == `[DomainResult.OFFLINE_CODE].
 *
 * ### Escopo de host (lição MeuFrete — nunca vazar token para outro host)
 * O token só é anexado às requisições **deste** cliente, cujo destino é sempre `baseUrl + path`
 * (host fixado na construção). O cliente **não instala** um `Authorization` default no [HttpClient],
 * então um `HttpClient` compartilhado com outros hosts nunca recebe o Bearer de domínio. Passe sempre
 * caminhos **relativos** ([getJson]/[postJson]/...); um caminho absoluto para outro host é rejeitado
 * ([DomainResult.Error] sem token anexado).
 */
class DomainApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: DomainTokenProvider,
    baseUrl: String,
    private val texts: DomainApiTexts = DomainApiTexts(),
) {
    private val root: String = baseUrl.trimEnd('/')
    private val host: String = runCatching { Url(root).host }.getOrDefault("")

    /** Conveniência: constrói sobre o [IAuthRepository] da lib (Bearer = Firebase ID token). */
    constructor(
        httpClient: HttpClient,
        auth: IAuthRepository,
        baseUrl: String,
        texts: DomainApiTexts = DomainApiTexts(),
    ) : this(httpClient, auth.asDomainTokenProvider(), baseUrl, texts)

    private fun url(path: String): String = root + "/" + path.trimStart('/')

    /** Garante que o path é do mesmo host (nunca vaza o Bearer). */
    private fun sameHost(path: String): Boolean {
        val p = path.trim()
        if (!p.startsWith("http://", true) && !p.startsWith("https://", true)) return true
        return runCatching { Url(p).host == host }.getOrDefault(false)
    }

    // ---- JSON ------------------------------------------------------------

    suspend fun getJson(path: String): DomainResult<String> =
        execute(path) { token -> httpClient.get(url(path)) { bearer(token) } }.texto()

    suspend fun postJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.post(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    suspend fun putJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.put(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    suspend fun patchJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.patch(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    suspend fun delete(path: String): DomainResult<Unit> =
        execute(path) { token -> httpClient.delete(url(path)) { bearer(token) } }.map { }

    /**
     * `DELETE` que **devolve o corpo** — para a API que responde com o estado depois de apagar.
     *
     * O [delete] acima descarta a resposta, e é o certo quando o servidor responde 204. Mas um
     * `DELETE` que devolve a lista já sem o item apagado é padrão comum, e com ele a tela não precisa
     * de uma segunda chamada para se atualizar — nem do intervalo em que o item sumiu da tela e os
     * contadores ainda não sabem disso. Sem esta variante, quem precisa do corpo escreve o `execute`
     * à mão no projeto e perde o tratamento de token, quota e erro que mora aqui.
     */
    suspend fun deleteJson(path: String): DomainResult<String> =
        execute(path) { token -> httpClient.delete(url(path)) { bearer(token) } }.texto()

    /**
     * `DELETE` que **leva corpo** — para a rota que identifica o que apagar por um valor que não
     * pode viajar na URL.
     *
     * O caso que a trouxe: desregistrar o aparelho no logout (`DELETE /dispositivos` com
     * `{"token": "..."}`). O token de push é a identidade do aparelho, e pôr um segredo desses num
     * segmento de caminho o entrega ao log de acesso de todo intermediário — a regra da fábrica é
     * que **toda** requisição registra método e URL. O corpo não vai para o log.
     *
     * A alternativa que este método evita é pior: sem ele, o projeto monta o `HttpClient` na mão
     * para uma chamada só, e perde de uma vez o 401→refresh, o 402→[DomainResult.Quota] e o
     * transporte que nunca lança.
     *
     * `DELETE` com corpo é permitido pela RFC 9110 §9.3.5 (sem semântica definida, e é por isso que
     * ela mora numa variante explícita, não no [delete] de sempre).
     */
    suspend fun deleteJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.delete(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.texto()

    // ---- Binário (anexos) ------------------------------------------------

    /** Upload multipart (campo `file`) de um binário autenticado — ex.: `POST /v1/.../anexos`. */
    suspend fun postMultipart(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        fieldName: String = "file",
    ): DomainResult<String> =
        postMultipartParts(path, listOf(MultipartPart(fieldName, fileName, fileBytes, mimeType)))

    /**
     * Upload multipart de **múltiplas partes nomeadas** num único request autenticado — ex.: uma
     * foto-prova enviando `full` + `thumb` (JPEG) juntas (`POST /v1/inspections/{id}/photos`). O
     * [postMultipart] de parte única delega a este. Mesma resiliência (401→refresh, host-scoped, 402→
     * quota, transporte nunca lança).
     */
    suspend fun postMultipartParts(
        path: String,
        parts: List<MultipartPart>,
    ): DomainResult<String> =
        execute(path) { token ->
            httpClient.post(url(path)) {
                bearer(token)
                setBody(multipartBody(parts))
            }
        }.texto()

    /**
     * Upload multipart via **PUT** — para recursos que já existem e cujo binário é *substituído*
     * (ex.: `PUT /v1/me/professionals/{id}/photo` troca a foto do profissional). Mesmo corpo e mesma
     * resiliência do [postMultipart]; muda só o verbo, porque a semântica é substituir, não criar.
     */
    suspend fun putMultipart(
        path: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        fieldName: String = "file",
    ): DomainResult<String> =
        putMultipartParts(path, listOf(MultipartPart(fieldName, fileName, fileBytes, mimeType)))

    /** Versão PUT do [postMultipartParts] (múltiplas partes nomeadas). */
    suspend fun putMultipartParts(
        path: String,
        parts: List<MultipartPart>,
    ): DomainResult<String> =
        execute(path) { token ->
            httpClient.put(url(path)) {
                bearer(token)
                setBody(multipartBody(parts))
            }
        }.texto()

    private fun multipartBody(parts: List<MultipartPart>) = MultiPartFormDataContent(
        formData {
            parts.forEach { part ->
                append(
                    key = part.fieldName,
                    value = part.bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, part.mimeType)
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"${part.fileName.ifBlank { part.fieldName.ifBlank { "anexo" } }}\"",
                        )
                    },
                )
            }
        },
    )

    /** Stream autenticado dos bytes de um binário — ex.: `GET /v1/anexos/{id}/bytes`. */
    suspend fun getBytes(path: String): DomainResult<ByteArray> =
        execute(path) { token -> httpClient.get(url(path)) { bearer(token) } }.bytes()

    // ---- Núcleo ----------------------------------------------------------

    private fun HttpRequestBuilder.bearer(token: String?) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    /**
     * Lê o CORPO da resposta sob a mesma proteção do [execute].
     *
     * ## O furo que isto fecha
     *
     * O `try/catch` do [execute] cobre a requisição — mas o corpo só é lido **depois** que ele
     * retornou. Escrito como `execute(...).map { it.bodyAsText() }`, o download acontecia FORA de
     * qualquer proteção: uma conexão derrubada no meio da leitura (o caso comum num payload grande
     * e demorado) lançava, a exceção subia pelo repositório e caía no `launch` do `BaseViewModel`,
     * que não captura nada. A corrotina morria em silêncio — sem log, sem erro na tela, com o
     * `carregando` aceso. **A tela girava para sempre.**
     *
     * Diagnosticado em 26/ago/2026 no relatório de 10 páginas do NeuroCoreX, gerado sob demanda: o
     * defeito só aparecia na PRIMEIRA abertura, que é a mais lenta. Todo consumidor de
     * `getJson`/`postJson`/`getBytes` da fábrica estava exposto ao mesmo travamento mudo.
     */
    private suspend fun DomainResult<HttpResponse>.texto(): DomainResult<String> =
        corpo { it.bodyAsText() }

    /** Par de [texto] para binário — mesma proteção, ver o KDoc acima. */
    private suspend fun DomainResult<HttpResponse>.bytes(): DomainResult<ByteArray> =
        corpo { it.readRawBytes() }

    private suspend fun <T> DomainResult<HttpResponse>.corpo(
        ler: suspend (HttpResponse) -> T,
    ): DomainResult<T> = when (this) {
        is DomainResult.Success -> try {
            DomainResult.Success(ler(data))
        } catch (e: CancellationException) {
            // Encerramento normal do escopo (a pessoa saiu da tela). Engolir isto pintaria erro de
            // rede toda vez que uma tela fosse fechada no meio de uma chamada.
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao ler o corpo da resposta: ${e.message}")
            DomainResult.Error(code = DomainResult.OFFLINE_CODE, message = texts.offline)
        }
        // `Error` e `Quota` não têm corpo a ler — atravessam intactos. O `Quota` é o 402 do
        // paywall, cujo contexto já foi extraído em `classify`; relê-lo aqui consumiria um corpo
        // que já acabou.
        is DomainResult.Error -> this
        is DomainResult.Quota -> this
    }

    /**
     * Executa a requisição com o ID token corrente; em **401**, força refresh e tenta 1 vez de novo.
     * Classifica o status em [DomainResult]. Toda exceção de transporte vira [DomainResult.Error].
     */
    private suspend fun execute(
        path: String,
        block: suspend (token: String?) -> HttpResponse,
    ): DomainResult<HttpResponse> {
        if (!sameHost(path)) {
            AppLogger.w(TAG, "Requisição bloqueada: path de outro host ($path) — token não anexado.")
            return DomainResult.Error(DomainResult.OFFLINE_CODE, texts.offline)
        }
        return try {
            val token = tokenProvider.token(forceRefresh = false)
            val response = block(token)
            if (response.status.value == 401) {
                val fresh = tokenProvider.token(forceRefresh = true)
                classify(block(fresh))
            } else {
                classify(response)
            }
        } catch (e: CancellationException) {
            // Sair da tela cancela o escopo, e o `catch (Exception)` abaixo capturaria isso como se
            // fosse falha de rede — a tela seguinte nasceria com "sem conexão" sem nunca ter feito
            // uma chamada.
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha de transporte em requisição de domínio: ${e.message}")
            DomainResult.Error(code = DomainResult.OFFLINE_CODE, message = texts.offline)
        }
    }

    private suspend fun classify(response: HttpResponse): DomainResult<HttpResponse> {
        val status = response.status.value
        return when {
            status in 200..299 -> DomainResult.Success(response)
            status == 402 -> {
                val quota = parseQuotaExceeded(runCatching { response.bodyAsText() }.getOrNull())
                if (quota != null) DomainResult.Quota(quota) else DomainResult.Error(status, texts.quotaReached)
            }
            status == 429 -> DomainResult.Error(429, texts.rateLimited, codigoDoServidor(response))
            status == 401 -> DomainResult.Error(401, texts.sessionExpired, codigoDoServidor(response))
            else -> DomainResult.Error(status, texts.serverError(status), codigoDoServidor(response))
        }
    }

    /**
     * O `code` do envelope de erro da backlib (`{"message": ..., "code": ..., "traceId": ...}`).
     *
     * Ler o corpo aqui é seguro: só acontece em resposta de ERRO, e nenhum chamador consome o corpo
     * de uma resposta que já virou [DomainResult.Error]. Qualquer falha — corpo vazio, HTML de um
     * proxy no meio, JSON sem `code` — devolve `null`, nunca uma exceção: um erro de transporte não
     * pode virar um segundo erro dentro do tratamento do primeiro.
     */
    private suspend fun codigoDoServidor(response: HttpResponse): String? = runCatching {
        val corpo = response.bodyAsText()
        if (!corpo.trimStart().startsWith("{")) return@runCatching null
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(corpo)
            .jsonObject["code"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        private const val TAG = "DomainApi"
    }
}

/**
 * Uma parte nomeada de um upload multipart ([DomainApiClient.postMultipartParts]). Ex.: a foto-prova
 * de uma vistoria envia duas partes (`full` e `thumb`) num único request.
 */
class MultipartPart(
    val fieldName: String,
    val fileName: String,
    val bytes: ByteArray,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultipartPart) return false
        return fieldName == other.fieldName &&
            fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fieldName.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * Fornece o **Firebase ID token** para o [DomainApiClient]. `forceRefresh = true` no retry pós-401.
 * `null` = sem header (usuário deslogado — o backend responde 401 e a UI reage).
 */
fun interface DomainTokenProvider {
    suspend fun token(forceRefresh: Boolean): String?
}

/** Adapta o [IAuthRepository] da lib a um [DomainTokenProvider] (Bearer = Firebase ID token). */
fun IAuthRepository.asDomainTokenProvider(): DomainTokenProvider =
    DomainTokenProvider { forceRefresh -> getIdToken(forceRefresh).getOrNull() }

/** Mensagens de erro do [DomainApiClient] (defaults pt-BR; injete traduções via `*Texts` do app). */
data class DomainApiTexts(
    val offline: String = "Sem conexão com o servidor.",
    val rateLimited: String = "Muitas requisições. Tente novamente em instantes.",
    val sessionExpired: String = "Sessão expirada. Entre novamente.",
    val quotaReached: String = "Limite atingido.",
    val serverError: (Int) -> String = { code -> "Erro do servidor ($code)." },
)

/**
 * Resultado tipado de uma chamada ao backend de domínio. Distingue **cota estourada** (402 → paywall)
 * de erro comum — os repositórios propagam essa distinção para os ViewModels (abrir o Paywall vs. erro).
 */
sealed class DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>()

    /** 402 — cota estourada; abre o Paywall com o contexto ([QuotaExceeded]). */
    data class Quota(val quota: QuotaExceeded) : DomainResult<Nothing>()

    /**
     * @param code o status HTTP, ou [OFFLINE_CODE] quando nem chegou a haver resposta.
     * @param serverCode o **código de negócio** que o backend mandou no corpo (`{"code": "..."}`).
     *
     * `serverCode` existe porque o status sozinho não distingue dois erros diferentes com o mesmo
     * número: um 409 pode ser "o resultado ainda está sendo preparado" e outro "esta resposta já
     * foi enviada", e a tela precisa dizer coisas opostas em cada caso. Sem ele, cada app ou
     * adivinhava pelo status — acertando por acaso enquanto houvesse um 409 só na rota — ou refazia
     * a chamada para ler o corpo que este cliente tinha acabado de descartar.
     *
     * É `null` quando o corpo não é JSON, não tem `code`, ou o erro é de transporte.
     */
    data class Error(
        val code: Int,
        val message: String,
        val serverCode: String? = null,
    ) : DomainResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Quota -> this
        is Error -> this
    }

    fun getOrNull(): T? = (this as? Success)?.data
    val isSuccess: Boolean get() = this is Success

    /** `true` se é erro de transporte/sem-rede (código sentinela [OFFLINE_CODE]). */
    val isOffline: Boolean get() = this is Error && code == OFFLINE_CODE

    companion object {
        /** Código sentinela de "sem conexão / falha de transporte" (não é um status HTTP). */
        const val OFFLINE_CODE: Int = -1
    }
}
