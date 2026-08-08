package br.com.codecacto.kmplib.auth

import kotlinx.serialization.Serializable

/**
 * Par de tokens devolvido pelo backend own-auth em `register`/`login`/`refresh`.
 *
 * Espelha o contrato `AuthTokens` do `backlib-auth-local` (issuer próprio do projeto): o `accessToken`
 * é um JWT curto (Bearer) e o `refreshToken` é opaco e **rotativo** (cada `refresh` devolve um novo,
 * o antigo é revogado). `expiresInSeconds` é o tempo de vida do access token, usado para o refresh
 * proativo.
 */
@Serializable
data class OwnAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val tokenType: String = "Bearer",
)

/**
 * Nonce de uso único emitido pelo **servidor** (`GET {authBasePath}/social/nonce`), a ser embutido
 * no `idToken` do provedor social.
 *
 * **Por que vem do servidor:** o nonce existe para amarrar *aquele* `idToken` a *aquela* requisição
 * de login. Um nonce escolhido pelo próprio aparelho não amarra nada — quem obtiver um `idToken`
 * válido (log, proxy, app malicioso no mesmo dispositivo) o reapresenta escolhendo o mesmo valor. Só
 * o emissor que também verifica pode declarar "este token foi feito para esta sessão".
 *
 * @property expiresInSeconds validade informada pelo servidor (o backend do ecossistema usa 5 min).
 *   É informativo: quem invalida é o servidor, o cliente não decide expiração.
 */
@Serializable
data class SocialNonce(
    val nonce: String,
    val expiresInSeconds: Long = 0,
)

/**
 * Corpo de `POST {authBasePath}/social`.
 *
 * **`accessToken` do Google NÃO existe aqui, de propósito** — ele não é prova de identidade
 * (ver `GoogleSignInResult`). Só o `idToken` viaja.
 *
 * `name`/`email` são aceitos pelo servidor **apenas na criação** da identidade (a Apple só entrega
 * nome/e-mail na primeira autorização); em login subsequente o servidor os ignora.
 */
@Serializable
internal data class SocialBody(
    val provider: String,
    val idToken: String,
    val nonce: String,
    val name: String? = null,
    val email: String? = null,
)

@Serializable
internal data class RegisterBody(
    val name: String,
    val email: String,
    val password: String,
    val acceptedTerms: Boolean,
)

@Serializable
internal data class LoginBody(val email: String, val password: String)

@Serializable
internal data class RefreshBody(val refreshToken: String)

@Serializable
internal data class LogoutBody(val refreshToken: String)

@Serializable
internal data class PasswordForgotBody(val email: String)

@Serializable
internal data class PasswordResetBody(val token: String, val newPassword: String)
