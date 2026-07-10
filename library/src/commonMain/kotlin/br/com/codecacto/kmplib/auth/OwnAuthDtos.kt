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
