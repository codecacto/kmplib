package br.com.codecacto.kmplib.auth.social

/**
 * Resultado do Google Sign-In **nativo** (Credential Manager no Android, GoogleSignIn-iOS no iOS).
 *
 * Neutro quanto ao backend: serve tanto o Firebase Auth (`AuthRepository`) quanto o **own-auth**
 * (`EmailPasswordAuthRepository` + `POST /auth/social`).
 *
 * ### `accessToken` NÃO é prova de identidade
 * Só o [idToken] (JWT assinado pelo Google, com `aud`/`nonce`/`exp` verificáveis) prova quem é o
 * usuário. O [accessToken] é uma credencial de **autorização** para chamar APIs do Google em nome
 * dele — qualquer app pode obter um para o próprio projeto e apresentá-lo a um servidor terceiro
 * (ataque clássico de *token substitution*). Por isso a rota own-auth da lib **ignora** este campo
 * e envia apenas o `idToken`. Ele fica aqui só para quem precise chamar uma API Google.
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
