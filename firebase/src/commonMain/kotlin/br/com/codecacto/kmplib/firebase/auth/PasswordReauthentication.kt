package br.com.codecacto.kmplib.firebase.auth

/**
 * A conta logada, vista só pelo que as operações sensíveis precisam (troca de senha e exclusão).
 *
 * Existe para a sequência "reconfirmar a credencial → executar" ser testável sem o Firebase: o
 * [AuthRepository] adapta o `FirebaseUser` do GitLive (`FirebaseReauthTarget`), o teste usa um dublê.
 *
 * ⚠️ Reautenticar é **`FirebaseUser.reauthenticate(EmailAuthProvider.credential(email, senha))`**,
 * o caminho oficial do Firebase para operação sensível (`updatePassword`, `delete`). Até a 2.340.1 a
 * lib fazia `signInWithEmailAndPassword` — funcionava, mas abria uma SESSÃO NOVA (troca o
 * `currentUser`, dispara `authStateChanged`, e com outra conta no meio o `updatePassword` iria para o
 * objeto antigo), em vez de só reconfirmar a credencial da sessão que já existe.
 */
internal interface ReauthTarget {
    val email: String?
    suspend fun reauthenticateWithPassword(email: String, password: String)
    suspend fun updatePassword(newPassword: String)
    suspend fun delete()
}

/**
 * Troca a senha de [target]: reconfirma a senha atual na MESMA sessão e só então troca.
 * Senha atual errada não chega ao `updatePassword`.
 */
internal suspend fun changePasswordReauthenticated(
    target: ReauthTarget?,
    currentPassword: String,
    newPassword: String,
): Result<Unit> {
    return try {
        val user = target ?: return Result.failure(AuthException.NotAuthenticated)
        val email = user.email
            ?: return Result.failure(AuthException.UnknownError("Email não encontrado"))

        user.reauthenticateWithPassword(email, currentPassword)
        user.updatePassword(newPassword)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(mapFirebaseAuthException(e))
    }
}

/**
 * Exclui a conta de [target]. Com [password] e e-mail, reconfirma a credencial antes (conta de
 * e-mail/senha); sem eles (conta social) vai direto — o Firebase responde `requires-recent-login`
 * se a sessão for antiga, e isso chega como [AuthException.RequiresRecentLogin].
 */
internal suspend fun deleteAccountReauthenticated(
    target: ReauthTarget?,
    password: String?,
): Result<Unit> {
    return try {
        val user = target ?: return Result.failure(AuthException.NotAuthenticated)
        val email = user.email
        if (password != null && email != null) {
            user.reauthenticateWithPassword(email, password)
        }
        user.delete()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(mapFirebaseAuthException(e))
    }
}

/** Mensagem do SDK (Android/iOS, via GitLive) → [AuthException]. Contrato estável para o app. */
internal fun mapFirebaseAuthException(e: Exception): AuthException {
    val message = e.message?.lowercase() ?: ""

    return when {
        "invalid-email" in message || "badly formatted" in message ->
            AuthException.InvalidEmail

        "wrong-password" in message || "invalid-credential" in message ||
                "invalid credential" in message ->
            AuthException.InvalidCredentials

        "user-not-found" in message || "no user record" in message ->
            AuthException.UserNotFound

        "email-already-in-use" in message || "already in use" in message ->
            AuthException.EmailAlreadyInUse

        "weak-password" in message || "should be at least" in message ->
            AuthException.WeakPassword

        "too-many-requests" in message || "blocked" in message ->
            AuthException.TooManyRequests

        "network" in message || "connection" in message ->
            AuthException.NetworkError

        "requires-recent-login" in message || "recent" in message ->
            AuthException.RequiresRecentLogin

        else -> AuthException.UnknownError(e.message ?: "Erro desconhecido")
    }
}
