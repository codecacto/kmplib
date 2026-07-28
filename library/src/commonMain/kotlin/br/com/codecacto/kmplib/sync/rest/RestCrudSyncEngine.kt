package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.LegacyRowsPolicy
import br.com.codecacto.kmplib.sync.SyncState
import br.com.codecacto.kmplib.sync.SyncStore
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
 * **A ordem dos [participants] deixou de ser a única defesa (2.93.0).** O remap acumulado aqui vale
 * só para este ciclo; desde a 2.93.0 cada migração de id é gravada de forma **durável** no espelho
 * ([SyncStore.rememberServerId][br.com.codecacto.kmplib.sync.SyncStore.rememberServerId]), então um
 * filho que **não** drenou junto do pai — sinal caiu no meio do drain, app fechado entre os dois
 * `POST`s — resolve a FK do pai no ciclo seguinte, ou noutra execução do app. Antes disso ele subia
 * a FK com o id local, o backend recusava (`FOREIGN KEY`/UUID), a recusa era **terminal** e o
 * registro se perdia. Continue passando pais antes de filhos: é o que faz o caso comum resolver num
 * ciclo só.
 *
 * Dispara automaticamente ao (re)conectar ([ConnectivityObserver]) e sob demanda ([syncNow], p/
 * pull-to-refresh). **Best-effort:** falha de rede pausa sem quebrar a UI (a outbox é preservada e
 * retentada no próximo online). Serializado por mutex (nunca há dois ciclos concorrentes).
 *
 * ### O titular é reconferido DURANTE o ciclo (2.94.0 — GAP-KL-M-SYNC-SCOPERACE)
 * Até a 2.93.0 o escopo era conferido **uma vez, no início**. Um ciclo em voo atravessava um
 * `setAccountScope` e misturava Bearer e bucket: o PUSH subia a outbox do titular anterior usando o
 * token de quem acabou de entrar — **vazamento de dado entre contas**. Agora o titular é reconferido
 * antes de cada participante (e, dentro do participante, antes de cada linha da outbox); mudou,
 * o ciclo **aborta** preservando a outbox de cada conta.
 *
 * A trava completa é [setAccountScope]: trocar de conta por ela **espera o ciclo em execução
 * terminar** (mesmo mutex), então não sobra sequer uma requisição em voo. É o caminho recomendado
 * para todo app com login — trocar direto no `SyncStore` deixa a rede de segurança acima como única
 * defesa.
 *
 * @param participants repositórios offline-first **na ordem de dependência** (pais primeiro).
 * @param connectivity observador de conectividade da lib.
 * @param scope escopo das coroutines de auto-sync (default `SupervisorJob + Default`).
 * @param accountScope (2.91.0, opcional) escopo de conta do espelho
 *   ([SyncStore.accountScope]). **Declare o titular em todo app com login:** enquanto ele estiver em
 *   branco (ninguém reivindicou o espelho ainda), nenhum ciclo roda — é a trava que impede o PUSH de
 *   subir a outbox do bucket sem escopo com o Bearer de quem acabou de entrar. Se [store] for
 *   informado, este parâmetro é dispensável (o escopo vem dele).
 * @param store (2.94.0, opcional, **preferido**) o espelho local. Informando-o, o motor deriva o
 *   [accountScope] sozinho e passa a oferecer [setAccountScope] serializado com o ciclo.
 */
