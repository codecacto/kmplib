package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.OwnAuthApi
import br.com.codecacto.kmplib.auth.OwnAuthSocialService
import br.com.codecacto.kmplib.auth.OwnAuthTokens
import br.com.codecacto.kmplib.auth.SocialReauthenticator
import br.com.codecacto.kmplib.auth.SocialProvider
import br.com.codecacto.kmplib.auth.SocialDirectExchangeDisabledException
import br.com.codecacto.kmplib.auth.asSocialReauthRejection
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.firebase.auth.User

/**
 * O login social inteiro, num método só — nos **dois** modos ([SocialLoginMode]).
 *
 * ## Por que existe
 * Os dois caminhos têm o mesmo começo (o usuário toca no botão) e o mesmo fim (a sessão own-auth
 * adotada), mas passos completamente diferentes no meio: um pede nonce ao servidor e entrega um
 * `idToken`; o outro gera PKCE, abre o navegador e volta com um código. Sem esta fachada, **cada
 * tela de login do portfólio reescreve os dois roteiros** — e é no meio deles que moram os erros que
 * não aparecem no build: pular o nonce do servidor, guardar o `verifier` no lugar errado, mandar o
 * `accessToken` no lugar do `idToken`, tratar cancelamento como falha.
 *
 * A tela passa a fazer:
 * ```kotlin
 * socialSignIn.signIn(SocialProvider.GOOGLE)
 *     .onSuccess { navegarParaHome() }
 *     .onFailure { if (it.foiCancelado()) Unit else mostrarErro(it.message) }
 * ```
 * e **não sabe qual modo o projeto usa** — trocar de modo é trocar o argumento da construção.
 *
 * ## Cancelamento não é erro
 * Nos dois modos, desistir chega como [SocialBrowserException] com `reason = "cancelado"`. É o único
 * caso em que a tela deve ficar quieta: mostrar "falha no login" para quem fechou a folha de
 * propósito é acusar o usuário de um erro que ele não cometeu.
 *
 * @param mode qual dos dois fluxos este projeto usa.
 * @param api cliente own-auth (é dele que saem a URL de start e a troca do código).
 * @param social serviço social do own-auth — quem adota a sessão no fim, nos dois modos.
 * @param nativeWebClientId **modo [SocialLoginMode.NATIVE]**: o client ID do tipo **Web**, que vira
 *   o `aud` do `idToken` que o backend confere. Não é o client de Android/iOS.
 * @param backendAppId **modo [SocialLoginMode.BACKEND]**: qual app está pedindo o login. Numa
 *   família de flavors é o que decide para onde o usuário volta, e o backend o confere contra a
 *   allowlist — por isso ele é do servidor, não do parâmetro do cliente.
 * @param redirectScheme **modo [SocialLoginMode.BACKEND]**: o esquema do *deep link* de volta,
 *   registrado pelo app (Android: `intent-filter`; iOS: o próprio `ASWebAuthenticationSession`).
 */
