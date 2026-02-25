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
    val providerId: String = "password"
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
