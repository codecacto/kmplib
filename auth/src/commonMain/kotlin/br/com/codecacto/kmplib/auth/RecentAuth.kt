package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.auth.social.foiCancelado
import br.com.codecacto.kmplib.auth.social.provedoresSociaisDaPlataforma
import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Faz o login social e devolve os tokens **sem adotar a sessão** — a peça social da
 * reautenticação. O `SocialSignIn` da lib implementa (nos dois modos, `NATIVE` e `BACKEND`).
 */
fun interface SocialReauthenticator {
    suspend fun reauthenticate(provider: SocialProvider): Result<OwnAuthTokens>
}

/** A credencial que a pessoa forneceu para reautenticar. */
sealed interface ReauthCredential {
    /** A senha da conta (o identificador é o da sessão — a pessoa não o digita de novo). */
    class Password(val password: String) : ReauthCredential {
        /** Nunca imprime a senha (log, mensagem de teste, crash report). */
        override fun toString(): String = "Password(***)"
    }

    /** Refazer o login social com [provider]. */
    data class Social(val provider: SocialProvider) : ReauthCredential
}

/**
 * O que a tela precisa para pedir a credencial — ver [ReauthPrompt].
 *
 * @property identifier o e-mail (ou usuário) da sessão, com que a senha é conferida; `null` quando
 *   a sessão não tem nenhum — aí só o caminho social é possível.
 * @property socialProviders provedores que dá para usar aqui (vazio sem `SocialReauthenticator`).
 * @property sessionProvider o provedor social com que a sessão entrou, se for o caso — a tela o põe
 *   em destaque.
 * @property maxAgeSeconds a janela que o servidor exigiu (`null` se não informou).
 * @property attempt 1 na primeira vez; sobe a cada credencial recusada.
 * @property previousError por que a tentativa anterior falhou (`null` na primeira e depois de um
 *   cancelamento do login social). `OwnAuthException.InvalidCredentials` = senha errada → erro NO
 *   campo; o resto (rede, limite) vai junto do botão.
 */
data class ReauthRequest(
    val identifier: String?,
    val socialProviders: Set<SocialProvider>,
    val sessionProvider: SocialProvider?,
    val maxAgeSeconds: Long?,
    val attempt: Int,
    val previousError: Throwable?,
) {
    /** `true` se dá para reautenticar por senha. */
    val passwordAvailable: Boolean get() = !identifier.isNullOrBlank()
}

/**
 * Quem pergunta à pessoa. Devolver `null` = ela desistiu — a ação é abortada com
 * [ReauthCancelledException]. O `RecentAuthState` (UI pronta) implementa; teste usa um lambda.
 */
fun interface ReauthPrompt {
    suspend fun requestCredential(request: ReauthRequest): ReauthCredential?
}

/** A pessoa fechou o pedido de credencial — a ação sensível NÃO foi executada. Não é erro a exibir. */
class ReauthCancelledException : Exception("Confirmação de identidade cancelada.")

/**
 * A credencial informada é de **outra conta**. Os tokens novos foram descartados (e revogados no
 * servidor) e a ação **não** foi repetida — senão o "tentar de novo" excluiria a conta de quem
 * acabou de digitar a senha, não a do titular da sessão.
 */
class ReauthAccountMismatchException :
    Exception("A confirmação foi feita com outra conta. Entre com a conta desta sessão.")

/** `true` se a pessoa desistiu da reautenticação — a tela fica quieta. */
fun Throwable.isReauthCancelled(): Boolean = this is ReauthCancelledException

/**
 * **Step-up de autenticação para ação sensível** (2.261.0) — o lado do app do
 * `requireRecentAuth()` da backlib (≥ 0.151.0).
 *
 * ```kotlin
 * val recentAuth = ownAuth.recentAuth(social = socialSignIn)          // uma vez, no Koin
 * recentAuth.withRecentAuth(prompt) { deletion.deleteAccountAndData(confirmacao) }
 * ```
 * Na tela, use o `RecentAuthHost` + `rememberRecentAuthState(recentAuth)`, que já é o [ReauthPrompt]
 * (diálogo de senha com o olho de mostrar, e os botões sociais).
 *
 * ## O contrato (literal do servidor)
 * 1. **Não é logout.** O 401 `REAUTH_REQUIRED` chega aqui como [ReauthRequiredException] — os
 *    clientes da lib já o separam do 401 de sessão expirada (sem refresh, sem `onUnauthorized`).
 * 2. Reautentica (`POST /auth/login` com a senha, ou o fluxo social) **sem adotar** os tokens, e
 *    confere que o `sub` do novo access token é o `sub` da sessão. Outra conta → revoga os tokens
 *    novos no servidor, descarta e aborta com [ReauthAccountMismatchException].
 * 3. Mesma conta → troca o par (atômico, sob a trava da renovação), revoga a família antiga e
 *    **repete a ação UMA vez**. Se ela voltar a pedir reautenticação, devolve o erro — nunca entra
 *    em laço.
 *
 * Senha errada (ou falha de rede, ou login social cancelado) **não** aborta: a pessoa é perguntada
 * de novo, com o motivo em [ReauthRequest.previousError], até [maxAttempts] — o servidor tem o
 * próprio limite de tentativas de login, e este teto é só para nunca girar sem fim.
 *
 * @param maxAttempts quantas credenciais pedir antes de desistir (default 5).
 * @param socialProviders provedores oferecidos; default = os da plataforma quando há [social].
 */
