package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * [SyncStore] em memória para exercitar o [DefaultSyncEngine] e a camada REST-CRUD pelo caminho
 * REAL em `commonTest`, sem driver SQLDelight (o módulo não tem driver de banco em teste comum).
 *
 * Espelha fielmente o [SqlDelightSyncStore], inclusive o **escopo de conta** (2.91.0): as linhas
 * ficam num mapa por `(account, entity, localId)` e toda leitura/escrita usa o titular corrente.
 */
class FakeSyncStore : SyncStore {

    private data class Key(val account: String, val entity: String, val localId: String)

    private val rows = LinkedHashMap<Key, Synced_entity>()
    private val cursors = HashMap<Pair<String, String>, String?>()

    /** Remap durável `clientId → serverId`, escopado por conta (espelha `sync_id_remap`). */
    private val remap = LinkedHashMap<Pair<String, String>, String>()
    private val revision = MutableStateFlow(0)

    private val _accountScope = MutableStateFlow(SyncStore.NO_ACCOUNT)
    override val accountScope: StateFlow<String> = _accountScope.asStateFlow()

    private val account: String get() = _accountScope.value

    private fun key(entity: String, localId: String) = Key(account, entity, localId)

    override fun setAccountScope(accountId: String?, legacy: LegacyRowsPolicy) {
        val titular = accountId?.trim().orEmpty()
        if (titular.isEmpty()) {
            _accountScope.value = SyncStore.NO_ACCOUNT
            return
        }
        val legadas = rows.keys.filter { it.account == SyncStore.NO_ACCOUNT }
        val remapLegado = remap.keys.filter { it.first == SyncStore.NO_ACCOUNT }
        when (legacy) {
            LegacyRowsPolicy.Adopt -> {
                legadas.forEach { k ->
                    val row = rows.remove(k) ?: return@forEach
                    rows[k.copy(account = titular)] = row
                }
                remapLegado.forEach { k ->
                    val v = remap.remove(k) ?: return@forEach
                    remap[titular to k.second] = v
                }
            }
            LegacyRowsPolicy.Discard -> {
                legadas.forEach { rows.remove(it) }
                remapLegado.forEach { remap.remove(it) }
            }
            LegacyRowsPolicy.Isolate -> Unit
        }
        _accountScope.value = titular
        revision.value++
    }

    override fun countLegacyRows(): Long = rows.keys.count { it.account == SyncStore.NO_ACCOUNT }.toLong()

