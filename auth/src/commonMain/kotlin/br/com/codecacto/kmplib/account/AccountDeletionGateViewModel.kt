package br.com.codecacto.kmplib.account

import androidx.lifecycle.viewModelScope
import br.com.codecacto.kmplib.auth.OwnAuthException
import br.com.codecacto.kmplib.auth.ReauthAccountMismatchException
import br.com.codecacto.kmplib.auth.ReauthCancelledException
import br.com.codecacto.kmplib.auth.RecentAuthCoordinator
import br.com.codecacto.kmplib.ui.components.RecentAuthState
import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Estado do [AccountDeletionGate] (2.301.0).
 *
 * @property pending a exclusão programada vigente (vinda do [AccountDeletionState]); `null` = conta normal.
 * @property isRestoring a restauração está em curso (botões travados, indicador no "Restaurar").
 * @property restoreError a última restauração falhou — a tela mostra junto do botão.
 */
data class AccountDeletionGateState(
    val pending: AccountDeletionStatus? = null,
    val isRestoring: Boolean = false,
    val restoreError: AccountDeletionGateError? = null,
) {
    /** `true` quando a área logada tem de ficar coberta. */
    val isBlocking: Boolean get() = pending != null
}

/**
 * Falha da restauração. [message] = a frase do servidor (recusa 4xx, ex.: 409) ou da rede, já no
 * idioma da tela; `null` = usar o texto padrão do gate ("Não foi possível restaurar…").
 *
 * @property needsSignIn (2.302.0) o servidor exige uma sessão aberta DEPOIS do pedido (401
 *   `REAUTH_REQUIRED`, `SESSION_PREDATES_DELETION_REQUEST`) e não deu para reautenticar aqui: a
 *   tela diz "Entre de novo para restaurar a conta." e o **Sair** vira a ação principal.
 * @property accountMismatch (2.302.0) a reautenticação foi feita com OUTRA conta — nada restaurado.
 */
data class AccountDeletionGateError(
    val message: String? = null,
    val isOffline: Boolean = false,
    val needsSignIn: Boolean = false,
    val accountMismatch: Boolean = false,
)

/** Ações do [AccountDeletionGate]. */
sealed interface AccountDeletionGateAction {
    /**
     * A conta da sessão é [accountKey] (`null` = ninguém logado). Na primeira vez de cada conta, o
     * gate consulta `GET {me}/deletion` — é o "logo após o login". Repetir a mesma conta (rotação,
     * recomposição) não consulta de novo.
     */
    data class AccountChanged(val accountKey: Any?) : AccountDeletionGateAction

    /** "Restaurar conta". */
    data object Restore : AccountDeletionGateAction

    /** "Sair" — a exclusão continua programada; a cobertura fica até a conta virar `null`. */
    data object SignOut : AccountDeletionGateAction

    /** Some com a mensagem de erro. */
    data object DismissError : AccountDeletionGateAction
}

/** Efeitos de uma vez do [AccountDeletionGate]. */
sealed interface AccountDeletionGateEffect {
    /** A conta foi restaurada: o app recarrega o que estiver na tela. */
    data object Restored : AccountDeletionGateEffect

    /** A pessoa escolheu sair: o app encerra a sessão. */
    data object SignOutRequested : AccountDeletionGateEffect
}

/**
 * O ViewModel do portão de conta com exclusão programada (2.301.0). Espelha o [AccountDeletionState]
 * (que a rede alimenta pelo `AccountDeletionDetector`), consulta a situação logo depois do login e
 * conduz o "Restaurar" e o "Sair".
 *
 * A consulta pós-login que FALHA (sem rede, 5xx) **não** bloqueia: quem garante o bloqueio é o
 * servidor, que responde 403 a toda rota protegida — e esse 403 cobre a tela pelo detector.
 *
 * ### Restaurar com reautenticação (2.302.0)
 * A backlib ≥ 0.183.0 só restaura com uma sessão aberta **depois** do pedido de exclusão; a sessão
 * antiga recebe 401 `REAUTH_REQUIRED`. Com [recentAuth], o "Restaurar" passa pelo [recentAuthState]
 * (o mesmo `RecentAuthState.run` das ações sensíveis): pede a senha/login social da MESMA conta e
 * repete a restauração **uma** vez — o gate desenha o `RecentAuthHost`. Sem [recentAuth] (ou sem
 * como reautenticar: sessão sem identificador, sem login social), o erro vem com
 * [AccountDeletionGateError.needsSignIn] — "Entre de novo para restaurar a conta." e o **Sair** em
 * destaque. Desistir do diálogo de senha não é erro: a tela volta como estava.
 *
 * @param recentAuth o coordenador do app (`ownAuth.recentAuth(social)`, normalmente o `single` do
 *   Koin). `null` = sem reautenticação aqui.
 */
