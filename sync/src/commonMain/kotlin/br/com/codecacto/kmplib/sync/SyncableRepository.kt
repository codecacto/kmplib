package br.com.codecacto.kmplib.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Repositório base offline-first para uma entidade [T]. O app estende esta classe
 * (ou a usa por composição) para cada entidade sincronizável.
 *
 * Padrão de leitura: SEMPRE do espelho local (SQLDelight) via [observeAll]/[observeById]
 * — a UI nunca espera a rede. Padrão de escrita: grava o espelho + enfileira a op na
 * outbox via [upsertLocal]/[deleteLocal]; o [SyncEngine] drena depois.
 *
 * @param entity descritor da entidade ([SyncableEntity]).
 * @param store espelho local.
 * @param engine motor de sync (recebe as ops enfileiradas).
 * @param json Json para (de)serializar o payload (default o do engine).
 */
open class SyncableRepository<T : Any>(
    protected val entity: SyncableEntity<T>,
    protected val store: SyncStore,
    protected val engine: SyncEngine,
    protected val json: Json = defaultSyncJson,
) {
    /** Observa todos os registros visíveis (não-deletados) da entidade. */
    fun observeAll(): Flow<List<T>> =
        store.observeVisible(entity.name).map { rows ->
            rows.mapNotNull { decode(it.payload_json) }
        }

    /** Observa um registro por id local (== clientId). */
    fun observeById(localId: String): Flow<T?> =
        store.observeVisibleById(entity.name, localId).map { row ->
            row?.let { decode(it.payload_json) }
        }

    /** Lê (síncrono) o modelo em cache por id local ou serverId. */
    fun getCached(id: String): T? {
        val row = store.getByLocalId(entity.name, id)
            ?: store.getByServerId(entity.name, id)
            ?: store.getByClientId(entity.name, id)
        return row?.let { decode(it.payload_json) }
    }

    /**
     * Grava/atualiza o modelo no espelho e enfileira create/update na outbox.
     * Use no fluxo de salvar do app (a UI reflete imediatamente via [observeAll]).
     */
    suspend fun upsertLocal(model: T) {
        val op = if (store.getByLocalId(entity.name, entity.clientIdOf(model))?.server_id != null ||
            entity.serverIdOf(model) != null
        ) SyncOpType.UPDATE else SyncOpType.CREATE
        engine.enqueue(entity.name, op, model)
    }

    /** Marca o registro como excluído (tombstone) e enfileira o delete. */
    suspend fun deleteLocal(localId: String) {
        val model = getCached(localId) ?: return
        engine.enqueue(entity.name, SyncOpType.DELETE, model)
    }

    private fun decode(payloadJson: String): T? =
        runCatching { json.decodeFromString(entity.serializer, payloadJson) }.getOrNull()
}
