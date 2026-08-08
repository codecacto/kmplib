package br.com.codecacto.kmplib.auth.social

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Credential Manager + `GetGoogleIdOption` — a API oficial vigente do Google no Android (a
 * `GoogleSignIn` do Play Services está deprecada). **Zero Firebase.**
 */
actual class GoogleAuthProvider actual constructor(private val webClientId: String) {

    actual suspend fun signIn(): GoogleSignInResult = perform(nonce = null)

    actual suspend fun signIn(nonce: String): GoogleSignInResult = perform(nonce = nonce)

    private suspend fun perform(nonce: String?): GoogleSignInResult {
        val activity = GoogleAuthHolder.getActivity()
            ?: return GoogleSignInResult(
                idToken = null,
                error = "GoogleAuthHolder sem Activity. Chame KmpLib.setActivity(this) no Activity.onResume()"
            )
        val context = activity

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .apply { if (!nonce.isNullOrBlank()) setNonce(nonce) }
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            GoogleSignInResult(
                idToken = googleIdTokenCredential.idToken,
                // De propósito: o Credential Manager NÃO devolve access token, e o own-auth não o
                // aceitaria como prova de identidade de qualquer forma (ver GoogleSignInResult).
                accessToken = null,
                email = googleIdTokenCredential.id.takeIf { it.isNotBlank() },
                displayName = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() },
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
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
