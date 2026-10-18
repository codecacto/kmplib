package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    /**
     * **Single-flight da renovação** — só o refresh (que vai à rede) passa por aqui. Quem só lê ou
     * grava o estado usa [stateLock] e nunca espera a rede (2.304.1).
     */
    private val mutex = Mutex()

    /** Teto do refresh ([REFRESH_TIMEOUT_MILLIS]); `internal` e mutável só para o teste encurtar. */
    internal var refreshTimeoutMillis: Long = REFRESH_TIMEOUT_MILLIS

    /**
     * Guarda o par **cofre + [session]**: toda gravação (login, perfil, limpeza, a confirmação de um
     * refresh) passa por ela, e nenhuma ida à rede acontece com ela travada. É o que deixa o
     * `signOut` encerrar a sessão na hora mesmo com um refresh pendurado na rede — e é sob ela que o
     * refresh, ao voltar, confere se a sessão de que partiu ainda é a corrente antes de gravar.
     * Ordem de aquisição fixa: [mutex] → [stateLock], nunca o contrário.
     */
    private val stateLock = Mutex()
    private val _session = MutableStateFlow<OwnAuthSession?>(null)

    /** Sessão corrente (null = deslogado). Espelha o cofre; reagir a null = ir para o login. */
    val session: StateFlow<OwnAuthSession?> = _session.asStateFlow()

    /**
     * Semeia [session] a partir do cofre. Chamar no bootstrap, **na raiz do app** (um deep link não
     * passa pela Splash) — e pode ser chamada de mais de um lugar: é **idempotente**.
     *
     * ### ⚠️ Roda sob a trava do estado, a mesma em que o refresh confirma o par novo (2.203.0; 2.304.1)
     * Até a 2.202.0 a leitura do cofre e a publicação corriam fora da trava. Com `restore()` em
     * paralelo a uma renovação, a ordem "restore lê r1 → refresh troca r1 por r2 e publica → restore
     * publica r1" deixava a sessão **velha** em memória; o refresh seguinte mandava r1, que o servidor
     * já tinha rotacionado — **reuso**, e a família inteira de tokens é revogada: a pessoa é deslogada
     * sem ter feito nada. Sob a trava, quem lê o cofre enxerga sempre o último par gravado.
     *
     * Havendo sessão em memória, ela é devolvida sem reler o cofre: tudo o que publica em memória
     * grava no cofre antes, sob a mesma trava, e a memória nunca está atrás dele — já o contrário
     * acontece (uma gravação no cofre que falhou), e reler ali trocaria o par novo pelo antigo.
     */
    suspend fun restore(): OwnAuthSession? = stateLock.withLock {
        _session.value ?: store.load().also { _session.value = it }
    }

    /**
     * Persiste e publica uma sessão nova (após login/registro bem-sucedidos). Serializada com a
     * renovação: um refresh em voo com o par anterior não sobrescreve a sessão recém-adotada.
     */
    suspend fun adopt(
        tokens: OwnAuthTokens,
        email: String,
        name: String,
        providerId: String = OwnAuthSession.DEFAULT_PROVIDER_ID,
        /** Nome de usuário já conhecido (2.229.0) — preservado ao trocar o par de tokens. */
        username: String? = null,
    ) {
        val session = tokens.toSession(email, name, providerId, username)
        stateLock.withLock {
            store.save(session)
            _session.value = session
        }
    }

    /**
     * **Troca o par de tokens só se for da MESMA conta** (2.261.0, reautenticação de step-up).
     *
     * Atômico sob a trava da renovação: confere que a sessão corrente ainda é de [expectedAccountId]
     * **e** que o `sub` de [tokens] é o mesmo, e só então grava — preservando e-mail, nome, usuário e
     * a origem do login. Conta sem `sub` legível nunca casa (falha fechada).
     */
    internal suspend fun adoptIfSameAccount(tokens: OwnAuthTokens, expectedAccountId: String): AdoptOutcome =
        stateLock.withLock {
            val current = _session.value
            if (current == null || expectedAccountId.isBlank() || current.accountId != expectedAccountId) {
                return@withLock AdoptOutcome.SessionChanged
            }
            if (JwtDecoder.subject(tokens.accessToken).orEmpty() != expectedAccountId) {
                return@withLock AdoptOutcome.OtherAccount
            }
            val renewed = tokens.toSession(current.email, current.name, current.providerId, current.username)
            store.save(renewed)
            _session.value = renewed
            AdoptOutcome.Adopted(replaced = current)
        }

    /** Resultado de [adoptIfSameAccount]. */
    internal sealed interface AdoptOutcome {
        /** Trocou; [replaced] é a sessão anterior (a família dela fica órfã e deve ser revogada). */
        class Adopted(val replaced: OwnAuthSession) : AdoptOutcome
        /** Os tokens são de outra conta — nada foi trocado. */
        data object OtherAccount : AdoptOutcome
        /** A sessão acabou (ou virou outra conta) durante a reautenticação — nada foi trocado. */
        data object SessionChanged : AdoptOutcome
    }

    /**
     * Mescla o perfil lido do servidor (`GET`/`PATCH {authBasePath}/me`) na sessão corrente e
     * publica — é o que faz `currentUser` passar a ter nome, e-mail e **nome de usuário** reais
     * (2.229.0). Devolve a sessão resultante, ou `null` quando não aplicou.
     *
     * **Só aplica se o perfil é da conta da sessão** (`profile.id == accountId`): entre pedir e
     * receber o `/me` a pessoa pode ter saído e entrado com outra conta, e o perfil da primeira não
     * pode ser gravado sobre a segunda. Valor vazio/nulo do servidor **não apaga** o conhecido — na
     * sessão com senha temporária o `/me` omite e-mail e usuário de propósito, e isso não significa
     * que a conta deixou de tê-los.
     */
    suspend fun applyProfile(profile: OwnAuthProfile): OwnAuthSession? = stateLock.withLock {
        val current = _session.value ?: return@withLock null
        if (profile.id.isNotBlank() && current.accountId.isNotBlank() && profile.id != current.accountId) {
            AppLogger.w(TAG, "Perfil de outra conta ignorado (a sessão mudou durante a leitura).")
            return@withLock null
        }
        val merged = current.copy(
            email = profile.email.trim().ifEmpty { current.email },
            name = profile.name?.trim()?.takeIf { it.isNotEmpty() } ?: current.name,
            username = profile.username?.trim()?.takeIf { it.isNotEmpty() } ?: current.username,
        )
        if (merged != current) {
            store.save(merged)
            _session.value = merged
        }
        merged
    }

    /**
     * Encerra a sessão local (revogação server-side é do chamador via [OwnAuthApi.logout]).
     * Serializada com a renovação: um refresh em voo não ressuscita a sessão encerrada.
     */
    suspend fun clear() {
        stateLock.withLock {
            store.clear()
            _session.value = null
        }
    }

    /**
     * **Encerra a sessão local e devolve a que estava valendo** (2.304.1, `signOut`) — numa só
     * passada sob a trava do estado, sem rede nenhuma — e **sem esperar um refresh em voo** (ele usa
     * outra trava para a rede). É o que deixa o "Sair" limpar o aparelho ANTES de falar com o
     * servidor; o refresh pendurado, quando voltar, vê que a sessão acabou e descarta o par novo
     * (ver [performRefresh]).
     *
     * Lê o cofre quando a memória está vazia (app que saiu antes do `restore()`), para que a
     * revogação no servidor não seja pulada. Roda inteira em [NonCancellable]: cancelar quem chamou
     * no meio não deixa o par de tokens no cofre.
     */
    internal suspend fun clearAndTake(): OwnAuthSession? = withContext(NonCancellable) {
        stateLock.withLock {
            val previous = _session.value ?: store.load()
            store.clear()
            _session.value = null
            previous
        }
    }

    /**
     * Devolve um access token válido para o `Authorization: Bearer`, renovando proativamente se
     * estiver perto de expirar (ou se [forceRefresh], usado no retry pós-401). `null` = sem sessão.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): String? {
        // Sem sessão em memória, semeia pelo `restore()` — sob a trava, pelo mesmo motivo dele.
        val current = _session.value ?: restore() ?: return null
        if (!forceRefresh && !isExpiringSoon(current)) return current.accessToken
        return mutex.withLock {
            // Reavalia sob o lock: outra corrotina pode já ter renovado enquanto esperávamos.
            val latest = _session.value ?: return@withLock null
            // Single-flight: o refresh é ROTATIVO, então uma renovação concorrente troca o refresh
            // token guardado. Se ele mudou desde a nossa leitura pré-lock, alguém já rotacionou usando
            // o MESMO refresh que usaríamos — refazer reusaria um token já revogado. Reaproveita o novo.
            // Mas só se ainda for a MESMA conta (2.304.2): se a sessão foi trocada enquanto se esperava a
            // trava, o token corrente é de OUTRA pessoa e não pode autenticar um pedido feito pela primeira.
            if (latest.refreshToken != current.refreshToken) {
                return@withLock tokenForSameAccount(origin = current, now = latest)
            }
            performRefresh(latest)
        }
    }

    /** `true` se falta menos que [refreshSkewSeconds] para o access token expirar (ou já expirou). */
    private fun isExpiringSoon(session: OwnAuthSession): Boolean {
        val nowSeconds = nowMillis() / 1000
        return session.accessExpiresAtEpochSeconds - nowSeconds <= refreshSkewSeconds
    }

    /**
     * Executa o refresh rotativo já sob o [mutex]. Devolve o novo access token, ou trata a falha.
     *
     * ### ⚠️ A rotação é atômica frente ao cancelamento (2.304.1)
     * `api.refresh` + `store.save` + a publicação rodam em [NonCancellable]. Até a 2.304.0, se a
     * corrotina de quem pediu o token fosse cancelada (a tela saiu, o `viewModelScope` acabou) **depois**
     * de o servidor rotacionar o refresh e **antes** do `store.save`, o cofre ficava com o refresh
     * ANTIGO — já revogado no servidor. O refresh seguinte mandava esse token, o servidor via
     * **reuso** e revogava a família inteira: a pessoa era deslogada por uma tela lenta, sem ter
     * feito nada.
     *
     * Agora a rotação ou se completa inteira (pedido → gravação → publicação) ou não começa (o
     * cancelamento chegou antes, ainda na espera da trava). Quem chamou continua livre para deixar de
     * esperar: o `withContext` relança o cancelamento ao voltar, e o resultado é descartado — mas o
     * par novo já está no cofre e em [session].
     *
     * ### ⚠️ Teto próprio de [REFRESH_TIMEOUT_MILLIS] (2.304.2)
     * Um pedido não cancelável sem teto seguraria o [mutex] enquanto a rede deixasse — e o teto do
     * `HttpClient` injetado no [OwnAuthConfig] não é garantido (o `createHttpClient` traz 30 s, um
     * `HttpClient()` cru não traz nenhum). O pedido roda em `withTimeoutOrNull` DENTRO do
     * `NonCancellable`: o cancelamento de quem chamou não o interrompe, o relógio sim. Estouro =
     * falha passageira (como rede fora): a sessão é preservada. Se o servidor tiver rotacionado e só
     * a resposta tiver se perdido, o próximo refresh é visto como reuso — o mesmo risco de qualquer
     * resposta perdida, inevitável sem o servidor tolerar a repetição.
     *
     * ### ⚠️ Nunca devolve o token de OUTRA conta (2.304.2)
     * Se a sessão foi trocada durante o refresh (login de outra pessoa), quem pediu o token partiu da
     * sessão antiga: devolver o access token corrente refaria um pedido da conta A com a credencial
     * da conta B. Nesses casos a resposta é `null` (sem sessão para aquele pedido) — ver
     * [tokenForSameAccount].
     *
     * ### ⚠️ O `signOut` vence o refresh em voo (2.304.1)
     * A ida à rede acontece SEM a [stateLock]; a confirmação, com ela. Na volta, o par novo só é
     * gravado se a sessão corrente ainda for a de que o refresh partiu (mesmo refresh token). Se ela
     * foi encerrada ([clear]/[clearAndTake]) ou trocada ([adopt]/[adoptIfSameAccount]) no meio, o
     * par novo é **descartado** — nada volta ao cofre, nenhum estado logado é republicado — e
     * revogado no servidor, best-effort, para não deixar uma família viva que ninguém guarda.
     * O mesmo vale para a recusa (4xx): só derruba a sessão se ela ainda for a mesma — um login
     * novo feito durante o refresh não é apagado pela recusa do par antigo.
     */
    private suspend fun performRefresh(session: OwnAuthSession): String? = withContext(NonCancellable) {
        // Teto PRÓPRIO (2.304.2): sem ele, o limite do pedido não cancelável dependia do `HttpTimeout`
        // do `HttpClient` injetado — e um cliente sem timeout segurava a trava da renovação para
        // sempre (todo pedido autenticado do app parado). O estouro é falha PASSAGEIRA: a sessão
        // fica, e o próximo pedido tenta de novo.
        // O relógio do teto é o do `Dispatchers.Default` — tempo REAL, não o do dispatcher de quem
        // chamou (sob `runTest`, um teto em tempo virtual estouraria antes de a rede responder).
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(refreshTimeoutMillis) { api.refresh(session.refreshToken) }
        } ?: Result.failure(OwnAuthException.Network(REFRESH_TIMEOUT_MESSAGE))
        result.fold(
            onSuccess = { tokens ->
                val outcome = stateLock.withLock {
                    val current = _session.value
                    if (current == null || current.refreshToken != session.refreshToken) {
                        return@withLock RefreshCommit.Discarded(tokenForSameAccount(origin = session, now = current))
                    }
                    // O refresh preserva a identidade da sessão (inclusive a ORIGEM do login): renovar
                    // o token não converte um login social em login por senha. Lê da sessão CORRENTE,
                    // para não desfazer um perfil aplicado enquanto o pedido estava na rede.
                    val renewed = tokens.toSession(current.email, current.name, current.providerId, current.username)
                    store.save(renewed)
                    _session.value = renewed
                    RefreshCommit.Saved(renewed.accessToken)
                }
                when (outcome) {
                    is RefreshCommit.Saved -> outcome.accessToken
                    is RefreshCommit.Discarded -> {
                        AppLogger.w(TAG, "Sessão encerrada ou trocada durante o refresh; par novo descartado.")
                        revokeDiscarded(tokens.refreshToken)
                        outcome.accessToken
                    }
                }
            },
            onFailure = { error ->
                val authError = error as? OwnAuthException
                if (authError != null && authError.isClientError) {
                    // 4xx: refresh inválido/revogado/reusado → fail-closed (derruba a sessão) — se ela
                    // ainda for a mesma de que o refresh partiu.
                    AppLogger.w(TAG, "Refresh rejeitado (${authError.code}); encerrando sessão.")
                    stateLock.withLock {
                        val current = _session.value
                        if (current != null && current.refreshToken == session.refreshToken) {
                            store.clear()
                            _session.value = null
                        }
                        tokenForSameAccount(origin = session, now = _session.value)
                    }
                } else {
                    // Rede/5xx/teto: transitório — preserva a sessão e devolve o access corrente.
                    AppLogger.w(TAG, "Refresh transitório falhou (${authError?.code}); mantendo sessão.")
                    tokenForSameAccount(origin = session, now = _session.value)
                }
            },
        )
    }

    /**
     * O access token de [now] **só se for a mesma conta de [origin]** (o `accountId`, `sub` do JWT,
     * igual e não vazio). Sessão encerrada, trocada por outra conta ou sem `sub` legível → `null`
     * (falha fechada): o pedido feito em nome de [origin] não pode sair com a credencial de outra.
     */
    private fun tokenForSameAccount(origin: OwnAuthSession, now: OwnAuthSession?): String? {
        if (now == null) return null
        if (origin.accountId.isBlank() || now.accountId != origin.accountId) {
            AppLogger.w(TAG, "Sessão trocada por outra conta durante a renovação; token não reaproveitado.")
            return null
        }
        return now.accessToken
    }

    /** Resultado da confirmação de um refresh sob a [stateLock]. */
    private sealed interface RefreshCommit {
        class Saved(val accessToken: String) : RefreshCommit
        class Discarded(val accessToken: String?) : RefreshCommit
    }

    /** Revoga, com teto curto e sem lançar, o par que o refresh trouxe e não foi adotado. */
    private suspend fun revokeDiscarded(refreshToken: String) {
        if (refreshToken.isBlank()) return
        val result = try {
            withTimeoutOrNull(DISCARDED_REVOCATION_TIMEOUT_MILLIS) { api.logout(refreshToken) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        if (result == null || result.isFailure) {
            AppLogger.w(TAG, "Revogação do par descartado não confirmada; ele expira sozinho no servidor.")
        }
    }

    private fun OwnAuthTokens.toSession(
        email: String,
        name: String,
        providerId: String = OwnAuthSession.DEFAULT_PROVIDER_ID,
        username: String? = null,
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
            passwordChangeRequired = passwordChangeRequired,
            username = username,
        )
    }

    companion object {
        private const val TAG = "OwnAuthTokenManager"
        private const val DISCARDED_REVOCATION_TIMEOUT_MILLIS = 5_000L

        /**
         * Teto do `POST {auth}/refresh` (2.304.2), independente do `HttpClient` injetado — o mesmo
         * valor do `requestTimeoutMillis` default do `createHttpClient`.
         */
        const val REFRESH_TIMEOUT_MILLIS: Long = 30_000L
        private const val REFRESH_TIMEOUT_MESSAGE = "Renovação da sessão passou do teto."
    }
}
