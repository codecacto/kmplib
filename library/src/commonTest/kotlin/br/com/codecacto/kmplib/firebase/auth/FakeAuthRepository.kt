package br.com.codecacto.kmplib.firebase.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Fake reutilizável de [IAuthRepository] para `commonTest` — permite testar ViewModels/serviços que
 * dependem de auth sem Firebase real. Mantém o usuário corrente num [MutableStateFlow] e simula
 * sucesso/erro de forma determinística.
 *
 * - `signInWithEmail`/`signUpWithEmail`/`signInWithGoogle`/`signInWithApple` logam o usuário
 *   ([nextResult] pode forçar falha).
 * - `signOut` limpa o usuário.
 * - `getIdToken` devolve [idToken] quando logado, senão falha.
 */
class FakeAuthRepository(initialUser: User? = null) : IAuthRepository {

    private val userState = MutableStateFlow(initialUser)

    /** Se setado, a próxima operação de login/cadastro falha com esta exceção (e é consumido). */
    var nextResult: Throwable? = null

    /** Token devolvido por [getIdToken] quando há usuário logado. */
    var idToken: String = "fake-id-token"

    override val currentUser: Flow<User?> = userState
    override val isLoggedIn: Flow<Boolean> = userState.map { it != null }
    override val currentUserSync: User? get() = userState.value
    override val isLoggedInSync: Boolean get() = userState.value != null

    private fun login(user: User): Result<User> {
        nextResult?.let { nextResult = null; return Result.failure(it) }
        userState.value = user
        return Result.success(user)
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<User> =
        login(User(id = "uid-$email", email = email, providerId = "password"))

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): Result<User> =
        login(User(id = "uid-google", email = "g@example.com", providerId = "google.com"))

    override suspend fun signInWithApple(idToken: String, nonce: String): Result<User> =
        login(User(id = "uid-apple", email = "a@example.com", providerId = "apple.com"))

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): Result<User> =
        login(User(id = "uid-$email", email = email, displayName = displayName, providerId = "password"))

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateProfile(displayName: String?, photoUrl: String?): Result<Unit> {
        val u = userState.value ?: return Result.failure(IllegalStateException("no user"))
        userState.value = u.copy(displayName = displayName ?: u.displayName, photoUrl = photoUrl ?: u.photoUrl)
        return Result.success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteAccount(password: String?): Result<Unit> {
        userState.value = null
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        userState.value = null
    }

    override suspend fun sendEmailVerification(): Result<Unit> = Result.success(Unit)

    override suspend fun getIdToken(forceRefresh: Boolean): Result<String> =
        if (userState.value != null) Result.success(idToken)
        else Result.failure(IllegalStateException("not authenticated"))
}
