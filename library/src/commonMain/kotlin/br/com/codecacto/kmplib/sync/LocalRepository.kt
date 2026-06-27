package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Repositório de **persistência local pura** (arquétipo A — app 100% no dispositivo, SEM backend
 * e SEM sincronização). Ex.: Super 8, Calculadora BTU — dados que nascem e morrem no aparelho.
 *
 * **Reusa a MESMA infra SQLDelight do offline-first** ([SyncStore]/[createSyncDatabase] — tabela
 * `synced_entity`), mas, ao contrário do [SyncableRepository], **não usa [SyncEngine], outbox nem
 * rede**: cada escrita grava o registro já "limpo" (`dirty=0`, sem `pending_op`) e cada exclusão é
 * física (hard delete) — não há servidor para confirmar tombstone. Isso evita acumular fila de push
 * inútil num app que nunca sincroniza.
 *
 * Se o app precisar sincronizar com a API central, use [SyncableRepository] + [DefaultSyncEngine]
 * (e, como transporte, o [RestSyncPort]) — NÃO este.
 *
 * ### Padrão de uso (Koin)
 * ```kotlin
 * single { createSyncDatabase() }                 // 1 banco por app
 * single<SyncStore> { SyncStore(get()) }
 * single { LocalRepository(TournamentLocal, get()) }   // 1 por entidade
 * ```
 * onde `TournamentLocal : SyncableEntity<Tournament>` descreve nome/serializer/clientId da entidade.
 * Leitura é SEMPRE reativa do banco ([observeAll]/[observeById]) — a UI reflete na hora.
 *
 * @param T modelo de domínio (`@Serializable`).
 * @param entity descritor da entidade ([SyncableEntity]) — reusa o mesmo contrato do sync.
 * @param store espelho local (SQLDelight).
 * @param json Json para (de)serializar o payload (default o do módulo sync).
 * @param now relógio injetável (ISO-8601) p/ `updated_at` provisório — testável.
 */
open class LocalRepository<T : Any>(
    protected val entity: SyncableEntity<T>,
    protected val store: SyncStore,
    protected val json: Json = defaultSyncJson,
    private val now: () -> String = ::isoNow,
) {
    /** Observa (reativo) todos os registros da entidade, mais recentes primeiro. */
    fun observeAll(): Flow<List<T>> =
        store.observeVisible(entity.name).map { rows -> rows.mapNotNull { decode(it.payload_json) } }

    /** Observa (reativo) um registro por id local (== clientId). */
    fun observeById(localId: String): Flow<T?> =
        store.observeVisibleById(entity.name, localId).map { row -> row?.let { decode(it.payload_json) } }

    /** Lê (síncrono) todos os registros da entidade. */
    fun getAll(): List<T> =
        store.getVisible(entity.name).mapNotNull { decode(it.payload_json) }

    /** Lê (síncrono) um registro por id local / clientId / serverId; `null` se não existir. */
    fun get(id: String): T? {
        val row = store.getByLocalId(entity.name, id)
            ?: store.getByClientId(entity.name, id)
            ?: store.getByServerId(entity.name, id)
        return row?.let { decode(it.payload_json) }
    }

    /**
     * Insere/atualiza o registro localmente (gravação limpa, sem enfileirar push). O id local é o
     * [SyncableEntity.clientIdOf] do modelo — passe o mesmo clientId para atualizar.
     */
    suspend fun put(model: T) {
        val clientId = entity.clientIdOf(model)
        store.upsert(
            Synced_entity(
                entity = entity.name,
                local_id = clientId,
                server_id = entity.serverIdOf(model),
                client_id = clientId,
                payload_json = json.encodeToString(entity.serializer, model),
                updated_at = entity.updatedAtOf(model) ?: now(),
                dirty = false.toDbLong(),
                pending_op = null,
                deleted = false.toDbLong(),
                base_updated_at = null,
                last_error = null,
            ),
        )
    }

    /** Insere/atualiza vários registros numa única transação. */
    suspend fun putAll(models: List<T>) {
        store.transaction { models.forEach { runCatchingPut(it) } }
    }

    /** Remove (físico) o registro por id local. No-op se não existir. */
    suspend fun delete(localId: String) {
        store.deleteHard(entity.name, localId)
    }

    /** Remove TODOS os registros visíveis desta entidade (não toca outras entidades). */
    suspend fun clear() {
        store.transaction {
            store.getVisible(entity.name).forEach { store.deleteHard(entity.name, it.local_id) }
        }
    }

    private fun runCatchingPut(model: T) {
        val clientId = entity.clientIdOf(model)
        store.upsert(
            Synced_entity(
                entity = entity.name,
                local_id = clientId,
                server_id = entity.serverIdOf(model),
                client_id = clientId,
                payload_json = json.encodeToString(entity.serializer, model),
                updated_at = entity.updatedAtOf(model) ?: now(),
                dirty = false.toDbLong(),
                pending_op = null,
                deleted = false.toDbLong(),
                base_updated_at = null,
                last_error = null,
            ),
        )
    }

    private fun decode(payloadJson: String): T? =
        runCatching { json.decodeFromString(entity.serializer, payloadJson) }.getOrNull()
}
