package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.OwnAuthApi
import br.com.codecacto.kmplib.auth.OwnAuthSocialService
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
) {

    /** Executa o fluxo completo e devolve o usuário já com a sessão adotada. */
    suspend fun signIn(provider: SocialProvider): Result<User> = when (mode) {
        SocialLoginMode.NATIVE -> signInNativo(provider)
        SocialLoginMode.BACKEND -> signInPeloBackend(provider)
    }

    // ── Nativo ────────────────────────────────────────────────────────────────

    private suspend fun signInNativo(provider: SocialProvider): Result<User> {
        // O NONCE VEM DO SERVIDOR, sempre, e é o passo 1. Nonce escolhido pelo cliente não amarra
        // nada: um `idToken` vazado é reapresentado com o mesmo valor e passa.
        val nonce = social.socialNonce().getOrElse { return Result.failure(it) }.nonce
        if (nonce.isBlank()) {
            return falha("O servidor não emitiu o nonce do login social.")
        }
        return when (provider) {
            SocialProvider.GOOGLE -> {
                if (nativeWebClientId.isBlank()) {
                    return falha(
                        "Login com Google não configurado nesta build: falta o client ID do tipo Web."
                    )
                }
                val r = GoogleAuthProvider(nativeWebClientId).signIn(nonce)
                when {
                    r.isCancelled -> cancelado()
                    r.idToken.isNullOrBlank() -> falha(r.error ?: "O Google não devolveu o idToken.")
                    else -> social.signInWithSocial(
                        provider = SocialProvider.GOOGLE,
                        idToken = r.idToken!!,
                        nonce = nonce,
                        name = r.displayName,
                        email = r.email,
                    )
                }
            }

            SocialProvider.APPLE -> {
                val r = AppleAuthProvider().signIn(nonce)
                when {
                    r.isCancelled -> cancelado()
                    r.idToken.isNullOrBlank() -> falha(r.error ?: "A Apple não devolveu o identityToken.")
                    else -> social.signInWithSocial(
                        provider = SocialProvider.APPLE,
                        idToken = r.idToken!!,
                        // O valor CRU: a Apple recebeu o SHA-256 dele, e é o cru que o backend
                        // precisa para refazer o hash.
                        nonce = r.nonce ?: nonce,
                        name = r.fullName,
                        email = r.email,
                    )
                }
            }
        }
    }

    // ── Pelo backend ──────────────────────────────────────────────────────────

    private suspend fun signInPeloBackend(provider: SocialProvider): Result<User> {
        if (backendAppId.isBlank() || redirectScheme.isBlank()) {
            return falha(
                "Login social pelo backend não configurado nesta build: falta o identificador do " +
                    "app ou o esquema do deep link de volta."
            )
        }
        // O par PKCE é gerado a CADA tentativa e vive só nesta função: guardá-lo em campo faria duas
        // tentativas simultâneas trocarem de verifier, e o backend recusaria as duas.
        val pkce = PkcePair.generate()
        val url = api.socialStartUrl(provider, appId = backendAppId, codeChallenge = pkce.challenge)
        val codigo = try {
            SocialBrowserLogin().authenticate(url, redirectScheme)
        } catch (e: SocialBrowserException) {
            return Result.failure(e)
        }
        return social.signInWithSocialCode(codigo, pkce.verifier)
    }

    // ── Erros ─────────────────────────────────────────────────────────────────

    private fun <T> falha(mensagem: String): Result<T> =
        Result.failure(SocialBrowserException(mensagem, reason = "falha"))

    private fun <T> cancelado(): Result<T> =
        Result.failure(SocialBrowserException("Login cancelado.", reason = "cancelado"))
}

/**
 * `true` quando a pessoa desistiu do login — nos dois modos, e em qualquer plataforma.
 *
 * Existe para a tela não ter de comparar strings de erro (era assim que "cancelado" virava
 * "falha no login" na primeira tradução que mudasse).
 */
fun Throwable.foiCancelado(): Boolean =
    this is SocialBrowserException && reason == "cancelado"
