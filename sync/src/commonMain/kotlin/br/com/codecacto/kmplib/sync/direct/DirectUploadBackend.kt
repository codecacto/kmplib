@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * As quatro conversas com o **nosso** servidor num envio multipart pré-assinado. O que vai para o
 * storage (R2/S3) não passa por aqui — é o [DirectUploadPartTransport], direto, sem Bearer.
 *
 * Quase todo app usa o [RestDirectUploadBackend] pronto (contrato `VideoUploadDto` da fábrica);
 * implemente esta interface só se o contrato for outro.
 *
 * Todas devolvem [DomainResult]: 402 = cota, `OFFLINE_CODE` = sem rede, 4xx/5xx com o envelope da
 * backlib — a [DirectUploadOutbox] decide pela mesma régua das outras filas (`classifyRestFailure`).
 */
interface DirectUploadBackend {
    /** Abre o envio (ex.: `POST /v1/aluno/video-requests/{id}/uploads`) e devolve a sessão com as primeiras URLs. */
    suspend fun start(job: DirectUploadJob): DomainResult<DirectUploadSession>

    /** Reassina as [partNumbers] (URLs vencidas, ou retomada depois de horas). */
    suspend fun presignParts(job: DirectUploadJob, session: DirectUploadSession, partNumbers: List<Int>): DomainResult<DirectUploadPartUrls>

    /** Fecha o envio com as ETags; devolve o corpo da resposta (o recurso criado). */
    suspend fun complete(job: DirectUploadJob, session: DirectUploadSession, parts: List<DirectUploadCompletedPart>): DomainResult<String>

    /** Desiste do envio no servidor (apaga o objeto parcial). Melhor esforço. */
    suspend fun abort(job: DirectUploadJob, session: DirectUploadSession): DomainResult<Unit>
}

/**
 * [DirectUploadBackend] sobre o [DomainApiClient] (Bearer + renovação + 402), no contrato de upload
 * multipart da fábrica (App do Personal §4.13 — `VideoUploadDto`):
 *
 * ```
 * POST <start>                         → 201 {uploadId, videoId, partSizeBytes, partCount, parts:[{partNumber,url}], expiresAt}
 * POST …/uploads/{uploadId}/parts      {partNumbers}            → 200 {parts:[{partNumber,url}], expiresAt}
 * POST …/uploads/{uploadId}/complete   {parts:[{partNumber,etag}]} → 200 <recurso>
 * POST …/uploads/{uploadId}/abort      → 204
 * ```
 *
 * ```kotlin
 * val backend = RestDirectUploadBackend(
 *     api = get(),
 *     startPath = { job -> "/v1/aluno/video-requests/${job.targetId}/uploads" },
 *     startBody = { job -> buildJsonObject {
 *         put("contentType", job.contentType); put("sizeBytes", job.sizeBytes)
 *         put("durationSeconds", job.durationSeconds); job.metadata["sessionSetId"]?.let { put("sessionSetId", it) }
 *     } },
 *     uploadBasePath = "/v1/aluno/videos/uploads",
 * )
 * ```
 *
 * @param startPath caminho do `start` para o envio (normalmente leva o [DirectUploadJob.targetId]).
 * @param startBody corpo do `start`. Default: `contentType`, `sizeBytes`, `durationSeconds`,
 *   `fileName` e os [DirectUploadJob.metadata] como texto.
 * @param uploadBasePath prefixo dos caminhos por envio — `{base}/{uploadId}/parts|complete|abort`.
 * @param remoteIdField campo da resposta do `start` com o id do recurso (`videoId`).
 */
