package br.com.codecacto.kmplib.auth.social

/**
 * Google Sign-In no iOS via SDK oficial **GoogleSignIn-iOS** (SPM), alimentado pelo Swift através do
 * [GoogleSignInBridge]. Ver o KDoc do bridge para o passo a passo do `AppDelegate`/`@main App`.
 *
 * **Sem o executor registrado no Swift, devolve erro explícito** (nunca um resultado vazio que a
 * tela confunda com "cancelado").
 */
actual class GoogleAuthProvider actual constructor(private val webClientId: String) {

    actual suspend fun signIn(): GoogleSignInResult =
        GoogleSignInBridge.awaitSignIn(webClientId, nonce = null)

    actual suspend fun signIn(nonce: String): GoogleSignInResult =
        GoogleSignInBridge.awaitSignIn(webClientId, nonce = nonce.takeIf { it.isNotBlank() })
}
