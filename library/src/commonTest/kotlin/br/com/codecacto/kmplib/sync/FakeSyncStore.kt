package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [SyncStore] em memória para exercitar o [DefaultSyncEngine] pelo caminho REAL em
 * `commonTest`, sem driver SQLDelight (o módulo não tem driver de banco em teste comum).
 * Mantém o espelho num mapa por (entity, localId) e preserva a ordem de inserção.
 */
class FakeSyncStore : SyncStore {

    private val rows = LinkedHashMap<Pair<String, String>, Synced_entity>()
    private val cursors = HashMap<String, String?>()
    private val visibleFlow = MutableStateFlow(0)

    private fun key(entity: String, localId: String) = entity to localId

    override fun observeVisible(entity: String): Flow<List<Synced_entity>> =
        visibleFlow.map { rows.values.filter { it.entity == entity && !it.deleted.toDbBoolean() } }

    override fun observeVisibleById(entity: String, localId: String): Flow<Synced_entity?> =
        visibleFlow.map { rows[key(entity, localId)]?.takeIf { !it.deleted.toDbBoolean() } }

    override fun observeAllDirty(): Flow<List<Synced_entity>> =
        visibleFlow.map { rows.values.filter { it.dirty.toDbBoolean() } }

    override fun getByLocalId(entity: String, localId: String): Synced_entity? =
        rows[key(entity, localId)]

    override fun getByServerId(entity: String, serverId: String): Synced_entity? =
        rows.values.firstOrNull { it.entity == entity && it.server_id == serverId }

    override fun getByClientId(entity: String, clientId: String): Synced_entity? =
        rows.values.firstOrNull { it.entity == entity && it.client_id == clientId }

    override fun getDirty(entity: String): List<Synced_entity> =
        rows.values.filter { it.entity == entity && it.dirty.toDbBoolean() }

    override fun getAllDirty(): List<Synced_entity> =
        rows.values.filter { it.dirty.toDbBoolean() }

    override fun countDirty(): Long = rows.values.count { it.dirty.toDbBoolean() }.toLong()

    override fun upsert(row: Synced_entity) {
        rows[key(row.entity, row.local_id)] = row
        visibleFlow.value++
    }

    override fun markClean(entity: String, localId: String, serverId: String?, updatedAt: String?, baseUpdatedAt: String?) {
        val existing = rows[key(entity, localId)] ?: return
        rows[key(entity, localId)] = existing.copy(
            server_id = serverId,
            updated_at = updatedAt,
            base_updated_at = baseUpdatedAt,
            dirty = false.toDbLong(),
            pending_op = null,
            last_error = null,
        )
        visibleFlow.value++
    }

    override fun setError(entity: String, localId: String, error: String?) {
        val existing = rows[key(entity, localId)] ?: return
        rows[key(entity, localId)] = existing.copy(last_error = error)
        visibleFlow.value++
    }

    override fun deleteHard(entity: String, localId: String) {
        rows.remove(key(entity, localId))
        visibleFlow.value++
    }

    override fun deleteAll() {
        rows.clear()
        cursors.clear()
        visibleFlow.value++
    }

    override fun transaction(body: () -> Unit) {
        body()
    }

    override fun getCursor(entity: String): String? = cursors[entity]

    override fun setCursor(entity: String, cursor: String?) {
        cursors[entity] = cursor
    }
}
