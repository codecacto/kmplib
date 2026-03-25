package br.com.codecacto.kmplib.firebase.auth

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

actual class GoogleAuthProvider actual constructor(private val webClientId: String) {

    actual suspend fun signIn(): GoogleSignInResult {
        val context = GoogleAuthHolder.getContext()
            ?: return GoogleSignInResult(
                idToken = null,
                error = "GoogleAuthHolder não foi inicializado. Chame KmpLib.init(context) no Application.onCreate()"
            )

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            GoogleSignInResult(
                idToken = idToken,
                accessToken = null
            )
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult(
                idToken = null,
                error = "Login cancelado pelo usuário"
            )
        } catch (e: Exception) {
            GoogleSignInResult(
                idToken = null,
                error = e.message ?: "Erro ao realizar login com Google"
            )
        }
    }
}
