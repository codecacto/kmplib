package br.com.codecacto.kmplib.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Implementação **genérica** de [SyncPort] sobre o `HttpClient` (Ktor) do app — o transporte REST
 * de sincronização offline-first contra a **API central** (`backlib`/apps-api). Remove o boilerplate
 * de cada app reescrever `pull`/`push`; padroniza o contrato de fio (os DTOs de [SyncWire]).
 *
 * Segue o mesmo padrão dos demais serviços REST da kmplib (ex.: `AdminApiEntitlementRepository`):
 * **`ktor-client-core` puro + kotlinx-json**, SEM exigir `ContentNegotiation` no `HttpClient` do
 * consumidor (serializa/deserializa manualmente). Auth por **Firebase ID token** em
 * `Authorization: Bearer` (via [tokenProvider]).
 *
 * ### Contrato de fio (paridade EXATA com o backend)
 * - `POST {baseUrl}{pathPrefix}/pull`  body [SyncPullRequest] → [SyncPullResponse]
 * - `POST {baseUrl}{pathPrefix}/push`  body [SyncPushRequest] → [SyncPushResponse]
 *
 * `pathPrefix` tipicamente embute o slug do projeto, ex.: `"/sync/v1/meu-fisio"`.
 *
 * ### Propagação de erro (intencional)
 * Diferente dos serviços best-effort (feedback/ads), o port **LANÇA** em rede/HTTP 4xx-5xx/corpo
 * inválido. É o comportamento correto: o [DefaultSyncEngine] já envolve `pull`/`push` em try/catch e
 * converte a falha em `SyncResultSummary.failure(...)` + `SyncState.Error`, preservando a outbox para
 * retentar quando a rede voltar. Nunca "engole" um erro de sync.
 *
 * @param httpClient HttpClient já configurado pelo app (base, timeouts).
 * @param baseUrl URL base da API de sync, ex.: "https://apps-api.codecacto.com.br".
 * @param pathPrefix prefixo das rotas de sync (sem barra final), ex.: "/sync/v1/meu-fisio".
 * @param tokenProvider fornece o Firebase ID token do usuário por requisição (`null` = sem header).
 * @param json Json para (de)serializar os DTOs de fio (default o do módulo sync — tolerante).
 */
class RestSyncPort(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val pathPrefix: String,
    private val tokenProvider: suspend () -> String? = { null },
    private val json: Json = defaultSyncJson,
) : SyncPort {

    private val root: String get() = baseUrl.trimEnd('/') + "/" + pathPrefix.trim('/')

    override suspend fun pull(req: SyncPullRequest): SyncPullResponse {
        val body = postJson("$root/pull", json.encodeToString(SyncPullRequest.serializer(), req))
        return json.decodeFromString(SyncPullResponse.serializer(), body)
    }

    override suspend fun push(req: SyncPushRequest): SyncPushResponse {
        val body = postJson("$root/push", json.encodeToString(SyncPushRequest.serializer(), req))
        return json.decodeFromString(SyncPushResponse.serializer(), body)
    }

    private suspend fun postJson(url: String, payload: String): String {
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            tokenProvider()?.let { header("Authorization", "Bearer $it") }
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            error("sync ${response.status.value} em $url")
        }
        return response.bodyAsText()
    }
}
