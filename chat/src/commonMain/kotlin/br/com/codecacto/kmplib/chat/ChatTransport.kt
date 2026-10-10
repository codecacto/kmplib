@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.chat

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.MultipartPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A mensagem a mandar, já com o áudio subido (quando é áudio). */
data class ChatSendRequest(
    val id: String,
    val kind: ChatMessageKind,
    val text: String?,
    val audioAssetId: String?,
) {
    override fun toString(): String = "ChatSendRequest(id=$id, kind=$kind)"
}

/**
 * As conversas com o servidor de uma conversa. Quase todo app usa o [RestChatTransport] (contrato
 * `MessageDto` da fábrica); implemente só se o contrato for outro.
 */
interface ChatTransport {
    /** Página mais recente (mais nova primeiro no servidor — a ordem aqui não importa: a lib ordena). */
    suspend fun latest(size: Int): DomainResult<ChatPage>

    /** Anteriores a [beforeId] (rolar para trás). */
    suspend fun before(beforeId: String, size: Int): DomainResult<ChatPage>

    /** Posteriores a [afterId] — o **polling**. [ChatPage.hasMore] = há mais além desta página. */
    suspend fun after(afterId: String, size: Int): DomainResult<ChatPage>

    /** Manda a mensagem. **Mesmo id de novo = a mesma mensagem** (o servidor devolve a existente). */
    suspend fun send(request: ChatSendRequest): DomainResult<ChatMessage>

    /** Sobe o áudio e devolve o id do asset. */
    suspend fun uploadAudio(bytes: ByteArray, durationSeconds: Int): DomainResult<String>

    /** Marca como lidas por mim as recebidas até [upToMessageId]. */
    suspend fun markRead(upToMessageId: String): DomainResult<Unit>
}

/**
 * [ChatTransport] sobre o [DomainApiClient], no contrato de conversa da fábrica (App do Personal §2.2):
 *
 * ```
 * GET  <messagesPath>?before=<id>|after=<id>&size=30  → {items:[MessageDto], hasMore}
 * POST <messagesPath>        {id, kind, text?, audioAssetId?} → 201|200 MessageDto
 * POST <messagesPath>/read   {upToMessageId}                    → 204
 * POST <audioUploadPath>     multipart file + durationSeconds   → 201 {id, …}
 * MessageDto = {id, senderRole, kind, text, audioAssetId, audioDurationSeconds, createdAt, readByOtherAt}
 * ```
 *
 * ```kotlin
 * // aluno:     RestChatTransport(api, messagesPath = "/v1/aluno/messages", myRole = "ALUNO")
 * // personal:  RestChatTransport(api, messagesPath = "/v1/personal/students/$alunoId/messages", myRole = "PERSONAL")
 * ```
 *
 * @param myRole o `senderRole` da conta logada — é por ele que a mensagem vira "minha".
 */
class RestChatTransport(
    private val api: DomainApiClient,
    private val messagesPath: String,
    private val myRole: String,
    private val audioUploadPath: String = "/v1/media/audio",
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ChatTransport {

    private val base = messagesPath.trimEnd('/')

    private suspend fun pagina(query: String): DomainResult<ChatPage> =
        api.getJson("$base?$query").ler { parseChatPage(json.parseToJsonElement(it).jsonObject, myRole) }

    override suspend fun latest(size: Int): DomainResult<ChatPage> = pagina("size=$size")

    override suspend fun before(beforeId: String, size: Int): DomainResult<ChatPage> =
        pagina("before=${encodeQuery(beforeId)}&size=$size")

    override suspend fun after(afterId: String, size: Int): DomainResult<ChatPage> =
        pagina("after=${encodeQuery(afterId)}&size=$size")

    override suspend fun send(request: ChatSendRequest): DomainResult<ChatMessage> {
        val corpo = buildJsonObject {
            put("id", request.id)
            put("kind", request.kind.name)
            request.text?.let { put("text", it) }
            request.audioAssetId?.let { put("audioAssetId", it) }
        }
        return api.postJson(base, corpo.toString()).ler { parseChatMessage(json.parseToJsonElement(it).jsonObject, myRole) }
    }

    override suspend fun uploadAudio(bytes: ByteArray, durationSeconds: Int): DomainResult<String> =
        api.postMultipartParts(
            audioUploadPath,
            listOf(MultipartPart("file", "voz.m4a", bytes, "audio/mp4")),
            mapOf("durationSeconds" to durationSeconds.toString()),
        ).ler { corpo ->
            (json.parseToJsonElement(corpo).jsonObject["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }

    override suspend fun markRead(upToMessageId: String): DomainResult<Unit> =
        api.postJson("$base/read", buildJsonObject { put("upToMessageId", upToMessageId) }.toString()).map { }

    private inline fun <T> DomainResult<String>.ler(block: (String) -> T?): DomainResult<T> = when (this) {
        is DomainResult.Success -> runCatching { block(data) }.getOrNull()?.let { DomainResult.Success(it) }
            ?: DomainResult.Error(INVALID_RESPONSE_CODE, "Resposta do servidor ilegível.")
        is DomainResult.Quota -> this
        is DomainResult.Error -> this
    }

    companion object {
        /** Sentinela: 2xx com corpo que não é o esperado. Terminal. */
        const val INVALID_RESPONSE_CODE: Int = -40
    }
}

/** `MessagePageDto` → [ChatPage]; itens ilegíveis são ignorados. `null` sem `items`. */
fun parseChatPage(obj: JsonObject, myRole: String): ChatPage? {
    val itens = obj["items"] as? JsonArray ?: return null
    val mensagens = itens.mapNotNull { (it as? JsonObject)?.let { o -> parseChatMessage(o, myRole) } }
    val mais = (obj["hasMore"] as? JsonPrimitive)?.booleanOrNull ?: false
    return ChatPage(mensagens, mais)
}

/** `MessageDto` → [ChatMessage]. `null` se faltar id, tipo conhecido ou data. */
fun parseChatMessage(obj: JsonObject, myRole: String): ChatMessage? {
    fun texto(k: String) = (obj[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    val id = texto("id")?.takeIf { it.isNotBlank() } ?: return null
    val kind = texto("kind")?.let { k -> ChatMessageKind.entries.firstOrNull { it.name == k } } ?: return null
    val criada = parseChatInstant(texto("createdAt")) ?: return null
    return ChatMessage(
        id = id,
        fromMe = texto("senderRole") == myRole,
        kind = kind,
        text = texto("text"),
        audioAssetId = texto("audioAssetId"),
        audioDurationMillis = (obj["audioDurationSeconds"] as? JsonPrimitive)?.intOrNull?.let { it * 1000L },
        createdAtMillis = criada,
        readByOtherAtMillis = parseChatInstant(texto("readByOtherAt")),
    )
}

/** ISO-8601 → ms; `null` se ausente/ilegível. */
fun parseChatInstant(value: String?): Long? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

private fun encodeQuery(value: String): String = buildString {
    value.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') append(ch)
        else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}
