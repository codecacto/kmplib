package br.com.codecacto.kmplib.firebase.auth

/**
 * Resultado do Google Sign-In nativo.
 *
 * Use esta classe para encapsular o resultado do Google Sign-In
 * implementado nativamente em cada plataforma (Android/iOS).
 */
data class GoogleSignInResult(
    val idToken: String? = null,
    val accessToken: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val error: String? = null
) {
    /**
     * Verifica se o login foi bem-sucedido.
     */
    val isSuccess: Boolean get() = idToken != null && error == null

    /**
     * Verifica se o usuário cancelou o login.
     */
    val isCancelled: Boolean get() = error?.contains("cancel", ignoreCase = true) == true

    companion object {
        fun success(
            idToken: String,
            accessToken: String? = null,
            email: String? = null,
            displayName: String? = null,
            photoUrl: String? = null
        ) = GoogleSignInResult(
            idToken = idToken,
            accessToken = accessToken,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl
        )

        fun error(message: String) = GoogleSignInResult(error = message)

        fun cancelled() = GoogleSignInResult(error = "Login cancelado pelo usuário")
    }
}
