package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.monetization.PremiumStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * **Acesso liberado na central** (2.374.0) — o que o admin global concedeu a esta conta SEM a loja:
 * isenção (`billing_exemptions` → `categoria = "isento"`), conta por contrato (`categoria =
 * "contrato"`), cortesia/vitalício (grant manual com `ativo = true`, `validoAte` nulo ou futuro).
 *
 * Até a 2.373.0 o app só ouvia a loja (RevenueCat): o tenant isento continuava vendo anúncio e
 * paywall. Com [br.com.codecacto.kmplib.monetization.MonetizationManager.enableCentralAccess] o
 * premium passa a ser **loja OU central** — opt-in, nunca derruba o premium da loja.
 *
 * - [DISABLED] — app não ligou a leitura (comportamento de antes).
 * - [PENDING] — primeira leitura desta conta em andamento (com teto: vira [UNAVAILABLE]).
 * - [GRANTED] — a central diz que a conta está liberada (ou o último valor conhecido, lembrado).
 * - [NOT_GRANTED] — a central respondeu: não há liberação; ou não há conta logada.
 * - [UNAVAILABLE] — a primeira leitura falhou/estourou o teto sem valor conhecido. Tratado como não
 *   liberado (fail-closed); a próxima [refresh][br.com.codecacto.kmplib.monetization.MonetizationManager.refreshCentralAccess] tenta de novo.
 */
enum class CentralAccessState {
    DISABLED,
    PENDING,
    GRANTED,
    NOT_GRANTED,
    UNAVAILABLE;

    val isGranted: Boolean get() = this == GRANTED
}

/**
 * Configuração da leitura do acesso liberado na central.
 *
 * @param repository de onde ler — o [AdminApiEntitlementRepository] do app (`GET
 *   /v1/projects/{slug}/me/entitlement`, com o token da sessão: o tenant é o da própria conta).
 *   A liberação vale quando [Entitlement.isPremium]: categoria `isento`/`contrato` ou plano pago ativo.
 * @param accounts a conta da sessão (`null` = ninguém logado). Cada troca relê do zero e **nunca
 *   herda** a liberação da conta anterior. `null` aqui (o default) = app de conta única por
 *   instalação (ex.: uid anônimo estável) — lê uma vez e segue.
 * @param minRefreshInterval intervalo mínimo entre duas leituras bem-sucedidas no
 *   [refresh][br.com.codecacto.kmplib.monetization.MonetizationManager.refreshCentralAccess] sem
 *   `force` (o cache curto). Falha não conta: a próxima chamada tenta de novo.
 * @param firstReadTimeout teto da primeira leitura de cada conta. Enquanto ela corre, um
 *   [PremiumStatus] ainda não resolvido continua `Unknown` (o gate não abre paywall para um isento);
 *   passado o teto, vira [CentralAccessState.UNAVAILABLE].
 * @param memory onde lembrar o último valor entre aberturas (app offline-first: o isento que abre o
 *   app sem rede continua liberado). `null` = só na memória do processo.
 * @param rememberFor validade do valor lembrado; mais velho que isso, a abertura começa em
 *   [CentralAccessState.PENDING].
 */
data class CentralAccessConfig(
    val repository: EntitlementRepository,
    val accounts: Flow<String?>? = null,
    val minRefreshInterval: Duration = 60.seconds,
    val firstReadTimeout: Duration = PremiumStatus.DEFAULT_TIMEOUT,
    val memory: CentralAccessMemory? = null,
    val rememberFor: Duration = 7.days,
)

/** O último valor lido da central para [account] (salvo em [savedAtEpochMillis]). */
data class RememberedCentralAccess(
    val account: String,
    val granted: Boolean,
    val savedAtEpochMillis: Long,
) {
    override fun toString(): String = "RememberedCentralAccess(granted=$granted, savedAt=$savedAtEpochMillis)"
}

