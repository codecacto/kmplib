package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Implementação default do [SyncEngine] (reconciliador push→pull, LWW server-side).
 *
 * @param store persistência local (espelho SQLDelight via [SyncStore]).
 * @param port transporte de sync fornecido pelo app.
 * @param json Json para (de)serializar payloads.
 * @param scope escopo das coroutines do auto-sync (default Supervisor próprio).
 * @param now provedor de timestamp ISO-8601 local provisório (injetável p/ teste).
 */
class DefaultSyncEngine(
    private val store: SyncStore,
    private val port: SyncPort,
    private val json: Json = defaultSyncJson,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
    private val now: () -> String = ::isoNow,
) : SyncEngine {

    private val registry = LinkedHashMap<String, SyncableEntity<*>>()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    override val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<SyncQueueItem>>(emptyList())
    override val queue: StateFlow<List<SyncQueueItem>> = _queue.asStateFlow()

    private val syncMutex = Mutex()
    private var autoSyncJob: Job? = null
    private var lastOnline: Boolean? = null

    override fun <T : Any> register(entity: SyncableEntity<T>) {
        registry[entity.name] = entity
        refreshQueue()
    }

    @Suppress("UNCHECKED_CAST")
    private fun entityFor(name: String): SyncableEntity<Any>? =
        registry[name] as SyncableEntity<Any>?

    // ----------------------------------------------------------------------
    // Enqueue (outbox)
    // ----------------------------------------------------------------------

    override suspend fun enqueue(entity: String, op: SyncOpType, model: Any) {
        val syncable = entityFor(entity)
            ?: error("Entidade '$entity' não registrada no SyncEngine.")
        val clientId = syncable.clientIdOf(model)
        val serverId = syncable.serverIdOf(model)
        val localId = clientId
        val existing = store.getByLocalId(entity, localId)
        val payloadJson = json.encodeToString(syncable.serializer, model)
        val updatedAt = syncable.updatedAtOf(model) ?: now()

        if (op == SyncOpType.DELETE) {
            // Tombstone: marca deleted + dirty. Se nunca foi ao servidor, apaga direto.
            if (existing?.server_id == null && serverId == null) {
                store.deleteHard(entity, localId)
            } else {
                store.upsert(
                    (existing ?: newRow(entity, localId, clientId, serverId, payloadJson)).copy(
                        deleted = true.toDbLong(),
                        dirty = true.toDbLong(),
                        pending_op = SyncOpType.DELETE.wire,
                        updated_at = updatedAt,
                        last_error = null,
                    )
                )
            }
        } else {
            val resolvedOp = if (existing?.server_id != null || serverId != null) SyncOpType.UPDATE else SyncOpType.CREATE
            store.upsert(
                Synced_entity(
                    account_id = "",
                    entity = entity,
                    local_id = localId,
                    server_id = serverId ?: existing?.server_id,
                    client_id = clientId,
                    payload_json = payloadJson,
                    updated_at = updatedAt,
                    dirty = true.toDbLong(),
                    pending_op = resolvedOp.wire,
                    deleted = false.toDbLong(),
                    base_updated_at = existing?.base_updated_at,
                    last_error = null,
                    failed = 0L,
                    fail_code = null,
                    attempts = 0L,
                )
            )
        }
        refreshQueue()
    }

    private fun newRow(
        entity: String,
        localId: String,
        clientId: String,
        serverId: String?,
        payloadJson: String,
    ) = Synced_entity(
        account_id = "",
        entity = entity,
        local_id = localId,
        server_id = serverId,
        client_id = clientId,
        payload_json = payloadJson,
        updated_at = now(),
        dirty = false.toDbLong(),
        pending_op = null,
        deleted = false.toDbLong(),
        base_updated_at = null,
        last_error = null,
        failed = 0L,
        fail_code = null,
        attempts = 0L,
    )

    // ----------------------------------------------------------------------
    // PUSH
    // ----------------------------------------------------------------------

    override suspend fun push(): SyncResultSummary = syncMutex.withLock { pushInternal() }

    private suspend fun pushInternal(): SyncResultSummary {
        // Outbox na ordem de dependência (ordem de registro das entidades, depois updated_at).
        val dirtyRows = store.getAllDirty().sortedBy { registryIndex(it.entity) }
        if (dirtyRows.isEmpty()) return SyncResultSummary.EMPTY

        _state.value = SyncState.Syncing(dirtyRows.size)
        markRowsSyncing(dirtyRows)

        val ops = dirtyRows.mapNotNull { row -> row.toSyncOp() }
        return try {
            val resp = port.push(SyncPushRequest(ops))
            applyPushResults(resp)
        } catch (e: Throwable) {
            _state.value = SyncState.Error(e.message ?: "Falha ao enviar alterações")
            refreshQueue()
            SyncResultSummary.failure(e.message ?: "push failed")
        }
    }

    private fun Synced_entity.toSyncOp(): SyncOp? {
        val opType = SyncOpType.fromWire(pending_op ?: return null)
        val rawPayload: JsonElement? = if (opType == SyncOpType.DELETE) null
        else runCatching { json.parseToJsonElement(payload_json) }.getOrNull()

        // Resolve as FKs por-clientId declaradas pela entidade (refsOf):
        //  - pai já com serverId no espelho → backfill no payload (FK canônica direto);
        //  - pai ainda pendente no lote      → entra em SyncOp.refs (servidor resolve).
        // Garante que o servidor NUNCA receba uma FK que seja clientId local órfão sem refs.
        val declaredRefs = declaredRefsOf(this)
        val resolved = SyncRefResolver.resolve(
            payload = rawPayload,
            declaredRefs = declaredRefs,
            resolveServerId = { parentClientId -> serverIdForClientId(parentClientId) },
        )

        return SyncOp(
            entity = entity,
            clientId = client_id,
            op = opType.wire,
            baseUpdatedAt = base_updated_at,
            updatedAt = updated_at ?: isoNow(),
            payload = resolved.payload,
            refs = resolved.refs,
        )
    }

    /** Decodifica o payload espelhado e extrai as FKs por-clientId declaradas pela entidade. */
    private fun declaredRefsOf(row: Synced_entity): Map<String, String> {
        val syncable = entityFor(row.entity) ?: return emptyMap()
        val model = runCatching { json.decodeFromString(syncable.serializer, row.payload_json) }
            .getOrNull() ?: return emptyMap()
        return runCatching { syncable.refsOf(model) }.getOrDefault(emptyMap())
    }

    /**
     * serverId conhecido de um clientId de pai (qualquer entidade registrada). NULL se o pai
     * ainda não foi sincronizado (sem serverId no espelho) — caso em que vai para [SyncOp.refs].
     */
    private fun serverIdForClientId(clientId: String): String? {
        for (name in registry.keys) {
            store.getByClientId(name, clientId)?.let { return it.server_id }
        }
        return null
    }

    private fun applyPushResults(resp: SyncPushResponse): SyncResultSummary {
        var pushed = 0
        var conflicts = 0
        var rejected = 0
        store.transaction {
            for (result in resp.results) {
                val row = findRowByClientId(result.clientId) ?: continue
                when (result.status) {
                    SyncOpStatus.APPLIED -> {
                        pushed++
                        if (row.deleted.toDbBoolean()) {
                            // tombstone confirmado → remove de vez
                            store.deleteHard(row.entity, row.local_id)
                        } else {
                            store.markClean(
                                entity = row.entity,
                                localId = row.local_id,
                                serverId = result.serverId ?: row.server_id,
                                updatedAt = result.serverUpdatedAt ?: row.updated_at,
                                baseUpdatedAt = result.serverUpdatedAt ?: row.updated_at,
                            )
                        }
                    }
                    SyncOpStatus.CONFLICT -> {
                        conflicts++
                        // Servidor venceu: aplica o payload canônico e marca limpo.
                        applyServerCanonical(row, result.serverId, result.serverUpdatedAt, result.payload)
                    }
                    SyncOpStatus.REJECTED -> {
                        rejected++
                        store.setError(row.entity, row.local_id, result.reason ?: "Rejeitado pelo servidor")
                    }
                }
            }
        }
        _state.value = if (store.countDirty() > 0L) SyncState.Syncing(store.countDirty().toInt()) else SyncState.Idle
        refreshQueue()
        return SyncResultSummary(pushed = pushed, conflicts = conflicts, rejected = rejected)
    }

    private fun applyServerCanonical(
        row: Synced_entity,
        serverId: String?,
        serverUpdatedAt: String?,
        payload: JsonElement?,
    ) {
        if (payload == null) {
            // conflito de exclusão sem payload → trata como deletado pelo servidor
            store.deleteHard(row.entity, row.local_id)
            return
        }
        store.upsert(
            row.copy(
                server_id = serverId ?: row.server_id,
                payload_json = json.encodeToString(JsonElement.serializer(), payload),
                updated_at = serverUpdatedAt ?: row.updated_at,
                base_updated_at = serverUpdatedAt ?: row.updated_at,
                dirty = false.toDbLong(),
                pending_op = null,
                deleted = false.toDbLong(),
                last_error = null,
            )
        )
    }

    // ----------------------------------------------------------------------
    // PULL
    // ----------------------------------------------------------------------

    override suspend fun pull(): SyncResultSummary = syncMutex.withLock { pullInternal() }

    private suspend fun pullInternal(): SyncResultSummary {
        val entities = registry.keys.toList()
        if (entities.isEmpty()) return SyncResultSummary.EMPTY

        // Usa o menor cursor entre as entidades como `since` global; o servidor pode
        // ignorar e paginar. Cursores por-entidade são avançados no nextCursor.
        var since = entities.firstNotNullOfOrNull { store.getCursor(it) }
        var pulled = 0
        var deletedApplied = 0
        try {
            while (true) {
                val resp = port.pull(SyncPullRequest(since = since, entities = entities))
                store.transaction {
                    for ((entityName, deltas) in resp.changes) {
                        for (delta in deltas) {
                            if (applyDelta(entityName, delta)) deletedApplied++ else pulled++
                        }
                        resp.nextCursor?.let { store.setCursor(entityName, it) }
                    }
                }
                since = resp.nextCursor
                if (!resp.hasMore || since == null) break
            }
            _state.value = if (store.countDirty() > 0L) SyncState.Syncing(store.countDirty().toInt()) else SyncState.Idle
            refreshQueue()
            return SyncResultSummary(pulled = pulled, deletedApplied = deletedApplied)
        } catch (e: Throwable) {
            _state.value = SyncState.Error(e.message ?: "Falha ao baixar alterações")
            return SyncResultSummary.failure(e.message ?: "pull failed")
        }
    }

    /** Aplica um delta do servidor. Retorna `true` se foi um tombstone aplicado. */
    private fun applyDelta(entityName: String, delta: SyncDelta): Boolean {
        // Localiza linha local por serverId, ou por clientId (remapeia clientId→serverId).
        val local = store.getByServerId(entityName, delta.serverId)
            ?: delta.clientId?.let { store.getByClientId(entityName, it) }

        // Conflito local: se houver edição local dirty mais nova, ainda assim o servidor
        // é a fonte de verdade no LWW server-side → aplicamos o canônico do servidor.
        if (delta.deleted) {
            local?.let { store.deleteHard(it.entity, it.local_id) }
            return true
        }

        val payloadJson = delta.payload?.let { json.encodeToString(JsonElement.serializer(), it) }
            ?: local?.payload_json ?: return false

        val localId = local?.local_id ?: delta.clientId ?: delta.serverId
        val clientId = local?.client_id ?: delta.clientId ?: delta.serverId
        store.upsert(
            Synced_entity(
                account_id = "",
                entity = entityName,
                local_id = localId,
                server_id = delta.serverId,
                client_id = clientId,
                payload_json = payloadJson,
                updated_at = delta.updatedAt,
                dirty = false.toDbLong(),
                pending_op = null,
                deleted = false.toDbLong(),
                base_updated_at = delta.updatedAt,
                last_error = null,
                failed = 0L,
                fail_code = null,
                attempts = 0L,
            )
        )
        return false
    }

    // ----------------------------------------------------------------------
    // syncNow + auto-sync
    // ----------------------------------------------------------------------

    override suspend fun syncNow(): SyncResultSummary {
        val pushSummary = push()
        if (pushSummary.failed) return pushSummary
        val pullSummary = pull()
        return pushSummary + pullSummary
    }

    override fun startAutoSync(connectivity: ConnectivityObserver) {
        if (autoSyncJob != null) return
        connectivity.start()
        autoSyncJob = connectivity.isOnline
            .onEach { online ->
                val was = lastOnline
                lastOnline = online
                if (!online) {
                    if (store.countDirty() > 0L) _state.value = SyncState.Offline
                    return@onEach
                }
                // offline→online (ou primeira leitura online com pendências): dispara com backoff.
                if (was == false || (was == null && store.countDirty() > 0L)) {
                    scope.launch { syncWithBackoff() }
                }
            }
            .launchIn(scope)
    }

    override fun stopAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
    }

    private suspend fun syncWithBackoff(maxAttempts: Int = 4) {
        var attempt = 0
        var backoff = 1_000L
        while (attempt < maxAttempts) {
            val summary = syncNow()
            if (!summary.failed) return
            attempt++
            if (attempt >= maxAttempts) return
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    // ----------------------------------------------------------------------
    // Fila visível
    // ----------------------------------------------------------------------

    private fun markRowsSyncing(rows: List<Synced_entity>) {
        _queue.value = rows.map { it.toQueueItem(SyncItemStatus.Syncing) }
    }

    private fun refreshQueue() {
        val dirty = store.getAllDirty()
        _queue.value = dirty.map { row ->
            val status = when {
                row.last_error != null -> SyncItemStatus.Error
                else -> SyncItemStatus.Pending
            }
            row.toQueueItem(status)
        }
    }

    private fun Synced_entity.toQueueItem(status: SyncItemStatus): SyncQueueItem {
        val op = SyncOpType.fromWire(pending_op ?: SyncOpType.UPDATE.wire)
        return SyncQueueItem(
            localId = local_id,
            entity = entity,
            op = op,
            status = if (status == SyncItemStatus.Error && last_error != null) SyncItemStatus.Error else status,
            label = "$entity · ${op.wire}",
            error = last_error,
        )
    }

    private fun findRowByClientId(clientId: String): Synced_entity? {
        for (name in registry.keys) {
            store.getByClientId(name, clientId)?.let { return it }
        }
        return null
    }

    private fun registryIndex(entity: String): Int =
        registry.keys.indexOf(entity).let { if (it < 0) Int.MAX_VALUE else it }

    /** Descarta um item da outbox (reverte a edição local pendente). */
    suspend fun discard(entity: String, localId: String) {
        val row = store.getByLocalId(entity, localId) ?: return
        if (row.server_id == null) {
            // nunca foi ao servidor → remove de vez
            store.deleteHard(entity, localId)
        } else {
            // havia versão no servidor → limpa o dirty (perde a edição local)
            store.markClean(entity, localId, row.server_id, row.updated_at, row.base_updated_at)
        }
        refreshQueue()
    }

    /** Reenfileira um item em erro (limpa last_error e mantém dirty). */
    suspend fun retry(entity: String, localId: String) {
        store.setError(entity, localId, null)
        refreshQueue()
        syncNow()
    }

    /** Reenfileira todos os itens em erro e dispara sync. */
    suspend fun retryAll() {
        store.getAllDirty().filter { it.last_error != null }.forEach {
            store.setError(it.entity, it.local_id, null)
        }
        refreshQueue()
        syncNow()
    }

    companion object {
        val defaultSyncJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}

/** Json default do protocolo de sync (tolerante a campos extras do servidor). */
val defaultSyncJson: Json get() = DefaultSyncEngine.defaultSyncJson
