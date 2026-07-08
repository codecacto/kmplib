package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.SyncStore
import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Espelho local (offline-first) de UMA entidade de domínio, sobre o [SyncStore] SQLDelight da lib
 * (tabela `synced_entity`). Reusa a MESMA infra de persistência do módulo `sync`, mas com **semântica
 * REST-CRUD** (não o protocolo `/pull`+`/push` do
 * [DefaultSyncEngine][br.com.codecacto.kmplib.sync.DefaultSyncEngine], que backends REST-CRUD não
 * oferecem): escritas gravam **limpo** quando confirmadas online, ou **sujo** (outbox) quando offline;
 * a reconciliação é por **GET de lista** (não por delta/cursor).
 *
 * `pending_op` marca a operação pendente na outbox (`create`|`update`|`delete`, do [SyncOpType.wire]);
 * `dirty=1` = há mudança local ainda não confirmada. É a peça de baixo nível usada pelo
 * [OfflineFirstRestRepository]; expõe operações finas para os endpoints **custom** do app (ex.: um
 * `PATCH /.../status`) reconciliarem o espelho sem passar pela CRUD genérica.
 *
 * @param T modelo de domínio (`@Serializable`).
 * @param name nome lógico estável da entidade (chave no espelho). Ex.: "empresa".
 * @param store espelho local (SQLDelight).
 * @param serializer serializer kotlinx do modelo (payload_json).
 * @param idOf id canônico do modelo (server id quando sincronizado; client id offline).
 * @param json Json tolerante (default [restMirrorJson]).
 */
