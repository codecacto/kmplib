package br.com.codecacto.kmplib.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs de fio (wire) do protocolo de sync offline-first — **contrato compartilhado
 * com o backend** (paridade EXATA: o `backlib`/admin-api deve falar exatamente este
 * shape). Mantenha qualquer mudança aqui sincronizada com a rota correspondente do
 * servidor.
 *
 * Convenções:
 *  - Datas são strings ISO-8601 (UTC) — o servidor é a fonte de verdade do tempo.
 *  - `payload` é [JsonElement] (não um tipo selado) para que o app reuse seus DTOs
 *    por-papel sem união de tipos na lib. O app (de)serializa o payload com o
 *    [SyncableEntity.serializer] da entidade.
 *  - O cliente NUNCA decide o vencedor de um conflito: aplica a resposta do servidor.
 *
 * Transporte HTTP é fornecido pelo app via [SyncPort] (sobre o ApiClient dele) — a lib
 * não acopla engine Ktor nem baseUrl.
 */

// ---------------------------------------------------------------------------
// PULL — cliente baixa deltas do servidor
// ---------------------------------------------------------------------------

/**
 * Requisição de pull.
 * @param since cursor da última sincronização aplicada (NULL = full pull). O servidor
 *   interpreta como "mande tudo que mudou depois disto".
 * @param entities filtro opcional de entidades a sincronizar (NULL = todas registradas).
 */
@Serializable
data class SyncPullRequest(
    val since: String? = null,
    val entities: List<String>? = null,
)

/**
 * Um delta de uma entidade vindo do servidor (criação/atualização/exclusão).
 * @param serverId id do servidor (sempre presente em delta).
 * @param clientId clientId original (se o servidor souber correlacionar), p/ remapear localmente.
 * @param deleted `true` = tombstone (apagar localmente).
 * @param updatedAt updatedAt canônico do servidor (vira base de conflito local).
 * @param payload modelo canônico do servidor (NULL quando [deleted]).
 */
@Serializable
data class SyncDelta(
    val serverId: String,
    val clientId: String? = null,
    val deleted: Boolean = false,
    val updatedAt: String,
    val payload: JsonElement? = null,
)

/**
 * Resposta de pull.
 * @param serverTime relógio do servidor no momento da resposta.
 * @param nextCursor cursor a persistir e enviar no próximo pull (NULL = sem avanço).
 * @param hasMore `true` se há mais páginas (chamar pull de novo com [nextCursor]).
 * @param changes mapa entidade → lista de deltas.
 */
@Serializable
data class SyncPullResponse(
    val serverTime: String,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val changes: Map<String, List<SyncDelta>> = emptyMap(),
)

// ---------------------------------------------------------------------------
// PUSH — cliente envia operações da outbox
// ---------------------------------------------------------------------------

/**
 * Uma operação local a ser empurrada para o servidor.
 * @param entity nome lógico da entidade.
 * @param clientId uuid idempotente do registro (correlaciona com [SyncResult]).
 * @param op "create" | "update" | "delete".
 * @param baseUpdatedAt updatedAt-base p/ o servidor detectar conflito (LWW). NULL em create.
 * @param updatedAt updatedAt local provisório do momento da edição.
 * @param payload modelo serializado (NULL em delete).
 * @param refs referências a outras entidades por clientId → o servidor (e o cliente, antes
 *   do envio) remapeiam clientId→serverId. Permite enviar filho antes do pai estar confirmado.
 */
@Serializable
data class SyncOp(
    val entity: String,
    val clientId: String,
    val op: String,
    val baseUpdatedAt: String? = null,
    val updatedAt: String,
    val payload: JsonElement? = null,
    val refs: Map<String, String>? = null,
)

/** Requisição de push: a outbox drenada (ordenada por dependência). */
@Serializable
data class SyncPushRequest(
    val ops: List<SyncOp>,
)

/**
 * Resultado por operação retornado pelo servidor.
 * @param clientId correlaciona com a [SyncOp] enviada.
 * @param serverId id atribuído/confirmado pelo servidor (presente em applied).
 * @param status veja [SyncOpStatus] — "applied" | "conflict" | "rejected".
 * @param serverUpdatedAt updatedAt canônico após aplicar (vira base local).
 * @param reason motivo quando conflict/rejected.
 * @param payload payload canônico do servidor (em conflict, é o vencedor a aplicar).
 */
@Serializable
data class SyncResult(
    val clientId: String,
    val serverId: String? = null,
    val status: String,
    val serverUpdatedAt: String? = null,
    val reason: String? = null,
    val payload: JsonElement? = null,
)

/** Resposta de push. */
@Serializable
data class SyncPushResponse(
    val serverTime: String,
    val results: List<SyncResult> = emptyList(),
)

/** Status canônicos de uma operação de push (string no fio). */
object SyncOpStatus {
    const val APPLIED = "applied"
    const val CONFLICT = "conflict"
    const val REJECTED = "rejected"
}

/** Operações canônicas (string no fio, mapeadas de/para [SyncOp]). */
@Serializable
enum class SyncOp_Kind {
    @SerialName("create") CREATE,
    @SerialName("update") UPDATE,
    @SerialName("delete") DELETE,
}
