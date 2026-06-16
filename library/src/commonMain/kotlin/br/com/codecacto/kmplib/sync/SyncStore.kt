package br.com.codecacto.kmplib.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.codecacto.kmplib.sync.db.SyncDatabase
import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * Espelho local do sync (outbox + estado por registro). Abstração fina sobre a
 * persistência: a impl default ([SqlDelightSyncStore]) usa SQLDelight, mas o contrato
 * é uma interface para permitir fakes em `commonTest` (o módulo não tem driver de banco
 * em teste comum) e exercitar o [DefaultSyncEngine] pelo caminho real.
 *
 * **Compatibilidade:** construir com `SyncStore(db)` continua válido — o `operator fun
 * invoke` no companion devolve a impl SQLDelight (call-site idêntico ao anterior, quando
 * `SyncStore` era a classe concreta).
 */
interface SyncStore {

    // -- Leitura reativa (Flow) --------------------------------------------
    fun observeVisible(entity: String): Flow<List<Synced_entity>>
    fun observeVisibleById(entity: String, localId: String): Flow<Synced_entity?>
    fun observeAllDirty(): Flow<List<Synced_entity>>

    // -- Leitura pontual ----------------------------------------------------
    fun getByLocalId(entity: String, localId: String): Synced_entity?
    fun getByServerId(entity: String, serverId: String): Synced_entity?
    fun getByClientId(entity: String, clientId: String): Synced_entity?
    fun getDirty(entity: String): List<Synced_entity>
    fun getAllDirty(): List<Synced_entity>
    fun countDirty(): Long

    // -- Escrita ------------------------------------------------------------
    fun upsert(row: Synced_entity)
    fun markClean(entity: String, localId: String, serverId: String?, updatedAt: String?, baseUpdatedAt: String?)
    fun setError(entity: String, localId: String, error: String?)
    fun deleteHard(entity: String, localId: String)
    fun deleteAll()
    fun transaction(body: () -> Unit)

    // -- Cursor -------------------------------------------------------------
    fun getCursor(entity: String): String?
    fun setCursor(entity: String, cursor: String?)

    companion object {
        /**
         * Cria o espelho default sobre SQLDelight. Mantém o call-site histórico
         * `SyncStore(createSyncDatabase())`.
         */
        operator fun invoke(
            db: SyncDatabase,
            ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): SyncStore = SqlDelightSyncStore(db, ioDispatcher)
    }
}

/**
 * Impl default do [SyncStore] sobre as queries do [SyncDatabase] — isola o SQLDelight do
 * [DefaultSyncEngine]/[SyncableRepository] e centraliza a conversão de tipos
 * (Long↔Boolean do espelho). Não contém regra de sync; só persistência local.
 */
class SqlDelightSyncStore(
    db: SyncDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SyncStore {
    private val q = db.syncEntityQueries

    override fun observeVisible(entity: String): Flow<List<Synced_entity>> =
        q.selectAllVisible(entity).asFlow().mapToList(ioDispatcher)

    override fun observeVisibleById(entity: String, localId: String): Flow<Synced_entity?> =
        q.selectVisibleById(entity, localId).asFlow().mapToOneOrNull(ioDispatcher)

    override fun observeAllDirty(): Flow<List<Synced_entity>> =
        q.selectAllDirty().asFlow().mapToList(ioDispatcher)

    override fun getByLocalId(entity: String, localId: String): Synced_entity? =
        q.selectByLocalId(entity, localId).executeAsOneOrNull()

    override fun getByServerId(entity: String, serverId: String): Synced_entity? =
        q.selectByServerId(entity, serverId).executeAsOneOrNull()

    override fun getByClientId(entity: String, clientId: String): Synced_entity? =
        q.selectByClientId(entity, clientId).executeAsOneOrNull()

    override fun getDirty(entity: String): List<Synced_entity> =
        q.selectDirty(entity).executeAsList()

    override fun getAllDirty(): List<Synced_entity> =
        q.selectAllDirty().executeAsList()

    override fun countDirty(): Long = q.countDirty().executeAsOne()

    override fun upsert(row: Synced_entity) {
        q.upsert(
            entity = row.entity,
            localId = row.local_id,
            serverId = row.server_id,
            clientId = row.client_id,
            payloadJson = row.payload_json,
            updatedAt = row.updated_at,
            dirty = row.dirty,
            pendingOp = row.pending_op,
            deleted = row.deleted,
            baseUpdatedAt = row.base_updated_at,
            lastError = row.last_error,
        )
    }

    override fun markClean(entity: String, localId: String, serverId: String?, updatedAt: String?, baseUpdatedAt: String?) {
        q.markClean(serverId, updatedAt, baseUpdatedAt, entity, localId)
    }

    override fun setError(entity: String, localId: String, error: String?) {
        q.setError(error, entity, localId)
    }

    override fun deleteHard(entity: String, localId: String) {
        q.deleteHard(entity, localId)
    }

    override fun deleteAll() {
        q.deleteAll()
        q.clearCursors()
    }

    override fun transaction(body: () -> Unit) {
        q.transaction { body() }
    }

    override fun getCursor(entity: String): String? =
        q.getCursor(entity).executeAsOneOrNull()?.cursor

    override fun setCursor(entity: String, cursor: String?) {
        q.setCursor(entity, cursor)
    }
}

/** Helpers de conversão Long↔Boolean do espelho. */
internal fun Boolean.toDbLong(): Long = if (this) 1L else 0L
internal fun Long.toDbBoolean(): Boolean = this != 0L