class SocialSignIn internal constructor(
    private val mode: SocialLoginMode,
    private val api: OwnAuthApi,
    private val social: OwnAuthSocialService,
    private val nativeWebClientId: String,
    private val backendAppId: String,
    private val redirectScheme: String,
    /** Quem abre o navegador do sistema e devolve o código — trocável só em teste. */
    private val browser: suspend (startUrl: String, redirectScheme: String) -> String,
    /**
     * Quem pede a credencial ao SDK nativo (provedor, nonce, pedir `auth_time`) — trocável só em
     * teste; `null` = os provedores reais ([GoogleAuthProvider]/[AppleAuthProvider]).
     */
    private val nativeCredential: (suspend (SocialProvider, String, Boolean) -> Result<NativeSocialCredential>)? = null,
    /** `ios`/`android` — o client ID que o servidor amarra ao nonce. Trocável só em teste. */
    private val platform: String = currentPlatform,
) : SocialReauthenticator {

    constructor(
        mode: SocialLoginMode,
        api: OwnAuthApi,
        social: OwnAuthSocialService,
        nativeWebClientId: String = "",
        backendAppId: String = "",
        redirectScheme: String = "",
    ) : this(
        mode, api, social, nativeWebClientId, backendAppId, redirectScheme,
        browser = { url, scheme -> SocialBrowserLogin().authenticate(url, scheme) },
    )

    /** Executa o fluxo completo e devolve o usuário já com a sessão adotada. */
    suspend fun signIn(provider: SocialProvider): Result<User> = when (caminhoDoLogin(mode, provider)) {
        CaminhoDoLoginSocial.NATIVO -> signInNativo(provider)
        CaminhoDoLoginSocial.NAVEGADOR -> signInPeloBackend(provider)
    }

    /**
     * **Reautenticação** (2.261.0; contrato da backlib ≥ 0.190.0 desde a 2.304.0): o login social
     * de novo, nos dois modos, devolvendo os tokens **sem adotar a sessão**. É o que o
     * `RecentAuthCoordinator` usa no 401 `REAUTH_REQUIRED` — e a sessão aberta pelo social leva
     * `amr: ["fed"]`, que o servidor nunca aceita como autenticação recente. O coordenador confere
     * que os tokens novos são da MESMA conta antes de trocar.
     *
     * Os dois caminhos são amarrados à conta pelo [accessToken] da sessão, e o servidor só aceita a
     * identidade **já vinculada** a ela (identidade nova/de outra conta é recusada sem escrita):
     *
     * - **Navegador** (Google no modo BACKEND): `POST /social/reauth/start` (Bearer) →
     *   `authorizationUrl` → Custom Tab / `ASWebAuthenticationSession` → `?codigo=` → `POST
     *   /social/exchange`. `?erro=reautenticacao_necessaria` vira
     *   [br.com.codecacto.kmplib.auth.SocialReauthRejectedException].
     * - **Nativo** (Apple no iOS, Google no modo NATIVE): `POST /social/reauth/nonce` (Bearer) →
     *   pedido nativo com o nonce (Apple: SHA-256 hex; Google: `setNonce` **e** a claim
     *   `auth_time` essencial — Credential Manager `GetGoogleIdOption.setClaims`, GIDSignIn ≥ 9.1
     *   `GIDClaim.essentialAuthTime()`) → `POST /social`. 401 `REAUTH_REQUIRED` vira
     *   `SocialReauthRejectedException`; 403 `SOCIAL_DIRECT_EXCHANGE_DISABLED` refaz pelo
     *   navegador quando o app tem `backendAppId`/`redirectScheme`.
     * - 409 `SOCIAL_IDENTITY_NOT_LINKED` (nos dois) vira
     *   [br.com.codecacto.kmplib.auth.SocialIdentityNotLinkedException] — a tela oferece a senha.
     *
     * **Apple não tem pedido de reautenticação** (`ASAuthorizationAppleIDRequest` não tem
     * `prompt`/`max_age`/`claims`). O que vale como prova é a folha do sistema, que autentica o dono
     * do aparelho (Face ID/Touch ID/código), e o nonce de uso único amarrado à conta: o servidor
     * aceita `auth_time` se vier, senão o `iat` do token cunhado para este pedido.
     */
    override suspend fun reauthenticate(provider: SocialProvider, accessToken: String): Result<OwnAuthTokens> {
        val result = when (caminhoDoLogin(mode, provider)) {
            CaminhoDoLoginSocial.NATIVO -> {
                val nativo = reautenticarNativo(provider, accessToken)
                val semTrocaDireta = nativo.exceptionOrNull() is SocialDirectExchangeDisabledException
                if (semTrocaDireta && provider == SocialProvider.GOOGLE && backendConfigurado) {
                    reautenticarPeloNavegador(provider, accessToken)
                } else {
                    nativo
                }
            }
            CaminhoDoLoginSocial.NAVEGADOR -> reautenticarPeloNavegador(provider, accessToken)
        }
        return result.exceptionOrNull()?.let { Result.failure(it.asSocialReauthRejection(provider)) } ?: result
    }

    /**
     * Sem a sessão não há reautenticação social (backlib ≥ 0.190.0): o servidor amarra o fluxo à
     * conta do Bearer. Use [reauthenticate] com o access token — o `RecentAuthCoordinator` já o faz.
     */
    override suspend fun reauthenticate(provider: SocialProvider): Result<OwnAuthTokens> =
        falha("A reautenticação social exige a sessão atual (reauthenticate(provider, accessToken)).")

    private val backendConfigurado: Boolean
        get() = backendAppId.isNotBlank() && redirectScheme.isNotBlank()

    private suspend fun reautenticarNativo(provider: SocialProvider, accessToken: String): Result<OwnAuthTokens> {
        val nonce = api.socialReauthNonce(provider, platform, accessToken).getOrElse { return Result.failure(it) }.nonce
        if (nonce.isBlank()) return falha("O servidor não emitiu o nonce da reautenticação.")
        // Google: `auth_time` pedido como claim essencial — sem ele o servidor recusa a prova.
        val c = obterCredencialNativa(provider, nonce, requestAuthTime = provider == SocialProvider.GOOGLE)
            .getOrElse { return Result.failure(it) }
        return api.social(provider, c.idToken, c.nonce)
    }

    private suspend fun reautenticarPeloNavegador(provider: SocialProvider, accessToken: String): Result<OwnAuthTokens> {
        if (!backendConfigurado) return falhaBackendNaoConfigurado()
        val pkce = PkcePair.generate()
        val url = api.socialReauthStart(provider, backendAppId, pkce.challenge, accessToken)
            .getOrElse { return Result.failure(it) }
        val codigo = abrirNavegador(url).getOrElse { return Result.failure(it) }
        return api.socialExchange(codigo, pkce.verifier)
    }

    // ── Nativo ────────────────────────────────────────────────────────────────

    private suspend fun signInNativo(provider: SocialProvider): Result<User> {
        // O NONCE VEM DO SERVIDOR, sempre, e é o passo 1. Nonce escolhido pelo cliente não amarra
        // nada: um `idToken` vazado é reapresentado com o mesmo valor e passa.
        val nonce = social.socialNonce().getOrElse { return Result.failure(it) }.nonce
        if (nonce.isBlank()) {
            return falha("O servidor não emitiu o nonce do login social.")
        }
        val c = obterCredencialNativa(provider, nonce, requestAuthTime = false).getOrElse { return Result.failure(it) }
        return social.signInWithSocial(
            provider = provider,
            idToken = c.idToken,
            nonce = c.nonce,
            name = c.name,
            email = c.email,
        )
    }

    private suspend fun obterCredencialNativa(
        provider: SocialProvider,
        nonce: String,
        requestAuthTime: Boolean,
    ): Result<NativeSocialCredential> =
        nativeCredential?.invoke(provider, nonce, requestAuthTime) ?: credencialNativa(provider, nonce, requestAuthTime)

    /**
     * Passo 2 do nativo: o provedor embute [nonce] no `idToken`; com [requestAuthTime], o Google
     * recebe a claim `auth_time` essencial. Cancelamento vira [cancelado].
     */
    private suspend fun credencialNativa(
        provider: SocialProvider,
        nonce: String,
        requestAuthTime: Boolean,
    ): Result<NativeSocialCredential> =
        when (provider) {
            SocialProvider.GOOGLE -> {
                if (nativeWebClientId.isBlank()) {
                    falha(
                        "Login com Google não configurado nesta build: falta o client ID do tipo Web."
                    )
                } else {
                    val r = GoogleAuthProvider(nativeWebClientId).signIn(nonce, requestAuthTime)
                    when {
                        r.isCancelled -> cancelado()
                        r.idToken.isNullOrBlank() -> falha(r.error ?: "O Google não devolveu o idToken.")
                        else -> Result.success(NativeSocialCredential(r.idToken!!, nonce, r.displayName, r.email))
                    }
                }
            }

            SocialProvider.APPLE -> {
                val r = AppleAuthProvider().signIn(nonce)
                when {
                    r.isCancelled -> cancelado()
                    r.idToken.isNullOrBlank() -> falha(r.error ?: "A Apple não devolveu o identityToken.")
                    // O valor CRU: a Apple recebeu o SHA-256 dele, e é o cru que o backend precisa
                    // para refazer o hash.
                    else -> Result.success(NativeSocialCredential(r.idToken!!, r.nonce ?: nonce, r.fullName, r.email))
                }
            }
        }

    // ── Pelo backend ──────────────────────────────────────────────────────────

    private suspend fun signInPeloBackend(provider: SocialProvider): Result<User> {
        // O par PKCE é gerado a CADA tentativa e vive só nesta função: guardá-lo em campo faria duas
        // tentativas simultâneas trocarem de verifier, e o backend recusaria as duas.
        val pkce = PkcePair.generate()
        val codigo = codigoPeloNavegador(provider, pkce).getOrElse { return Result.failure(it) }
        return social.signInWithSocialCode(codigo, pkce.verifier)
    }

    /** Abre o navegador do sistema no `/social/start` e devolve o código do *deep link* de volta. */
    private suspend fun codigoPeloNavegador(provider: SocialProvider, pkce: PkcePair): Result<String> {
        if (!backendConfigurado) return falhaBackendNaoConfigurado()
        return abrirNavegador(api.socialStartUrl(provider, appId = backendAppId, codeChallenge = pkce.challenge))
    }

    /** Abre [url] no navegador do sistema e devolve o código do *deep link* de volta. */
    private suspend fun abrirNavegador(url: String): Result<String> =
        try {
            Result.success(browser(url, redirectScheme))
        } catch (e: SocialBrowserException) {
            Result.failure(e)
        }

    private fun <T> falhaBackendNaoConfigurado(): Result<T> = falha(
        "Login social pelo backend não configurado nesta build: falta o identificador do " +
            "app ou o esquema do deep link de volta."
    )

    // ── Erros ─────────────────────────────────────────────────────────────────

    private fun <T> falha(mensagem: String): Result<T> =
        Result.failure(SocialBrowserException(mensagem, reason = "falha"))

    private fun <T> cancelado(): Result<T> =
        Result.failure(SocialBrowserException("Login cancelado.", reason = "cancelado"))
}

