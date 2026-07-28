package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Participante de um ciclo de sync REST-CRUD (implementado por [OfflineFirstRestRepository]). O
 * [RestCrudSyncEngine] coordena vários destes, na **ordem de dependência** (pais primeiro), para o
 * remap de FK fluir dos pais para os filhos.
 */
interface RestCrudSyncParticipant {
    /**
     * Drena a outbox do participante contra o backend, aplicando o [parentRemap] (FK de pais já
     * sincronizados neste ciclo) e devolvendo o `clientId → serverId` dos creates que confirmou.
     */
    suspend fun drainOutbox(parentRemap: Map<String, String> = emptyMap()): Map<String, String>

    /** Reconcilia o espelho local com o servidor (GET de lista). `true` se aplicou com sucesso. */
    suspend fun refresh(): Boolean
}

/**
 * **Coordenador de sincronização para backends REST-CRUD** — a variante offline-first que a
 * [SyncEngine][br.com.codecacto.kmplib.sync.SyncEngine] `/pull`+`/push` (baseada em cursor) não cobre.
 * Promovido do `SyncCoordinator` do piloto MinhasHoras (Onda 3) para reuso pelos ~14 apps da migração.
 *
 * Orquestra, por ciclo:
 * 1. **PUSH** — drena a outbox de cada [participants] na ordem passada (pais → filhos), acumulando o
 *    remap `clientId → serverId` para que os filhos corrijam suas FKs antes de subir (via
 *    [RestCrudEntity.remapRefs]).
 * 2. **PULL** — [refresh] de cada participante (reconciliação por GET de lista).
 *
 * Dispara automaticamente ao (re)conectar ([ConnectivityObserver]) e sob demanda ([syncNow], p/
 * pull-to-refresh). **Best-effort:** falha de rede pausa sem quebrar a UI (a outbox é preservada e
 * retentada no próximo online). Serializado por mutex (nunca há dois ciclos concorrentes).
 *
 * @param participants repositórios offline-first **na ordem de dependência** (pais primeiro).
 * @param connectivity observador de conectividade da lib.
 * @param scope escopo das coroutines de auto-sync (default `SupervisorJob + Default`).
 * @param accountScope (2.91.0, opcional) escopo de conta do espelho
 *   ([SyncStore.accountScope][br.com.codecacto.kmplib.sync.SyncStore.accountScope]). **Passe-o em
 *   todo app com login:** enquanto ele estiver em branco (ninguém reivindicou o espelho ainda),
 *   nenhum ciclo roda — é a trava que impede o PUSH de subir a outbox do bucket sem escopo com o
 *   Bearer de quem acabou de entrar. `null` (default) mantém o comportamento anterior.
 */
class RestCrudSyncEngine(
    private val participants: List<RestCrudSyncParticipant>,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val accountScope: StateFlow<String>? = null,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    /** Estado agregado do sync (para banner de UI). */
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Ativa a sincronização automática ao (re)conectar e faz um primeiro sync na inicialização. */
    fun start() {
        connectivity.start()
        scope.launch { syncNow() }
        connectivity.isOnline
            .drop(1) // ignora o valor inicial já tratado acima
            .onEach { online -> if (online) syncNow() else _state.value = SyncState.Offline }
            .launchIn(scope)
    }

    /**
     * Executa um ciclo completo de sync (push de todas as outboxes na ordem + pull/reconcile).
     * Serializado por mutex. Retorna `true` se todo o ciclo (push + todos os refresh) teve sucesso.
     */
    suspend fun syncNow(): Boolean = mutex.withLock {
        if (accountScope != null && accountScope.value.isBlank()) {
            // Sem titular declarado não há como saber de quem é a outbox — não se sobe nada.
            AppLogger.w(TAG, "Ciclo ignorado: espelho ainda sem escopo de conta (setAccountScope).")
            _state.value = SyncState.Idle
            return@withLock false
        }
        _state.value = SyncState.Syncing(pending = 0)
        runCatching {
            // 1) PUSH — pais primeiro; o remap acumulado corrige as FKs dos filhos.
            val remap = mutableMapOf<String, String>()
            for (participant in participants) {
                remap += participant.drainOutbox(remap)
            }
            // 2) PULL — reconcilia todos (não interrompe se um falhar).
            var allOk = true
            for (participant in participants) {
                if (!participant.refresh()) allOk = false
            }
            allOk
        }.fold(
            onSuccess = { ok ->
                _state.value = if (ok) SyncState.Idle else SyncState.Error("Sincronização parcial.")
                ok
            },
            onFailure = {
                AppLogger.w(TAG, "Ciclo de sync falhou (outbox preservada): ${it.message}")
                _state.value = SyncState.Error(it.message ?: "Falha de sincronização.")
                false
            },
        )
    }

    companion object {
        private const val TAG = "RestCrudSyncEngine"
    }
}