/**
 * Memória do último valor da central entre aberturas. É **conforto de exibição** (não esconder o
 * isento atrás de anúncio sem rede), nunca autorização: o servidor continua sendo quem cobra.
 */
interface CentralAccessMemory {
    suspend fun load(): RememberedCentralAccess?
    suspend fun save(value: RememberedCentralAccess)
    suspend fun clear()
}

/** [CentralAccessMemory] nas [AppPreferences] do app (`appPreferences()`). */
class PreferencesCentralAccessMemory(
    private val preferences: AppPreferences,
    private val keyPrefix: String = "kmplib.central_access",
) : CentralAccessMemory {
    private val keyAccount get() = "$keyPrefix.account"
    private val keyGranted get() = "$keyPrefix.granted"
    private val keySavedAt get() = "$keyPrefix.saved_at"

    override suspend fun load(): RememberedCentralAccess? {
        val account = preferences.getString(keyAccount, "")
        if (account.isEmpty()) return null
        return RememberedCentralAccess(
            account = account,
            granted = preferences.getBoolean(keyGranted, false),
            savedAtEpochMillis = preferences.getLong(keySavedAt, 0L),
        )
    }

    override suspend fun save(value: RememberedCentralAccess) {
        preferences.setString(keyAccount, value.account)
        preferences.setBoolean(keyGranted, value.granted)
        preferences.setLong(keySavedAt, value.savedAtEpochMillis)
    }

    override suspend fun clear() {
        preferences.remove(keyAccount)
        preferences.remove(keyGranted)
        preferences.remove(keySavedAt)
    }
}

/** O estado e a conta a que ele se refere. */
internal data class CentralAccessSnapshot(
    val state: CentralAccessState = CentralAccessState.DISABLED,
    val account: String? = null,
)

/**
 * O motor da leitura (interno; o app usa o `MonetizationManager`). Testável com relógio próprio.
 *
 * Regras: falha de rede **mantém** o último valor (liberado continua liberado, não liberado
 * continua); troca de conta **zera**; resposta que chega depois da troca de conta é descartada.
 */