/** O que o provedor nativo devolve e o backend precisa para emitir a sessão. */
internal class NativeSocialCredential(val idToken: String, val nonce: String, val name: String?, val email: String?)

/** Por onde um provedor negocia o login. */
internal enum class CaminhoDoLoginSocial { NATIVO, NAVEGADOR }

/**
 * Qual caminho [SocialSignIn] toma para cada provedor (2.206.0).
 *
 * **A Apple é SEMPRE nativa, em qualquer modo.** O [SocialLoginMode] existe por causa do Google — o
 * teto de clientes OAuth por projeto do Google Cloud — e esse teto não existe na Apple, que
 * identifica o app pelo bundle id. No iOS o caminho recomendado pela própria Apple é o
 * `AuthenticationServices` (folha do sistema, Face ID); negociar pelo navegador exigiria Services ID,
 * domínio verificado e um `client_secret` JWT assinado com o `.p8`, para uma experiência pior.
 *
 * Até a 2.205.0 o modo [SocialLoginMode.BACKEND] mandava a Apple para o `/social/start`, onde o
 * backend (corretamente) só registra o Google — e o botão respondia "Provedor social não habilitado".
 */
internal fun caminhoDoLogin(mode: SocialLoginMode, provider: SocialProvider): CaminhoDoLoginSocial =
    when (provider) {
        SocialProvider.APPLE -> CaminhoDoLoginSocial.NATIVO
        SocialProvider.GOOGLE -> when (mode) {
            SocialLoginMode.NATIVE -> CaminhoDoLoginSocial.NATIVO
            SocialLoginMode.BACKEND -> CaminhoDoLoginSocial.NAVEGADOR
        }
    }

/**
 * `true` quando a pessoa desistiu do login — nos dois modos, e em qualquer plataforma.
 *
 * Existe para a tela não ter de comparar strings de erro (era assim que "cancelado" virava
 * "falha no login" na primeira tradução que mudasse).
 */
fun Throwable.foiCancelado(): Boolean =
    this is SocialBrowserException && reason == "cancelado"