class AccountDeletionGateViewModel(
    private val repository: AccountDeletionRepository,
    private val deletionState: AccountDeletionState = AccountDeletionState.Shared,
    recentAuth: RecentAuthCoordinator? = null,
) : BaseViewModel<AccountDeletionGateState, AccountDeletionGateAction, AccountDeletionGateEffect>(
    AccountDeletionGateState(pending = deletionState.pending.value),
) {
    /**
     * O pedido de credencial da restauração (2.302.0) — `null` sem [recentAuth]. O gate o desenha com
     * o `RecentAuthHost`; mora aqui para sobreviver à rotação junto com a restauração em curso.
     */
    val recentAuthState: RecentAuthState? = recentAuth?.let { RecentAuthState(it) }

    private var checkedAccount: Any? = null
    private var hasAccount = false
    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            deletionState.pending.collect { status ->
                setState {
                    copy(
                        pending = status,
                        // Saiu do bloqueio (restaurou por aqui ou por outro caminho): erro velho some.
                        restoreError = if (status == null) null else restoreError,
                        isRestoring = if (status == null) false else isRestoring,
                    )
                }
            }
        }
    }

    override fun onAction(action: AccountDeletionGateAction) {
        when (action) {
            is AccountDeletionGateAction.AccountChanged -> onAccountChanged(action.accountKey)
            AccountDeletionGateAction.Restore -> restore()
            AccountDeletionGateAction.SignOut -> signOut()
            AccountDeletionGateAction.DismissError -> setState { copy(restoreError = null) }
        }
    }

    private fun onAccountChanged(accountKey: Any?) {
        if (hasAccount && checkedAccount == accountKey) return
        val trocou = hasAccount
        hasAccount = true
        checkedAccount = accountKey
        checkJob?.cancel()
        // O bloqueio era da conta anterior (ou de ninguém): a seguinte não o herda.
        if (trocou || accountKey == null) deletionState.clear()
        if (accountKey == null) return
        checkJob = viewModelScope.launch {
            repository.deletionStatus().onFailure {
                AppLogger.w(TAG, "Consulta da exclusão após o login falhou; o 403 do servidor cobre")
            }
        }
    }

    private fun restore() {
        if (currentState.isRestoring || currentState.pending == null) return
        setState { copy(isRestoring = true, restoreError = null) }
        viewModelScope.launch {
            val reauth = recentAuthState
            val resultado = if (reauth != null) reauth.run { repository.restore() } else repository.restore()
            resultado
                .onSuccess { status ->
                    if (status.pending) {
                        setState { copy(isRestoring = false, restoreError = AccountDeletionGateError()) }
                    } else {
                        setState { copy(isRestoring = false) }
                        sendEffect(AccountDeletionGateEffect.Restored)
                    }
                }
                .onFailure { falha ->
                    val erro = restoreErrorOf(falha)
                    if (erro?.needsSignIn == true) {
                        AppLogger.w(TAG, "Restauração pede sessão nova; oferecendo Sair")
                    }
                    setState { copy(isRestoring = false, restoreError = erro) }
                }
        }
    }

    private fun signOut() {
        if (currentState.isRestoring) return
        checkJob?.cancel()
        // O bloqueio NÃO sai aqui: a cobertura fica até a sessão acabar de fato (a conta vira `null`
        // em [AccountChanged]) — tirá-la antes mostraria a área logada por um instante, e as chamadas
        // dela tomariam 403 de novo.
        sendEffect(AccountDeletionGateEffect.SignOutRequested)
    }

    private companion object {
        const val TAG = "AccountDeletionGate"
    }
}

/**
 * A falha do "Restaurar conta" como estado da tela (2.302.0) — `null` = nada a mostrar (a pessoa
 * fechou o pedido de senha). Puro e testado.
 *
 * - recusa do servidor com frase (4xx, ex.: 409) → a frase do servidor; sem rede → a frase local;
 *   5xx/ilegível → o texto padrão;
 * - 401 `REAUTH_REQUIRED` que sobrou (sem `RecentAuthCoordinator`, ou ele não tinha como pedir a
 *   credencial) e sessão que acabou durante o pedido → [AccountDeletionGateError.needsSignIn];
 * - a credencial era de outra conta → [AccountDeletionGateError.accountMismatch].
 */
internal fun restoreErrorOf(falha: Throwable): AccountDeletionGateError? = when (falha) {
    is ReauthCancelledException -> null
    is ReauthRequiredException -> AccountDeletionGateError(needsSignIn = true)
    is OwnAuthException.NotAuthenticated, is OwnAuthException.Unsupported ->
        AccountDeletionGateError(needsSignIn = true)
    is ReauthAccountMismatchException -> AccountDeletionGateError(accountMismatch = true)
    is AccountDeletionRequestException -> if (falha.httpCode == 401) {
        // 401 comum (sessão que não renova mais) também só se resolve entrando de novo.
        AccountDeletionGateError(needsSignIn = true)
    } else {
        AccountDeletionGateError(
            message = when {
                falha.hasServerMessage || falha.isOffline -> falha.message?.takeIf { it.isNotBlank() }
                else -> null
            },
            isOffline = falha.isOffline,
        )
    }
    else -> AccountDeletionGateError()
}
