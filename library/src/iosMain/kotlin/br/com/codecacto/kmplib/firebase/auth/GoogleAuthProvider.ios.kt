package br.com.codecacto.kmplib.firebase.auth

actual class GoogleAuthProvider actual constructor(private val webClientId: String) {

    actual suspend fun signIn(): GoogleSignInResult {
        return GoogleSignInResult(
            idToken = null,
            error = "Google Sign-In deve ser implementado no nível do Swift/SwiftUI. Use googleSignInHandler no MainViewController."
        )
    }
}
