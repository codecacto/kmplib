package br.com.codecacto.kmplib.firebase.auth

/**
 * Resultado do Apple Sign-In nativo.
 *
 * Use esta classe para encapsular o resultado do Apple Sign-In
 * implementado nativamente no iOS.
 */
data class AppleSignInResult(
    val idToken: String? = null,
    val nonce: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val error: String? = null
) {
    /**
     * Verifica se o login foi bem-sucedido.
     */
    val isSuccess: Boolean get() = idToken != null && nonce != null && error == null

    /**
     * Verifica se o usuário cancelou o login.
     */
    val isCancelled: Boolean get() = error?.contains("cancel", ignoreCase = true) == true

    companion object {
        fun success(
            idToken: String,
            nonce: String,
            email: String? = null,
            fullName: String? = null
        ) = AppleSignInResult(
            idToken = idToken,
            nonce = nonce,
            email = email,
            fullName = fullName
        )

        fun error(message: String) = AppleSignInResult(error = message)

        fun cancelled() = AppleSignInResult(error = "Login cancelado pelo usuário")
    }
}