class RestCrudSyncEngine(
    private val participants: List<RestCrudSyncParticipant>,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    accountScope: StateFlow<String>? = null,
    private val store: SyncStore? = null,
) {
    private val mutex = Mutex()

    /** Titular corrente do espelho. `null` = o app não declara escopo (single-account). */
    private val accountScope: StateFlow<String>? = accountScope ?: store?.accountScope

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
     * **Troca o titular do espelho sem se interpor a um ciclo em execução** (2.94.0).
     *
     * Adquire o mesmo mutex do [syncNow]: se houver um ciclo rodando, a troca **espera** ele
     * terminar; e um ciclo novo só arranca depois de a troca ser aplicada. É isso que elimina a
     * janela em que a outbox de uma conta subiria com o `Bearer` de outra — inclusive a requisição
     * em voo, que a reconferência por linha sozinha não consegue cancelar.
     *
     * Use **sempre** que o app tiver login (`login`, `logout`, troca de perfil), no lugar de chamar
     * `store.setAccountScope(...)` direto. Depois dela, chame [syncNow] para o novo titular
     * convergir.
     *
     * Exige que o motor tenha sido construído com `store =` — sem ele não há o que trocar.
     */
    suspend fun setAccountScope(
        accountId: String?,
        legacy: LegacyRowsPolicy = LegacyRowsPolicy.Adopt,
    ) {
        val espelho = checkNotNull(store) {
            "RestCrudSyncEngine.setAccountScope exige o construtor com `store =` (o motor não conhece o espelho)."
        }
        mutex.withLock { espelho.setAccountScope(accountId, legacy) }
    }

    /**
     * Executa um ciclo completo de sync (push de todas as outboxes na ordem + pull/reconcile).
     * Serializado por mutex. Retorna `true` se todo o ciclo (push + todos os refresh) teve sucesso.
     *
     * Devolve `false` também quando o ciclo é **abortado por troca de titular** — nada é enviado
     * pela metade, e a outbox de cada conta fica intacta.
     */
    suspend fun syncNow(): Boolean = mutex.withLock {
        val titular = accountScope?.value.orEmpty()
        if (accountScope != null && titular.isBlank()) {
            // Sem titular declarado não há como saber de quem é a outbox — não se sobe nada.
            AppLogger.w(TAG, "Ciclo ignorado: espelho ainda sem escopo de conta (setAccountScope).")
            _state.value = SyncState.Idle
            return@withLock false
        }
        _state.value = SyncState.Syncing(pending = 0)
        runCatching { executarCiclo(titular) }.fold(
            onSuccess = { desfecho ->
                when (desfecho) {
                    CycleOutcome.Ok -> {
                        _state.value = SyncState.Idle
                        true
                    }
                    CycleOutcome.Partial -> {
                        _state.value = SyncState.Error("Sincronização parcial.")
                        false
                    }
                    CycleOutcome.OwnerChanged -> {
                        AppLogger.w(TAG, "Ciclo abortado: o titular do espelho mudou durante a sincronização.")
                        _state.value = SyncState.Idle
                        false
                    }
                }
            },
            onFailure = {
                AppLogger.w(TAG, "Ciclo de sync falhou (outbox preservada): ${it.message}")
                _state.value = SyncState.Error(it.message ?: "Falha de sincronização.")
                false
            },
        )
    }

    private suspend fun executarCiclo(titular: String): CycleOutcome {
        // 1) PUSH — pais primeiro; o remap acumulado corrige as FKs dos filhos.
        val remap = mutableMapOf<String, String>()
        for (participant in participants) {
            if (titularMudou(titular)) return CycleOutcome.OwnerChanged
            remap += participant.drainOutbox(remap)
        }
        // 2) PULL — reconcilia todos (não interrompe se um falhar).
        var allOk = true
        for (participant in participants) {
            if (titularMudou(titular)) return CycleOutcome.OwnerChanged
            if (!participant.refresh()) allOk = false
        }
        return if (allOk) CycleOutcome.Ok else CycleOutcome.Partial
    }

    /** O espelho deixou de pertencer a quem pertencia quando o ciclo começou. */
    private fun titularMudou(esperado: String): Boolean =
        accountScope != null && accountScope.value != esperado

    /** Desfecho de um ciclo — "abortado por troca de titular" **não** é "falha de sincronização". */
    private enum class CycleOutcome { Ok, Partial, OwnerChanged }

    companion object {
        private const val TAG = "RestCrudSyncEngine"
    }
}
