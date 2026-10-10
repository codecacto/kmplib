package br.com.codecacto.kmplib.chat

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.createBlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Diretório default da fila de mensagens pendentes (privado, fora do backup — `BlobStore` do core). */
const val DEFAULT_CHAT_OUTBOX_DIRECTORY: String = "kmplib_chat_outbox"

/**
 * Onde as mensagens **não confirmadas** esperam — por conta e por conversa, no `BlobStore` do core
 * (armazenamento privado, fora do backup). É o que faz a mensagem escrita sem sinal sobreviver ao
 * app fechado, e o áudio gravado não precisar ser gravado de novo.
 *
 * Os nomes no disco levam um **resumo** (hash) da conta e da conversa, nunca o id cru.
 */
class ChatPendingStore(
    private val blobs: BlobStore = createBlobStore(DEFAULT_CHAT_OUTBOX_DIRECTORY),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val serializer = ListSerializer(ChatOutgoing.serializer())

    private fun filaId(accountId: String, conversationKey: String) = "q-${chatHash(accountId)}-${chatHash(conversationKey)}.json"

    /** Pendentes da conversa (vazio se não há ou o arquivo está ilegível). */
    suspend fun load(accountId: String, conversationKey: String): List<ChatOutgoing> {
        val bytes = blobs.read(filaId(accountId, conversationKey)) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, bytes.decodeToString()) }
            .onFailure { AppLogger.w(TAG, "fila de mensagens ilegível; ignorada") }
            .getOrDefault(emptyList())
    }

    /** Grava a fila inteira (vazia = apaga o arquivo). */
    suspend fun save(accountId: String, conversationKey: String, pending: List<ChatOutgoing>): Boolean {
        val id = filaId(accountId, conversationKey)
        if (pending.isEmpty()) {
            blobs.delete(id)
            return true
        }
        return blobs.write(id, json.encodeToString(serializer, pending).encodeToByteArray())
    }

    /** Guarda os bytes do áudio de [messageId]; devolve o id do blob, ou `null` se não gravou. */
    suspend fun putAudio(accountId: String, messageId: String, bytes: ByteArray): String? {
        val id = "a-${chatHash(accountId)}-${chatHash(messageId)}"
        return id.takeIf { blobs.write(it, bytes) }
    }

    suspend fun readAudio(blobId: String): ByteArray? = blobs.read(blobId)

    suspend fun deleteAudio(blobId: String) {
        blobs.delete(blobId)
    }

    /** Quantas mensagens a conta [accountId] tem esperando (todas as conversas) — para avisar no logout. */
    suspend fun pendingCount(accountId: String): Int {
        val prefixo = "q-${chatHash(accountId)}-"
        return blobs.ids().filter { it.startsWith(prefixo) }.sumOf { id ->
            val bytes = blobs.read(id) ?: return@sumOf 0
            runCatching { json.decodeFromString(serializer, bytes.decodeToString()).size }.getOrDefault(0)
        }
    }

    /**
     * Apaga tudo o que a conta [accountId] tem esperando (filas e áudios) — no logout que descarta e
     * na exclusão de conta (passe no `extraCleanup` do `SyncAccountDataPurger`, se o app tiver sync).
     */
    suspend fun purgeAccount(accountId: String): Int {
        require(accountId.isNotBlank()) { "conversa: limpeza sem conta nomeada recusada" }
        val h = chatHash(accountId)
        val alvos = blobs.ids().filter { it.startsWith("q-$h-") || it.startsWith("a-$h-") }
        alvos.forEach { blobs.delete(it) }
        return alvos.size
    }

    private companion object {
        const val TAG = "ChatPendingStore"
    }
}

/** Resumo FNV-1a de 64 bits em hex — nome de arquivo estável, sem o id cru. */
internal fun chatHash(value: String): String {
    var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    value.encodeToByteArray().forEach { b ->
        h = h xor (b.toLong() and 0xFF)
        h *= 0x100000001b3L
    }
    return h.toULong().toString(16).padStart(16, '0')
}
