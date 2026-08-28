package br.com.codecacto.kmplib.auth.social

/**
 * Resultado do Sign in with Apple **nativo** (`ASAuthorizationController`, iOS).
 *
 * Neutro quanto ao backend: serve tanto o Firebase Auth (`AuthRepository`) quanto o **own-auth**
 * (`EmailPasswordAuthRepository` + `POST /auth/social`).
 *
 * ### O [nonce] devolvido é o valor CRU
 * A Apple recebe o **SHA-256 (hex)** do nonce e o publica na claim `nonce` do `identityToken`; quem
 * verifica compara o hash. Portanto este campo carrega o valor **cru**, que é o que o servidor
 * precisa para refazer o hash e conferir.
 *
 * ### [email]/[fullName] só vêm UMA vez
 * A Apple entrega nome e e-mail **apenas na primeira autorização** daquele Apple ID para aquele app;
 * em logins seguintes vêm `null`. Por isso o contrato own-auth aceita `name`/`email` só na criação
 * da identidade — reenviá-los depois não teria efeito (e o servidor ignora de propósito).
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
