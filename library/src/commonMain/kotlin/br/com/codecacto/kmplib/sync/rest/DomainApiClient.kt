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
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType

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
        execute(path) { token -> httpClient.get(url(path)) { bearer(token) } }.map { it.bodyAsText() }

    suspend fun postJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.post(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.map { it.bodyAsText() }

    suspend fun putJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.put(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.map { it.bodyAsText() }

    suspend fun patchJson(path: String, body: String): DomainResult<String> =
        execute(path) { token ->
            httpClient.patch(url(path)) {
                bearer(token); contentType(ContentType.Application.Json); setBody(body)
            }
        }.map { it.bodyAsText() }

    suspend fun delete(path: String): DomainResult<Unit> =
        execute(path) { token -> httpClient.delete(url(path)) { bearer(token) } }.map { }

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
                setBody(
                    MultiPartFormDataContent(
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
                    ),
                )
            }
        }.map { it.bodyAsText() }

    /** Stream autenticado dos bytes de um binário — ex.: `GET /v1/anexos/{id}/bytes`. */
    suspend fun getBytes(path: String): DomainResult<ByteArray> =
        execute(path) { token -> httpClient.get(url(path)) { bearer(token) } }.map { it.readRawBytes() }

    // ---- Núcleo ----------------------------------------------------------

    private fun HttpRequestBuilder.bearer(token: String?) {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
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
            status == 429 -> DomainResult.Error(429, texts.rateLimited)
            status == 401 -> DomainResult.Error(401, texts.sessionExpired)
            else -> DomainResult.Error(status, texts.serverError(status))
        }
    }

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

    data class Error(val code: Int, val message: String) : DomainResult<Nothing>()

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
