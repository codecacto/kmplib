package br.com.codecacto.kmplib.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Motor de sincronização offline-first genérico.
 *
 * Fluxo de uso (no app):
 *  1. `val engine = DefaultSyncEngine(store, port, json)`.
 *  2. `engine.register(FreteSyncable)` para cada entidade (a ORDEM importa: define a
 *     ordem de dependência no push — registre o pai antes do filho).
 *  3. Repositórios [SyncableRepository] gravam no espelho via `upsertLocal/deleteLocal`,
 *     que enfileiram a op na outbox.
 *  4. `engine.startAutoSync(connectivity)` dispara sync no offline→online.
 *  5. A UI observa [state]/[queue] (SyncBanner/SyncQueueView).
 *
 * O cliente NUNCA decide o vencedor de um conflito — aplica o payload canônico do
 * servidor (LWW server-side).
 */
interface SyncEngine {
    /** Estado agregado para a UI. */
    val state: StateFlow<SyncState>

    /** Fila visível de itens pendentes/erro/conflito para a UI. */
    val queue: StateFlow<List<SyncQueueItem>>

    /** Registra uma entidade sincronizável. A ordem define a dependência no push. */
    fun <T : Any> register(entity: SyncableEntity<T>)

    /**
     * Enfileira uma operação local (grava no espelho + marca dirty). Normalmente
     * chamado pelos [SyncableRepository]; exposto para casos avançados.
     */
    suspend fun enqueue(entity: String, op: SyncOpType, model: Any)

    /** Drena a outbox para o servidor (push). */
    suspend fun push(): SyncResultSummary

    /** Baixa e aplica deltas do servidor (pull, paginado). */
    suspend fun pull(): SyncResultSummary

    /** Push seguido de pull. */
    suspend fun syncNow(): SyncResultSummary

    /**
     * Liga o auto-sync: observa [br.com.codecacto.kmplib.core.network.ConnectivityObserver]
     * e dispara [syncNow] na transição offline→online (com backoff). Idempotente.
     */
    fun startAutoSync(connectivity: br.com.codecacto.kmplib.core.network.ConnectivityObserver)

    /** Para o auto-sync e cancela o escopo interno. */
    fun stopAutoSync()
}
