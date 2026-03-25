package br.com.codecacto.kmplib.firebase.auth

expect class GoogleAuthProvider(webClientId: String) {
    suspend fun signIn(): GoogleSignInResult
}
