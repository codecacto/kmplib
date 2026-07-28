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

    /**
     * A linha existe no espelho e **nunca foi confirmada pelo servidor** (`server_id == null`): ela
     * só existe neste aparelho, sob um id local. Toda escrita sobre ela ainda é uma **criação**, e
     * apagá-la se resolve **localmente** — nenhum `PUT`/`DELETE` em `/…/local-…` faz sentido (o
     * servidor não conhece esse id e responderia 404).
     */
    fun isLocalOnly(localId: String): Boolean {
        val linha = store.getByLocalId(name, localId) ?: return false
        return linha.server_id == null
    }

    /** Linhas sujas (outbox) desta entidade, mais antigas primeiro — **inclusive as recusadas**. */
    fun dirtyRows(): List<Synced_entity> = store.getDirty(name)

    /**
     * Linhas **drenáveis** (sujas que ainda não foram recusadas de forma terminal). É o que o push
     * consome: uma linha `Failed` só volta por retry explícito ([clearFailure]).
     */
    fun drainableRows(): List<Synced_entity> = store.getDrainable(name)

    // -- Estado de escrita por linha (2.91.0) ------------------------------

    /** Estado de sync de uma linha: pendente / falhou / sincronizada. */
    fun stateOf(localId: String): RestRowState =
        (store.getByLocalId(name, localId) ?: store.getByServerId(name, localId))
            ?.toRestRowState()
            ?: RestRowState.Synced

    /** Espelho visível **com o estado de cada linha** — a lista que a UI renderiza sem overlay próprio. */
    fun observeVisibleWithState(): Flow<List<RestRow<T>>> =
        store.observeVisible(name).map { rows -> rows.mapNotNull { it.toRestRow() } }

    /** Uma linha visível com o seu estado. */
    fun observeVisibleWithStateById(localId: String): Flow<RestRow<T>?> =
        store.observeVisibleById(name, localId).map { row -> row?.toRestRow() }

    fun getVisibleWithState(): List<RestRow<T>> = store.getVisible(name).mapNotNull { it.toRestRow() }

    /** Linhas que o servidor **recusou** (com o erro preservado). */
    fun failedRows(): List<RestRow<T>> = store.getFailed(name).mapNotNull { it.toRestRow() }

    /** Marca a linha como recusada de forma terminal, preservando código e mensagem do servidor. */
    fun markFailed(localId: String, code: Int, message: String?) =
        store.markFailed(name, localId, code, message)

    /** Devolve a linha recusada à outbox drenável (retry explícito do app). */
    fun clearFailure(localId: String) = store.clearFailed(name, localId)

    /** Conta uma tentativa que falhou de forma **retentável** (a linha continua na outbox). */
    fun bumpAttempt(localId: String, error: String?) = store.bumpAttempt(name, localId, error)

    private fun Synced_entity.toRestRow(): RestRow<T>? =
        decode(payload_json)?.let { RestRow(it, toRestRowState()) }

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

    /**
     * Grava SUJO (offline) enfileirando [op] na outbox — **preservando a operação pendente enquanto
     * a linha ainda não subiu**. `localId` = id local (client id no create).
     *
     * A operação efetivamente gravada sai de [resolveOutboxOp]: enquanto `server_id == null` a linha
     * continua pendente de **CREATE** (um `update` só troca o payload), e um `delete` sobre ela
     * **remove a linha localmente** em vez de enfileirar um `DELETE` que só produziria 404.
     */
    fun putDirty(model: T, op: SyncOpType) {
        val localId = idOf(model)
        val existing = store.getByLocalId(name, localId)
        val efetiva = resolveOutboxOp(
            requested = op,
            knownLocally = existing != null,
            hasServerId = existing?.server_id != null,
        ) ?: run { store.deleteHard(name, localId); return }
        store.upsert(
            row(
                model,
                serverId = existing?.server_id,
                dirty = true,
                pendingOp = efetiva.wire,
                clientId = existing?.client_id ?: localId,
                updatedAt = null,
            ),
        )
    }

    /**
     * Marca exclusão (tombstone) mantendo o payload para replay do delete na reconexão.
     *
     * **Linha que nunca subiu** (`server_id == null` — ainda pendente de CREATE) é **removida de
     * vez**: não há nada a apagar no servidor, e enfileirar um `DELETE /…/local-…` só renderia um
     * 404 previsível, que a máquina de estados classificaria como recusa terminal.
     */
    fun tombstone(localId: String) {
        val existing = store.getByLocalId(name, localId) ?: return
        val efetiva = resolveOutboxOp(
            requested = SyncOpType.DELETE,
            knownLocally = true,
            hasServerId = existing.server_id != null,
        )
        if (efetiva == null) {
            store.deleteHard(name, localId)
            return
        }
        store.upsert(
            existing.copy(
                dirty = 1L,
                pending_op = SyncOpType.DELETE.wire,
                deleted = 1L,
                // Intenção nova do usuário: sai do estado "recusado" e volta à outbox drenável.
                failed = 0L,
                fail_code = null,
                last_error = null,
            ),
        )
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

    /**
     * Monta a linha do espelho. `account_id` sai em branco de propósito: **quem escopa é o
     * [SyncStore]**, que grava sempre na conta corrente — assim nenhuma linha pode ser escrita no
     * bucket de outra conta por engano.
     *
     * Escrever (limpo ou sujo) **zera o estado de falha**: uma alteração nova do usuário substitui
     * a recusa anterior e volta à outbox drenável.
     */
    private fun row(
        model: T,
        serverId: String?,
        dirty: Boolean,
        pendingOp: String?,
        clientId: String = idOf(model),
        updatedAt: String?,
    ): Synced_entity = Synced_entity(
        account_id = "",
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
        failed = 0L,
        fail_code = null,
        attempts = 0L,
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
