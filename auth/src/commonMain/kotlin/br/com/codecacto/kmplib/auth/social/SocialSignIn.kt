package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.OwnAuthApi
import br.com.codecacto.kmplib.auth.OwnAuthSocialService
import br.com.codecacto.kmplib.auth.OwnAuthTokens
import br.com.codecacto.kmplib.auth.SocialReauthenticator
import br.com.codecacto.kmplib.auth.SocialProvider
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
class SocialSignIn(
    private val mode: SocialLoginMode,
    private val api: OwnAuthApi,
    private val social: OwnAuthSocialService,
    private val nativeWebClientId: String = "",
    private val backendAppId: String = "",
    private val redirectScheme: String = "",
) : SocialReauthenticator {

    /** Executa o fluxo completo e devolve o usuário já com a sessão adotada. */
    suspend fun signIn(provider: SocialProvider): Result<User> = when (caminhoDoLogin(mode, provider)) {
        CaminhoDoLoginSocial.NATIVO -> signInNativo(provider)
        CaminhoDoLoginSocial.NAVEGADOR -> signInPeloBackend(provider)
    }

    /**
     * **Reautenticação** (2.261.0): o mesmo fluxo de [signIn], nos dois modos, mas devolve os tokens
     * **sem adotar a sessão**. É o que o `RecentAuthCoordinator` usa no 401 `REAUTH_REQUIRED`: ele
     * confere que os tokens novos são da MESMA conta antes de trocar — adotar primeiro e conferir
     * depois deixaria, por um instante, a sessão de outra pessoa no lugar da do titular.
     *
     * O nonce aqui sai direto do [api] (não passa pelo "nonce em voo" do repositório), e cada
     * tentativa gera o próprio par PKCE, como no login.
     */
    override suspend fun reauthenticate(provider: SocialProvider): Result<OwnAuthTokens> =
        when (caminhoDoLogin(mode, provider)) {
            CaminhoDoLoginSocial.NATIVO -> {
                val nonce = api.socialNonce().getOrElse { return Result.failure(it) }.nonce
                if (nonce.isBlank()) return falha("O servidor não emitiu o nonce do login social.")
                credencialNativa(provider, nonce).fold(
                    onSuccess = { c -> api.social(provider, c.idToken, c.nonce, c.name, c.email) },
                    onFailure = { Result.failure(it) },
                )
            }
            CaminhoDoLoginSocial.NAVEGADOR -> {
                val pkce = PkcePair.generate()
                codigoPeloNavegador(provider, pkce).fold(
                    onSuccess = { codigo -> api.socialExchange(codigo, pkce.verifier) },
                    onFailure = { Result.failure(it) },
                )
            }
        }

    // ── Nativo ────────────────────────────────────────────────────────────────

    private suspend fun signInNativo(provider: SocialProvider): Result<User> {
        // O NONCE VEM DO SERVIDOR, sempre, e é o passo 1. Nonce escolhido pelo cliente não amarra
        // nada: um `idToken` vazado é reapresentado com o mesmo valor e passa.
        val nonce = social.socialNonce().getOrElse { return Result.failure(it) }.nonce
        if (nonce.isBlank()) {
            return falha("O servidor não emitiu o nonce do login social.")
        }
        val c = credencialNativa(provider, nonce).getOrElse { return Result.failure(it) }
        return social.signInWithSocial(
            provider = provider,
            idToken = c.idToken,
            nonce = c.nonce,
            name = c.name,
            email = c.email,
        )
    }

    /** O que o provedor nativo devolve e o backend precisa para emitir a sessão. */
    private class CredencialNativa(val idToken: String, val nonce: String, val name: String?, val email: String?)

    /** Passo 2 do nativo: o provedor embute [nonce] no `idToken`. Cancelamento vira [cancelado]. */
    private suspend fun credencialNativa(provider: SocialProvider, nonce: String): Result<CredencialNativa> =
        when (provider) {
            SocialProvider.GOOGLE -> {
                if (nativeWebClientId.isBlank()) {
                    falha(
                        "Login com Google não configurado nesta build: falta o client ID do tipo Web."
                    )
                } else {
                    val r = GoogleAuthProvider(nativeWebClientId).signIn(nonce)
                    when {
                        r.isCancelled -> cancelado()
                        r.idToken.isNullOrBlank() -> falha(r.error ?: "O Google não devolveu o idToken.")
                        else -> Result.success(CredencialNativa(r.idToken!!, nonce, r.displayName, r.email))
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
                    else -> Result.success(CredencialNativa(r.idToken!!, r.nonce ?: nonce, r.fullName, r.email))
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
        if (backendAppId.isBlank() || redirectScheme.isBlank()) {
            return falha(
                "Login social pelo backend não configurado nesta build: falta o identificador do " +
                    "app ou o esquema do deep link de volta."
            )
        }
        val url = api.socialStartUrl(provider, appId = backendAppId, codeChallenge = pkce.challenge)
        return try {
            Result.success(SocialBrowserLogin().authenticate(url, redirectScheme))
        } catch (e: SocialBrowserException) {
            Result.failure(e)
        }
    }

    // ── Erros ─────────────────────────────────────────────────────────────────

    private fun <T> falha(mensagem: String): Result<T> =
        Result.failure(SocialBrowserException(mensagem, reason = "falha"))

    private fun <T> cancelado(): Result<T> =
        Result.failure(SocialBrowserException("Login cancelado.", reason = "cancelado"))
}

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
