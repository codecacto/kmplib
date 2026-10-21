package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * O bilhete de envio por **PUT único** que o servidor devolve ao declarar a mídia (2.312.0).
 *
 * @param remoteId o id do recurso no servidor (ecoado da declaração).
 * @param target para onde e como mandar o arquivo; `null` = o servidor não emitiu bilhete agora
 *   (storage indisponível) **ou** não precisa ([alreadyReceived]).
 * @param alreadyReceived o servidor já tem o arquivo conferido (repetição depois do fim).
 * @param state o estado textual que veio (`PENDENTE`, `CONFERIDA`, `DIVERGENTE`…), para diagnóstico.
 */
data class DirectUploadPutTicket(
    val remoteId: String?,
    val target: DirectUploadPutTarget?,
    val alreadyReceived: Boolean,
    val state: String? = null,
)

/**
 * Para onde mandar o arquivo: **exatamente** este [method], esta [url] e estes [headers] (tipo,
 * tamanho e hash estão assinados; outro valor = 403 do storage).
 */
data class DirectUploadPutTarget(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val expiresAtMillis: Long?,
) {
    override fun toString(): String =
        "DirectUploadPutTarget(method=$method, url=${safeUrl(url)}, headers=${headers.keys.sorted()}, expiresAtMillis=$expiresAtMillis)"
}

/**
 * [DirectUploadBackend] para o contrato de **PUT único pré-assinado** (2.312.0) — declarar → `PUT`
 * direto ao storage com os cabeçalhos assinados → concluir. É o caminho para foto e vídeo curto de
 * captura (até ~100 MB), em que o servidor confere tamanho, tipo e SHA-256 do objeto no fim:
 *
 * ```
 * POST <declare>            {id, contentType, sizeBytes, sha256, …}
 *                           → 201/200 {id, estado, upload: {method, url, headers, expiraEm} | null}
 * PUT  <upload.url>         (arquivo inteiro, com upload.headers)         → 200 do storage
 * POST <complete>           {}  → 200 {id, estado}   · 409 <notReceived> = o arquivo não chegou
 * ```
 * - `upload: null` e estado não recebido = storage indisponível agora → **503 retentável**: a fila
 *   mantém o arquivo e repete a declaração com recuo.
 * - estado em [receivedStates] (`CONFERIDA`) = nada a enviar; a fila só chama o `complete`
 *   (idempotente) e entrega a resposta.
 * - Bilhete vencido (403 do storage) → a fila **declara de novo** (mesmo id) e repete o `PUT` — o
 *   [presignParts] daqui é a própria declaração.
 * - O `complete` que responde com um [notReceivedServerCodes] vira
 *   [DirectUploadOutbox.NOT_RECEIVED_CODE]: a fila reenvia o arquivo.
 * - Não há `abort` neste contrato (o servidor expira a mídia não conferida sozinho): no-op.
 *
 * Uso (RondAki §11.4 — o app gera o UUID v7 da mídia e o põe em `targetId`):
 * ```kotlin
 * val backend = RestSinglePutUploadBackend(
 *     api = get(),
 *     declarePath = { "/rondaki/midias" },
 *     completePath = { _, midiaId -> "/rondaki/midias/$midiaId/concluir" },
 *     declareBody = { job -> buildJsonObject {
 *         put("id", job.targetId); put("tipo", job.kind); put("contentType", job.contentType)
 *         put("tamanhoBytes", job.sizeBytes); put("sha256", job.sha256)
 *     } },
 * )
 * outbox.enqueue(DirectUploadRequest(sourcePath = foto.path, kind = "FOTO", targetId = midiaId,
 *     contentType = "image/jpeg", computeSha256 = true))
 * ```
 *
 * @param declarePath caminho da declaração (pede o bilhete; idempotente pelo id).
 * @param completePath caminho do `concluir`, com o id remoto (o da resposta, ou o [DirectUploadJob.targetId]).
 * @param declareBody corpo da declaração. Default: [defaultSinglePutDeclareBody].
 * @param completeBody corpo do `concluir`. Default: `{}`.
 * @param ticketParser leitura da resposta da declaração. Default: [parseDirectUploadPutTicket].
 * @param notReceivedServerCodes códigos de negócio do `complete` que significam "o arquivo ainda não
 *   está no storage".
 */