internal class CentralAccessTracker(
    private val config: CentralAccessConfig,
    private val nowEpochMillis: () -> Long = { currentTimeMillis() },
    private val onChange: (CentralAccessSnapshot) -> Unit = {},
) {
    private val _snapshot = MutableStateFlow(CentralAccessSnapshot(CentralAccessState.PENDING, null))
    val snapshot: StateFlow<CentralAccessSnapshot> = _snapshot.asStateFlow()

    private val mutex = Mutex()
    private var generation = 0L
    private var hasAccount = false
    private var lastSuccess: TimeSource.Monotonic.ValueTimeMark? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        val accounts = config.accounts ?: flowOf(SINGLE_ACCOUNT)
        publish(CentralAccessSnapshot(CentralAccessState.PENDING, null))
        job = scope.launch {
            // Fluxo de contas que demora a emitir não prende o gate: o mesmo teto da 1ª leitura.
            launch {
                delay(config.firstReadTimeout)
                mutex.withLock {
                    val current = _snapshot.value
                    if (generation == 0L && current.state == CentralAccessState.PENDING) {
                        publish(current.copy(state = CentralAccessState.UNAVAILABLE))
                    }
                }
            }
            accounts.distinctUntilChanged().collectLatest { account -> onAccount(account) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun publish(value: CentralAccessSnapshot) {
        _snapshot.value = value
        onChange(value)
    }

    private suspend fun onAccount(account: String?) {
        val gen = mutex.withLock {
            generation += 1
            lastSuccess = null
            hasAccount = account != null
            generation
        }
        if (account == null) {
            publish(CentralAccessSnapshot(CentralAccessState.NOT_GRANTED, null))
            return
        }
        val remembered = runCatching { config.memory?.load() }.getOrNull()
            ?.takeIf { it.account == account && isFresh(it) }
        publish(
            CentralAccessSnapshot(
                if (remembered?.granted == true) CentralAccessState.GRANTED else CentralAccessState.PENDING,
                account,
            ),
        )
        (config.repository as? AdminApiEntitlementRepository)?.invalidateCache()
        coroutineScope {
            val timer = launch {
                delay(config.firstReadTimeout)
                mutex.withLock {
                    val current = _snapshot.value
                    if (gen == generation && current.state == CentralAccessState.PENDING) {
                        AppLogger.w(TAG, "Central sem resposta em ${config.firstReadTimeout}: acesso tratado como não liberado")
                        publish(current.copy(state = CentralAccessState.UNAVAILABLE))
                    }
                }
            }
            read(account, gen)
            timer.cancel()
        }
    }

    private fun isFresh(remembered: RememberedCentralAccess): Boolean {
        val age = nowEpochMillis() - remembered.savedAtEpochMillis
        return age >= 0 && age <= config.rememberFor.inWholeMilliseconds
    }

    /** Releitura (ON_RESUME, depois de uma ação). Sem conta, ou dentro do cache curto sem [force], não lê. */
    suspend fun refresh(force: Boolean) {
        val (account, gen) = mutex.withLock {
            val account = _snapshot.value.account
            if (!hasAccount || account == null) return
            val fresh = lastSuccess?.let { it.elapsedNow() < config.minRefreshInterval } == true
            if (fresh && !force) return
            account to generation
        }
        if (force) (config.repository as? AdminApiEntitlementRepository)?.invalidateCache()
        read(account, gen)
    }

    private suspend fun read(account: String, gen: Long) {
        val result = runCatching { config.repository.getEntitlement() }
            .getOrElse { ApiResult.Error(-1, it.message ?: "falha") }
        val toSave: RememberedCentralAccess? = mutex.withLock {
            if (gen != generation) return // conta trocou no meio: resposta de outra conta
            val current = _snapshot.value
            when (result) {
                is ApiResult.Success -> {
                    lastSuccess = TimeSource.Monotonic.markNow()
                    val granted = result.data.isPremium
                    publish(
                        current.copy(
                            state = if (granted) CentralAccessState.GRANTED else CentralAccessState.NOT_GRANTED,
                        ),
                    )
                    RememberedCentralAccess(account, granted, nowEpochMillis())
                }
                else -> {
                    // Erro (ou Loading, que um repositório não devolve): mantém o último valor.
                    AppLogger.w(TAG, "Leitura do acesso na central falhou: mantém o último valor")
                    if (current.state == CentralAccessState.PENDING) {
                        publish(current.copy(state = CentralAccessState.UNAVAILABLE))
                    }
                    null
                }
            }
        }
        if (toSave != null) {
            runCatching { config.memory?.save(toSave) }
        }
    }

    companion object {
        private const val TAG = "CentralAccess"

        /** Conta única por instalação (quando o app não passa [CentralAccessConfig.accounts]). */
        const val SINGLE_ACCOUNT: String = "__instalacao__"
    }
}

/**
 * O premium publicado = **loja OU central** (regra pura, testada).
 *
 * - central [CentralAccessState.GRANTED] → `Premium` (vence até loja ainda sem resposta);
 * - loja `Premium` → `Premium` (a central nunca derruba a loja);
 * - loja sem resposta → `Unknown`;
 * - central [CentralAccessState.PENDING] e nada publicado ainda → `Unknown` (o gate espera a central,
 *   com o teto dela, em vez de abrir paywall para um isento); com algo já publicado, não regride;
 * - senão, o que a loja disse.
 */
internal fun combinePremiumStatus(
    previous: PremiumStatus,
    store: PremiumStatus,
    central: CentralAccessState,
): PremiumStatus = when {
    central == CentralAccessState.GRANTED -> PremiumStatus.Premium
    store == PremiumStatus.Premium -> PremiumStatus.Premium
    !store.isResolved -> PremiumStatus.Unknown
    central == CentralAccessState.PENDING && !previous.isResolved -> PremiumStatus.Unknown
    else -> store
}
