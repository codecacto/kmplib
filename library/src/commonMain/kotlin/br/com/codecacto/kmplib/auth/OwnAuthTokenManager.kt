package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gerencia os tokens da autenticação própria: **persiste** a sessão no cofre seguro, **renova** o
 * access token proativamente (antes de expirar) e faz **single-flight** para o refresh — nunca dispara
 * dois refresh concorrentes que quebrariam a rotação do refresh token.
 *
 * ### Contrato de segurança (own-auth com refresh rotativo)
 * - **Refresh ROTATIVO:** cada `refresh` devolve um novo par; o refresh guardado é SEMPRE substituído.
 * - **Single-flight:** duas corrotinas que precisem renovar ao mesmo tempo não disparam dois `refresh`
 *   (o 2º usaria um refresh token já revogado → derrubaria a sessão). O `Mutex` serializa e o 2º
 *   reaproveita o token recém-renovado pelo 1º.
 * - **Falha de rede = transitória:** NÃO zera a sessão; devolve o access token corrente (mesmo vencido)
 *   para a chamada tentar (o servidor decide). O refresh volta a ser tentado depois.
 * - **4xx no refresh = fail-closed:** refresh token inválido/revogado/reusado → **derruba a sessão**
 *   (logout local). A UI reage ao `session` virar `null`.
 *
 * Fonte de verdade da sessão em memória: [session] (`StateFlow`), espelhando o cofre. Sempre semeado
 * a partir do cofre em [restore].
 */
class OwnAuthTokenManager(
    private val api: OwnAuthApi,
    private val store: AuthSessionStore,
    private val refreshSkewSeconds: Long = OwnAuthConfig.DEFAULT_REFRESH_SKEW_SECONDS,
    private val nowMillis: () -> Long = ::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _session = MutableStateFlow<OwnAuthSession?>(null)

    /** Sessão corrente (null = deslogado). Espelha o cofre; reagir a null = ir para o login. */
    val session: StateFlow<OwnAuthSession?> = _session.asStateFlow()

    /** Lê o cofre e semeia [session]. Chamar uma vez no bootstrap (antes de exigir a sessão). */
    suspend fun restore(): OwnAuthSession? {
        val loaded = store.load()
        _session.value = loaded
        return loaded
    }

    /** Persiste e publica uma sessão nova (após login/registro bem-sucedidos). */
    suspend fun adopt(
        tokens: OwnAuthTokens,
        email: String,
        name: String,
        providerId: String = OwnAuthSession.DEFAULT_PROVIDER_ID,
    ) {
        val session = tokens.toSession(email, name, providerId)
        store.save(session)
        _session.value = session
    }

    /** Encerra a sessão local (revogação server-side é do chamador via [OwnAuthApi.logout]). */
    suspend fun clear() {
        store.clear()
        _session.value = null
    }

    /**
     * Devolve um access token válido para o `Authorization: Bearer`, renovando proativamente se
     * estiver perto de expirar (ou se [forceRefresh], usado no retry pós-401). `null` = sem sessão.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String? {
        val current = _session.value ?: store.load()?.also { _session.value = it } ?: return null
        if (!forceRefresh && !isExpiringSoon(current)) return current.accessToken
        return mutex.withLock {
            // Reavalia sob o lock: outra corrotina pode já ter renovado enquanto esperávamos.
            val latest = _session.value ?: return@withLock null
            // Single-flight: o refresh é ROTATIVO, então uma renovação concorrente troca o refresh
            // token guardado. Se ele mudou desde a nossa leitura pré-lock, alguém já rotacionou usando
            // o MESMO refresh que usaríamos — refazer reusaria um token já revogado. Reaproveita o novo.
            if (latest.refreshToken != current.refreshToken) {
                return@withLock latest.accessToken
            }
            performRefresh(latest)
        }
    }

    /** `true` se falta menos que [refreshSkewSeconds] para o access token expirar (ou já expirou). */
    private fun isExpiringSoon(session: OwnAuthSession): Boolean {
        val nowSeconds = nowMillis() / 1000
        return session.accessExpiresAtEpochSeconds - nowSeconds <= refreshSkewSeconds
    }

    /** Executa o refresh rotativo já sob o [mutex]. Devolve o novo access token, ou trata a falha. */
    private suspend fun performRefresh(session: OwnAuthSession): String? {
        val result = api.refresh(session.refreshToken)
        return result.fold(
            onSuccess = { tokens ->
                // O refresh preserva a identidade da sessão (inclusive a ORIGEM do login): renovar o
                // token não converte um login social em login por senha.
                val renewed = tokens.toSession(session.email, session.name, session.providerId)
                store.save(renewed)
                _session.value = renewed
                renewed.accessToken
            },
            onFailure = { error ->
                val authError = error as? OwnAuthException
                if (authError != null && authError.isClientError) {
                    // 4xx: refresh inválido/revogado/reusado → fail-closed (derruba a sessão).
                    AppLogger.w(TAG, "Refresh rejeitado (${authError.code}); encerrando sessão.")
                    store.clear()
                    _session.value = null
                    null
                } else {
                    // Rede/5xx: transitório — preserva a sessão e devolve o access corrente.
                    AppLogger.w(TAG, "Refresh transitório falhou (${authError?.code}); mantendo sessão.")
                    session.accessToken
                }
            },
        )
    }

    private fun OwnAuthTokens.toSession(
        email: String,
        name: String,
        providerId: String = OwnAuthSession.DEFAULT_PROVIDER_ID,
    ): OwnAuthSession {
        val accountId = JwtDecoder.subject(accessToken).orEmpty()
        val expiresAt = nowMillis() / 1000 + expiresInSeconds
        return OwnAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAtEpochSeconds = expiresAt,
            accountId = accountId,
            email = email,
            name = name,
            providerId = providerId,
        )
    }

    companion object {
        private const val TAG = "OwnAuthTokenManager"
    }
}