class RecentAuthCoordinator(
    private val api: OwnAuthApi,
    private val tokenManager: OwnAuthTokenManager,
    private val social: SocialReauthenticator? = null,
    private val socialProviders: Set<SocialProvider> =
        if (social != null) provedoresSociaisDaPlataforma else emptySet(),
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts precisa ser ao menos 1" }
    }

    /**
     * Executa [action]; se ela falhar com [ReauthRequiredException], pede a credencial por [prompt],
     * reautentica a MESMA conta e repete [action] uma vez. Qualquer outro resultado de [action]
     * (sucesso ou erro) atravessa intacto. Nunca lança (exceto cancelamento de corrotina).
     */
    suspend fun <T> withRecentAuth(prompt: ReauthPrompt, action: suspend () -> Result<T>): Result<T> {
        val first = attempt(action)
        val challenge = first.exceptionOrNull() as? ReauthRequiredException ?: return first

        val session = tokenManager.session.value
            ?: return Result.failure(OwnAuthException.NotAuthenticated(loadOwnAuthTexts().sessionExpired))
        val expectedAccount = session.accountId
        if (expectedAccount.isBlank()) {
            // Sem `sub` legível não há como provar "mesma conta": falha fechada, sem perguntar nada.
            AppLogger.w(TAG, "Sessão sem id de conta; reautenticação recusada.")
            return Result.failure(challenge)
        }

        val identifier = session.email.trim().ifEmpty { session.username?.trim().orEmpty() }.ifEmpty { null }
        val providers = if (social != null) socialProviders else emptySet()
        if (identifier == null && providers.isEmpty()) {
            AppLogger.w(TAG, "Sessão sem identificador e sem login social; reautenticação impossível aqui.")
            return Result.failure(challenge)
        }
        val sessionProvider = SocialProvider.entries.firstOrNull { it.userProviderId == session.providerId }

        var previousError: Throwable? = null
        var tokens: OwnAuthTokens? = null
        var tentativa = 0
        while (tokens == null) {
            tentativa++
            if (tentativa > maxAttempts) return Result.failure(previousError ?: challenge)
            val request = ReauthRequest(
                identifier = identifier,
                socialProviders = providers,
                sessionProvider = sessionProvider,
                maxAgeSeconds = challenge.maxAgeSeconds,
                attempt = tentativa,
                previousError = previousError,
            )
            val credential = prompt.requestCredential(request)
                ?: return Result.failure(ReauthCancelledException())
            val obtained = obtainTokens(credential, identifier, providers)
            obtained.onSuccess { tokens = it }.onFailure { e ->
                // Login social cancelado não é erro a mostrar: a pessoa volta ao pedido, limpo.
                previousError = if (e.foiCancelado()) null else e
            }
        }
        val novos = tokens!!

        // Da troca à revogação, sem cancelamento no meio: sair da tela aqui deixaria um refresh vivo
        // no servidor que ninguém guarda (o par de outra conta, ou a família antiga).
        val falha: Throwable? = withContext(NonCancellable) {
            when (val troca = tokenManager.adoptIfSameAccount(novos, expectedAccount)) {
                is OwnAuthTokenManager.AdoptOutcome.Adopted -> {
                    // A família antiga ficou órfã (ninguém mais a guarda): revoga.
                    if (troca.replaced.refreshToken != novos.refreshToken) revokeQuietly(troca.replaced.refreshToken)
                    null
                }
                OwnAuthTokenManager.AdoptOutcome.OtherAccount -> {
                    AppLogger.w(TAG, "Reautenticação com outra conta; tokens novos descartados.")
                    revokeQuietly(novos.refreshToken)
                    ReauthAccountMismatchException()
                }
                OwnAuthTokenManager.AdoptOutcome.SessionChanged -> {
                    // A sessão acabou (logout, refresh recusado) durante o pedido: os tokens novos
                    // não podem ficar vivos, e a ação não roda.
                    AppLogger.w(TAG, "Sessão encerrada durante a reautenticação; tokens novos descartados.")
                    revokeQuietly(novos.refreshToken)
                    OwnAuthException.NotAuthenticated(loadOwnAuthTexts().sessionExpired)
                }
            }
        }
        if (falha != null) return Result.failure(falha)

        // UMA repetição. Se o servidor pedir de novo, o erro volta — sem laço.
        return attempt(action)
    }

    private suspend fun obtainTokens(
        credential: ReauthCredential,
        identifier: String?,
        providers: Set<SocialProvider>,
    ): Result<OwnAuthTokens> = when (credential) {
        is ReauthCredential.Password -> when {
            identifier == null -> Result.failure(OwnAuthException.Unsupported(loadOwnAuthTexts().unsupported))
            credential.password.isEmpty() ->
                Result.failure(OwnAuthException.InvalidCredentials(loadOwnAuthTexts().invalidCredentials))
            else -> api.login(identifier, credential.password)
        }
        is ReauthCredential.Social -> {
            val reauth = social
            if (reauth == null || credential.provider !in providers) {
                Result.failure(OwnAuthException.Unsupported(loadOwnAuthTexts().unsupported))
            } else {
                try {
                    reauth.reauthenticate(credential.provider)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    private suspend fun <T> attempt(action: suspend () -> Result<T>): Result<T> =
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReauthRequiredException) {
            // Ação que LANÇA em vez de devolver `Result.failure` também é atendida.
            Result.failure(e)
        }

    private suspend fun revokeQuietly(refreshToken: String) {
        try {
            api.logout(refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Revogação best-effort falhou: ${e::class.simpleName}")
        }
    }

    companion object {
        private const val TAG = "RecentAuth"
        const val DEFAULT_MAX_ATTEMPTS: Int = 5
    }
}