    override fun deleteAccountData(accountId: String) {
        rows.keys.filter { it.account == accountId }.forEach { rows.remove(it) }
        cursors.keys.filter { it.first == accountId }.forEach { cursors.remove(it) }
        remap.keys.filter { it.first == accountId }.forEach { remap.remove(it) }
        revision.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVisible(entity: String): Flow<List<Synced_entity>> =
        combine(revision, _accountScope) { _, acc -> acc }
            .map { acc -> rows.entries.filter { it.key.account == acc && it.key.entity == entity && !it.value.deleted.toDbBoolean() }.map { it.value } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVisibleById(entity: String, localId: String): Flow<Synced_entity?> =
        combine(revision, _accountScope) { _, acc -> acc }
            .map { acc -> rows[Key(acc, entity, localId)]?.takeIf { !it.deleted.toDbBoolean() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeVisibleByHandle(entity: String, handle: String): Flow<Synced_entity?> =
        combine(revision, _accountScope) { _, acc -> acc }
            .map { acc -> findByHandle(acc, entity, handle)?.takeIf { !it.deleted.toDbBoolean() } }

    /** Espelha o `selectVisibleByHandle`/`selectByHandle`: casa local_id, depois server_id, depois client_id. */
    private fun findByHandle(acc: String, entity: String, handle: String): Synced_entity? {
        rows[Key(acc, entity, handle)]?.let { return it }
        val candidatos = rows.entries.filter { it.key.account == acc && it.key.entity == entity }
        return candidatos.firstOrNull { it.value.server_id == handle }?.value
            ?: candidatos.firstOrNull { it.value.client_id == handle }?.value
    }

    override fun getByHandle(entity: String, handle: String): Synced_entity? =
        findByHandle(account, entity, handle)

    override fun observeAllDirty(): Flow<List<Synced_entity>> =
        combine(revision, _accountScope) { _, acc -> acc }
            .map { acc -> rows.entries.filter { it.key.account == acc && it.value.dirty.toDbBoolean() }.map { it.value } }

    override fun getVisible(entity: String): List<Synced_entity> =
        rows.entries.filter { it.key.account == account && it.key.entity == entity && !it.value.deleted.toDbBoolean() }.map { it.value }

    override fun getByLocalId(entity: String, localId: String): Synced_entity? =
        rows[key(entity, localId)]

    override fun getByServerId(entity: String, serverId: String): Synced_entity? =
        rows.entries.firstOrNull { it.key.account == account && it.key.entity == entity && it.value.server_id == serverId }?.value

    override fun getByClientId(entity: String, clientId: String): Synced_entity? =
        rows.entries.firstOrNull { it.key.account == account && it.key.entity == entity && it.value.client_id == clientId }?.value

    override fun getDirty(entity: String): List<Synced_entity> =
        rows.entries.filter { it.key.account == account && it.key.entity == entity && it.value.dirty.toDbBoolean() }.map { it.value }

    override fun getDrainable(entity: String): List<Synced_entity> =
        getDirty(entity).filter { it.failed == 0L }

    override fun getFailed(entity: String): List<Synced_entity> =
        getDirty(entity).filter { it.failed != 0L }

    override fun getAllDirty(): List<Synced_entity> =
        rows.entries.filter { it.key.account == account && it.value.dirty.toDbBoolean() }.map { it.value }

    /** Espelha o `selectEntityAllAccounts`: ignora o escopo (só a varredura de blobs órfãos usa). */
    override fun getRowsAcrossAccounts(entity: String): List<Synced_entity> =
        rows.entries.filter { it.key.entity == entity }.map { it.value }

    override fun countDirty(): Long = getAllDirty().size.toLong()

    override fun upsert(row: Synced_entity) {
        rows[key(row.entity, row.local_id)] = row.copy(account_id = account)
        revision.value++
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
            failed = 0L,
            fail_code = null,
            attempts = 0L,
            // O servidor aceitou: o histórico de entrega cumpriu o papel e é zerado junto.
            rejections = 0L,
            reject_code = null,
            reject_error = null,
        )
        revision.value++
    }

    override fun setError(entity: String, localId: String, error: String?) {
        val existing = rows[key(entity, localId)] ?: return
        rows[key(entity, localId)] = existing.copy(last_error = error)
        revision.value++
    }

    override fun markFailed(entity: String, localId: String, code: Int, error: String?) {
        val existing = rows[key(entity, localId)] ?: return
        rows[key(entity, localId)] = existing.copy(
            failed = 1L,
            fail_code = code.toLong(),
            last_error = error,
            attempts = existing.attempts + 1,
            deleted = 0L,
            // Histórico: sobrevive à próxima escrita do usuário (espelha `markFailed` da SQL).
            rejections = existing.rejections + 1,
            reject_code = code.toLong(),
            reject_error = error,
        )
        revision.value++
    }

    /** Retry explícito: NÃO apaga o histórico — pedir "tentar de novo" não desfaz a recusa. */
    override fun clearFailed(entity: String, localId: String) {
        val existing = rows[key(entity, localId)] ?: return
        rows[key(entity, localId)] = existing.copy(failed = 0L, fail_code = null, last_error = null)
        revision.value++
    }

    override fun bumpAttempt(entity: String, localId: String, error: String?) {
        val existing = rows[key(entity, localId)] ?: return
        rows[key(entity, localId)] = existing.copy(attempts = existing.attempts + 1, last_error = error)
        revision.value++
    }

    override fun deleteHard(entity: String, localId: String) {
        rows.remove(key(entity, localId))
        revision.value++
    }

    override fun rememberServerId(entity: String, clientId: String, serverId: String) {
        if (clientId.isBlank() || serverId.isBlank() || clientId == serverId) return
        remap[account to clientId] = serverId
    }

    override fun resolveServerId(clientId: String): String? = remap[account to clientId]

    override fun resolveClientId(serverId: String): String? =
        remap.entries.firstOrNull { it.key.first == account && it.value == serverId }?.key?.second

    override fun countIdRemap(): Long = remap.keys.count { it.first == account }.toLong()

    override fun forgetServerId(clientId: String) {
        remap.remove(account to clientId)
    }

    override fun deleteAll() {
        rows.clear()
        cursors.clear()
        remap.clear()
        revision.value++
    }

    override fun transaction(body: () -> Unit) {
        body()
    }

    override fun getCursor(entity: String): String? = cursors[account to entity]

    override fun setCursor(entity: String, cursor: String?) {
        cursors[account to entity] = cursor
    }
}
