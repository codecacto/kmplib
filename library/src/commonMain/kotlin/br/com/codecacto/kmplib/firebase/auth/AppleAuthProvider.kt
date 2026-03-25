package br.com.codecacto.kmplib.firebase.auth

expect class AppleAuthProvider() {
    suspend fun signIn(): AppleSignInResult
}
