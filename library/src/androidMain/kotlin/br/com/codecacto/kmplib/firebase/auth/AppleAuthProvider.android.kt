package br.com.codecacto.kmplib.firebase.auth

actual class AppleAuthProvider actual constructor() {

    actual suspend fun signIn(): AppleSignInResult {
        return AppleSignInResult(
            idToken = null,
            nonce = null,
            error = "Login com Apple não disponível no Android. Use o login com Google."
        )
    }
}