class RestEntityMirror<T : Any>(
    private val name: String,
    private val store: SyncStore,
    private val serializer: KSerializer<T>,
    private val idOf: (T) -> String,
    private val json: Json = restMirrorJson,
) {
    // -- Leitura -----------------------------------------------------------

    fun observeVisible(): Flow<List<T>> =
        store.observeVisible(name).map { rows -> rows.mapNotNull { decode(it.payload_json) } }

    fun observeVisibleById(localId: String): Flow<T?> =
        store.observeVisibleById(name, localId).map { row -> row?.let { decode(it.payload_json) } }

    fun getVisible(): List<T> = store.getVisible(name).mapNotNull { decode(it.payload_json) }

    fun get(localId: String): T? =
        (store.getByLocalId(name, localId) ?: store.getByServerId(name, localId))
            ?.let { decode(it.payload_json) }

    /** Server id conhecido para um id local (== client id offline). `null` se ainda não sincronizado. */
    fun serverIdOf(localId: String): String? = store.getByLocalId(name, localId)?.server_id

    /** Linhas sujas (outbox) desta entidade, mais antigas primeiro. */
    fun dirtyRows(): List<Synced_entity> = store.getDirty(name)

    // -- Escrita -----------------------------------------------------------

    /** Grava/atualiza LIMPO (confirmado pelo servidor). `localId` = id do servidor. */
    fun putClean(model: T, serverId: String = idOf(model), updatedAt: String? = null) {
        store.upsert(row(model, serverId = serverId, dirty = false, pendingOp = null, updatedAt = updatedAt))
    }

    /** Reconcilia o conjunto vindo do servidor: substitui as linhas LIMPAS, preservando as sujas. */
    fun reconcile(serverModels: List<T>) {
        store.transaction {
            val serverIds = serverModels.mapTo(mutableSetOf()) { idOf(it) }
            // Remove linhas limpas locais que sumiram do servidor (soft-delete server-side refletido).
            store.getVisible(name).forEach { row ->
                val stillClean = row.dirty == 0L && row.server_id != null
                if (stillClean && row.server_id !in serverIds) store.deleteHard(name, row.local_id)
            }
            // Upsert limpo dos itens do servidor que NÃO têm edição local pendente.
            serverModels.forEach { model ->
                val id = idOf(model)
                val existing = store.getByLocalId(name, id)
                if (existing == null || existing.dirty == 0L) {
                    store.upsert(row(model, serverId = id, dirty = false, pendingOp = null, updatedAt = null))
                }
            }
        }
    }

    /**
     * Upsert LIMPO de [serverModels] **preservando** linhas com edição local pendente (dirty) — a
     * metade "aditiva" da [reconcile], **sem** remover nada. Serve o refresh **upsert-only** de domínios
     * com paginação/busca **server-side pura** (o espelho é cache PARCIAL de janelas visitadas, não o
     * dataset completo): ali apagar "o que não veio nesta página" removeria itens de outras páginas.
     * Diferente da [reconcile], que assume o conjunto COMPLETO e pode `deleteHard` os ausentes.
     */
    fun mergeClean(serverModels: List<T>) {
        store.transaction {
            serverModels.forEach { model ->
                val id = idOf(model)
                val existing = store.getByLocalId(name, id)
                if (existing == null || existing.dirty == 0L) {
                    store.upsert(row(model, serverId = id, dirty = false, pendingOp = null, updatedAt = null))
                }
            }
        }
    }

    /** Grava SUJO (offline) enfileirando [op] na outbox. `localId` = id local (client id no create). */
    fun putDirty(model: T, op: SyncOpType) {
        val localId = idOf(model)
        val existing = store.getByLocalId(name, localId)
        store.upsert(
            row(
                model,
                serverId = existing?.server_id,
                dirty = true,
                pendingOp = op.wire,
                clientId = existing?.client_id ?: localId,
                updatedAt = null,
            ),
        )
    }

    /** Marca exclusão (tombstone) mantendo o payload para replay do delete na reconexão. */
    fun tombstone(localId: String) {
        val existing = store.getByLocalId(name, localId) ?: return
        store.upsert(existing.copy(dirty = 1L, pending_op = SyncOpType.DELETE.wire, deleted = 1L))
    }

    /** Após confirmar no servidor: migra o id local (client id) para o server id e grava LIMPO. */
    fun markSynced(oldLocalId: String, serverModel: T) {
        val serverId = idOf(serverModel)
        store.transaction {
            if (oldLocalId != serverId) store.deleteHard(name, oldLocalId)
            store.upsert(row(serverModel, serverId = serverId, dirty = false, pendingOp = null, updatedAt = null))
        }
    }

    /** Confirma um update/status (mesmo id) como LIMPO. */
    fun confirm(model: T) {
        store.upsert(row(model, serverId = idOf(model), dirty = false, pendingOp = null, updatedAt = null))
    }

    /**
     * Atualiza o payload de um registro **preservando** seu estado de sync (dirty/pending_op/server_id).
     * Usado para ajustes locais que não devem virar nova operação de outbox nem apagar uma pendente
     * (ex.: contagem denormalizada de filhos após upload/remoção online).
     */
    fun patch(model: T) {
        val localId = idOf(model)
        val existing = store.getByLocalId(name, localId) ?: run { confirm(model); return }
        store.upsert(existing.copy(payload_json = json.encodeToString(serializer, model)))
    }

    /** Remoção física local (após confirmar o delete no servidor, ou reconciliação). */
    fun removeHard(localId: String) = store.deleteHard(name, localId)

    private fun row(
        model: T,
        serverId: String?,
        dirty: Boolean,
        pendingOp: String?,
        clientId: String = idOf(model),
        updatedAt: String?,
    ): Synced_entity = Synced_entity(
        entity = name,
        local_id = serverId ?: idOf(model),
        server_id = serverId,
        client_id = clientId,
        payload_json = json.encodeToString(serializer, model),
        updated_at = updatedAt,
        dirty = if (dirty) 1L else 0L,
        pending_op = pendingOp,
        deleted = 0L,
        base_updated_at = null,
        last_error = null,
    )

    fun decode(payloadJson: String): T? =
        runCatching { json.decodeFromString(serializer, payloadJson) }.getOrNull()
}

/** Json tolerante do espelho REST-CRUD (mesma política do módulo `sync`). */
val restMirrorJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}