class RestSinglePutUploadBackend(
    private val api: DomainApiClient,
    private val declarePath: (DirectUploadJob) -> String,
    private val completePath: (job: DirectUploadJob, remoteId: String) -> String,
    private val declareBody: (DirectUploadJob) -> JsonObject = ::defaultSinglePutDeclareBody,
    private val completeBody: (DirectUploadJob) -> JsonObject = { JsonObject(emptyMap()) },
    private val ticketParser: (JsonObject) -> DirectUploadPutTicket? = { parseDirectUploadPutTicket(it) },
    private val notReceivedServerCodes: Set<String> = DEFAULT_NOT_RECEIVED_CODES,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DirectUploadBackend {

    override suspend fun start(job: DirectUploadJob): DomainResult<DirectUploadSession> =
        when (val r = declare(job)) {
            is DomainResult.Success -> sessionFor(job, r.data)
            is DomainResult.Quota -> r
            is DomainResult.Error -> r
        }

    /** Bilhete novo = declarar de novo (mesmo id). */
    override suspend fun presignParts(
        job: DirectUploadJob,
        session: DirectUploadSession,
        partNumbers: List<Int>,
    ): DomainResult<DirectUploadPartUrls> = when (val r = declare(job)) {
        is DomainResult.Success -> {
            val ticket = r.data
            val alvo = ticket.target
            when {
                ticket.alreadyReceived -> DomainResult.Success(DirectUploadPartUrls(emptyMap(), null, alreadyReceived = true))
                alvo == null -> storageUnavailable()
                else -> DomainResult.Success(
                    DirectUploadPartUrls(mapOf(1 to alvo.url), alvo.expiresAtMillis, signedHeaders = alvo.headers),
                )
            }
        }
        is DomainResult.Quota -> r
        is DomainResult.Error -> r
    }

    override suspend fun complete(
        job: DirectUploadJob,
        session: DirectUploadSession,
        parts: List<DirectUploadCompletedPart>,
    ): DomainResult<String> {
        val id = session.remoteId ?: job.targetId
        return when (val r = api.postJson(completePath(job, id), completeBody(job).toString())) {
            is DomainResult.Error ->
                if (r.serverCode != null && r.serverCode in notReceivedServerCodes) {
                    r.copy(code = DirectUploadOutbox.NOT_RECEIVED_CODE)
                } else {
                    r
                }
            else -> r
        }
    }

    override suspend fun abort(job: DirectUploadJob, session: DirectUploadSession): DomainResult<Unit> =
        DomainResult.Success(Unit)

    private suspend fun declare(job: DirectUploadJob): DomainResult<DirectUploadPutTicket> =
        when (val r = api.postJson(declarePath(job), declareBody(job).toString())) {
            is DomainResult.Success -> runCatching { ticketParser(json.parseToJsonElement(r.data).jsonObject) }.getOrNull()
                ?.let { DomainResult.Success(it) }
                ?: DomainResult.Error(RestDirectUploadBackend.INVALID_RESPONSE_CODE, "Resposta do servidor ilegível.")
            is DomainResult.Quota -> r
            is DomainResult.Error -> r
        }

    private fun sessionFor(job: DirectUploadJob, ticket: DirectUploadPutTicket): DomainResult<DirectUploadSession> {
        val id = ticket.remoteId?.takeIf { it.isNotBlank() } ?: job.targetId.takeIf { it.isNotBlank() } ?: job.id
        val alvo = ticket.target
        return when {
            ticket.alreadyReceived -> DomainResult.Success(
                DirectUploadSession.singlePut(remoteId = id, sizeBytes = job.sizeBytes, url = null, alreadyReceived = true),
            )
            alvo == null -> storageUnavailable()
            else -> DomainResult.Success(
                DirectUploadSession.singlePut(
                    remoteId = id,
                    sizeBytes = job.sizeBytes,
                    url = alvo.url,
                    method = alvo.method,
                    signedHeaders = alvo.headers,
                    expiresAtMillis = alvo.expiresAtMillis,
                ),
            )
        }
    }

    private fun storageUnavailable(): DomainResult.Error =
        DomainResult.Error(503, "Armazenamento indisponível agora.", serverCode = STORAGE_UNAVAILABLE_CODE)

    companion object {
        /** `serverCode` do 503 sintetizado quando a declaração volta sem bilhete. */
        const val STORAGE_UNAVAILABLE_CODE: String = "STORAGE_UNAVAILABLE"

        /** Os "arquivo ainda não chegou" conhecidos (RondAki `MIDIA_NAO_ENVIADA`; genérico `UPLOAD_NOT_RECEIVED`). */
        val DEFAULT_NOT_RECEIVED_CODES: Set<String> = setOf("MIDIA_NAO_ENVIADA", "UPLOAD_NOT_RECEIVED")
    }
}

/**
 * Corpo default da declaração: `id` (= [DirectUploadJob.targetId]), `contentType`, `sizeBytes`,
 * `sha256` (quando calculado) e os [DirectUploadJob.metadata] como texto.
 */
fun defaultSinglePutDeclareBody(job: DirectUploadJob): JsonObject = buildJsonObject {
    put("id", job.targetId)
    put("contentType", job.contentType)
    put("sizeBytes", job.sizeBytes)
    job.sha256?.let { put("sha256", it) }
    job.metadata.forEach { (k, v) -> if (k.isNotBlank()) put(k, v) }
}

/** Estados que significam "o servidor já tem o arquivo conferido". */
val DEFAULT_DIRECT_UPLOAD_RECEIVED_STATES: Set<String> = setOf("CONFERIDA", "RECEIVED", "VERIFIED", "COMPLETED")

/**
 * Lê a resposta da declaração: `{id, estado|status, upload: {method, url, headers, expiraEm|expiresAt} | null}`.
 *
 * - `expiraEm`/`expiresAt` aceita número (epoch em ms; `0` ou negativo = não informado) ou ISO-8601.
 * - `headers` só com valores primitivos (texto/número); o resto é ignorado.
 * - `null` quando a resposta não é desse formato (nem bilhete válido, nem estado de recebido, nem
 *   `upload: null` explícito).
 */
fun parseDirectUploadPutTicket(
    obj: JsonObject,
    receivedStates: Set<String> = DEFAULT_DIRECT_UPLOAD_RECEIVED_STATES,
): DirectUploadPutTicket? {
    val estado = (obj.textOf("estado") ?: obj.textOf("status"))
    val recebido = estado != null && receivedStates.any { it.equals(estado, ignoreCase = true) }
    val upload = obj["upload"]
    val alvo = (upload as? JsonObject)?.let(::parsePutTarget)
    if (upload is JsonObject && alvo == null && !recebido) return null
    if (upload == null && estado == null) return null
    return DirectUploadPutTicket(remoteId = obj.textOf("id"), target = if (recebido) null else alvo, alreadyReceived = recebido, state = estado)
}

private fun parsePutTarget(o: JsonObject): DirectUploadPutTarget? {
    val url = o.textOf("url") ?: return null
    val metodo = o.textOf("method")?.uppercase() ?: "PUT"
    val cabecalhos = (o["headers"] as? JsonObject)?.mapNotNull { (k, v) ->
        val valor = (v as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        if (k.isBlank()) null else k to valor
    }?.toMap().orEmpty()
    val vence = (o["expiraEm"] ?: o["expiresAt"]) as? JsonPrimitive
    val venceMs = when {
        vence == null -> null
        vence.isString -> parseIsoInstantMillis(vence.contentOrNull)
        else -> vence.longOrNull?.takeIf { it > 0L }
    }
    return DirectUploadPutTarget(metodo, url, cabecalhos, venceMs)
}

private fun JsonObject.textOf(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
