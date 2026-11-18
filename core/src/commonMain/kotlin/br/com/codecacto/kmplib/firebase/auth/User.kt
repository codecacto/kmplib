package br.com.codecacto.kmplib.firebase.auth

import kotlinx.serialization.Serializable

/**
 * Modelo de usuário autenticado.
 */
@Serializable
data class User(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isEmailVerified: Boolean = false,
    val providerId: String = "password",
    /**
     * **Nome de usuário** da conta (2.229.0) — o identificador que o login aceita além do e-mail
     * (constituição: toda conta tem os dois, login `BOTH`).
     *
     * Aditivo e nulável: `null` quando o backend não o devolve (backlib anterior à 0.140.0, produto
     * que não preenche no `findProfile`, **sessão com a senha temporária** — em que o servidor o
     * omite de propósito) e sempre no Firebase, que não tem o conceito. No own-auth ele vem do
     * `GET {authBasePath}/me`, lido logo depois de cada login e por
     * `OwnAuthService.refreshOwnProfile()`.
     */
    val username: String? = null,
    /**
     * **Quando a conta foi criada** (2.371.0) — ISO-8601 com fuso, como o servidor manda
     * (`2026-11-18T13:05:00Z`). Aditivo e nulável: no own-auth vem do `GET {authBasePath}/me`
     * (backlib-auth-local ≥ 0.273.0, `createdAt`), lido logo depois de cada login; `null` quando o
     * backend não o informa, na sessão com senha temporária e sempre no Firebase. Uso: as
     * boas-vindas do 1º acesso (`WelcomeAnnouncementConfig(accountCreatedAt = user.createdAt, …)`),
     * que só aparecem para conta de até 7 dias — sem a data, não aparecem.
     */
    val createdAt: String? = null,
) {
    /**
     * Verifica se o usuário fez login com email/senha.
     */
    val isEmailProvider: Boolean get() = providerId == "password"

    /**
     * Verifica se o usuário fez login com Google.
     */
    val isGoogleProvider: Boolean get() = providerId == "google.com"

    /**
     * Verifica se o usuário fez login com Apple.
     */
    val isAppleProvider: Boolean get() = providerId == "apple.com"
}