class RestDirectUploadBackend(
    private val api: DomainApiClient,
    private val startPath: (DirectUploadJob) -> String,
    private val uploadBasePath: String,
    private val startBody: (DirectUploadJob) -> JsonObject = ::defaultDirectUploadStartBody,
    private val remoteIdField: String = "videoId",
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DirectUploadBackend {

    private fun porEnvio(uploadId: String, acao: String): String =
        uploadBasePath.trimEnd('/') + "/" + encodePathSegment(uploadId) + "/" + acao

    override suspend fun start(job: DirectUploadJob): DomainResult<DirectUploadSession> =
        api.postJson(startPath(job), startBody(job).toString()).parse { corpo ->
            parseDirectUploadSession(json.parseToJsonElement(corpo).jsonObject, remoteIdField)
        }

    override suspend fun presignParts(
        job: DirectUploadJob,
        session: DirectUploadSession,
        partNumbers: List<Int>,
    ): DomainResult<DirectUploadPartUrls> {
        val corpo = buildJsonObject { put("partNumbers", buildJsonArray { partNumbers.forEach { add(JsonPrimitive(it)) } }) }
        return api.postJson(porEnvio(session.uploadId, "parts"), corpo.toString()).parse { resposta ->
            parseDirectUploadPartUrls(json.parseToJsonElement(resposta).jsonObject)
        }
    }

    override suspend fun complete(
        job: DirectUploadJob,
        session: DirectUploadSession,
        parts: List<DirectUploadCompletedPart>,
    ): DomainResult<String> {
        val corpo = buildJsonObject {
            put(
                "parts",
                buildJsonArray {
                    parts.forEach { p -> add(buildJsonObject { put("partNumber", p.partNumber); put("etag", p.etag) }) }
                },
            )
        }
        return api.postJson(porEnvio(session.uploadId, "complete"), corpo.toString())
    }

    override suspend fun abort(job: DirectUploadJob, session: DirectUploadSession): DomainResult<Unit> =
        api.postJson(porEnvio(session.uploadId, "abort"), "{}").map { }

    private inline fun <T> DomainResult<String>.parse(block: (String) -> T?): DomainResult<T> = when (this) {
        is DomainResult.Success -> runCatching { block(data) }.getOrNull()
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Error(INVALID_RESPONSE_CODE, "Resposta do servidor ilegível.")
        is DomainResult.Quota -> this
        is DomainResult.Error -> this
    }

    companion object {
        /** Sentinela: 2xx com corpo que não é a sessão esperada. Terminal (não adianta repetir igual). */
        const val INVALID_RESPONSE_CODE: Int = -20
    }
}

/** Corpo default do `start`: `contentType`, `sizeBytes`, `durationSeconds`, `fileName` + metadados. */
fun defaultDirectUploadStartBody(job: DirectUploadJob): JsonObject = buildJsonObject {
    put("contentType", job.contentType)
    put("sizeBytes", job.sizeBytes)
    job.durationSeconds?.let { put("durationSeconds", it) }
    put("fileName", job.fileName)
    job.metadata.forEach { (k, v) -> if (k.isNotBlank()) put(k, v) }
}

/** Lê o `VideoUploadDto` (`uploadId`, `partSizeBytes`, `partCount`, `parts`, `expiresAt`). `null` se faltar o essencial. */
fun parseDirectUploadSession(obj: JsonObject, remoteIdField: String = "videoId"): DirectUploadSession? {
    val uploadId = obj.texto("uploadId") ?: return null
    val partSize = obj.prim("partSizeBytes")?.longOrNull ?: return null
    val partCount = obj.prim("partCount")?.intOrNull ?: return null
    val urls = parsePartUrlList(obj["parts"])
    return DirectUploadSession(
        uploadId = uploadId,
        remoteId = obj.texto(remoteIdField),
        partSizeBytes = partSize,
        partCount = partCount,
        partUrls = urls,
        urlsExpireAtMillis = parseIsoInstantMillis(obj.texto("expiresAt")),
    )
}

/** Lê o `VideoPartsDto` (`parts`, `expiresAt`). `null` se não houver lista. */
fun parseDirectUploadPartUrls(obj: JsonObject): DirectUploadPartUrls? {
    if (obj["parts"] !is JsonArray) return null
    return DirectUploadPartUrls(parsePartUrlList(obj["parts"]), parseIsoInstantMillis(obj.texto("expiresAt")))
}

/** ISO-8601 (`2026-10-10T12:00:00Z`) → ms; `null` se ausente/ilegível. */
fun parseIsoInstantMillis(value: String?): Long? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

private fun parsePartUrlList(element: JsonElement?): Map<Int, String> {
    val lista = (element as? JsonArray) ?: return emptyMap()
    return buildMap {
        lista.forEach { item ->
            val o = item as? JsonObject ?: return@forEach
            val n = o.prim("partNumber")?.intOrNull ?: return@forEach
            val url = o.texto("url") ?: return@forEach
            if (n >= 1) put(n, url)
        }
    }
}

private fun JsonObject.prim(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonObject.texto(key: String): String? =
    prim(key)?.takeIf { it.isString || it.contentOrNull != "null" }?.contentOrNull?.takeIf { it.isNotBlank() }

/** Escapa um segmento de caminho (o `uploadId` vem do servidor; não pode quebrar a URL). */
internal fun encodePathSegment(value: String): String = buildString {
    value.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
            append(ch)
        } else {
            append('%').append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
